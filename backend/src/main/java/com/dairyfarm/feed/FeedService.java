package com.dairyfarm.feed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dairyfarm.finance.FinancePoster;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.contact.Contact;
import com.dairyfarm.contact.ContactRepository;
import com.dairyfarm.contact.ContactResponse.Ref;
import com.dairyfarm.contact.ContactService;
import com.dairyfarm.farm.FarmService;
import com.dairyfarm.feed.FeedPlanner.Catalog;
import com.dairyfarm.feed.FeedPlanner.Herd;
import com.dairyfarm.feed.FeedRepository.NewTxn;
import com.dairyfarm.feed.FeedRepository.RuleRow;
import com.dairyfarm.feed.FeedRepository.Txn;
import com.dairyfarm.feed.FeedViews.AnimalRation;
import com.dairyfarm.feed.FeedViews.Category;
import com.dairyfarm.feed.FeedViews.CategoryTotals;
import com.dairyfarm.feed.FeedViews.FeedItem;
import com.dairyfarm.feed.FeedViews.FeedItemRequest;
import com.dairyfarm.feed.FeedViews.FeedNeed;
import com.dairyfarm.feed.FeedViews.FeedingRequest;
import com.dairyfarm.feed.FeedViews.FeedingSheet;
import com.dairyfarm.feed.FeedViews.IngredientRow;
import com.dairyfarm.feed.FeedViews.IngredientUse;
import com.dairyfarm.feed.FeedViews.ItemRef;
import com.dairyfarm.feed.FeedViews.Move;
import com.dairyfarm.feed.FeedViews.MoveTarget;
import com.dairyfarm.feed.FeedViews.NeedPerDay;
import com.dairyfarm.feed.FeedViews.NeedRow;
import com.dairyfarm.feed.FeedViews.NutrientCheck;
import com.dairyfarm.feed.FeedViews.Overview;
import com.dairyfarm.feed.FeedViews.PaymentMethod;
import com.dairyfarm.feed.FeedViews.PurchaseRequest;
import com.dairyfarm.feed.FeedViews.PurchaseSaved;
import com.dairyfarm.feed.FeedViews.RationAnimal;
import com.dairyfarm.feed.FeedViews.RationCategory;
import com.dairyfarm.feed.FeedViews.RationLine;
import com.dairyfarm.feed.FeedViews.RationSummary;
import com.dairyfarm.feed.FeedViews.RationTotals;
import com.dairyfarm.feed.FeedViews.RuleRef;
import com.dairyfarm.feed.FeedViews.RuleSet;
import com.dairyfarm.feed.FeedViews.RuleVersion;
import com.dairyfarm.feed.FeedViews.Section;
import com.dairyfarm.feed.FeedViews.Session;
import com.dairyfarm.feed.FeedViews.SessionStatus;
import com.dairyfarm.feed.FeedViews.SheetEntry;
import com.dairyfarm.feed.FeedViews.SheetLine;
import com.dairyfarm.feed.FeedViews.Shortfall;
import com.dairyfarm.feed.FeedViews.StockItem;
import com.dairyfarm.feed.FeedViews.StockRequest;
import com.dairyfarm.feed.FeedViews.Target;
import com.dairyfarm.feed.FeedViews.Totals;
import com.dairyfarm.feed.RationCalc.ItemInfo;
import com.dairyfarm.feed.RationCalc.Plan;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.stock.Item;
import com.dairyfarm.stock.ItemRepository;

@Service
public class FeedService {

    private FinancePoster finance;

    /** Keeps automatic finance entries in step with these records. */
    @Autowired
    void setFinance(FinancePoster finance) {
        this.finance = finance;
    }

    private final ItemRepository items;
    private final FeedRepository repo;
    private final FeedPlanner planner;
    private final ContactService contactService;
    private final ContactRepository contacts;

    public FeedService(ItemRepository items, FeedRepository repo, FeedPlanner planner, ContactService contactService,
                       ContactRepository contacts) {
        this.items = items;
        this.repo = repo;
        this.planner = planner;
        this.contactService = contactService;
        this.contacts = contacts;
    }

