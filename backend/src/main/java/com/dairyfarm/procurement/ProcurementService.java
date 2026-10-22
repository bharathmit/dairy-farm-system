package com.dairyfarm.procurement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.breeding.SemenService;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.contact.ContactRepository;
import com.dairyfarm.contact.ContactResponse.Ref;
import com.dairyfarm.feed.FeedService;
import com.dairyfarm.feed.FeedViews.StockStatus;
import com.dairyfarm.finance.FinancePoster;
import com.dairyfarm.finance.FinanceRepository;
import com.dairyfarm.finance.FinanceViews.AllocKind;
import com.dairyfarm.finance.FinanceViews.Category;
import com.dairyfarm.finance.FinanceViews.Direction;
import com.dairyfarm.finance.FinanceViews.Status;
import com.dairyfarm.procurement.ProcurementViews.*;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.security.CurrentUser;
import com.dairyfarm.stock.Item;
import com.dairyfarm.stock.ItemRepository;

@Service
public class ProcurementService {

    private static final int SEMEN_LOW = 5;

    private final JdbcClient jdbc;
    private final ItemRepository items;
    private final ContactRepository contacts;
    private final FeedService feed;
    private final SemenService semen;
    private final FinanceRepository finance;
    private final FinancePoster poster;

    public ProcurementService(JdbcClient jdbc, ItemRepository items, ContactRepository contacts, FeedService feed,
                              SemenService semen, FinanceRepository finance, FinancePoster poster) {
        this.jdbc = jdbc;
        this.items = items;
        this.contacts = contacts;
        this.feed = feed;
        this.semen = semen;
        this.finance = finance;
        this.poster = poster;
    }

    private static final String SELECT = """
            select n.*, i.name as item_name, i.item_type, c.name as supplier_name
            from procurement_note n left join item i on i.id = n.item_id left join contact c on c.id = n.supplier_contact_id
            """;

    @Transactional(readOnly = true)
    public Board board(Long farmId, NoteStatus status) {
        List<Note> all = jdbc.sql(SELECT + """
                 where n.farm_id = :farm
                 order by case n.status when 'PLANNED' then 0 when 'ORDERED' then 1 when 'RECEIVED' then 2 else 3 end,
                          n.needed_by nulls last, n.id desc""")
                .param("farm", farmId).query((rs, i) -> note(rs)).list();
        Set<Long> open = openItemIds(all);
        List<Suggestion> suggestions = new ArrayList<>();
        for (var s : feed.overview(farmId, DailyDates.today(), true).items()) {
            if (s.status() == StockStatus.REORDER && !open.contains(s.item().id())) {
                Item i = items.findById(s.item().id()).orElse(null);
                suggestions.add(new Suggestion(new ItemInfo(s.item().id(), s.item().name(), "FEED"), s.stock(),
                        s.item().unit(), i == null ? null : i.getReorderLevel(), s.daysLeft(),
                        s.daysLeft() != null ? "About " + s.daysLeft().setScale(0, RoundingMode.HALF_UP) + " days left"
                                : "Below reorder level"));
            }
        }
        for (var b : semen.list(farmId, true).batches()) {
            if (b.straws() < SEMEN_LOW && !open.contains(b.id())) {
                suggestions.add(new Suggestion(new ItemInfo(b.id(), b.bullName() + (b.breed() == null ? "" : " · " + b.breed()),
                        "SEMEN"), BigDecimal.valueOf(b.straws()), "STRAW", BigDecimal.valueOf(SEMEN_LOW), null,
                        b.straws() == 0 ? "No straws left" : b.straws() + " straws left"));
            }
        }
        List<Note> shown = status == null ? all : all.stream().filter(n -> n.status() == status).toList();
        int planned = (int) all.stream().filter(n -> n.status() == NoteStatus.PLANNED).count();
        int ordered = (int) all.stream().filter(n -> n.status() == NoteStatus.ORDERED).count();
        int received = (int) all.stream().filter(n -> n.status() == NoteStatus.RECEIVED).count();
        int active = (int) all.stream().filter(n -> n.status() != NoteStatus.CANCELLED).count();
        return new Board(new Counts(active, planned, ordered, received), suggestions, shown);
    }

    @Transactional(readOnly = true)
    public Note get(Long farmId, Long id) {
        return jdbc.sql(SELECT + " where n.farm_id = :farm and n.id = :id").param("farm", farmId).param("id", id)
                .query((rs, i) -> note(rs)).optional().orElseThrow(() -> ApiException.notFound("Note not found."));
    }

