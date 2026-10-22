package com.dairyfarm.breeding;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dairyfarm.finance.FinancePoster;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.animal.Animal;
import com.dairyfarm.animal.AnimalEnums.Acquisition;
import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.animal.AnimalEnums.Status;
import com.dairyfarm.animal.AnimalRepository;
import com.dairyfarm.animal.AnimalRequest;
import com.dairyfarm.animal.AnimalService;
import com.dairyfarm.breeding.BreedingRecord.Method;
import com.dairyfarm.breeding.BreedingRecord.Outcome;
import com.dairyfarm.breeding.BreedingRecord.Pregnancy;
import com.dairyfarm.breeding.BreedingViews.AnimalRef;
import com.dairyfarm.breeding.BreedingViews.CalfIn;
import com.dairyfarm.breeding.BreedingViews.CalfOut;
import com.dairyfarm.breeding.BreedingViews.Calving;
import com.dairyfarm.breeding.BreedingViews.CalvingRequest;
import com.dairyfarm.breeding.BreedingViews.CalvingResult;
import com.dairyfarm.breeding.BreedingViews.ColostrumRequest;
import com.dairyfarm.breeding.BreedingViews.Heat;
import com.dairyfarm.breeding.BreedingViews.HeatRequest;
import com.dairyfarm.breeding.BreedingViews.HeatSaved;
import com.dairyfarm.breeding.BreedingViews.Intensity;
import com.dairyfarm.breeding.BreedingViews.PregCheck;
import com.dairyfarm.breeding.BreedingViews.PregCheckRequest;
import com.dairyfarm.breeding.BreedingViews.SemenRef;
import com.dairyfarm.breeding.BreedingViews.ServiceRequest;
import com.dairyfarm.breeding.BreedingViews.ServiceView;
import com.dairyfarm.breeding.BreedingViews.Sign;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.common.Sex;
import com.dairyfarm.contact.Contact;
import com.dairyfarm.contact.ContactRepository;
import com.dairyfarm.contact.ContactResponse.Ref;
import com.dairyfarm.farm.Farm;
import com.dairyfarm.farm.FarmRepository;
import com.dairyfarm.farm.FarmService;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.record.FarmEvent;
import com.dairyfarm.record.FarmEvent.Details;
import com.dairyfarm.record.FarmEvent.EventType;
import com.dairyfarm.record.FarmEventRepository;
import com.dairyfarm.record.HealthService;
import com.dairyfarm.record.HealthViews.HealthRequest;
import com.dairyfarm.record.HealthViews.HealthType;
import com.dairyfarm.stock.Item;
import com.dairyfarm.stock.ItemRepository;
import com.dairyfarm.stock.StockRepository;
import com.dairyfarm.stock.StockRepository.TxnType;

/** Heats, services, pregnancy checks, calvings and the breeding settings. */
@Service
public class BreedingService {

    private FinancePoster finance;

    /** Keeps automatic finance entries in step with these records. */
    @Autowired
    void setFinance(FinancePoster finance) {
        this.finance = finance;
    }

    static final String SOURCE = "BREEDING";

    private final BreedingRecordRepository records;
    private final FarmEventRepository events;
    private final AnimalRepository animals;
    private final AnimalService animalService;
    private final ContactRepository contacts;
    private final ItemRepository items;
    private final StockRepository stock;
    private final FarmRepository farms;
    private final HealthService health;

    public BreedingService(BreedingRecordRepository records, FarmEventRepository events, AnimalRepository animals,
                           AnimalService animalService, ContactRepository contacts, ItemRepository items,
                           StockRepository stock, FarmRepository farms, HealthService health) {
        this.records = records;
        this.events = events;
        this.animals = animals;
        this.animalService = animalService;
        this.contacts = contacts;
        this.items = items;
        this.stock = stock;
        this.farms = farms;
        this.health = health;
    }

    // ---------- settings ----------