    // ---- items -------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<FeedItem> list(Long farmId, Category category, boolean includeInactive, boolean showMoney) {
        var all = items.findByFarmIdAndItemTypeOrderByNameAsc(farmId, Item.Type.FEED).stream()
                .filter(i -> includeInactive || i.isActive())
                .filter(i -> category == null || category.name().equals(i.getCategory())).toList();
        Map<Long, Contact> sup = suppliers(farmId, all);
        return all.stream().map(i -> view(i, sup, showMoney)).toList();
    }

    @Transactional(readOnly = true)
    public FeedItem get(Long farmId, Long id, boolean showMoney) {
        Item i = load(farmId, id);
        FeedItem v = view(i, suppliers(farmId, List.of(i)), showMoney);
        StockItem st = stockItemOf(farmId, i, showMoney);
        return new FeedItem(v.id(), v.name(), v.category(), v.unit(), v.costPerUnit(), v.dmPct(), v.cpPct(), v.tdnPct(),
                v.caPct(), v.pPct(), v.maxInclusionPct(), v.excluded(), v.excludedReason(), v.reorderLevel(), v.leadTimeDays(),
                v.supplier(), v.notes(), v.active(), st);
    }

    @Transactional
    public FeedItem create(Long farmId, FeedItemRequest req) {
        nameFree(farmId, req.name(), null);
        Item i = new Item(farmId, Item.Type.FEED, unit(req.unit()));
        apply(farmId, i, req);
        items.save(i);
        return view(i, suppliers(farmId, List.of(i)), true);
    }

    @Transactional
    public FeedItem update(Long farmId, Long id, FeedItemRequest req) {
        Item i = load(farmId, id);
        nameFree(farmId, req.name(), id);
        if (req.unit() != null && unit(req.unit()) != i.getUnit()) {
            throw ApiException.badRequest("The unit can't change once an item exists.");
        }
        apply(farmId, i, req);
        return view(i, suppliers(farmId, List.of(i)), true);
    }

    @Transactional
    public void delete(Long farmId, Long id) {
        Item i = load(farmId, id);
        if (repo.hasMoves(id) || repo.usedInFormulaOrRule(farmId, id)) {
            throw ApiException.conflict("This item has stock records or is used in a formula or ration rule. Make it inactive instead.");
        }
        items.delete(i);
        finance.sync(farmId);
    }

    private void nameFree(Long farmId, String name, Long exceptId) {
        boolean taken = items.findByFarmIdAndItemTypeOrderByNameAsc(farmId, Item.Type.FEED).stream()
                .anyMatch(i -> !i.getId().equals(exceptId) && i.getName().equalsIgnoreCase(name.trim()));
        if (taken) {
            throw ApiException.conflict("A feed item named \"" + name.trim() + "\" already exists.");
        }
    }

    private static Item.Unit unit(String u) {
        if (u == null || u.isBlank()) {
            return Item.Unit.KG;
        }
        return switch (u.trim().toUpperCase(Locale.ROOT)) {
            case "KG" -> Item.Unit.KG;
            case "L" -> Item.Unit.L;
            case "NOS" -> Item.Unit.NOS;
            default -> throw ApiException.badRequest("Use kg, L or nos for feed.");
        };
    }

    private void apply(Long farmId, Item i, FeedItemRequest req) {
        Contact s = contactService.require(farmId, req.supplierContactId(), Contact.Role.SUPPLIER);
        i.setName(req.name().trim());
        i.setCategory(req.category().name());
        if (req.costPerUnit() != null) {
            i.setCostPerUnit(req.costPerUnit());
        }
        boolean excluded = Boolean.TRUE.equals(req.excluded());
        i.setDetails(new Item.Details(null, null, null, req.dmPct(), req.cpPct(), req.tdnPct(), req.caPct(), req.pPct(),
                req.maxInclusionPct(), excluded ? Boolean.TRUE : null, excluded ? blank(req.excludedReason()) : null));
        i.setReorderLevel(req.reorderLevel());
        i.setLeadTimeDays(req.leadTimeDays());
        i.setSupplierContactId(s == null ? null : s.getId());
        i.setNotes(blank(req.notes()));
        if (req.active() != null) {
            i.setActive(req.active());
        }
    }

    Item load(Long farmId, Long id) {
        return items.findByIdAndFarmIdAndItemType(id, farmId, Item.Type.FEED)
                .orElseThrow(() -> ApiException.notFound("Feed item not found."));
    }

    private Map<Long, Contact> suppliers(Long farmId, List<Item> list) {
        Set<Long> ids = new HashSet<>();
        list.forEach(i -> {
            if (i.getSupplierContactId() != null) {
                ids.add(i.getSupplierContactId());
            }
        });
        Map<Long, Contact> m = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            contacts.findByFarmIdAndIdIn(farmId, ids).forEach(c -> m.put(c.getId(), c));
        }
        return m;
    }

    private static FeedItem view(Item i, Map<Long, Contact> sup, boolean showMoney) {
        var d = i.getDetails();
        Contact s = i.getSupplierContactId() == null ? null : sup.get(i.getSupplierContactId());
        return new FeedItem(i.getId(), i.getName(), i.getCategory() == null ? null : Category.valueOf(i.getCategory()),
                i.getUnit().name(), showMoney ? i.getCostPerUnit() : null, d.dmPct(), d.cpPct(), d.tdnPct(), d.caPct(),
                d.pPct(), d.maxInclusionPct(), d.isExcluded(), d.excludedReason(), i.getReorderLevel(), i.getLeadTimeDays(),
                Ref.of(s), i.getNotes(), i.isActive(), null);
    }

    // ---- stock, purchases, moves -------------------------------------------------------------------------------

    @Transactional
    public StockItem stock(Long farmId, Long id, StockRequest req, boolean showMoney) {
        Item i = load(farmId, id);
        DailyDates.notFuture(req.date());
        if (req.quantity().signum() == 0) {
            throw ApiException.badRequest("Enter a quantity other than 0.");
        }
        switch (req.type()) {
            case HARVEST -> {
                if (req.quantity().signum() < 0) {
                    throw ApiException.badRequest("A harvest adds stock; enter a quantity above 0.");
                }
                repo.insert(farmId, new NewTxn(id, "PRODUCE", req.date(), null, req.quantity(), null, null, null, null, null,
                        null, null, "HARVEST", null, null, blank(req.notes())));
            }
            case ADJUSTMENT -> repo.insert(farmId, new NewTxn(id, "ADJUSTMENT", req.date(), null, req.quantity(), null, null,
                    null, null, null, null, null, "ADJUSTMENT", null, null, blank(req.notes())));
        }
        return stockItemOf(farmId, i, showMoney);
    }

    @Transactional
    public PurchaseSaved purchase(Long farmId, PurchaseRequest req, boolean showMoney) {
        Item i = load(farmId, req.itemId());
        DailyDates.notFuture(req.date());
        BigDecimal qty = req.quantity();
        BigDecimal total = null;
        BigDecimal per = null;
        if (req.totalAmount() != null) {
            total = req.totalAmount().setScale(2, RoundingMode.HALF_UP);
            per = total.divide(qty, 2, RoundingMode.HALF_UP);
        } else if (req.costPerUnit() != null) {
            per = req.costPerUnit();
            total = per.multiply(qty).setScale(2, RoundingMode.HALF_UP);
        }
        Long supplierId = req.supplierContactId() != null
                ? contactService.require(farmId, req.supplierContactId(), Contact.Role.SUPPLIER).getId()
                : i.getSupplierContactId();
        Long id = repo.insert(farmId, new NewTxn(i.getId(), "PURCHASE", req.date(), null, qty, total, supplierId,
                req.paymentMethod() == null ? null : req.paymentMethod().name(), req.paid() == null ? Boolean.TRUE : req.paid(),
                null, null, null, "PURCHASE", null, null, blank(req.notes())));
        if (per != null) {
            i.setCostPerUnit(per);
        }
        Txn t = repo.txn(farmId, id).orElseThrow();
        finance.sync(farmId);
        return new PurchaseSaved(move(t, supplierMap(farmId, List.of(t)), showMoney), stockItemOf(farmId, i, showMoney));
    }

    @Transactional(readOnly = true)
    public List<Move> moves(Long farmId, Long itemId, String type, LocalDate from, LocalDate to, boolean showMoney) {
        if (itemId != null) {
            load(farmId, itemId);
        }
        LocalDate t = to == null ? DailyDates.today() : to;
        LocalDate f = from == null ? t.minusDays(90) : from;
        DailyDates.range(f, t, 400);
        List<Txn> txns = repo.moves(farmId, itemId, type, f, t);
        Map<Long, Contact> sup = supplierMap(farmId, txns);
        return txns.stream().map(x -> move(x, sup, showMoney)).toList();
    }

    private Map<Long, Contact> supplierMap(Long farmId, List<Txn> txns) {
        Set<Long> ids = new HashSet<>();
        txns.forEach(t -> {
            if (t.supplierId() != null) {
                ids.add(t.supplierId());
            }
        });
        Map<Long, Contact> m = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            contacts.findByFarmIdAndIdIn(farmId, ids).forEach(c -> m.put(c.getId(), c));
        }
        return m;
    }

    private static Move move(Txn t, Map<Long, Contact> sup, boolean showMoney) {
        MoveTarget target = t.animalId() != null ? new MoveTarget(Target.ANIMAL, t.animalId(), t.targetLabel())
                : t.groupId() != null ? new MoveTarget(Target.GROUP, t.groupId(), t.targetLabel())
                : new MoveTarget(Target.FARM, null, "Whole farm");
        BigDecimal per = t.cost() == null || t.quantity().signum() == 0 ? null
                : t.cost().divide(t.quantity().abs(), 2, RoundingMode.HALF_UP);
        String type = "PRODUCE".equals(t.type()) && "HARVEST".equals(t.sourceType()) ? "HARVEST" : t.type();
        return new Move(t.id(), t.date(), t.time(), type, t.quantity(), showMoney ? t.cost() : null, showMoney ? per : null,
                t.supplierId() == null ? null : Ref.of(sup.get(t.supplierId())),
                t.paymentMethod() == null ? null : PaymentMethod.valueOf(t.paymentMethod()), t.paid(),
                t.session() == null ? null : Session.valueOf(t.session()), target, t.sourceType(), t.notes());
    }

    private StockItem stockItemOf(Long farmId, Item i, boolean showMoney) {
        Catalog c = planner.catalog(farmId);
        Herd h = planner.herd(farmId, c, DailyDates.today(), null, null);
        Item fresh = c.entities().getOrDefault(i.getId(), i);
        return planner.stockItem(c, fresh, h.totalKg().getOrDefault(i.getId(), BigDecimal.ZERO), showMoney);
    }

    // ---- overview, need, ration --------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Overview overview(Long farmId, LocalDate date, boolean showMoney) {
        LocalDate d = date == null ? DailyDates.today() : date;
        Catalog c = planner.catalog(farmId);
        Herd h = planner.herd(farmId, c, d, null, null);
        Totals t = totals(h.plans(), showMoney);
        BigDecimal half = t.greenKg().add(t.dryKg()).add(t.concentrateKg()).divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
        var rows = repo.sessions(farmId, d);
        Map<String, SessionStatus> sessions = new LinkedHashMap<>();
        for (Session s : Session.values()) {
            var r = rows.get(s.name());
            sessions.put(s.name(), r == null ? new SessionStatus(false, BigDecimal.ZERO, half, null, null)
                    : new SessionStatus(true, r.kg().setScale(1, RoundingMode.HALF_UP), half, r.time(), r.by()));
        }
        List<StockItem> stock = planner.stockItems(c, h, showMoney, false);
        int reorder = (int) stock.stream().filter(s -> s.status() == FeedViews.StockStatus.REORDER
                || s.status() == FeedViews.StockStatus.PREPARE).count();
        return new Overview(d, sessions, new NeedPerDay(t.greenKg(), t.dryKg(), t.concentrateKg()), t.costPerDay(), reorder, stock);
    }

    private static Totals totals(List<Plan> plans, boolean showMoney) {
        BigDecimal g = BigDecimal.ZERO, dr = BigDecimal.ZERO, cn = BigDecimal.ZERO, cost = BigDecimal.ZERO;
        boolean costKnown = !plans.isEmpty();
        for (Plan p : plans) {
            g = g.add(p.greenKg());
            dr = dr.add(p.dryKg());
            cn = cn.add(p.concKg());
            if (p.costPerDay() == null) {
                costKnown = false;
            } else {
                cost = cost.add(p.costPerDay());
            }
        }
        return new Totals(scale1(g), scale1(dr), scale1(cn), showMoney && costKnown ? cost.setScale(2, RoundingMode.HALF_UP) : null);
    }

    @Transactional(readOnly = true)
    public RationSummary summary(Long farmId, LocalDate date, boolean showMoney) {
        LocalDate d = date == null ? DailyDates.today() : date;
        Catalog c = planner.catalog(farmId);
        Herd h = planner.herd(farmId, c, d, null, null);
        Map<RationCategory, List<Plan>> by = new EnumMap<>(RationCategory.class);
        h.plans().forEach(p -> by.computeIfAbsent(p.animal().category(), k -> new ArrayList<>()).add(p));
        List<CategoryTotals> cats = new ArrayList<>();
        for (RationCategory rc : List.of(RationCategory.MILKING, RationCategory.DRY, RationCategory.HEIFER, RationCategory.CALF,
                RationCategory.BULL)) {
            List<Plan> ps = by.getOrDefault(rc, List.of());
            cats.add(new CategoryTotals(rc, ps.size(), totals(ps, showMoney)));
        }
        return new RationSummary(d, totals(h.plans(), showMoney), cats);
    }

    @Transactional(readOnly = true)
    public AnimalRation ration(Long farmId, Long animalId, LocalDate date, boolean showMoney) {
        LocalDate d = date == null ? DailyDates.today() : date;
        Catalog c = planner.catalog(farmId);
        Herd h = planner.herd(farmId, c, d, null, animalId);
        if (h.plans().isEmpty()) {
            throw ApiException.notFound("No ration for that animal — it must be an active cow or buffalo on this farm.");
        }
        Plan p = h.plans().get(0);
        var a = p.animal();
        List<RationLine> lines = p.lines().stream()
                .map(l -> new RationLine(l.section(), l.itemId() == null ? null : c.ref(l.itemId()), l.kg(), l.timing())).toList();
        List<IngredientUse> ingr = p.ingredients().stream()
                .map(x -> new IngredientUse(c.ref(x.itemId()), x.kgPerDay(), x.limitKg(), x.over())).toList();
        List<NutrientCheck> nut = p.nutrients().stream()
                .map(n -> new NutrientCheck(n.name(), n.ration(), n.need(), n.unit(), n.status())).toList();
        return new AnimalRation(new RationAnimal(a.id(), a.tag(), a.name(), a.category().name(), a.breed()), d,
                a.bodyWeightKg(), a.weightSource(), a.weighedOn(), a.milkYesterday(), p.daysInMilk(),
                p.daysInMilk() == null ? null : p.stage().name(),
                new RationTotals(p.greenKg(), p.dryKg(), p.concKg(), p.totalKg(), p.targetKg(), p.dmiPct(), p.waterL()), p.explain(), p.capped(),
                lines, ingr, showMoney ? p.costPerDay() : null, nut,
                new RuleRef(a.category(), FeedPlanner.ruleFrom(c, a.category(), d)));
    }

    @Transactional(readOnly = true)
    public FeedNeed need(Long farmId, int days, boolean showMoney) {
        if (days < 1 || days > 60) {
            throw ApiException.badRequest("Pick 1 to 60 days.");
        }
        LocalDate today = DailyDates.today();
        Catalog c = planner.catalog(farmId);
        Herd h = planner.herd(farmId, c, today, null, null);
        Totals perDay = totals(h.plans(), showMoney);
        BigDecimal concCost = BigDecimal.ZERO;
        for (Plan p : h.plans()) {
            concCost = concCost.add(p.concCostPerDay() == null ? BigDecimal.ZERO : p.concCostPerDay());
        }
        List<NeedRow> rows = new ArrayList<>();
        List<Shortfall> shortfalls = new ArrayList<>();
        for (var e : h.directKg().entrySet()) {
            ItemInfo it = c.items().get(e.getKey());
            Item ent = c.entities().get(e.getKey());
            BigDecimal stock = c.stock(e.getKey());
            BigDecimal daysLeft = daysLeft(stock, e.getValue());
            rows.add(new NeedRow(FeedViews.sectionOf(it.category()), c.ref(e.getKey()), scale1(e.getValue()), stock, daysLeft,
                    overLead(daysLeft, ent)));
            shortfall(c, ent, e.getValue(), stock, days, today, showMoney).ifPresent(shortfalls::add);
        }
        rows.sort(Comparator.comparing(NeedRow::section).thenComparing(r -> r.item().name().toLowerCase()));
        List<IngredientRow> ingr = new ArrayList<>();
        for (var e : h.ingredientKg().entrySet()) {
            Item ent = c.entities().get(e.getKey());
            BigDecimal stock = c.stock(e.getKey());
            BigDecimal daysLeft = daysLeft(stock, e.getValue());
            ingr.add(new IngredientRow(c.ref(e.getKey()), scale1(e.getValue()), stock, daysLeft, overLead(daysLeft, ent)));
            shortfall(c, ent, e.getValue(), stock, days, today, showMoney).ifPresent(shortfalls::add);
        }
        ingr.sort(Comparator.comparing(r -> r.daysLeft() == null ? new BigDecimal("99999") : r.daysLeft()));
        BigDecimal month = perDay.costPerDay() == null ? null : perDay.costPerDay().multiply(BigDecimal.valueOf(30));
        return new FeedNeed(h.plans().size(), perDay, month, showMoney ? concCost.setScale(2, RoundingMode.HALF_UP) : null,
                showMoney ? concCost.multiply(BigDecimal.valueOf(30)).setScale(2, RoundingMode.HALF_UP) : null, rows, ingr,
                shortfalls);
    }

    private static BigDecimal daysLeft(BigDecimal stock, BigDecimal need) {
        return need.signum() > 0 ? stock.max(BigDecimal.ZERO).divide(need, 1, RoundingMode.HALF_UP) : null;
    }

    private static boolean overLead(BigDecimal daysLeft, Item i) {
        int lead = i.getLeadTimeDays() == null ? 2 : i.getLeadTimeDays();
        return daysLeft != null && daysLeft.doubleValue() <= lead;
    }

    private static java.util.Optional<Shortfall> shortfall(Catalog c, Item i, BigDecimal perDay, BigDecimal stock, int days,
                                                           LocalDate today, boolean showMoney) {
        BigDecimal needed = perDay.multiply(BigDecimal.valueOf(days));
        BigDecimal have = stock.max(BigDecimal.ZERO);
        if (perDay.signum() <= 0 || have.compareTo(needed) >= 0) {
            return java.util.Optional.empty();
        }
        BigDecimal qty = needed.subtract(have).setScale(1, RoundingMode.HALF_UP);
        long daysLeft = (long) Math.floor(have.doubleValue() / perDay.doubleValue());
        BigDecimal price = showMoney && i.getCostPerUnit() != null ? qty.multiply(i.getCostPerUnit()).setScale(2, RoundingMode.HALF_UP) : null;
        return java.util.Optional.of(new Shortfall(c.ref(i.getId()), qty, today.plusDays(daysLeft), price));
    }

    // ---- feeding sheet -----------------------------------------------------------------------------------------

    private record TargetSpec(Target target, Long groupId, Long animalId, String label) {
    }

    private TargetSpec target(Long farmId, Target t, Long groupId, Long animalId) {
        Target kind = t == null ? (animalId != null ? Target.ANIMAL : groupId != null ? Target.GROUP : Target.FARM) : t;
        return switch (kind) {
            case FARM -> new TargetSpec(Target.FARM, null, null, "Whole farm");
            case GROUP -> {
                if (groupId == null) {
                    throw ApiException.badRequest("Pick a group.");
                }
                yield new TargetSpec(Target.GROUP, groupId, null,
                        repo.groupName(farmId, groupId).orElseThrow(() -> ApiException.badRequest("That group isn't on this farm.")));
            }
            case ANIMAL -> {
                if (animalId == null) {
                    throw ApiException.badRequest("Pick an animal.");
                }
                var rows = repo.animals(farmId, DailyDates.today(), null, animalId);
                if (rows.isEmpty()) {
                    throw ApiException.badRequest("That animal isn't an active cow or buffalo on this farm.");
                }
                var a = rows.get(0);
                yield new TargetSpec(Target.ANIMAL, null, animalId, a.tag() + (a.name() == null ? "" : " " + a.name()));
            }
        };
    }

    @Transactional(readOnly = true)
    public FeedingSheet sheet(Long farmId, LocalDate date, Session session, Target t, Long groupId, Long animalId) {
        LocalDate d = date == null ? DailyDates.today() : date;
        Session s = session == null ? (LocalTime.now(FarmService.FARM_ZONE).getHour() >= 12 ? Session.PM : Session.AM) : session;
        TargetSpec spec = target(farmId, t, groupId, animalId);
        Catalog c = planner.catalog(farmId);
        Herd h = planner.herd(farmId, c, d, spec.groupId(), spec.animalId());
        Map<Long, BigDecimal> planned = new LinkedHashMap<>();
        boolean unpicked = false;
        for (Plan p : h.plans()) {
            p.kgByItem().forEach((k, v) -> planned.merge(k, v, BigDecimal::add));
            unpicked |= p.lines().stream().anyMatch(l -> l.itemId() == null && l.kg().signum() > 0);
        }
        Map<Long, BigDecimal> actual = repo.feeding(farmId, d, s.name(), spec.groupId(), spec.animalId());
        Set<Long> ids = new java.util.LinkedHashSet<>(planned.keySet());
        ids.addAll(actual.keySet());
        List<SheetLine> lines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        BigDecimal pTot = BigDecimal.ZERO, aTot = BigDecimal.ZERO;
        for (Long id : ids) {
            ItemInfo it = c.items().get(id);
            if (it == null) {
                continue;
            }
            BigDecimal pl = planned.containsKey(id) ? planned.get(id).divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP) : null;
            BigDecimal ac = actual.get(id);
            BigDecimal st = c.stock(id);
            lines.add(new SheetLine(c.ref(id), FeedViews.sectionOf(it.category()), pl, ac == null ? null : scale1(ac), st));
            pTot = pTot.add(pl == null ? BigDecimal.ZERO : pl);
            aTot = aTot.add(ac == null ? BigDecimal.ZERO : ac);
            if (pl != null && ac != null) {
                BigDecimal diff = pl.subtract(ac);
                if (diff.doubleValue() >= Math.max(1.0, pl.doubleValue() * 0.1)) {
                    warnings.add(it.name() + " " + plain(diff) + " kg under plan.");
                }
            }
            Item ent = c.entities().get(id);
            if (st.signum() < 0) {
                warnings.add(it.name() + ": stock is below zero — record a purchase or harvest.");
            } else if (ent.getReorderLevel() != null && st.compareTo(ent.getReorderLevel()) < 0 && (pl != null || ac != null)) {
                warnings.add(it.name() + ": stock left " + plain(st) + " kg — reorder.");
            }
        }
        if (unpicked) {
            warnings.add("Some ration parts have no feed items picked yet — set them in the ration rules.");
        }
        lines.sort(Comparator.comparing(SheetLine::section).thenComparing(l -> l.item().name().toLowerCase()));
        return new FeedingSheet(d, s, spec.target(), spec.label(), h.plans().size(), lines, scale1(pTot),
                actual.isEmpty() ? null : scale1(aTot), !actual.isEmpty(), warnings);
    }

    @Transactional
    public FeedingSheet saveSheet(Long farmId, FeedingRequest req) {
        DailyDates.notFuture(req.date());
        TargetSpec spec = target(farmId, req.target(), req.groupId(), req.animalId());
        Map<Long, BigDecimal> kg = new LinkedHashMap<>();
        for (SheetEntry e : req.lines()) {
            if (e.kg() != null && e.kg().signum() > 0) {
                kg.merge(e.itemId(), e.kg(), BigDecimal::add);
            }
        }
        Map<Long, Item> found = new LinkedHashMap<>();
        for (Long id : kg.keySet()) {
            Item i = items.findByIdAndFarmIdAndItemType(id, farmId, Item.Type.FEED)
                    .orElseThrow(() -> ApiException.badRequest("That feed item isn't on this farm."));
            if (i.getDetails().isExcluded()) {
                throw ApiException.badRequest(i.getName() + " is marked Excluded and can't be fed.");
            }
            found.put(id, i);
        }
        repo.deleteFeeding(farmId, req.date(), req.session().name(), spec.groupId(), spec.animalId());
        LocalTime time = req.time() != null ? req.time() : LocalTime.now(FarmService.FARM_ZONE).withSecond(0).withNano(0);
        UUID batch = UUID.randomUUID();
        for (var e : kg.entrySet()) {
            repo.insert(farmId, new NewTxn(e.getKey(), "USAGE", req.date(), time, e.getValue(), null, null, null, null,
                    req.session().name(), spec.animalId(), spec.groupId(), "FEEDING", null, batch, blank(req.notes())));
        }
        return sheet(farmId, req.date(), req.session(), spec.target(), spec.groupId(), spec.animalId());
    }

    // ---- ration rules ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RuleSet> rules(Long farmId) {
        LocalDate today = DailyDates.today();
        List<RuleRow> all = repo.rules(farmId);
        var current = FeedRepository.inForce(all, today);
        List<RuleSet> out = new ArrayList<>();
        for (RationCategory rc : List.of(RationCategory.MILKING, RationCategory.DRY, RationCategory.HEIFER, RationCategory.CALF,
                RationCategory.BULL)) {
            RuleRow cur = current.get(rc);
            List<RuleVersion> upcoming = new ArrayList<>();
            List<RuleVersion> history = new ArrayList<>();
            for (RuleRow r : all) {
                if (r.category() != rc || (cur != null && r.effectiveFrom().equals(cur.effectiveFrom()))) {
                    continue;
                }
                (r.effectiveFrom().isAfter(today) ? upcoming : history).add(new RuleVersion(r.effectiveFrom(), r.params().resolved(rc)));
            }
            history.sort(Comparator.comparing(RuleVersion::effectiveFrom).reversed());
            out.add(new RuleSet(rc, cur == null ? new RuleVersion(null, RationParams.defaults(rc))
                    : new RuleVersion(cur.effectiveFrom(), cur.params().resolved(rc)), upcoming, history));
        }
        return out;
    }

    @Transactional
    public List<RuleSet> saveRule(Long farmId, RationCategory category, LocalDate from, RationParams params) {
        RationParams p = params.resolved(category);
        Map<Long, Item> feed = new LinkedHashMap<>();
        items.findByFarmIdAndItemTypeOrderByNameAsc(farmId, Item.Type.FEED).forEach(i -> feed.put(i.getId(), i));
        checkShares("Green", p.green(), feed, Set.of("GREEN_FODDER", "SILAGE"));
        checkShares("Dry", p.dry(), feed, Set.of("DRY_FODDER", "SILAGE"));
        if (p.concItemId() != null) {
            Item ci = usable(feed, p.concItemId());
            if (!Set.of("MIX", "CONCENTRATE_INGREDIENT").contains(ci.getCategory())) {
                throw ApiException.badRequest(ci.getName() + " isn't a concentrate or mix.");
            }
        }
        for (var l : p.limits()) {
            usable(feed, l.itemId());
        }
        repo.upsertRule(farmId, category, from, p);
        return rules(farmId);
    }

    private static void checkShares(String label, List<RationParams.Share> shares, Map<Long, Item> feed, Set<String> cats) {
        if (shares.isEmpty()) {
            return;
        }
        double sum = 0;
        Set<Long> seen = new HashSet<>();
        for (var s : shares) {
            Item i = usable(feed, s.itemId());
            if (!cats.contains(i.getCategory())) {
                throw ApiException.badRequest(i.getName() + " can't be in the " + label.toLowerCase() + " fodder list.");
            }
            if (!seen.add(s.itemId())) {
                throw ApiException.badRequest(i.getName() + " is listed twice.");
            }
            sum += s.sharePct().doubleValue();
        }
        if (Math.abs(sum - 100) > 0.5) {
            throw ApiException.badRequest(label + " shares must add up to 100 % (now " + BigDecimal.valueOf(sum).stripTrailingZeros().toPlainString() + " %).");
        }
    }

    private static Item usable(Map<Long, Item> feed, Long id) {
        Item i = feed.get(id);
        if (i == null) {
            throw ApiException.badRequest("That feed item isn't on this farm.");
        }
        if (i.getDetails().isExcluded()) {
            throw ApiException.badRequest(i.getName() + " is marked Excluded and can't be used in a ration.");
        }
        return i;
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    static BigDecimal scale1(BigDecimal v) {
        return v.setScale(1, RoundingMode.HALF_UP);
    }

    static String plain(BigDecimal v) {
        return v.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static ItemRef ref(Item i) {
        return new ItemRef(i.getId(), i.getName(), i.getCategory() == null ? null : Category.valueOf(i.getCategory()),
                i.getUnit().name());
    }
}
