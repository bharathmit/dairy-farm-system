package com.dairyfarm.record;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dairyfarm.finance.FinancePoster;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.animal.Animal;
import com.dairyfarm.animal.AnimalRepository;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.common.PageResponse;
import com.dairyfarm.contact.Contact;
import com.dairyfarm.contact.ContactRepository;
import com.dairyfarm.contact.ContactResponse.Ref;
import com.dairyfarm.contact.ContactService;
import com.dairyfarm.farm.FarmRepository;
import com.dairyfarm.farm.FarmService;
import com.dairyfarm.milk.MilkRepository;
import com.dairyfarm.milk.MilkService;
import com.dairyfarm.record.FarmEvent.Details;
import com.dairyfarm.record.FarmEvent.EventType;
import com.dairyfarm.record.HealthViews.BatchResult;
import com.dairyfarm.record.HealthViews.Fields;
import com.dairyfarm.record.HealthViews.HealthBatchRequest;
import com.dairyfarm.record.HealthViews.HealthRecord;
import com.dairyfarm.record.HealthViews.HealthRequest;
import com.dairyfarm.record.HealthViews.HealthType;
import com.dairyfarm.record.HealthViews.Overview;
import com.dairyfarm.record.HealthViews.Withdrawal;
import com.dairyfarm.record.HealthViews.Withdrawals;

/** Illness, treatment, vaccination, deworming and injury records, with milk/meat withdrawal and follow-ups. */
@Service
public class HealthService {

    private FinancePoster finance;

    /** Keeps automatic finance entries in step with these records. */
    @Autowired
    void setFinance(FinancePoster finance) {
        this.finance = finance;
    }

    private final FarmEventRepository events;
    private final AnimalRepository animals;
    private final ContactService contactService;
    private final ContactRepository contacts;
    private final FarmRepository farms;
    private final MilkService milkService;
    private final MilkRepository milk;

    public HealthService(FarmEventRepository events, AnimalRepository animals, ContactService contactService,
                         ContactRepository contacts, FarmRepository farms, MilkService milkService, MilkRepository milk) {
        this.events = events;
        this.animals = animals;
        this.contactService = contactService;
        this.contacts = contacts;
        this.farms = farms;
        this.milkService = milkService;
        this.milk = milk;
    }