    @Transactional(readOnly = true)
    public BreedingSettings settings(Long farmId) {
        return farm(farmId).getBreedingSettings();
    }

    @Transactional
    public BreedingSettings updateSettings(Long farmId, BreedingSettings req) {
        BreedingSettings s = req.resolved();
        int prevTo = -1;
        for (var step : s.calfTaper()) {
            if (step.toDay() < step.fromDay()) {
                throw ApiException.badRequest("Each taper step must end on or after its first day.");
            }
            if (step.fromDay() <= prevTo) {
                throw ApiException.badRequest("Taper steps must not overlap and must be in order.");
            }
            prevTo = step.toDay();
        }
        if (s.maleSellToDay() <= s.maleSellFromDay()) {
            throw ApiException.badRequest("The male calf sell window must end after it starts.");
        }
        if (s.sellDaysOpen() <= s.targetDaysOpen()) {
            throw ApiException.badRequest("'Sell over' days open must be more than the target days open.");
        }
        farm(farmId).setBreedingSettings(s);
        return s;
    }

    // ---------- heats ----------

    @Transactional
    public HeatSaved createHeat(Long farmId, HeatRequest req) {
        Animal cow = breedable(farmId, req.animalId());
        DailyDates.notFuture(req.date());
        FarmEvent e = new FarmEvent(farmId, EventType.HEAT);
        e.setAnimalId(cow.getId());
        e.setEventDate(req.date());
        e.setEventTime(req.time());
        e.setSubType(req.intensity().name());
        e.setDetails(Details.heat(req.signs() == null ? List.of()
                : req.signs().stream().distinct().map(Enum::name).toList()));
        e.setNotes(blank(req.notes()));
        events.saveAndFlush(e);
        var s = settings(farmId);
        LocalTime seen = req.time() != null ? req.time() : farm(farmId).getSettings().morningMilking();
        Instant best = req.date().atTime(seen).plusHours(12).atZone(FarmService.FARM_ZONE).toInstant();
        return new HeatSaved(heatViews(farmId, List.of(e)).get(0), best, req.date().plusDays(s.heatCycleDays()));
    }

