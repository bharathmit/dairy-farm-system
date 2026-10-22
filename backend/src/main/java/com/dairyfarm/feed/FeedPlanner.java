package com.dairyfarm.feed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.dairyfarm.feed.FeedRepository.AnimalRow;
import com.dairyfarm.feed.FeedRepository.LastPrice;
import com.dairyfarm.feed.FeedRepository.RuleRow;
import com.dairyfarm.feed.FeedViews.Category;
import com.dairyfarm.feed.FeedViews.ItemRef;
import com.dairyfarm.feed.FeedViews.RationCategory;
import com.dairyfarm.feed.FeedViews.StockItem;
import com.dairyfarm.feed.FeedViews.StockStatus;
import com.dairyfarm.feed.RationCalc.AnimalInput;
import com.dairyfarm.feed.RationCalc.ItemInfo;
import com.dairyfarm.feed.RationCalc.Plan;
import com.dairyfarm.stock.Item;
import com.dairyfarm.stock.ItemRepository;
import com.dairyfarm.stock.StockRepository;
import com.dairyfarm.stock.StockRepository.Level;

/** Loads a farm's feed items, formulas and rules, plans each animal's ration and works out stock status. */
@Component
public class FeedPlanner {

    /** Everything about a farm's feed that the screens need, loaded once per request. */
    public record Catalog(Map<Long, Item> entities, Map<Long, ItemInfo> items, Map<Long, Map<Long, BigDecimal>> formulaShares,
                          Long defaultConcId, Map<Long, Level> levels, Map<Long, LastPrice> lastPrices,
                          List<RuleRow> rules) {

        public ItemRef ref(Long id) {
            ItemInfo i = items.get(id);
            return i == null ? null : new ItemRef(i.id(), i.name(), i.category(), i.unit());
        }

        public BigDecimal stock(Long id) {
            Level l = levels.get(id);
            return l == null ? BigDecimal.ZERO : l.onHand().setScale(2, RoundingMode.HALF_UP);
        }
    }

    /** Herd plan: one plan per animal, plus kg per day by item (fed directly, and as concentrate ingredients). */
    public record Herd(LocalDate date, List<Plan> plans, Map<Long, BigDecimal> directKg, Map<Long, BigDecimal> ingredientKg) {

        public Map<Long, BigDecimal> totalKg() {
            Map<Long, BigDecimal> m = new LinkedHashMap<>(directKg);
            ingredientKg.forEach((k, v) -> m.merge(k, v, BigDecimal::add));
            return m;
        }
    }

    static final Map<RationCategory, BigDecimal> ESTIMATED_WEIGHT = Map.of(
            RationCategory.CALF, new BigDecimal("60"), RationCategory.HEIFER, new BigDecimal("250"),
            RationCategory.MILKING, new BigDecimal("400"), RationCategory.DRY, new BigDecimal("420"),
            RationCategory.BULL, new BigDecimal("500"));

    private final ItemRepository itemRepo;
    private final StockRepository stock;
    private final FeedRepository repo;

    public FeedPlanner(ItemRepository itemRepo, StockRepository stock, FeedRepository repo) {
        this.itemRepo = itemRepo;
        this.stock = stock;
        this.repo = repo;
    }