    @Transactional
    public Note create(Long farmId, NoteRequest req) {
        Fields f = fields(farmId, req);
        Long id = jdbc.sql("""
                insert into procurement_note (farm_id, item_id, title, quantity, unit, expected_price, needed_by,
                  supplier_contact_id, status, source, notes, created_by)
                values (:farm, :item, :title, :qty, :unit, :price, :by, :sup, 'PLANNED', :src, :notes, :u) returning id""")
                .param("farm", farmId).param("item", f.itemId()).param("title", f.title()).param("qty", req.quantity())
                .param("unit", f.unit()).param("price", req.expectedPrice()).param("by", req.neededBy())
                .param("sup", f.supplierId()).param("src", (req.source() == null ? NoteSource.MANUAL : req.source()).name())
                .param("notes", blank(req.notes())).param("u", CurrentUser.idOrNull()).query(Long.class).single();
        return get(farmId, id);
    }

    @Transactional
    public Note update(Long farmId, Long id, NoteRequest req) {
        Note n = get(farmId, id);
        Fields f = fields(farmId, req);
        NoteStatus status = req.status() == null ? n.status() : req.status();
        if (status == NoteStatus.RECEIVED && n.status() != NoteStatus.RECEIVED) {
            throw ApiException.badRequest("Use Mark received to record what arrived.");
        }
        jdbc.sql("""
                update procurement_note set item_id = :item, title = :title, quantity = :qty, unit = :unit,
                  expected_price = :price, needed_by = :by, supplier_contact_id = :sup, notes = :notes, status = :st,
                  updated_at = now() where id = :id""")
                .param("item", f.itemId()).param("title", f.title()).param("qty", req.quantity()).param("unit", f.unit())
                .param("price", req.expectedPrice()).param("by", req.neededBy()).param("sup", f.supplierId())
                .param("notes", blank(req.notes())).param("st", status.name()).param("id", id).update();
        return get(farmId, id);
    }

    @Transactional
    public void delete(Long farmId, Long id) {
        get(farmId, id);
        jdbc.sql("delete from procurement_note where id = :id").param("id", id).update();
    }

    @Transactional
    public Note order(Long farmId, Long id, OrderRequest req) {
        Note n = get(farmId, id);
        if (n.status() != NoteStatus.PLANNED) {
            throw ApiException.badRequest("Only a planned note can be marked ordered.");
        }
        LocalDate on = req == null || req.orderedOn() == null ? DailyDates.today() : req.orderedOn();
        DailyDates.notFuture(on);
        jdbc.sql("update procurement_note set status = 'ORDERED', ordered_on = :on, updated_at = now() where id = :id")
                .param("on", on).param("id", id).update();
        return get(farmId, id);
    }

    @Transactional
    public Note receive(Long farmId, Long id, ReceiveRequest req) {
        Note n = get(farmId, id);
        if (n.status() == NoteStatus.RECEIVED || n.status() == NoteStatus.CANCELLED) {
            throw ApiException.badRequest("This note is already " + n.status().name().toLowerCase() + ".");
        }
        LocalDate on = req.receivedOn() == null ? DailyDates.today() : req.receivedOn();
        DailyDates.notFuture(on);
        BigDecimal qty = req.quantity() != null ? req.quantity() : n.quantity();
        BigDecimal total = req.totalAmount() != null ? req.totalAmount() : n.expectedPrice();
        if (total != null) {
            total = total.setScale(2, RoundingMode.HALF_UP);
        }
        boolean paid = req.paid() == null || req.paid();
        Long supplierId = n.supplier() == null ? null : n.supplier().id();
        Long txnId = null;
        Long entryId = null;
        if (n.item() != null) {
            if (qty == null) {
                throw ApiException.badRequest("Enter the quantity received.");
            }
            Item item = items.findById(n.item().id()).orElseThrow(() -> ApiException.badRequest("That item no longer exists."));
            if (supplierId == null) {
                supplierId = item.getSupplierContactId();
            }
            txnId = jdbc.sql("""
                    insert into item_txn (farm_id, item_id, txn_type, txn_date, quantity, cost, supplier_contact_id,
                      payment_method, paid, source_type, source_id, notes, created_by)
                    values (:farm, :item, 'PURCHASE', :date, :qty, :cost, :sup, :pm, :paid, 'PROCUREMENT', :note, :notes, :u)
                    returning id""")
                    .param("farm", farmId).param("item", item.getId()).param("date", on).param("qty", qty)
                    .param("cost", total).param("sup", supplierId)
                    .param("pm", req.paymentMethod() == null ? null : req.paymentMethod().name()).param("paid", paid)
                    .param("note", id).param("notes", "Procurement: " + n.title()).param("u", CurrentUser.idOrNull())
                    .query(Long.class).single();
            if (total != null && qty.signum() > 0) {
                item.setCostPerUnit(total.divide(qty, 2, RoundingMode.HALF_UP));
            }
            poster.sync(farmId);
        } else if (total != null && total.signum() > 0) {
            entryId = finance.insert(farmId, new FinanceRepository.Write(Direction.EXPENSE, Category.OTHER_EXPENSE, total,
                    on, null, paid ? Status.PAID : Status.PENDING, req.paymentMethod(),
                    n.supplier() == null ? null : n.supplier().name(), supplierId, null, null, null, false, null,
                    AllocKind.FARM, null, null, qty, n.unit(), n.title()), CurrentUser.idOrNull());
        }
        jdbc.sql("""
                update procurement_note set status = 'RECEIVED', received_on = :on, paid_amount = :paid,
                  quantity = coalesce(:qty, quantity), purchase_txn_id = :txn, finance_entry_id = :entry, updated_at = now()
                where id = :id""")
                .param("on", on).param("paid", total).param("qty", qty).param("txn", txnId).param("entry", entryId)
                .param("id", id).update();
        return get(farmId, id);
    }

