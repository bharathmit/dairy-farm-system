package com.dairyfarm.feed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.dairyfarm.feed.FeedViews.Category;
import com.dairyfarm.feed.FeedViews.RationCategory;
import com.dairyfarm.feed.FeedViews.Section;
import com.dairyfarm.feed.RationParams.Limit;
import com.dairyfarm.feed.RationParams.Share;
import com.dairyfarm.feed.RationParams.Stage;

/**
 * One animal's daily ration from its category rule (docs/api-feed.md, "Ration rule"). Pure calculation:
 * total = body weight × bwPct %; concentrate = base + milk × perLitre (capped); the rest split green / dry;
 * each part split over its items by share; a MIX concentrate is broken into ingredients by its formula.
 */
public final class RationCalc {

    private RationCalc() {
    }

    /** What the calculator needs to know about a feed item. */
    public record ItemInfo(Long id, String name, Category category, String unit, BigDecimal costPerUnit,
                           BigDecimal dmPct, BigDecimal cpPct, BigDecimal tdnPct, BigDecimal caPct, BigDecimal pPct,
                           boolean excluded, boolean active) {
    }

    public record AnimalInput(Long id, String tag, String name, RationCategory category, String breed,
                              BigDecimal bodyWeightKg, String weightSource, LocalDate weighedOn,
                              BigDecimal milkYesterday, LocalDate lastCalvingDate) {
    }

    /** A line of the plan; item null = a part with no item picked in the rule yet. */
    public record Line(Section section, Long itemId, BigDecimal kg, String timing) {
    }

    public record Ingredient(Long itemId, BigDecimal kgPerDay, BigDecimal limitKg, boolean over) {
    }

    public record NutrientLine(String name, BigDecimal ration, BigDecimal need, String unit, String status) {
    }

