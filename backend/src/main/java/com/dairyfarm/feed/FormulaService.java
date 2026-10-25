package com.dairyfarm.feed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.feed.FeedPlanner.Catalog;
import com.dairyfarm.feed.FeedRepository.FormulaRow;
import com.dairyfarm.feed.FeedRepository.LineRow;
import com.dairyfarm.feed.FeedRepository.NewTxn;
import com.dairyfarm.feed.FeedViews.BatchPreview;
import com.dairyfarm.feed.FeedViews.BatchRequest;
import com.dairyfarm.feed.FeedViews.BatchSaved;
import com.dairyfarm.feed.FeedViews.Formula;
import com.dairyfarm.feed.FeedViews.FormulaLine;
import com.dairyfarm.feed.FeedViews.FormulaLineRequest;
import com.dairyfarm.feed.FeedViews.FormulaRequest;
import com.dairyfarm.feed.FeedViews.Nutrient;
import com.dairyfarm.feed.FeedViews.PreviewLine;
import com.dairyfarm.feed.FeedViews.Targets;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.stock.Item;

/** Concentrate formulas: cost and nutrients per kg of mix, and preparing batches from stock. */
@Service
public class FormulaService {

    private static final String[] NUTRIENTS = {"DM", "CP", "TDN", "Ca", "P"};

    private final FeedRepository repo;
    private final FeedPlanner planner;

    public FormulaService(FeedRepository repo, FeedPlanner planner) {
        this.repo = repo;
        this.planner = planner;
    }

    @Transactional(readOnly = true)
    public List<Formula> list(Long farmId, boolean showMoney) {
        Catalog c = planner.catalog(farmId);
        var rows = repo.formulas(farmId);
        var lines = repo.lines(rows.stream().map(FormulaRow::id).toList());
        return rows.stream().map(f -> view(c, f, lines.stream().filter(l -> l.formulaId().equals(f.id())).toList(), showMoney))
                .toList();
    }

    @Transactional(readOnly = true)
    public Formula get(Long farmId, Long id, boolean showMoney) {
        FormulaRow f = load(farmId, id);
        return view(planner.catalog(farmId), f, repo.lines(List.of(id)), showMoney);
    }

    @Transactional
    public Formula save(Long farmId, Long id, FormulaRequest req) {
        if (id != null) {
            load(farmId, id);
        }
        String name = req.name().trim();
        if (repo.formulaNameTaken(farmId, name, id)) {
            throw ApiException.conflict("A formula named \"" + name + "\" already exists.");
        }
        Catalog c = planner.catalog(farmId);
        Item mix = c.entities().get(req.mixItemId());
        if (mix == null || !"MIX".equals(mix.getCategory())) {
            throw ApiException.badRequest("The formula must make a feed item of category Mix.");
        }
        List<LineRow> lines = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<FormulaLineRequest> ordered = new ArrayList<>(req.lines());
        ordered.sort(java.util.Comparator.comparing((FormulaLineRequest l) -> l.sortOrder() == null ? Integer.MAX_VALUE : l.sortOrder()));
        for (FormulaLineRequest l : ordered) {
            Item i = c.entities().get(l.itemId());
            if (i == null) {
                throw ApiException.badRequest("That ingredient isn't a feed item on this farm.");
            }
            if (i.getId().equals(mix.getId())) {
                throw ApiException.badRequest("A mix can't be an ingredient of itself.");
            }
            if (i.getDetails().isExcluded()) {
                String why = i.getDetails().excludedReason();
                throw ApiException.badRequest(i.getName() + " can't be added. Its max inclusion is 0 % (excluded)"
                        + (why == null ? "." : " — " + why + "."));
            }
            if (!seen.add(i.getId())) {
                throw ApiException.badRequest(i.getName() + " is listed twice.");
            }
            lines.add(new LineRow(null, i.getId(), l.kg()));
        }
        Long fid = repo.saveFormula(farmId, id, name, mix.getId(), req.batchKg(), req.targets(), FeedService.blank(req.notes()),
                req.active() == null || req.active(), lines);
        return get(farmId, fid, true);
    }

    @Transactional
    public void delete(Long farmId, Long id) {
        load(farmId, id);
        repo.deleteFormula(farmId, id);
    }

    @Transactional(readOnly = true)
    public BatchPreview preview(Long farmId, Long id, BigDecimal batchKg, boolean showMoney) {
        FormulaRow f = load(farmId, id);
        Catalog c = planner.catalog(farmId);
        return preview(farmId, c, f, repo.lines(List.of(id)), batchKg == null ? f.batchKg() : batchKg, showMoney);
    }

