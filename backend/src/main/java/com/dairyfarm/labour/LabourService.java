package com.dairyfarm.labour;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.farm.FarmRepository;
import com.dairyfarm.finance.Dates;
import com.dairyfarm.finance.FinanceRepository;
import com.dairyfarm.finance.FinanceService;
import com.dairyfarm.finance.FinanceViews.AllocKind;
import com.dairyfarm.finance.FinanceViews.Category;
import com.dairyfarm.finance.FinanceViews.Direction;
import com.dairyfarm.finance.FinanceViews.Entry;
import com.dairyfarm.finance.FinanceViews.MemberRef;
import com.dairyfarm.finance.FinanceViews.Status;
import com.dairyfarm.labour.LabourViews.*;
import com.dairyfarm.member.FarmMember;
import com.dairyfarm.member.FarmMemberRepository;
import com.dairyfarm.member.WageType;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.security.CurrentUser;

@Service
public class LabourService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

    private final FarmMemberRepository members;
    private final FarmRepository farms;
    private final FinanceRepository financeRepo;
    private final FinanceService finance;
    private final JdbcClient jdbc;

    public LabourService(FarmMemberRepository members, FarmRepository farms, FinanceRepository financeRepo,
                         FinanceService finance, JdbcClient jdbc) {
        this.members = members;
        this.farms = farms;
        this.financeRepo = financeRepo;
        this.finance = finance;
        this.jdbc = jdbc;
    }

    record Mark(DayStatus status, BigDecimal hours) {
    }

    // ---- workers

    @Transactional(readOnly = true)
    public Workers workers(Long farmId, LocalDate weekOf) {
        Week week = week(weekOf);
        LocalDate today = DailyDates.today();
        List<Worker> out = new ArrayList<>();
        BigDecimal dueTotal = ZERO;
        for (FarmMember m : workerList(farmId)) {
            Map<LocalDate, Mark> marks = marks(m.getId(), week.from(), week.to());
            BigDecimal present = ZERO;
            BigDecimal hours = ZERO;
            for (Mark k : marks.values()) {
                present = present.add(units(k.status()));
                hours = hours.add(k.hours() == null ? ZERO : k.hours());
            }
            WageDue due = due(farmId, m, null, today);
            dueTotal = dueTotal.add(due.amountDue());
            out.add(new Worker(ref(m), m.getWageType(), m.getWageRate(), m.getUserId() != null,
                    new WeekStats(present, hours), due.amountDue(), pendingNote(farmId, m, today), lastPaid(m.getId())));
        }
        LocalDate monthStart = today.withDayOfMonth(1);
        BigDecimal paidMonth = jdbc.sql("""
                select coalesce(sum(amount), 0) from finance_entry where farm_id = :farm and category = 'LABOUR'
                  and status = 'PAID' and entry_date between :from and :to""")
                .param("farm", farmId).param("from", monthStart).param("to", today).query(BigDecimal.class).single();
        return new Workers(week, out, new WorkerTotals(out.size(), dueTotal, paidMonth));
    }

    // ---- attendance

    @Transactional(readOnly = true)
    public AttendanceWeek attendance(Long farmId, LocalDate weekOf) {
        Week week = week(weekOf);
        List<AttendanceRow> rows = new ArrayList<>();
        BigDecimal tDays = ZERO;
        BigDecimal tHours = ZERO;
        BigDecimal tAmount = ZERO;
        for (FarmMember m : workerList(farmId)) {
            Map<LocalDate, Mark> marks = marks(m.getId(), week.from(), week.to());
            List<Day> days = new ArrayList<>();
            BigDecimal present = ZERO;
            BigDecimal hours = ZERO;
            BigDecimal amount = ZERO;
            for (LocalDate d = week.from(); !d.isAfter(week.to()); d = d.plusDays(1)) {
                Mark k = marks.get(d);
                days.add(new Day(d, k == null ? null : k.status(), k == null ? null : k.hours()));
                if (k != null) {
                    present = present.add(units(k.status()));
                    hours = hours.add(k.hours() == null ? ZERO : k.hours());
                    amount = amount.add(units(k.status()).multiply(dailyRate(m, d)));
                }
            }
            amount = money(amount);
            rows.add(new AttendanceRow(ref(m), m.getWageType(), m.getWageRate(), days, present, hours, amount));
            tDays = tDays.add(present);
            tHours = tHours.add(hours);
            tAmount = tAmount.add(amount);
        }
        return new AttendanceWeek(week, rows, new AttendanceTotal(tDays, tHours, tAmount));
    }

    @Transactional
    public AttendanceWeek saveAttendance(Long farmId, AttendanceRequest req) {
        LocalDate first = null;
        for (AttendanceEntry e : req.entries()) {
            FarmMember m = member(farmId, e.memberId());
            // The week screen sends every day; a blank future day is simply nothing to save.
            if (e.status() != null) {
                DailyDates.notFuture(e.date());
            }
            if (e.status() == null) {
                jdbc.sql("delete from attendance where member_id = :m and work_date = :d")
                        .param("m", m.getId()).param("d", e.date()).update();
            } else {
                jdbc.sql("""
                        insert into attendance (farm_id, member_id, work_date, status, hours, created_by)
                        values (:farm, :m, :d, :s, :h, :u)
                        on conflict (member_id, work_date) do update set status = excluded.status,
                          hours = excluded.hours, updated_at = now()""")
                        .param("farm", farmId).param("m", m.getId()).param("d", e.date()).param("s", e.status().name())
                        .param("h", e.hours()).param("u", CurrentUser.idOrNull()).update();
            }
            if (first == null || e.date().isBefore(first)) {
                first = e.date();
            }
        }
        return attendance(farmId, first);
    }

    // ---- wages

    @Transactional(readOnly = true)
    public WageDue due(Long farmId, Long memberId, LocalDate from, LocalDate to) {
        return due(farmId, member(farmId, memberId), from, to);
    }

    WageDue due(Long farmId, FarmMember m, LocalDate from, LocalDate to) {
        if (m.getWageType() == null || m.getWageType() == WageType.NONE || m.getWageRate() == null) {
            throw ApiException.badRequest(m.getName() + " has no wage set. Set a daily or monthly wage on the team page.");
        }
        LocalDate today = DailyDates.today();
        if (from == null) {
            from = defaultFrom(m, today);
        }
        if (to == null) {
            to = m.getWageType() == WageType.MONTHLY ? YearMonth.from(from).atEndOfMonth() : today;
        }
        BigDecimal present = ZERO;
        BigDecimal half = ZERO;
        BigDecimal absent = ZERO;
        BigDecimal gross = ZERO;
        String explain;
        Map<LocalDate, Mark> marks = to.isBefore(from) ? Map.of() : marks(m.getId(), from, to);
        for (Mark k : marks.values()) {
            switch (k.status()) {
                case PRESENT -> present = present.add(BigDecimal.ONE);
                case HALF -> half = half.add(BigDecimal.ONE);
                case ABSENT -> absent = absent.add(BigDecimal.ONE);
            }
        }
        BigDecimal rate = m.getWageRate();
        if (to.isBefore(from)) {
            explain = "Nothing due yet.";
        } else if (m.getWageType() == WageType.DAILY) {
            BigDecimal units = present.add(half.multiply(HALF));
            gross = units.multiply(rate);
            explain = units.stripTrailingZeros().toPlainString() + " days × ₹" + plain(rate);
        } else {
            int leave = farms.findById(farmId).map(f -> f.getSettings().leaveDaysPerMonth()).orElse(4);
            Map<YearMonth, BigDecimal> absentByMonth = new HashMap<>();
            for (var e : marks.entrySet()) {
                BigDecimal u = switch (e.getValue().status()) {
                    case ABSENT -> BigDecimal.ONE;
                    case HALF -> HALF;
                    default -> ZERO;
                };
                absentByMonth.merge(YearMonth.from(e.getKey()), u, BigDecimal::add);
            }
            long days = 0;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                gross = gross.add(dailyRate(m, d));
                days++;
            }
            BigDecimal deduction = ZERO;
            for (var e : absentByMonth.entrySet()) {
                BigDecimal extra = e.getValue().subtract(BigDecimal.valueOf(leave));
                if (extra.signum() > 0) {
                    deduction = deduction.add(extra.multiply(rate.divide(BigDecimal.valueOf(e.getKey().lengthOfMonth()), 4,
                            RoundingMode.HALF_UP)));
                }
            }
            gross = gross.subtract(deduction);
            explain = "₹" + plain(rate) + " a month × " + days + " days"
                    + (deduction.signum() > 0 ? " − ₹" + plain(money(deduction)) + " for absences beyond "
                    + leave + " a month" : "");
        }
        gross = money(gross.max(ZERO));
        BigDecimal advances = outstandingAdvances(m.getId());
        BigDecimal dueAmount = gross.subtract(advances).max(ZERO);
        if (advances.signum() > 0) {
            explain += " − advance ₹" + plain(advances);
        }
        return new WageDue(ref(m), from, to, present, half, absent, rate, gross, advances, dueAmount, explain);
    }

    @Transactional
    public Entry pay(Long farmId, PaymentRequest req) {
        FarmMember m = member(farmId, req.memberId());
        boolean advance = Boolean.TRUE.equals(req.advance());
        LocalDate paidOn = req.paidOn() == null ? DailyDates.today() : req.paidOn();
        DailyDates.notFuture(paidOn);
        BigDecimal deducted = null;
        String notes = req.notes() == null || req.notes().isBlank() ? null : req.notes().trim();
        if (!advance) {
            if (req.periodFrom() == null || req.periodTo() == null) {
                throw ApiException.badRequest("Enter the period this payment covers.");
            }
            if (req.periodTo().isBefore(req.periodFrom())) {
                throw ApiException.badRequest("The period ends before it starts.");
            }
            WageDue due = due(farmId, m, req.periodFrom(), req.periodTo());
            deducted = due.advances().min(due.gross());
        }
        Long id = financeRepo.insert(farmId, new FinanceRepository.Write(Direction.EXPENSE, Category.LABOUR,
                req.amount().setScale(2, RoundingMode.HALF_UP), paidOn, null, Status.PAID, req.paymentMethod(),
                m.getName(), null, m.getId(), advance ? null : req.periodFrom(), advance ? null : req.periodTo(),
                advance, deducted, AllocKind.FARM, null, null, null, null, notes), CurrentUser.idOrNull());
        return finance.get(farmId, id);
    }

    // ---- helpers

    private LocalDate defaultFrom(FarmMember m, LocalDate today) {
        LocalDate lastTo = jdbc.sql("""
                select max(period_to) from finance_entry where member_id = :m and category = 'LABOUR' and not advance""")
                .param("m", m.getId()).query(LocalDate.class).optional().orElse(null);
        if (lastTo != null) {
            return lastTo.plusDays(1);
        }
        LocalDate firstMark = jdbc.sql("select min(work_date) from attendance where member_id = :m")
                .param("m", m.getId()).query(LocalDate.class).optional().orElse(null);
        // Days marked before the "joined on" date are still work to pay (e.g. a worker added to the app late).
        LocalDate start = m.getJoinedOn();
        if (firstMark != null && (start == null || firstMark.isBefore(start))) {
            start = firstMark;
        }
        if (m.getWageType() == WageType.MONTHLY) {
            LocalDate monthStart = today.withDayOfMonth(1);
            return start == null || start.isBefore(monthStart) ? monthStart : start;
        }
        // Daily wages: start at the first marked day, not before.
        if (firstMark != null) {
            start = firstMark;
        }
        return start == null ? today.withDayOfMonth(1) : start;
    }

    private String pendingNote(Long farmId, FarmMember m, LocalDate today) {
        if (m.getWageRate() == null) {
            return null;
        }
        LocalDate lastTo = jdbc.sql("""
                select max(period_to) from finance_entry where member_id = :m and category = 'LABOUR' and not advance""")
                .param("m", m.getId()).query(LocalDate.class).optional().orElse(null);
        if (m.getWageType() == WageType.DAILY) {
            LocalDate weekStart = today.with(DayOfWeek.MONDAY).minusWeeks(1);
            LocalDate weekEnd = weekStart.plusDays(6);
            LocalDate from = lastTo == null ? weekStart : (lastTo.isBefore(weekStart) ? weekStart : lastTo.plusDays(1));
            if (from.isAfter(weekEnd)) {
                return null;
            }
            BigDecimal amount = due(farmId, m, from, weekEnd).gross();
            return amount.signum() > 0 ? "Last week " + Dates.range(from, weekEnd) + " unpaid · ₹" + plain(amount) : null;
        }
        YearMonth prev = YearMonth.from(today).minusMonths(1);
        LocalDate from = prev.atDay(1);
        if (lastTo != null && !lastTo.isBefore(prev.atEndOfMonth())) {
            return null;
        }
        if (lastTo != null && !lastTo.isBefore(from)) {
            from = lastTo.plusDays(1);
        }
        if (m.getJoinedOn() != null && m.getJoinedOn().isAfter(from)) {
            from = m.getJoinedOn();
        }
        if (from.isAfter(prev.atEndOfMonth())) {
            return null;
        }
        BigDecimal amount = due(farmId, m, from, prev.atEndOfMonth()).gross();
        return amount.signum() > 0 ? MONTH.format(prev) + " salary unpaid · ₹" + plain(amount) : null;
    }

    private LastPaid lastPaid(Long memberId) {
        return jdbc.sql("""
                select period_to, amount from finance_entry where member_id = :m and category = 'LABOUR' and not advance
                order by period_to desc nulls last, id desc limit 1""")
                .param("m", memberId).query((rs, n) -> new LastPaid(rs.getObject("period_to", LocalDate.class),
                        rs.getBigDecimal("amount"))).optional().orElse(null);
    }

    private BigDecimal outstandingAdvances(Long memberId) {
        return jdbc.sql("""
                select coalesce(sum(case when advance then amount else -coalesce(advance_deducted, 0) end), 0)
                from finance_entry where member_id = :m and category = 'LABOUR'""")
                .param("m", memberId).query(BigDecimal.class).single().max(ZERO);
    }

    private Map<LocalDate, Mark> marks(Long memberId, LocalDate from, LocalDate to) {
        Map<LocalDate, Mark> out = new HashMap<>();
        jdbc.sql("select work_date, status, hours from attendance where member_id = :m and work_date between :f and :t")
                .param("m", memberId).param("f", from).param("t", to)
                .query((rs, n) -> out.put(rs.getObject("work_date", LocalDate.class),
                        new Mark(DayStatus.valueOf(rs.getString("status")), rs.getBigDecimal("hours")))).list();
        return out;
    }

    private static BigDecimal dailyRate(FarmMember m, LocalDate d) {
        if (m.getWageRate() == null) {
            return ZERO;
        }
        return m.getWageType() == WageType.MONTHLY
                ? m.getWageRate().divide(BigDecimal.valueOf(d.lengthOfMonth()), 4, RoundingMode.HALF_UP)
                : m.getWageRate();
    }

    private static BigDecimal units(DayStatus s) {
        return switch (s) {
            case PRESENT -> BigDecimal.ONE;
            case HALF -> HALF;
            case ABSENT -> ZERO;
        };
    }

    private List<FarmMember> workerList(Long farmId) {
        return members.findByFarmIdOrderByActiveDescRoleAscNameAsc(farmId).stream()
                .filter(FarmMember::isActive)
                .filter(m -> m.getWageType() == WageType.DAILY || m.getWageType() == WageType.MONTHLY)
                .toList();
    }

    private FarmMember member(Long farmId, Long id) {
        return members.findById(id).filter(m -> m.getFarmId().equals(farmId))
                .orElseThrow(() -> ApiException.badRequest("That team member isn't on this farm."));
    }

    static Week week(LocalDate weekOf) {
        LocalDate d = weekOf == null ? DailyDates.today() : weekOf;
        LocalDate monday = d.with(DayOfWeek.MONDAY);
        return new Week(monday, monday.plusDays(6));
    }

    private static MemberRef ref(FarmMember m) {
        return new MemberRef(m.getId(), m.getName());
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal v) {
        return String.format(Locale.ENGLISH, "%,.0f", v.setScale(0, RoundingMode.HALF_UP));
    }
}