    @Transactional
    public List<Note> fromFeedNeed(Long farmId, int days) {
        Set<Long> open = openItemIds(board(farmId, null).notes());
        List<Note> out = new ArrayList<>();
        for (var s : feed.need(farmId, days, true).shortfalls()) {
            if (open.contains(s.item().id())) {
                continue;
            }
            Item item = items.findById(s.item().id()).orElse(null);
            out.add(create(farmId, new NoteRequest(s.item().id(), s.item().name(),
                    s.quantityKg().setScale(1, RoundingMode.UP), s.item().unit(), s.expectedPrice(), s.neededBy(),
                    item == null ? null : item.getSupplierContactId(), null, NoteSource.FEED_NEED, null)));
            open.add(s.item().id());
        }
        return out;
    }

    record Fields(Long itemId, String title, String unit, Long supplierId) {
    }

    private Fields fields(Long farmId, NoteRequest req) {
        Item item = null;
        if (req.itemId() != null) {
            item = items.findById(req.itemId()).filter(i -> i.getFarmId().equals(farmId))
                    .orElseThrow(() -> ApiException.badRequest("That item isn't on this farm."));
        }
        String title = blank(req.title());
        if (title == null && item != null) {
            title = item.getName();
        }
        if (title == null) {
            throw ApiException.badRequest("Enter what to buy.");
        }
        Long supplier = req.supplierContactId();
        if (supplier != null && contacts.findByIdAndFarmId(supplier, farmId).isEmpty()) {
            throw ApiException.badRequest("That supplier isn't on this farm.");
        }
        if (supplier == null && item != null) {
            supplier = item.getSupplierContactId();
        }
        String unit = blank(req.unit());
        if (unit == null && item != null) {
            unit = item.getUnit().name();
        }
        return new Fields(item == null ? null : item.getId(), title, unit, supplier);
    }

    private static Set<Long> openItemIds(List<Note> notes) {
        Set<Long> out = new HashSet<>();
        for (Note n : notes) {
            if (n.item() != null && (n.status() == NoteStatus.PLANNED || n.status() == NoteStatus.ORDERED)) {
                out.add(n.item().id());
            }
        }
        return out;
    }

    private static Note note(java.sql.ResultSet rs) throws java.sql.SQLException {
        Long itemId = rs.getObject("item_id", Long.class);
        Long sup = rs.getObject("supplier_contact_id", Long.class);
        return new Note(rs.getLong("id"), itemId == null ? null
                : new ItemInfo(itemId, rs.getString("item_name"), rs.getString("item_type")),
                rs.getString("title"), rs.getBigDecimal("quantity"), rs.getString("unit"),
                rs.getBigDecimal("expected_price"), rs.getObject("needed_by", LocalDate.class),
                sup == null ? null : new Ref(sup, rs.getString("supplier_name")),
                NoteStatus.valueOf(rs.getString("status")), rs.getObject("ordered_on", LocalDate.class),
                rs.getObject("received_on", LocalDate.class), rs.getBigDecimal("paid_amount"),
                NoteSource.valueOf(rs.getString("source")), rs.getString("notes"));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