    @Transactional
    public BatchSaved prepare(Long farmId, Long id, BatchRequest req, Long callerMemberId, boolean showMoney) {
        DailyDates.notFuture(req.date());
        FormulaRow f = load(farmId, id);
        Catalog c = planner.catalog(farmId);
        List<LineRow> lines = repo.lines(List.of(id));
        BatchPreview p = preview(farmId, c, f, lines, req.batchKg(), true);
        if (p.shortCount() > 0 && !Boolean.TRUE.equals(req.allowShort())) {
            var first = p.lines().stream().filter(l -> l.shortKg().signum() > 0).findFirst().orElseThrow();
            throw ApiException.badRequest("Not enough stock: " + first.item().name() + " is short by "
                    + FeedService.plain(first.shortKg()) + " kg" + (p.shortCount() > 1 ? " (and " + (p.shortCount() - 1) + " more)" : "")
                    + ". Buy first, make a smaller batch, or confirm with the stock you have.");
        }
        for (Item i : List.of(c.entities().get(f.mixItemId()))) {
            if (i.getDetails().isExcluded()) {
                throw ApiException.badRequest(i.getName() + " is marked Excluded.");
            }
        }
        Long byId = req.preparedByMemberId() != null ? req.preparedByMemberId() : callerMemberId;
        String by = byId == null ? null : repo.memberName(farmId, byId)
                .orElseThrow(() -> ApiException.badRequest("That person isn't on this farm's team."));
        String note = "Batch of " + f.name() + (by == null ? "" : " · prepared by " + by);
        UUID batch = UUID.randomUUID();
        for (PreviewLine l : p.lines()) {
            Item i = c.entities().get(l.item().id());
            BigDecimal cost = i.getCostPerUnit() == null ? null : l.needKg().multiply(i.getCostPerUnit()).setScale(2, RoundingMode.HALF_UP);
            repo.insert(farmId, new NewTxn(i.getId(), "USAGE", req.date(), null, l.needKg(), cost, null, null, null, null, null,
                    null, "FORMULA_BATCH", id, batch, note));
        }
        Item mix = c.entities().get(f.mixItemId());
        repo.insert(farmId, new NewTxn(mix.getId(), "PRODUCE", req.date(), null, req.batchKg(), p.batchCost(), null, null, null,
                null, null, null, "FORMULA_BATCH", id, batch, note));
        if (p.costPerKg() != null) {
            mix.setCostPerUnit(p.costPerKg());
        }
        Catalog after = planner.catalog(farmId);
        var herd = planner.herd(farmId, after, DailyDates.today(), null, null);
        var mixStock = planner.stockItem(after, after.entities().get(mix.getId()),
                herd.totalKg().getOrDefault(mix.getId(), BigDecimal.ZERO), showMoney);
        return new BatchSaved(showMoney ? p : hideMoney(p), mixStock);
    }

    private BatchPreview preview(Long farmId, Catalog c, FormulaRow f, List<LineRow> lines, BigDecimal batchKg, boolean showMoney) {
        if (batchKg.signum() <= 0) {
            throw ApiException.badRequest("Batch must be at least 1 kg.");
        }
        BigDecimal total = lines.stream().map(LineRow::kg).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) {
            throw ApiException.badRequest("This formula has no ingredients.");
        }
        BigDecimal scale = batchKg.divide(total, 8, RoundingMode.HALF_UP);
        List<PreviewLine> out = new ArrayList<>();
        int shortCount = 0;
        BigDecimal cost = BigDecimal.ZERO;
        boolean costKnown = true;
        for (LineRow l : lines) {
            Item i = c.entities().get(l.itemId());
            BigDecimal need = l.kg().multiply(scale).setScale(2, RoundingMode.HALF_UP);
            BigDecimal stock = c.stock(l.itemId());
            BigDecimal shortKg = need.subtract(stock.max(BigDecimal.ZERO)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            if (shortKg.signum() > 0) {
                shortCount++;
            }
            if (i.getCostPerUnit() == null) {
                costKnown = false;
            } else {
                cost = cost.add(need.multiply(i.getCostPerUnit()));
            }
            out.add(new PreviewLine(c.ref(l.itemId()), need, stock, shortKg));
        }
        BigDecimal batchCost = costKnown ? cost.setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal perKg = batchCost == null ? null : batchCost.divide(batchKg, 2, RoundingMode.HALF_UP);
        BigDecimal before = c.stock(f.mixItemId());
        BigDecimal after = before.add(batchKg).setScale(2, RoundingMode.HALF_UP);
        var herd = planner.herd(farmId, c, DailyDates.today(), null, null);
        BigDecimal mixPerDay = herd.totalKg().getOrDefault(f.mixItemId(), BigDecimal.ZERO);
        BigDecimal extra = mixPerDay.signum() > 0 ? batchKg.divide(mixPerDay, 1, RoundingMode.HALF_UP) : null;
        BatchPreview p = new BatchPreview(f.id(), batchKg, out, shortCount, batchCost, perKg, before, after, extra);
        return showMoney ? p : hideMoney(p);
    }

    private static BatchPreview hideMoney(BatchPreview p) {
        return new BatchPreview(p.formulaId(), p.batchKg(), p.lines(), p.shortCount(), null, null, p.mixStockBefore(),
                p.mixStockAfter(), p.extraDays());
    }