    @Transactional(readOnly = true)
    public List<Heat> heats(Long farmId, Long animalId, LocalDate from, LocalDate to) {
        Specification<FarmEvent> spec = (root, q, cb) -> cb.and(cb.equal(root.get("farmId"), farmId),
                cb.equal(root.get("eventType"), EventType.HEAT));
        if (animalId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("animalId"), animalId));
        }
        if (from != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("eventDate"), from));
        }
        if (to != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("eventDate"), to));
        }
        return heatViews(farmId, events.findAll(spec, Sort.by(Sort.Order.desc("eventDate"), Sort.Order.desc("id"))));
    }

    @Transactional
    public void deleteHeat(Long farmId, Long id) {
        FarmEvent e = events.findByIdAndFarmIdAndEventType(id, farmId, EventType.HEAT)
                .orElseThrow(() -> ApiException.notFound("Heat record not found."));
        events.delete(e);
    }

    List<Heat> heatViews(Long farmId, List<FarmEvent> list) {
        Map<Long, Animal> as = animalsById(farmId, list.stream().map(FarmEvent::getAnimalId).toList());
        Map<Long, Long> serviceByHeat = new HashMap<>();
        if (!list.isEmpty()) {
            records.findByFarmIdAndHeatEventIdIn(farmId, list.stream().map(FarmEvent::getId).toList())
                    .forEach(r -> serviceByHeat.put(r.getHeatEventId(), r.getId()));
        }
        return list.stream().map(e -> new Heat(e.getId(), ref(as.get(e.getAnimalId())), e.getEventDate(), e.getEventTime(),
                e.getDetails().signs() == null ? List.of() : e.getDetails().signs().stream().map(Sign::valueOf).toList(),
                e.getSubType() == null ? null : Intensity.valueOf(e.getSubType()), e.getNotes(),
                serviceByHeat.get(e.getId()))).toList();
    }

    // ---------- services ----------

    @Transactional(readOnly = true)
    public List<ServiceView> services(Long farmId, Long animalId, LocalDate from, LocalDate to, boolean showMoney) {
        List<BreedingRecord> list = animalId == null ? records.findByFarmId(farmId) : records.findByFarmIdAndDamId(farmId, animalId);
        List<BreedingRecord> filtered = list.stream()
                .filter(r -> from == null || date(r) != null && !date(r).isBefore(from))
                .filter(r -> to == null || date(r) != null && !date(r).isAfter(to))
                .sorted(Comparator.comparing(BreedingService::date, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(BreedingRecord::getId, Comparator.reverseOrder()))
                .toList();
        return views(farmId, filtered, showMoney);
    }

    @Transactional(readOnly = true)
    public ServiceView service(Long farmId, Long id, boolean showMoney) {
        return views(farmId, List.of(load(farmId, id)), showMoney).get(0);
    }

    @Transactional
    public ServiceView createService(Long farmId, ServiceRequest req, boolean showMoney) {
        Animal cow = breedable(farmId, req.animalId());
        BreedingRecord r = new BreedingRecord(farmId, cow.getId());
        r.setLactationNumber(cow.getLactationNumber());
        applyService(farmId, r, cow, req, true);
        records.saveAndFlush(r);
        useSemen(farmId, r);
        finance.sync(farmId);
        return views(farmId, List.of(r), showMoney).get(0);
    }

    @Transactional
    public ServiceView updateService(Long farmId, Long id, ServiceRequest req, boolean showMoney) {
        BreedingRecord r = load(farmId, id);
        if (!Objects.equals(r.getDamId(), req.animalId())) {
            throw ApiException.badRequest("The cow of a service can't be changed. Delete it and add a new one.");
        }
        if (r.getMethod() == Method.UNKNOWN) {
            throw ApiException.badRequest("This record was made by a calving without a service and can't be edited as a service.");
        }
        Animal cow = breedable(farmId, req.animalId());
        applyService(farmId, r, cow, req, showMoney);
        if (r.getCalvingDate() != null && r.getCalvingDate().isBefore(r.getServiceDate())) {
            throw ApiException.badRequest("The service can't be after the calving it led to.");
        }
        records.saveAndFlush(r);
        useSemen(farmId, r);
        finance.sync(farmId);
        return views(farmId, List.of(r), showMoney).get(0);
    }

    @Transactional
    public void deleteService(Long farmId, Long id) {
        BreedingRecord r = load(farmId, id);
        if (r.getCalvingDate() != null) {
            throw ApiException.conflict("This record has a calving. It can't be deleted.");
        }
        stock.removeForSource(farmId, SOURCE, r.getId());
        records.delete(r);
        finance.sync(farmId);
    }

    @Transactional
    public ServiceView pregnancyCheck(Long farmId, Long id, PregCheckRequest req, boolean showMoney) {
        BreedingRecord r = load(farmId, id);
        if (r.getServiceDate() == null) {
            throw ApiException.badRequest("This record has no service to check.");
        }
        if (r.getCalvingDate() != null) {
            throw ApiException.badRequest("She has already calved from this service.");
        }
        DailyDates.notFuture(req.date());
        int minDays = settings(farmId).resolved().pdMinDays();
        java.time.LocalDate earliest = r.getServiceDate().plusDays(minDays);
        if (req.date().isBefore(earliest)) {
            throw ApiException.badRequest("A pregnancy check needs at least " + minDays + " days after service — earliest "
                    + earliest.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH)) + ".");
        }
        Contact by = technician(farmId, req.checkedByContactId());
        r.setPregCheckDate(req.date());
        r.setPregCheckMethod(req.method());
        r.setPregnancyStatus(req.result());
        r.setPregCheckedByContactId(by == null ? null : by.getId());
        if (showMoney || req.cost() != null) {
            r.setPregCheckCost(req.cost());
        }
        if (req.result() == Pregnancy.PREGNANT && req.expectedDueDate() != null) {
            if (!req.expectedDueDate().isAfter(r.getServiceDate())) {
                throw ApiException.badRequest("The due date must be after the service date.");
            }
            r.setExpectedDueDate(req.expectedDueDate());
        }
        finance.sync(farmId);
        return views(farmId, List.of(r), showMoney).get(0);
    }

    // ---------- calving ----------

    @Transactional
    public CalvingResult calving(Long farmId, CalvingRequest req, boolean showMoney) {
        Animal dam = animals.findByIdAndFarmId(req.damId(), farmId)
                .orElseThrow(() -> ApiException.badRequest("That dam isn't on this farm."));
        if (dam.getSex() != Sex.FEMALE || dam.getStatus() != Status.ACTIVE) {
            throw ApiException.badRequest("Pick an active female for the dam.");
        }
        DailyDates.notFuture(req.date());
        if (dam.getDateOfBirth() != null && !req.date().isAfter(dam.getDateOfBirth())) {
            throw ApiException.badRequest("The calving date must be after the dam's date of birth.");
        }
        if (dam.getLastCalvingDate() != null && !req.date().isAfter(dam.getLastCalvingDate())) {
            throw ApiException.badRequest("She already has a calving on or after this date.");
        }
        BreedingRecord existing = req.serviceId() != null ? load(farmId, req.serviceId()) : openService(farmId, dam);
        if (existing != null) {
            if (!existing.getDamId().equals(dam.getId())) {
                throw ApiException.badRequest("That service is for another cow.");
            }
            if (existing.getCalvingDate() != null) {
                throw ApiException.badRequest("This service already has a calving.");
            }
            if (existing.getServiceDate() != null && req.date().isBefore(existing.getServiceDate())) {
                throw ApiException.badRequest("The calving can't be before the service date.");
            }
        }
        List<CalfIn> calvesIn = req.calves() == null ? List.of() : req.calves();
        if (req.outcome() == Outcome.LIVE && calvesIn.isEmpty()) {
            throw ApiException.badRequest("Add the calf.");
        }
        Set<String> newTags = new HashSet<>();
        for (CalfIn c : calvesIn) {
            if (create(c, req.outcome())) {
                if (blank(c.tag()) == null) {
                    throw ApiException.badRequest("Enter a tag for each calf, or untick 'Create calf record'.");
                }
                if (!newTags.add(c.tag().trim().toLowerCase()) || animals.existsByFarmIdAndTagIgnoreCase(farmId, c.tag().trim())) {
                    throw ApiException.conflict("Tag " + c.tag().trim() + " is already used on this farm.");
                }
            }
        }

        // Sire and breed for the calves, from the service.
        Long sireId = existing == null ? null : existing.getSireId();
        String sireTag = existing == null ? null : existing.getSireTag();
        String sireBreed = null;
        if (existing != null && existing.getSemenItemId() != null) {
            Item semen = items.findById(existing.getSemenItemId()).orElse(null);
            if (semen != null) {
                sireTag = semen.getDetails().bullCode() != null ? semen.getDetails().bullCode() : semen.getName();
                sireBreed = semen.getDetails().breed();
            }
        } else if (sireId != null) {
            sireBreed = animals.findById(sireId).map(Animal::getBreed).orElse(null);
        }
        String calfBreed = crossBreed(dam.getBreed(), sireBreed);
        Long damId = dam.getId();
        var damSpecies = dam.getSpecies();
        Long existingId = existing == null ? null : existing.getId();

        // Creating calves re-links parents with bulk updates that clear the persistence context, so do it first.
        List<BreedingRecord.Calf> calfRows = new ArrayList<>();
        List<AnimalRef> created = new ArrayList<>();
        for (CalfIn c : calvesIn) {
            Long calfId = null;
            if (create(c, req.outcome())) {
                var detail = animalService.create(farmId, new AnimalRequest(c.tag().trim(), blank(c.name()), null, damSpecies,
                        calfBreed, c.sex(), req.date(), null, c.birthWeightKg(), Category.CALF, null, damId, null,
                        sireId, sireId == null ? sireTag : null, Acquisition.BORN, null, null, null, null, null, 0));
                calfId = detail.id();
                created.add(new AnimalRef(detail.id(), detail.tag(), detail.name()));
            }
            calfRows.add(new BreedingRecord.Calf(calfId, c.sex(), c.birthWeightKg()));
        }

        dam = animals.findByIdAndFarmId(damId, farmId).orElseThrow();
        BreedingRecord r;
        if (existingId != null) {
            r = records.findById(existingId).orElseThrow();
        } else {
            r = new BreedingRecord(farmId, damId);
            r.setMethod(Method.UNKNOWN);
            r.setLactationNumber(dam.getLactationNumber());
        }
        r.setCalvingDate(req.date());
        r.setCalvingTime(req.time());
        r.setCalvingOutcome(req.outcome());
        r.setCalvingEase(req.ease());
        r.setCalves(calfRows);
        r.setPregnancyStatus(Pregnancy.PREGNANT);
        r.setColostrumGivenAt(req.colostrumGivenAt());
        r.setColostrumLitres(req.colostrumLitres());
        r.setRetainedPlacenta(Boolean.TRUE.equals(req.retainedPlacenta()));
        if (blank(req.notes()) != null) {
            r.setNotes(blank(req.notes()));
        }
        records.saveAndFlush(r);

        dam.setLastCalvingDate(req.date());
        if (req.outcome() != Outcome.ABORTED) {
            dam.setCategory(Category.MILKING);
            dam.setLactationNumber(dam.getLactationNumber() + 1);
        }
        animals.saveAndFlush(dam);

        if (Boolean.TRUE.equals(req.retainedPlacenta())) {
            LocalDate today = DailyDates.today();
            health.create(farmId, new HealthRequest(damId, req.date(), HealthType.ILLNESS, "Retained placenta after calving",
                    "Retained placenta", null, null, null, null, null, null, null,
                    today.isBefore(req.date()) ? req.date() : today, "Vet check — retained placenta", null), showMoney);
        }
        finance.sync(farmId);
        return new CalvingResult(views(farmId, List.of(r), showMoney).get(0), created);
    }

    @Transactional
    public ServiceView colostrum(Long farmId, Long id, ColostrumRequest req, boolean showMoney) {
        BreedingRecord r = load(farmId, id);
        if (r.getCalvingDate() == null) {
            throw ApiException.badRequest("This record has no calving yet.");
        }
        r.setColostrumGivenAt(req.givenAt());
        r.setColostrumLitres(req.litres());
        return views(farmId, List.of(r), showMoney).get(0);
    }

    // ---------- helpers ----------

    /** Her latest service this lactation that hasn't calved or been found not pregnant. */
    BreedingRecord openService(Long farmId, Animal dam) {
        return records.findByFarmIdAndDamId(farmId, dam.getId()).stream()
                .filter(r -> r.getServiceDate() != null && r.getCalvingDate() == null
                        && r.getPregnancyStatus() != Pregnancy.NOT_PREGNANT
                        && r.getLactationNumber() == dam.getLactationNumber())
                .max(Comparator.comparing(BreedingRecord::getServiceDate).thenComparing(BreedingRecord::getId))
                .orElse(null);
    }

    private void applyService(Long farmId, BreedingRecord r, Animal cow, ServiceRequest req, boolean showMoney) {
        DailyDates.notFuture(req.date());
        if (cow.getLastCalvingDate() != null && req.date().isBefore(cow.getLastCalvingDate())) {
            throw ApiException.badRequest("The service can't be before her last calving ("
                    + cow.getLastCalvingDate() + ").");
        }
        if (req.method() == Method.UNKNOWN) {
            throw ApiException.badRequest("Pick Natural, AI or ET.");
        }
        Long heatId = null;
        if (req.heatId() != null) {
            FarmEvent heat = events.findByIdAndFarmIdAndEventType(req.heatId(), farmId, EventType.HEAT)
                    .orElseThrow(() -> ApiException.badRequest("That heat record isn't on this farm."));
            if (!heat.getAnimalId().equals(cow.getId())) {
                throw ApiException.badRequest("That heat was recorded for another cow.");
            }
            heatId = heat.getId();
        }
        r.setServiceDate(req.date());
        r.setServiceTime(req.time());
        r.setHeatEventId(heatId);
        r.setMethod(req.method());
        if (req.method() == Method.NATURAL) {
            r.setSemenItemId(null);
            r.setStraws(null);
            r.setSemenCost(null);
            if (req.sireAnimalId() != null) {
                Animal bull = animals.findByIdAndFarmId(req.sireAnimalId(), farmId)
                        .orElseThrow(() -> ApiException.badRequest("That bull isn't on this farm."));
                if (bull.getSex() != Sex.MALE) {
                    throw ApiException.badRequest(bull.getTag() + " isn't male.");
                }
                r.setSireId(bull.getId());
                r.setSireTag(null);
            } else {
                r.setSireId(null);
                r.setSireTag(blank(req.sireTag()));
            }
            if (req.bullExposureStart() != null && req.bullExposureEnd() != null
                    && req.bullExposureEnd().isBefore(req.bullExposureStart())) {
                throw ApiException.badRequest("Bull exposure must end after it starts.");
            }
            r.setBullExposureStart(req.bullExposureStart());
            r.setBullExposureEnd(req.bullExposureEnd());
        } else {
            if (req.semenItemId() == null) {
                throw ApiException.badRequest("Pick the semen batch.");
            }
            Item semen = items.findByIdAndFarmIdAndItemType(req.semenItemId(), farmId, Item.Type.SEMEN)
                    .orElseThrow(() -> ApiException.badRequest("That semen batch isn't on this farm."));
            if (semen.getExpiryDate() != null && semen.getExpiryDate().isBefore(req.date())) {
                throw ApiException.badRequest(semen.getName() + " semen expired on " + semen.getExpiryDate() + ".");
            }
            int straws = req.straws() == null ? 1 : req.straws();
            r.setSemenItemId(semen.getId());
            r.setStraws(straws);
            r.setSemenCost(semen.getCostPerUnit() == null ? null
                    : semen.getCostPerUnit().multiply(BigDecimal.valueOf(straws)));
            r.setSireId(null);
            r.setSireTag(null);
            r.setBullExposureStart(null);
            r.setBullExposureEnd(null);
        }
        Contact tech = technician(farmId, req.technicianContactId());
        r.setTechnicianContactId(tech == null ? null : tech.getId());
        Contact owner = null;
        if (req.bullOwnerContactId() != null) {
            owner = contacts.findByIdAndFarmId(req.bullOwnerContactId(), farmId)
                    .orElseThrow(() -> ApiException.badRequest("That contact isn't on this farm."));
        }
        r.setBullOwnerContactId(owner == null ? null : owner.getId());
        if (showMoney || req.technicianFee() != null) {
            r.setTechnicianFee(req.technicianFee());
        }
        if (showMoney || req.bullFee() != null) {
            r.setBullFee(req.bullFee());
        }
        if (req.feePaid() != null) {
            r.setFeePaid(req.feePaid());
        }
        LocalDate due = req.expectedDueDate() != null ? req.expectedDueDate()
                : req.date().plusDays(settings(farmId).gestationFor(cow.getSpecies().name()));
        if (!due.isAfter(req.date())) {
            throw ApiException.badRequest("The due date must be after the service date.");
        }
        r.setExpectedDueDate(due);
        r.setNotes(blank(req.notes()));
    }

    /** Re-writes the straws this service used. */
    private void useSemen(Long farmId, BreedingRecord r) {
        stock.removeForSource(farmId, SOURCE, r.getId());
        if (r.getSemenItemId() == null) {
            return;
        }
        BigDecimal left = stock.onHand(r.getSemenItemId());
        if (left.compareTo(BigDecimal.valueOf(r.getStraws())) < 0) {
            throw ApiException.badRequest("Only " + left.stripTrailingZeros().toPlainString()
                    + " straws left in this batch.");
        }
        stock.add(farmId, r.getSemenItemId(), TxnType.USAGE, r.getServiceDate(), r.getServiceTime(),
                BigDecimal.valueOf(r.getStraws()), r.getSemenCost(), null, r.getDamId(), SOURCE, r.getId(), null);
    }

    private Contact technician(Long farmId, Long id) {
        if (id == null) {
            return null;
        }
        Contact c = contacts.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> ApiException.badRequest("That contact isn't on this farm."));
        if (!c.hasRole(Contact.Role.AI_TECH) && !c.hasRole(Contact.Role.VET)) {
            throw ApiException.badRequest(c.getName() + " isn't saved as an AI technician or vet. Edit the contact to add it.");
        }
        return c;
    }

    /** An active female heifer, milking or dry cow of this farm. */
    Animal breedable(Long farmId, Long id) {
        Animal a = animals.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> ApiException.badRequest("That animal isn't on this farm."));
        if (a.getStatus() != Status.ACTIVE || a.getSex() != Sex.FEMALE
                || !(a.getCategory() == Category.HEIFER || a.getCategory() == Category.MILKING || a.getCategory() == Category.DRY)) {
            throw ApiException.badRequest(a.getTag() + " isn't an active heifer or cow.");
        }
        return a;
    }

    private static boolean create(CalfIn c, Outcome outcome) {
        return c.createRecord() != null ? c.createRecord() : outcome == Outcome.LIVE;
    }

    static String crossBreed(String dam, String sire) {
        if (blank(sire) == null) {
            return blank(dam);
        }
        if (blank(dam) == null || dam.trim().equalsIgnoreCase(sire.trim())) {
            return sire.trim();
        }
        String b = dam.trim() + " × " + sire.trim();
        return b.length() > 60 ? b.substring(0, 60) : b;
    }

    private static LocalDate date(BreedingRecord r) {
        return r.getServiceDate() != null ? r.getServiceDate() : r.getCalvingDate();
    }

    List<ServiceView> views(Long farmId, List<BreedingRecord> list, boolean showMoney) {
        if (list.isEmpty()) {
            return List.of();
        }
        var s = settings(farmId);
        Set<Long> animalIds = new HashSet<>();
        Set<Long> contactIds = new HashSet<>();
        Set<Long> itemIds = new HashSet<>();
        for (BreedingRecord r : list) {
            animalIds.add(r.getDamId());
            if (r.getSireId() != null) {
                animalIds.add(r.getSireId());
            }
            r.getCalves().forEach(c -> {
                if (c.animalId() != null) {
                    animalIds.add(c.animalId());
                }
            });
            for (Long c : new Long[] {r.getTechnicianContactId(), r.getBullOwnerContactId(), r.getPregCheckedByContactId()}) {
                if (c != null) {
                    contactIds.add(c);
                }
            }
            if (r.getSemenItemId() != null) {
                itemIds.add(r.getSemenItemId());
            }
        }
        Map<Long, Animal> as = animalsById(farmId, animalIds);
        Map<Long, Contact> cs = contactIds.isEmpty() ? Map.of() : contacts.findByFarmIdAndIdIn(farmId, contactIds).stream()
                .collect(Collectors.toMap(Contact::getId, Function.identity()));
        Map<Long, Item> is = itemIds.isEmpty() ? Map.of() : items.findByFarmIdAndIdIn(farmId, itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        return list.stream().map(r -> {
            Item semen = r.getSemenItemId() == null ? null : is.get(r.getSemenItemId());
            boolean open = r.getCalvingDate() == null && r.getServiceDate() != null;
            LocalDate pdDue = open && r.getPregnancyStatus() == Pregnancy.UNKNOWN
                    ? r.getServiceDate().plusDays(s.pdAfterDays()) : null;
            LocalDate dryOff = open && r.getPregnancyStatus() == Pregnancy.PREGNANT && r.getExpectedDueDate() != null
                    ? r.getExpectedDueDate().minusDays(s.dryOffDaysBeforeDue()) : null;
            PregCheck check = r.getPregCheckDate() == null ? null : new PregCheck(r.getPregCheckDate(), r.getPregCheckMethod(),
                    r.getPregnancyStatus(), contactRef(cs, r.getPregCheckedByContactId()), money(showMoney, r.getPregCheckCost()));
            Calving calving = null;
            if (r.getCalvingDate() != null) {
                LocalTime t = r.getCalvingTime() != null ? r.getCalvingTime() : LocalTime.of(6, 0);
                calving = new Calving(r.getCalvingDate(), r.getCalvingTime(), r.getCalvingOutcome(), r.getCalvingEase(),
                        r.getCalves().stream().map(c -> new CalfOut(c.animalId() == null ? null : ref(as.get(c.animalId())),
                                c.sex(), c.birthWeightKg())).toList(),
                        r.getColostrumGivenAt(), r.getColostrumLitres(),
                        r.getCalvingDate().atTime(t).plusHours(s.colostrumHours()).atZone(FarmService.FARM_ZONE).toInstant(),
                        r.isRetainedPlacenta());
            }
            return new ServiceView(r.getId(), ref(as.get(r.getDamId())), r.getServiceDate(), r.getServiceTime(), r.getMethod(),
                    r.getHeatEventId(),
                    semen == null ? null : new SemenRef(semen.getId(), semen.getName(), semen.getDetails().bullCode(),
                            semen.getDetails().breed()),
                    r.getStraws(), r.getSireId() == null ? null : ref(as.get(r.getSireId())), r.getSireTag(),
                    r.getBullExposureStart(), r.getBullExposureEnd(), contactRef(cs, r.getTechnicianContactId()),
                    money(showMoney, r.getTechnicianFee()), contactRef(cs, r.getBullOwnerContactId()),
                    money(showMoney, r.getBullFee()), money(showMoney, r.getSemenCost()), money(showMoney, r.totalCost()),
                    r.isFeePaid(), r.getExpectedDueDate(), pdDue, dryOff, r.getPregnancyStatus(), check, calving,
                    r.getLactationNumber(), r.getNotes());
        }).toList();
    }

    private static Ref contactRef(Map<Long, Contact> cs, Long id) {
        return id == null ? null : Ref.of(cs.get(id));
    }

    static BigDecimal money(boolean show, BigDecimal v) {
        return show ? v : null;
    }

    Map<Long, Animal> animalsById(Long farmId, Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : animals.findByFarmIdAndIdIn(farmId, set).stream()
                .collect(Collectors.toMap(Animal::getId, Function.identity()));
    }

    static AnimalRef ref(Animal a) {
        return a == null ? null : new AnimalRef(a.getId(), a.getTag(), a.getName());
    }

    BreedingRecord load(Long farmId, Long id) {
        return records.findByIdAndFarmId(id, farmId).orElseThrow(() -> ApiException.notFound("Breeding record not found."));
    }

    private Farm farm(Long farmId) {
        return farms.findById(farmId).orElseThrow(() -> ApiException.notFound("Farm not found."));
    }

    static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