    public Catalog catalog(Long farmId) {
        List<Item> all = itemRepo.findByFarmIdAndItemTypeOrderByNameAsc(farmId, Item.Type.FEED);
        Map<Long, Item> entities = all.stream().collect(Collectors.toMap(Item::getId, Function.identity(), (a, b) -> a,
                LinkedHashMap::new));
        Map<Long, ItemInfo> items = new LinkedHashMap<>();
        for (Item i : all) {
            items.put(i.getId(), info(i));
        }
        // Latest active formula per mix item → ingredient shares
        Map<Long, Map<Long, BigDecimal>> shares = new HashMap<>();
        var formulas = repo.formulas(farmId).stream().filter(FeedRepository.FormulaRow::active)
                .sorted(Comparator.comparing(FeedRepository.FormulaRow::id).reversed()).toList();
        var lines = repo.lines(formulas.stream().map(FeedRepository.FormulaRow::id).toList());
        for (var f : formulas) {
            if (shares.containsKey(f.mixItemId())) {
                continue;
            }
            var ls = lines.stream().filter(l -> l.formulaId().equals(f.id())).toList();
            BigDecimal total = ls.stream().map(FeedRepository.LineRow::kg).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.signum() > 0) {
                Map<Long, BigDecimal> m = new LinkedHashMap<>();
                for (var l : ls) {
                    m.merge(l.itemId(), l.kg().divide(total, 6, RoundingMode.HALF_UP), BigDecimal::add);
                }
                shares.put(f.mixItemId(), m);
            }
        }
        Long defaultConc = all.stream().filter(i -> i.isActive() && "MIX".equals(i.getCategory())).map(Item::getId)
                .findFirst().orElse(null);
        return new Catalog(entities, items, shares, defaultConc, stock.levels(entities.keySet()), repo.lastPrices(farmId),
                repo.rules(farmId));
    }

    static ItemInfo info(Item i) {
        var d = i.getDetails();
        return new ItemInfo(i.getId(), i.getName(), i.getCategory() == null ? null : Category.valueOf(i.getCategory()),
                i.getUnit().name(), i.getCostPerUnit(), d.dmPct(), d.cpPct(), d.tdnPct(), d.caPct(), d.pPct(), d.isExcluded(),
                i.isActive());
    }

    /** The rule in force for a category on a date (defaults when none is saved). */
    public static RationParams rule(Catalog c, RationCategory cat, LocalDate date) {
        RuleRow r = FeedRepository.inForce(c.rules(), date).get(cat);
        return r == null ? RationParams.defaults(cat) : r.params();
    }

    public static LocalDate ruleFrom(Catalog c, RationCategory cat, LocalDate date) {
        RuleRow r = FeedRepository.inForce(c.rules(), date).get(cat);
        return r == null ? null : r.effectiveFrom();
    }

    public static AnimalInput input(AnimalRow a) {
        RationCategory cat = RationCategory.valueOf(a.category());
        BigDecimal bw;
        String source;
        if (a.measuredKg() != null) {
            bw = a.measuredKg();
            source = "MEASURED";
        } else if (a.currentWeightKg() != null) {
            bw = a.currentWeightKg();
            source = "ANIMAL";
        } else {
            bw = ESTIMATED_WEIGHT.get(cat);
            source = "ESTIMATE";
        }
        return new AnimalInput(a.id(), a.tag(), a.name(), cat, a.breed(), bw, source,
                "MEASURED".equals(source) ? a.weighedOn() : null, a.milkYesterday(), a.lastCalvingDate());
    }

    public Herd herd(Long farmId, Catalog c, LocalDate date, Long groupId, Long animalId) {
        List<Plan> plans = new ArrayList<>();
        Map<Long, BigDecimal> direct = new LinkedHashMap<>();
        Map<Long, BigDecimal> ingredients = new LinkedHashMap<>();
        for (AnimalRow a : repo.animals(farmId, date, groupId, animalId)) {
            AnimalInput in = input(a);
            Plan p = RationCalc.plan(in, rule(c, in.category(), date), date, c.items(), c.formulaShares(), c.defaultConcId());
            plans.add(p);
            p.kgByItem().forEach((k, v) -> direct.merge(k, v, BigDecimal::add));
            p.ingredientKg().forEach((k, v) -> ingredients.merge(k, v, BigDecimal::add));
        }
        return new Herd(date, plans, direct, ingredients);
    }

    /** Stock status for each item against the herd's daily need. Money (last price) only for owners. */
    public List<StockItem> stockItems(Catalog c, Herd herd, boolean showMoney, boolean includeInactive) {
        Map<Long, BigDecimal> need = herd.totalKg();
        List<StockItem> out = new ArrayList<>();
        for (Item i : c.entities().values()) {
            if (!i.isActive() && !includeInactive) {
                continue;
            }
            out.add(stockItem(c, i, need.getOrDefault(i.getId(), BigDecimal.ZERO), showMoney));
        }
        out.sort(Comparator.comparing((StockItem s) -> s.status() == StockStatus.EXCLUDED || s.status() == StockStatus.UNUSED)
                .thenComparing(s -> s.daysLeft() == null ? new BigDecimal("99999") : s.daysLeft())
                .thenComparing(s -> s.item().name().toLowerCase()));
        return out;
    }

    public StockItem stockItem(Catalog c, Item i, BigDecimal needPerDay, boolean showMoney) {
        BigDecimal onHand = c.stock(i.getId());
        BigDecimal need = needPerDay.setScale(2, RoundingMode.HALF_UP);
        BigDecimal days = need.signum() > 0 ? onHand.max(BigDecimal.ZERO).divide(need, 1, RoundingMode.HALF_UP) : null;
        int lead = i.getLeadTimeDays() == null ? 2 : i.getLeadTimeDays();
        boolean belowLevel = i.getReorderLevel() != null && onHand.compareTo(i.getReorderLevel()) < 0;
        StockStatus status;
        if (i.getDetails().isExcluded()) {
            status = StockStatus.EXCLUDED;
        } else if (days == null) {
            status = belowLevel ? StockStatus.REORDER : StockStatus.UNUSED;
        } else if (days.doubleValue() <= lead || belowLevel) {
            status = StockStatus.REORDER;
        } else if (days.doubleValue() <= lead + 4) {
            status = StockStatus.LOW;
        } else {
            status = StockStatus.OK;
        }
        if ("MIX".equals(i.getCategory()) && (status == StockStatus.REORDER || status == StockStatus.LOW)) {
            status = StockStatus.PREPARE;
        }
        int bar = days == null ? (onHand.signum() > 0 ? 100 : 0) : (int) Math.min(100, Math.round(days.doubleValue() / 30 * 100));
        LastPrice lp = c.lastPrices().get(i.getId());
        return new StockItem(c.ref(i.getId()), onHand, need, days, status, bar,
                showMoney ? (lp != null ? lp.price() : i.getCostPerUnit()) : null, lp == null ? null : lp.date());
    }
}