    private Formula view(Catalog c, FormulaRow f, List<LineRow> lines, boolean showMoney) {
        BigDecimal total = lines.stream().map(LineRow::kg).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<FormulaLine> out = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        BigDecimal cost = BigDecimal.ZERO;
        boolean costKnown = !lines.isEmpty();
        double[] sums = new double[5];
        boolean[] known = {true, true, true, true, true};
        double[] bisSums = new double[4];                 // fat, fibre, acid insoluble ash, aflatoxin B1
        boolean[] bisKnown = {true, true, true, true};
        for (LineRow l : lines) {
            Item i = c.entities().get(l.itemId());
            var d = i.getDetails();
            BigDecimal pct = total.signum() == 0 ? BigDecimal.ZERO
                    : l.kg().multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
            BigDecimal max = d.maxInclusionPct();
            boolean over = max != null && pct.compareTo(max) > 0;
            if (over) {
                warnings.add(i.getName() + " " + pct.stripTrailingZeros().toPlainString() + " % is above its max inclusion "
                        + max.stripTrailingZeros().toPlainString() + " %.");
            }
            if (d.isExcluded()) {
                warnings.add(i.getName() + " is now marked Excluded — remove it from the formula.");
            }
            BigDecimal lineCost = i.getCostPerUnit() == null ? null : l.kg().multiply(i.getCostPerUnit()).setScale(2, RoundingMode.HALF_UP);
            if (lineCost == null) {
                costKnown = false;
            } else {
                cost = cost.add(lineCost);
            }
            BigDecimal[] v = {d.dmPct(), d.cpPct(), d.tdnPct(), d.caPct(), d.pPct()};
            for (int k = 0; k < 5; k++) {
                if (v[k] == null) {
                    known[k] = false;
                } else {
                    sums[k] += l.kg().doubleValue() * v[k].doubleValue();
                }
            }
            BigDecimal[] b = {d.fatPct(), d.fibrePct(), d.aiaPct(), d.aflatoxinPpb()};
            for (int k = 0; k < 4; k++) {
                if (b[k] == null) {
                    bisKnown[k] = false;
                } else {
                    bisSums[k] += l.kg().doubleValue() * b[k].doubleValue();
                }
            }
            out.add(new FormulaLine(c.ref(i.getId()), l.kg(), pct, showMoney ? lineCost : null, over, max));
        }
        if (total.compareTo(f.batchKg()) != 0) {
            warnings.add("Ingredients add up to " + FeedService.plain(total) + " kg, not the " + FeedService.plain(f.batchKg())
                    + " kg batch.");
        }
        Targets t = f.targets() == null ? Targets.EMPTY : f.targets();
        BigDecimal[] targets = {t.dmPct(), t.cpPct(), t.tdnPct(), t.caPct(), t.pPct()};
        List<Nutrient> nutrients = new ArrayList<>();
        for (int k = 0; k < 5; k++) {
            BigDecimal mixPct = known[k] && total.signum() > 0
                    ? BigDecimal.valueOf(sums[k] / total.doubleValue()).setScale(2, RoundingMode.HALF_UP) : null;
            String status = "UNKNOWN";
            if (mixPct != null && targets[k] != null && targets[k].signum() > 0) {
                double r = mixPct.doubleValue() / targets[k].doubleValue();
                status = r < 0.9 ? "LOW" : r > 1.1 ? "HIGH" : "OK";
            } else if (mixPct != null && targets[k] == null) {
                status = "OK";
            }
            nutrients.add(new Nutrient(NUTRIENTS[k], mixPct, targets[k], status));
        }
        BisGrade.Check bis = null;
        if (t.bisGrade() != null) {
            double tot = total.doubleValue();
            bis = t.bisGrade().check(new BisGrade.Mix(avg(known[0], sums[0], tot), avg(known[1], sums[1], tot),
                    avg(bisKnown[0], bisSums[0], tot), avg(bisKnown[1], bisSums[1], tot), avg(bisKnown[2], bisSums[2], tot),
                    avg(known[3], sums[3], tot), avg(known[4], sums[4], tot), avg(bisKnown[3], bisSums[3], tot)));
        }
        BigDecimal batchCost = costKnown ? cost.setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal perKg = batchCost == null || total.signum() == 0 ? null : batchCost.divide(total, 2, RoundingMode.HALF_UP);
        if (perKg != null && showMoney && ((t.costMin() != null && perKg.compareTo(t.costMin()) < 0)
                || (t.costMax() != null && perKg.compareTo(t.costMax()) > 0))) {
            warnings.add("Cost ₹" + perKg + "/kg is outside the target range.");
        }
        Targets shown = showMoney ? t : new Targets(t.dmPct(), t.cpPct(), t.tdnPct(), t.caPct(), t.pPct(), null, null, t.bisGrade());
        return new Formula(f.id(), f.name(), c.ref(f.mixItemId()), f.batchKg(), total.setScale(2, RoundingMode.HALF_UP),
                showMoney ? perKg : null, showMoney ? batchCost : null, shown, out, nutrients, bis, warnings, f.notes(), f.active());
    }

    /** Weighted average per kg of mix, or null when an ingredient has no value (or the mix is empty). */
    private static Double avg(boolean known, double sum, double totalKg) {
        return known && totalKg > 0 ? sum / totalKg : null;
    }

    private FormulaRow load(Long farmId, Long id) {
        return repo.formula(farmId, id).orElseThrow(() -> ApiException.notFound("Formula not found."));
    }
}