    public record Plan(AnimalInput animal, Integer daysInMilk, Stage stage, BigDecimal greenKg, BigDecimal dryKg,
                       BigDecimal concKg, BigDecimal totalKg, BigDecimal targetKg, boolean capped, String explain,
                       List<Line> lines, List<Ingredient> ingredients, BigDecimal costPerDay, BigDecimal concCostPerDay,
                       List<NutrientLine> nutrients, Map<Long, BigDecimal> kgByItem, Map<Long, BigDecimal> ingredientKg) {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param items         feed items by id
     * @param formulaShares MIX item id → ingredient share (fractions that sum to 1), from its formula
     * @param defaultConcId concentrate item when the rule names none (first MIX), or null
     */
    public static Plan plan(AnimalInput a, RationParams rule, LocalDate date, Map<Long, ItemInfo> items,
                            Map<Long, Map<Long, BigDecimal>> formulaShares, Long defaultConcId) {
        RationParams p = rule.resolved(a.category());
        double bw = num(a.bodyWeightKg());
        double milk = a.category() == RationCategory.MILKING ? num(a.milkYesterday()) : 0;
        double target = bw * num(p.bwPct()) / 100.0;
        double conc = num(p.concBaseKg()) + milk * num(p.concPerLitreKg());
        boolean capped = conc > num(p.maxConcKg());
        if (capped) {
            conc = num(p.maxConcKg());
        }
        double forage = Math.max(0, target - conc);
        double green = forage * num(p.greenSharePct()) / 100.0;
        double dry = forage - green;

        Integer dim = null;
        Stage stage = Stage.ANY;
        if (a.category() == RationCategory.MILKING && a.lastCalvingDate() != null && !date.isBefore(a.lastCalvingDate())) {
            dim = (int) ChronoUnit.DAYS.between(a.lastCalvingDate(), date) + 1;
            stage = dim <= 100 ? Stage.EARLY : dim <= 200 ? Stage.MID : Stage.LATE;
        }

        List<Line> lines = new ArrayList<>();
        Map<Long, BigDecimal> kgByItem = new LinkedHashMap<>();
        split(Section.GREEN, green, p.green(), "6 AM, before milking", items, lines, kgByItem);
        split(Section.DRY, dry, p.dry(), "after milking", items, lines, kgByItem);
        Long concId = p.concItemId() != null && items.containsKey(p.concItemId()) ? p.concItemId() : defaultConcId;
        if (conc > 0) {
            lines.add(new Line(Section.CONCENTRATE, concId, kg(conc), "after milking · AM + PM"));
            if (concId != null) {
                kgByItem.merge(concId, kg(conc), BigDecimal::add);
            }
        }

        // Concentrate ingredients from the mix's formula, with daily limits.
        Map<Long, BigDecimal> ingredientKg = new LinkedHashMap<>();
        Map<Long, BigDecimal> shares = concId == null ? null : formulaShares.get(concId);
        if (shares != null && conc > 0) {
            for (var e : shares.entrySet()) {
                ingredientKg.put(e.getKey(), kg(conc * e.getValue().doubleValue()));
            }
        }
        List<Ingredient> ingredients = new ArrayList<>();
        Map<Long, BigDecimal> limitFor = new LinkedHashMap<>();
        for (Limit l : p.limits()) {
            Stage s = l.stage() == null ? Stage.ANY : l.stage();
            if (s == Stage.ANY || s == stage) {
                limitFor.merge(l.itemId(), l.maxKgPerDay(), BigDecimal::min);
            }
        }
        for (var e : ingredientKg.entrySet()) {
            BigDecimal limit = limitFor.get(e.getKey());
            ingredients.add(new Ingredient(e.getKey(), e.getValue(), limit, limit != null && e.getValue().compareTo(limit) > 0));
        }
        for (var e : limitFor.entrySet()) {
            if (!ingredientKg.containsKey(e.getKey()) && kgByItem.containsKey(e.getKey())) {
                BigDecimal used = kgByItem.get(e.getKey());
                ingredients.add(new Ingredient(e.getKey(), used, e.getValue(), used.compareTo(e.getValue()) > 0));
            }
        }

        // Cost per day
        double cost = 0;
        double concCost = 0;
        boolean costKnown = true;
        for (Line l : lines) {
            ItemInfo it = l.itemId() == null ? null : items.get(l.itemId());
            if (it == null || it.costPerUnit() == null) {
                costKnown = costKnown && l.kg().signum() == 0;
                continue;
            }
            double c = l.kg().doubleValue() * it.costPerUnit().doubleValue();
            cost += c;
            if (l.section() == Section.CONCENTRATE) {
                concCost += c;
            }
        }

        List<NutrientLine> nutrients = nutrients(p, bw, lines, items, formulaShares);

        String explain = "body weight " + fmt(bw) + " kg × " + fmt(num(p.bwPct())) + " % = " + fmt(target) + " kg · concentrate = "
                + fmt(num(p.concBaseKg()))
                + (num(p.concPerLitreKg()) > 0 ? " + " + fmt(milk) + " L × " + fmt(num(p.concPerLitreKg())) : "")
                + (capped ? " (capped at " + fmt(num(p.maxConcKg())) + " kg)" : "");

        return new Plan(a, dim, stage, kg(green), kg(dry), kg(conc), kg(green + dry + conc), kg(target), capped, explain,
                lines, ingredients, costKnown ? money(cost) : null, money(concCost), nutrients, kgByItem,
                ingredientKg);
    }

    private static void split(Section section, double total, List<Share> shares, String timing, Map<Long, ItemInfo> items,
                              List<Line> lines, Map<Long, BigDecimal> kgByItem) {
        if (total <= 0) {
            return;
        }
        List<Share> usable = shares.stream().filter(s -> items.containsKey(s.itemId()) && s.sharePct().signum() > 0).toList();
        double sum = usable.stream().mapToDouble(s -> s.sharePct().doubleValue()).sum();
        if (usable.isEmpty() || sum <= 0) {
            lines.add(new Line(section, null, kg(total), timing));
            return;
        }
        for (Share s : usable) {
            BigDecimal k = kg(total * s.sharePct().doubleValue() / sum);
            lines.add(new Line(section, s.itemId(), k, timing));
            kgByItem.merge(s.itemId(), k, BigDecimal::add);
        }
    }

    /** Nutrient values per kg of an item; a MIX without its own values is worked out from its formula. */
    static BigDecimal[] perKg(ItemInfo it, Map<Long, ItemInfo> items, Map<Long, Map<Long, BigDecimal>> formulaShares) {
        BigDecimal[] own = {it.dmPct(), it.cpPct(), it.tdnPct(), it.caPct(), it.pPct()};
        Map<Long, BigDecimal> shares = formulaShares.get(it.id());
        if (it.category() != Category.MIX || shares == null) {
            return own;
        }
        BigDecimal[] out = new BigDecimal[5];
        Function<ItemInfo, BigDecimal[]> vals = i -> new BigDecimal[] {i.dmPct(), i.cpPct(), i.tdnPct(), i.caPct(), i.pPct()};
        for (int k = 0; k < 5; k++) {
            if (own[k] != null) {
                out[k] = own[k];
                continue;
            }
            double sum = 0;
            boolean known = true;
            for (var e : shares.entrySet()) {
                ItemInfo ing = items.get(e.getKey());
                BigDecimal v = ing == null ? null : vals.apply(ing)[k];
                if (v == null) {
                    known = false;
                    break;
                }
                sum += v.doubleValue() * e.getValue().doubleValue();
            }
            out[k] = known ? BigDecimal.valueOf(sum).setScale(2, RoundingMode.HALF_UP) : null;
        }
        return out;
    }

    private static List<NutrientLine> nutrients(RationParams p, double bw, List<Line> lines, Map<Long, ItemInfo> items,
                                                Map<Long, Map<Long, BigDecimal>> formulaShares) {
        String[] names = {"DM", "CP", "TDN", "Ca", "P"};
        double[] sums = new double[5];
        boolean[] known = {true, true, true, true, true};
        for (Line l : lines) {
            if (l.kg().signum() == 0) {
                continue;
            }
            ItemInfo it = l.itemId() == null ? null : items.get(l.itemId());
            BigDecimal[] v = it == null ? new BigDecimal[5] : perKg(it, items, formulaShares);
            for (int k = 0; k < 5; k++) {
                if (v[k] == null) {
                    known[k] = false;
                } else {
                    sums[k] += l.kg().doubleValue() * v[k].doubleValue() / 100.0;
                }
            }
        }
        double dmNeed = bw * num(p.dmIntakePct()) / 100.0;
        double[] need = {dmNeed, dmNeed * num(p.cpPct()) / 100, dmNeed * num(p.tdnPct()) / 100,
                dmNeed * num(p.caPct()) / 100 * 1000, dmNeed * num(p.pPct()) / 100 * 1000};
        List<NutrientLine> out = new ArrayList<>();
        for (int k = 0; k < 5; k++) {
            boolean grams = k >= 3;
            double ration = grams ? sums[k] * 1000 : sums[k];
            String status;
            if (!known[k]) {
                status = "UNKNOWN";
            } else if (need[k] <= 0) {
                status = "OK";
            } else {
                double r = ration / need[k];
                status = r < 0.9 ? "LOW" : r > 1.15 ? "HIGH" : "OK";
            }
            out.add(new NutrientLine(names[k], known[k] ? BigDecimal.valueOf(ration).setScale(grams ? 0 : 2, RoundingMode.HALF_UP) : null,
                    BigDecimal.valueOf(need[k]).setScale(grams ? 0 : 2, RoundingMode.HALF_UP), grams ? "g" : "kg", status));
        }
        return out;
    }

    static double num(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    static BigDecimal kg(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String fmt(double v) {
        BigDecimal b = BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        return b.scale() < 0 ? b.setScale(0).toPlainString() : b.toPlainString();
    }
}
