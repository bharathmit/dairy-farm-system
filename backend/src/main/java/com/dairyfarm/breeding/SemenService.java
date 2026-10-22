package com.dairyfarm.breeding;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dairyfarm.finance.FinancePoster;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.breeding.BreedingViews.AdjustRequest;
import com.dairyfarm.breeding.BreedingViews.SemenBatch;
import com.dairyfarm.breeding.BreedingViews.SemenList;
import com.dairyfarm.breeding.BreedingViews.SemenRequest;
import com.dairyfarm.breeding.BreedingViews.SemenTotals;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.contact.Contact;
import com.dairyfarm.contact.ContactRepository;
import com.dairyfarm.contact.ContactResponse.Ref;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.stock.Item;
import com.dairyfarm.stock.ItemRepository;
import com.dairyfarm.stock.StockRepository;
import com.dairyfarm.stock.StockRepository.Level;
import com.dairyfarm.stock.StockRepository.TxnType;

/** Semen batches (one per bull batch) and their straw counts. */
@Service
public class SemenService {

    private FinancePoster finance;

    /** Keeps automatic finance entries in step with these records. */
    @Autowired
    void setFinance(FinancePoster finance) {
        this.finance = finance;
    }

    static final int LOW = 5;

    private final ItemRepository items;
    private final StockRepository stock;
    private final ContactRepository contacts;

    public SemenService(ItemRepository items, StockRepository stock, ContactRepository contacts) {
        this.items = items;
        this.stock = stock;
        this.contacts = contacts;
    }

    @Transactional(readOnly = true)
    public SemenList list(Long farmId, boolean showMoney) {
        List<Item> all = items.findByFarmIdAndItemTypeOrderByNameAsc(farmId, Item.Type.SEMEN);
        List<SemenBatch> batches = new ArrayList<>(views(farmId, all, showMoney));
        batches.sort(Comparator.comparing((SemenBatch b) -> b.straws() == 0).thenComparing(SemenBatch::bullName, String.CASE_INSENSITIVE_ORDER));
        int straws = 0, low = 0, expiring = 0;
        for (SemenBatch b : batches) {
            straws += b.straws();
            if (b.low() && b.straws() > 0) {
                low++;
            }
            if (b.expiringSoon() && b.straws() > 0) {
                expiring++;
            }
        }
        return new SemenList(new SemenTotals(straws, low, expiring), batches);
    }

    @Transactional(readOnly = true)
    public SemenBatch get(Long farmId, Long id, boolean showMoney) {
        return views(farmId, List.of(load(farmId, id)), showMoney).get(0);
    }

    @Transactional
    public SemenBatch create(Long farmId, SemenRequest req, boolean showMoney) {
        Item i = new Item(farmId, Item.Type.SEMEN, Item.Unit.STRAW);
        apply(farmId, i, req, true);
        items.saveAndFlush(i);
        int straws = req.straws() == null ? 0 : req.straws();
        if (straws > 0) {
            stock.add(farmId, i.getId(), TxnType.PURCHASE, DailyDates.today(), null, BigDecimal.valueOf(straws),
                    i.getCostPerUnit() == null ? null : i.getCostPerUnit().multiply(BigDecimal.valueOf(straws)),
                    i.getSupplierContactId(), null, null, null, "Opening stock");
        }
        finance.sync(farmId);
        return views(farmId, List.of(i), showMoney).get(0);
    }

    @Transactional
    public SemenBatch update(Long farmId, Long id, SemenRequest req, boolean showMoney) {
        Item i = load(farmId, id);
        apply(farmId, i, req, showMoney);
        items.flush();
        finance.sync(farmId);
        return views(farmId, List.of(i), showMoney).get(0);
    }

    @Transactional
    public SemenBatch adjust(Long farmId, Long id, AdjustRequest req, boolean showMoney) {
        Item i = load(farmId, id);
        if (req.quantity() == 0) {
            throw ApiException.badRequest("Enter a change other than 0.");
        }
        BigDecimal now = stock.onHand(i.getId());
        if (now.add(BigDecimal.valueOf(req.quantity())).signum() < 0) {
            throw ApiException.badRequest("Only " + now.stripTrailingZeros().toPlainString() + " straws are in stock.");
        }
        stock.add(farmId, i.getId(), TxnType.ADJUSTMENT, DailyDates.today(), null, BigDecimal.valueOf(req.quantity()),
                null, null, null, null, null, blank(req.reason()));
        return views(farmId, List.of(i), showMoney).get(0);
    }

    @Transactional
    public void delete(Long farmId, Long id) {
        Item i = load(farmId, id);
        if (stock.usedOnRecords(i.getId())) {
            throw ApiException.conflict("This batch was used on services and is kept for history.");
        }
        items.delete(i);
        finance.sync(farmId);
    }

    private void apply(Long farmId, Item i, SemenRequest req, boolean showMoney) {
        Contact supplier = null;
        if (req.supplierContactId() != null) {
            supplier = contacts.findByIdAndFarmId(req.supplierContactId(), farmId)
                    .orElseThrow(() -> ApiException.badRequest("That supplier isn't on this farm."));
        }
        i.setName(req.bullName().trim());
        i.setCategory(blank(req.breed()));
        i.setDetails(new Item.Details(blank(req.bullCode()), blank(req.breed()), Boolean.TRUE.equals(req.sexed())));
        i.setSupplierContactId(supplier == null ? null : supplier.getId());
        i.setBatchNo(blank(req.batchNo()));
        i.setExpiryDate(req.expiryDate());
        if (showMoney || req.costPerStraw() != null) {
            i.setCostPerUnit(req.costPerStraw());
        }
        i.setNotes(blank(req.notes()));
    }

    private List<SemenBatch> views(Long farmId, List<Item> list, boolean showMoney) {
        Map<Long, Level> levels = stock.levels(list.stream().map(Item::getId).toList());
        Set<Long> supplierIds = new HashSet<>();
        list.forEach(i -> {
            if (i.getSupplierContactId() != null) {
                supplierIds.add(i.getSupplierContactId());
            }
        });
        Map<Long, Contact> cs = supplierIds.isEmpty() ? Map.of() : contacts.findByFarmIdAndIdIn(farmId, supplierIds).stream()
                .collect(Collectors.toMap(Contact::getId, Function.identity()));
        LocalDate today = DailyDates.today();
        return list.stream().map(i -> {
            Level l = levels.get(i.getId());
            int straws = l == null ? 0 : l.onHand().intValue();
            return new SemenBatch(i.getId(), i.getName(), i.getDetails().bullCode(), i.getDetails().breed(),
                    i.getSupplierContactId() == null ? null : Ref.of(cs.get(i.getSupplierContactId())), i.getBatchNo(),
                    i.getExpiryDate(), showMoney ? i.getCostPerUnit() : null, Boolean.TRUE.equals(i.getDetails().sexed()),
                    straws, straws < LOW,
                    i.getExpiryDate() != null && !i.getExpiryDate().isAfter(today.plusDays(30)),
                    straws == 0 && l != null ? l.lastTxnDate() : null, i.getNotes());
        }).toList();
    }

    private Item load(Long farmId, Long id) {
        return items.findByIdAndFarmIdAndItemType(id, farmId, Item.Type.SEMEN)
                .orElseThrow(() -> ApiException.notFound("Semen batch not found."));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
