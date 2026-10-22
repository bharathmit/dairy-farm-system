package com.dairyfarm.equipment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.contact.Contact;
import com.dairyfarm.contact.ContactRepository;
import com.dairyfarm.contact.ContactResponse.Ref;
import com.dairyfarm.equipment.EquipmentViews.*;
import com.dairyfarm.feed.FeedViews.PaymentMethod;
import com.dairyfarm.finance.FinancePoster;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.security.CurrentUser;
import com.dairyfarm.stock.Item;
import com.dairyfarm.stock.ItemRepository;

@Service
public class EquipmentService {

    static final int DEFAULT_REMINDER_DAYS = 7;

    private final ItemRepository items;
    private final ContactRepository contacts;
    private final FinancePoster finance;
    private final JdbcClient jdbc;

    public EquipmentService(ItemRepository items, ContactRepository contacts, FinancePoster finance, JdbcClient jdbc) {
        this.items = items;
        this.contacts = contacts;
        this.finance = finance;
        this.jdbc = jdbc;
    }

    record Last(LocalDate date, LocalDate next) {
    }

    @Transactional(readOnly = true)
    public EquipmentList list(Long farmId) {
        List<Item> all = items.findByFarmIdAndItemTypeOrderByNameAsc(farmId, Item.Type.EQUIPMENT).stream()
                .filter(Item::isActive).toList();
        Map<Long, Last> last = lastServices(farmId);
        List<Equipment> views = all.stream().map(i -> view(i, last.get(i.getId())))
                .sorted(Comparator.comparing((Equipment e) -> e.status().ordinal())
                        .thenComparing(e -> e.nextService() == null ? LocalDate.MAX : e.nextService()))
                .toList();
        int overdue = (int) views.stream().filter(e -> e.status() == ServiceStatus.OVERDUE).count();
        int soon = (int) views.stream().filter(e -> e.status() == ServiceStatus.DUE_SOON).count();
        return new EquipmentList(new Counts(views.size(), overdue, soon), views);
    }

    @Transactional(readOnly = true)
    public Equipment get(Long farmId, Long id) {
        Item i = load(farmId, id);
        return view(i, lastServices(farmId).get(i.getId()));
    }

    @Transactional
    public Equipment create(Long farmId, EquipmentRequest req) {
        Item i = new Item(farmId, Item.Type.EQUIPMENT, Item.Unit.NOS);
        apply(i, req);
        items.saveAndFlush(i);
        return get(farmId, i.getId());
    }

    @Transactional
    public Equipment update(Long farmId, Long id, EquipmentRequest req) {
        Item i = load(farmId, id);
        apply(i, req);
        items.flush();
        return get(farmId, id);
    }

    @Transactional
    public void delete(Long farmId, Long id) {
        Item i = load(farmId, id);
        long services = jdbc.sql("select count(*) from item_txn where item_id = :id").param("id", id)
                .query(Long.class).single();
        if (services > 0) {
            throw ApiException.conflict("This equipment has service records, kept for history.");
        }
        items.delete(i);
    }

    @Transactional
    public Equipment addService(Long farmId, Long id, ServiceRequest req) {
        Item i = load(farmId, id);
        DailyDates.notFuture(req.date());
        Long mechanicId = null;
        String mechanicName = blank(req.mechanicName());
        if (req.mechanicContactId() != null) {
            Contact c = contacts.findByIdAndFarmId(req.mechanicContactId(), farmId)
                    .orElseThrow(() -> ApiException.badRequest("That contact isn't on this farm."));
            if (!c.hasRole(Contact.Role.SUPPLIER) && !c.hasRole(Contact.Role.OTHER)) {
                throw ApiException.badRequest(c.getName() + " isn't saved as a supplier or other contact. Edit the contact to add it.");
            }
            mechanicId = c.getId();
        }
        Integer every = i.getDetails().serviceEveryDays();
        LocalDate next = req.nextServiceDate() != null ? req.nextServiceDate()
                : every == null ? null : req.date().plusDays(every);
        if (next != null && !next.isAfter(req.date())) {
            throw ApiException.badRequest("The next service must be after this one.");
        }
        jdbc.sql("""
                insert into item_txn (farm_id, item_id, txn_type, txn_date, quantity, cost, supplier_contact_id,
                  payment_method, paid, work_done, mechanic_name, next_service_date, source_type, notes, created_by)
                values (:farm, :item, 'SERVICE', :date, 1, :cost, :mech, :pm, :paid, :work, :mname, :next, 'SERVICE', :notes, :u)""")
                .param("farm", farmId).param("item", i.getId()).param("date", req.date())
                .param("cost", req.cost() == null || req.cost().signum() == 0 ? null : req.cost())
                .param("mech", mechanicId).param("pm", req.paymentMethod() == null ? null : req.paymentMethod().name())
                .param("paid", req.paid() == null ? Boolean.TRUE : req.paid())
                .param("work", (req.workDone() == null ? WorkDone.ROUTINE : req.workDone()).name())
                .param("mname", mechanicName).param("next", next).param("notes", blank(req.notes()))
                .param("u", CurrentUser.idOrNull()).update();
        finance.sync(farmId);
        return get(farmId, id);
    }