    @Transactional(readOnly = true)
    public PageResponse<HealthRecord> list(Long farmId, Long animalId, HealthType type, LocalDate from, LocalDate to,
                                           int page, int size, boolean showMoney) {
        Specification<FarmEvent> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("farmId"), farmId), cb.equal(root.get("eventType"), EventType.HEALTH));
        if (animalId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("animalId"), animalId));
        }
        if (type != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("subType"), type.name()));
        }
        if (from != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("eventDate"), from));
        }
        if (to != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("eventDate"), to));
        }
        Page<FarmEvent> result = events.findAll(spec, PageRequest.of(page, Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Order.desc("eventDate"), Sort.Order.desc("id"))));
        List<HealthRecord> views = views(farmId, result.getContent(), showMoney);
        return new PageResponse<>(views, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public HealthRecord get(Long farmId, Long id, boolean showMoney) {
        return views(farmId, List.of(load(farmId, id)), showMoney).get(0);
    }

    @Transactional
    public HealthRecord create(Long farmId, HealthRequest req, boolean showMoney) {
        requireAnimals(farmId, List.of(req.animalId()));
        Contact vet = vet(farmId, req);
        FarmEvent e = newEvent(farmId, req.animalId(), null);
        apply(farmId, e, req, vet, true);
        events.saveAndFlush(e);
        milkService.refresh(farmId, List.of(req.animalId()), req.date(), DailyDates.today());
        finance.sync(farmId);
        return views(farmId, List.of(e), showMoney).get(0);
    }

    @Transactional
    public BatchResult createBatch(Long farmId, HealthBatchRequest req, boolean showMoney) {
        Set<Long> ids = new LinkedHashSet<>(req.animalIds());
        requireAnimals(farmId, ids);
        Contact vet = vet(farmId, req);
        UUID batchId = UUID.randomUUID();
        List<FarmEvent> created = new ArrayList<>();
        for (Long id : ids) {
            FarmEvent e = newEvent(farmId, id, batchId);
            apply(farmId, e, req, vet, true);
            created.add(e);
        }
        events.saveAllAndFlush(created);
        milkService.refresh(farmId, ids, req.date(), DailyDates.today());
        finance.sync(farmId);
        return new BatchResult(batchId, created.size(), views(farmId, created, showMoney));
    }

    @Transactional
    public HealthRecord update(Long farmId, Long id, HealthRequest req, boolean showMoney) {
        FarmEvent e = load(farmId, id);
        requireAnimals(farmId, List.of(req.animalId()));
        Contact vet = vet(farmId, req);
        Long oldAnimal = e.getAnimalId();
        LocalDate oldDate = e.getEventDate();
        e.setAnimalId(req.animalId());
        apply(farmId, e, req, vet, showMoney);
        events.flush();
        LocalDate from = oldDate.isBefore(req.date()) ? oldDate : req.date();
        milkService.refresh(farmId, new HashSet<>(List.of(oldAnimal, req.animalId())), from, DailyDates.today());
        finance.sync(farmId);
        return views(farmId, List.of(e), showMoney).get(0);
    }

    @Transactional
    public HealthRecord followUp(Long farmId, Long id, boolean done, boolean showMoney) {
        FarmEvent e = load(farmId, id);
        if (e.getNextActionDate() == null) {
            throw ApiException.badRequest("This record has no follow-up.");
        }
        e.setNextActionDone(done);
        return views(farmId, List.of(e), showMoney).get(0);
    }

    @Transactional
    public void delete(Long farmId, Long id) {
        FarmEvent e = load(farmId, id);
        events.delete(e);
        events.flush();
        milkService.refresh(farmId, List.of(e.getAnimalId()), e.getEventDate(), DailyDates.today());
        finance.sync(farmId);
    }

    @Transactional(readOnly = true)
    public Overview overview(Long farmId, boolean showMoney) {
        LocalDate today = DailyDates.today();
        Instant now = Instant.now();
        var due = events.findDueFollowUps(farmId, today.plusDays(7));
        var recent = events.findAll((root, q, cb) -> cb.and(cb.equal(root.get("farmId"), farmId),
                        cb.equal(root.get("eventType"), EventType.HEALTH)),
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("eventDate"), Sort.Order.desc("id")))).getContent();
        return new Overview(events.countAnimalsInWithdrawal(farmId, now), events.countOpenFollowUps(farmId),
                events.countHealthBetween(farmId, today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth())),
                views(farmId, due, showMoney), views(farmId, recent, showMoney));
    }

    @Transactional(readOnly = true)
    public Withdrawals withdrawals(Long farmId, boolean showMoney) {
        Instant now = Instant.now();
        List<FarmEvent> active = events.findMilkWithdrawalsEnding(farmId, now, now.plus(3650, ChronoUnit.DAYS));
        List<FarmEvent> ended = new ArrayList<>(events.findMilkWithdrawalsEnding(farmId, now.minus(14, ChronoUnit.DAYS), now));
        java.util.Collections.reverse(ended);
        return new Withdrawals(withdrawalViews(farmId, active, showMoney), withdrawalViews(farmId, ended, showMoney));
    }

    private List<Withdrawal> withdrawalViews(Long farmId, List<FarmEvent> list, boolean showMoney) {
        List<HealthRecord> views = views(farmId, list, showMoney);
        List<Withdrawal> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            FarmEvent e = list.get(i);
            LocalDate end = e.getMilkWithdrawalUntil().atZone(FarmService.FARM_ZONE).toLocalDate();
            out.add(new Withdrawal(views.get(i), e.getMilkWithdrawalUntil(),
                    milk.withheldLitres(farmId, e.getAnimalId(), e.getEventDate(), end)));
        }
        return out;
    }

    // ---- helpers

    private FarmEvent newEvent(Long farmId, Long animalId, UUID batchId) {
        FarmEvent e = new FarmEvent(farmId, EventType.HEALTH);
        e.setAnimalId(animalId);
        e.setBatchId(batchId);
        return e;
    }

    private void apply(Long farmId, FarmEvent e, Fields req, Contact vet, boolean setCost) {
        DailyDates.notFuture(req.date());
        if (req.nextActionDate() != null && req.nextActionDate().isBefore(req.date())) {
            throw ApiException.badRequest("The next action can't be before the record date.");
        }
        e.setEventDate(req.date());
        e.setSubType(req.type().name());
        e.setContactId(vet == null ? null : vet.getId());
        if (setCost || req.cost() != null) {
            e.setCost(req.cost());
        }
        int milkDays = req.milkWithdrawalDays() == null ? 0 : req.milkWithdrawalDays();
        int meatDays = req.meatWithdrawalDays() == null ? 0 : req.meatWithdrawalDays();
        var evening = farms.findById(farmId).orElseThrow().getSettings().eveningMilking();
        e.setMilkWithdrawalUntil(milkDays == 0 ? null
                : req.date().plusDays(milkDays).atTime(evening).atZone(FarmService.FARM_ZONE).toInstant());
        e.setMeatWithdrawalUntil(meatDays == 0 ? null : req.date().plusDays(meatDays));
        boolean dateChanged = !Objects.equals(e.getNextActionDate(), req.nextActionDate());
        e.setNextActionDate(req.nextActionDate());
        e.setNextAction(req.nextActionDate() == null ? null : blank(req.nextAction()) == null ? "Follow-up" : blank(req.nextAction()));
        if (dateChanged || req.nextActionDate() == null) {
            e.setNextActionDone(false);
        }
        e.setDetails(Details.health(blank(req.symptoms()), blank(req.diagnosis()), blank(req.medicine()), blank(req.dose()),
                blank(req.route()), req.milkWithdrawalDays(), req.meatWithdrawalDays()));
        e.setNotes(blank(req.notes()));
    }

    private Contact vet(Long farmId, Fields req) {
        return contactService.require(farmId, req.vetContactId(), Contact.Role.VET);
    }

    private void requireAnimals(Long farmId, Collection<Long> ids) {
        Set<Long> found = animals.findByFarmIdAndIdIn(farmId, ids).stream().map(Animal::getId).collect(Collectors.toSet());
        for (Long id : ids) {
            if (!found.contains(id)) {
                throw ApiException.badRequest("Animal " + id + " isn't on this farm.");
            }
        }
    }

    private List<HealthRecord> views(Long farmId, List<FarmEvent> list, boolean showMoney) {
        Set<Long> animalIds = new HashSet<>();
        Set<Long> contactIds = new HashSet<>();
        list.forEach(e -> {
            animalIds.add(e.getAnimalId());
            if (e.getContactId() != null) {
                contactIds.add(e.getContactId());
            }
        });
        Map<Long, Animal> as = animalIds.isEmpty() ? Map.of() : animals.findByFarmIdAndIdIn(farmId, animalIds).stream()
                .collect(Collectors.toMap(Animal::getId, Function.identity()));
        Map<Long, Contact> cs = contactIds.isEmpty() ? Map.of() : contacts.findByFarmIdAndIdIn(farmId, contactIds).stream()
                .collect(Collectors.toMap(Contact::getId, Function.identity()));
        return list.stream().map(e -> {
            Animal a = as.get(e.getAnimalId());
            Details d = e.getDetails();
            return new HealthRecord(e.getId(), e.getAnimalId(), a == null ? null : a.getTag(), a == null ? null : a.getName(),
                    e.getEventDate(), HealthType.valueOf(e.getSubType()), d.symptoms(), d.diagnosis(), d.medicine(),
                    d.dose(), d.route(), e.getContactId() == null ? null : Ref.of(cs.get(e.getContactId())),
                    showMoney ? e.getCost() : null, d.milkWithdrawalDays(), d.meatWithdrawalDays(),
                    e.getMilkWithdrawalUntil(), e.getMeatWithdrawalUntil(), e.getNextActionDate(), e.getNextAction(),
                    e.isNextActionDone(), e.getBatchId(), e.getNotes());
        }).toList();
    }

    private FarmEvent load(Long farmId, Long id) {
        return events.findByIdAndFarmIdAndEventType(id, farmId, EventType.HEALTH)
                .orElseThrow(() -> ApiException.notFound("Health record not found."));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