    @Transactional(readOnly = true)
    public List<EquipmentViews.Service> services(Long farmId, Long id) {
        load(farmId, id);
        return jdbc.sql("""
                select t.id, t.txn_date, t.work_done, t.cost, t.payment_method, t.paid, t.supplier_contact_id,
                       c.name as contact_name, t.mechanic_name, t.notes, t.next_service_date
                from item_txn t left join contact c on c.id = t.supplier_contact_id
                where t.item_id = :id and t.txn_type = 'SERVICE' order by t.txn_date desc, t.id desc""")
                .param("id", id)
                .query((rs, n) -> {
                    String w = rs.getString("work_done");
                    String pm = rs.getString("payment_method");
                    Long cid = rs.getObject("supplier_contact_id", Long.class);
                    return new EquipmentViews.Service(rs.getLong("id"), rs.getObject("txn_date", LocalDate.class),
                            w == null ? null : WorkDone.valueOf(w), rs.getBigDecimal("cost"),
                            pm == null ? null : PaymentMethod.valueOf(pm), rs.getObject("paid", Boolean.class),
                            cid == null ? null : new Ref(cid, rs.getString("contact_name")), rs.getString("mechanic_name"),
                            rs.getString("notes"), rs.getObject("next_service_date", LocalDate.class));
                }).list();
    }

    private Map<Long, Last> lastServices(Long farmId) {
        Map<Long, Last> out = new HashMap<>();
        jdbc.sql("""
                select distinct on (item_id) item_id, txn_date, next_service_date from item_txn
                where farm_id = :farm and txn_type = 'SERVICE' order by item_id, txn_date desc, id desc""")
                .param("farm", farmId)
                .query((rs, n) -> out.put(rs.getLong("item_id"), new Last(rs.getObject("txn_date", LocalDate.class),
                        rs.getObject("next_service_date", LocalDate.class)))).list();
        return out;
    }

    static Equipment view(Item i, Last last) {
        var d = i.getDetails();
        Integer every = d.serviceEveryDays();
        LocalDate next = null;
        if (last != null) {
            next = last.next() != null ? last.next() : every == null ? null : last.date().plusDays(every);
        } else if (d.purchaseDate() != null && every != null) {
            next = d.purchaseDate().plusDays(every);
        }
        int reminder = d.reminderDays() == null ? DEFAULT_REMINDER_DAYS : d.reminderDays();
        Long daysTo = next == null ? null : ChronoUnit.DAYS.between(DailyDates.today(), next);
        ServiceStatus status = daysTo == null ? ServiceStatus.OK
                : daysTo < 0 ? ServiceStatus.OVERDUE : daysTo <= reminder ? ServiceStatus.DUE_SOON : ServiceStatus.OK;
        return new Equipment(i.getId(), i.getName(), d.brand(), d.model(), every, d.serviceTask(), reminder,
                last == null ? null : last.date(), next, status, daysTo, d.purchaseDate(), d.purchaseCost(), i.getNotes());
    }

    private void apply(Item i, EquipmentRequest req) {
        i.setName(req.name().trim());
        i.setDetails(Item.Details.equipment(blank(req.brand()), blank(req.model()), req.serviceEveryDays(),
                blank(req.serviceTask()), req.reminderDays(), req.purchaseDate(), req.purchaseCost()));
        i.setNotes(blank(req.notes()));
    }

    private Item load(Long farmId, Long id) {
        return items.findByIdAndFarmIdAndItemType(id, farmId, Item.Type.EQUIPMENT)
                .orElseThrow(() -> ApiException.notFound("Equipment not found."));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
