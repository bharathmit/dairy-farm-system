package com.dairyfarm.record;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.animal.Animal;
import com.dairyfarm.animal.AnimalRepository;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.record.FarmEvent.Details;
import com.dairyfarm.record.FarmEvent.EventType;
import com.dairyfarm.record.MeasurementViews.AnimalInfo;
import com.dairyfarm.record.MeasurementViews.Growth;
import com.dairyfarm.record.MeasurementViews.Latest;
import com.dairyfarm.record.MeasurementViews.Measurement;
import com.dairyfarm.record.MeasurementViews.MeasurementRequest;
import com.dairyfarm.record.MeasurementViews.Method;

/** Weight, height and body condition score records. The animal's current weight follows the latest weighing. */
@Service
public class MeasurementService {

    private final FarmEventRepository events;
    private final AnimalRepository animals;

    public MeasurementService(FarmEventRepository events, AnimalRepository animals) {
        this.events = events;
        this.animals = animals;
    }

    @Transactional(readOnly = true)
    public Growth growth(Long farmId, Long animalId) {
        Animal a = animal(farmId, animalId);
        List<FarmEvent> list = list(farmId, animalId);
        List<Measurement> items = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            items.add(view(list.get(i), previousWeighed(list, i)));
        }
        Latest latest = null;
        if (!list.isEmpty()) {
            latest = new Latest(list.get(0).getEventDate(),
                    first(list, FarmEvent::getWeightKg), first(list, FarmEvent::getHeightCm), first(list, FarmEvent::getBcs));
        }
        return new Growth(new AnimalInfo(a.getId(), a.getTag(), a.getName(), a.getCategory(), a.getDateOfBirth()),
                latest, adg90(list), items);
    }

    @Transactional
    public Measurement create(Long farmId, Long animalId, MeasurementRequest req) {
        animal(farmId, animalId);
        FarmEvent e = new FarmEvent(farmId, EventType.MEASUREMENT);
        e.setAnimalId(animalId);
        apply(e, req);
        events.save(e);
        syncWeight(farmId, animalId);
        return find(farmId, animalId, e.getId());
    }

    @Transactional
    public Measurement update(Long farmId, Long id, MeasurementRequest req) {
        FarmEvent e = load(farmId, id);
        apply(e, req);
        events.flush();
        syncWeight(farmId, e.getAnimalId());
        return find(farmId, e.getAnimalId(), id);
    }

    @Transactional
    public void delete(Long farmId, Long id) {
        FarmEvent e = load(farmId, id);
        events.delete(e);
        events.flush();
        syncWeight(farmId, e.getAnimalId());
    }

    private Measurement find(Long farmId, Long animalId, Long id) {
        List<FarmEvent> list = list(farmId, animalId);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) {
                return view(list.get(i), previousWeighed(list, i));
            }
        }
        throw ApiException.notFound("Measurement not found.");
    }

    private void syncWeight(Long farmId, Long animalId) {
        Animal a = animal(farmId, animalId);
        list(farmId, animalId).stream().filter(m -> m.getWeightKg() != null).findFirst()
                .ifPresent(m -> a.setCurrentWeightKg(m.getWeightKg()));
    }

    private static void apply(FarmEvent e, MeasurementRequest req) {
        DailyDates.notFuture(req.date());
        if (req.weightKg() == null && req.heightCm() == null && req.bcs() == null) {
            throw ApiException.badRequest("Enter a weight, height or body condition score.");
        }
        if (req.bcs() != null && req.bcs().multiply(BigDecimal.valueOf(4)).stripTrailingZeros().scale() > 0) {
            throw ApiException.badRequest("Body condition score goes in steps of 0.25.");
        }
        e.setEventDate(req.date());
        e.setSubType(req.method().name());
        e.setWeightKg(req.weightKg());
        e.setHeightCm(req.heightCm());
        e.setBcs(req.bcs());
        e.setDetails(new Details(null, null, null, null, null, null, null, req.heartGirthCm()));
        e.setNotes(req.notes() == null || req.notes().isBlank() ? null : req.notes().trim());
    }

    /** Newest first; same-day entries by id. */
    private List<FarmEvent> list(Long farmId, Long animalId) {
        return events.findByFarmIdAndAnimalIdAndEventTypeOrderByEventDateDescIdDesc(farmId, animalId, EventType.MEASUREMENT);
    }

    private static FarmEvent previousWeighed(List<FarmEvent> list, int i) {
        if (list.get(i).getWeightKg() == null) {
            return null;
        }
        for (int j = i + 1; j < list.size(); j++) {
            if (list.get(j).getWeightKg() != null) {
                return list.get(j);
            }
        }
        return null;
    }

    private static Measurement view(FarmEvent e, FarmEvent prev) {
        BigDecimal gain = null;
        BigDecimal adg = null;
        if (prev != null) {
            gain = e.getWeightKg().subtract(prev.getWeightKg());
            long days = ChronoUnit.DAYS.between(prev.getEventDate(), e.getEventDate());
            if (days > 0) {
                adg = gain.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
            }
        }
        return new Measurement(e.getId(), e.getAnimalId(), e.getEventDate(), Method.valueOf(e.getSubType()),
                e.getWeightKg(), e.getHeightCm(), e.getDetails().heartGirthCm(), e.getBcs(), e.getNotes(), gain, adg);
    }

    /** Gain per day between the latest weighing and the oldest one within the 90 days before it. */
    private static BigDecimal adg90(List<FarmEvent> list) {
        List<FarmEvent> weighed = list.stream().filter(m -> m.getWeightKg() != null).toList();
        if (weighed.size() < 2) {
            return null;
        }
        FarmEvent last = weighed.get(0);
        FarmEvent base = null;
        for (FarmEvent m : weighed.subList(1, weighed.size())) {
            if (!m.getEventDate().isBefore(last.getEventDate().minusDays(90))) {
                base = m;
            }
        }
        if (base == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(base.getEventDate(), last.getEventDate());
        return days <= 0 ? null : last.getWeightKg().subtract(base.getWeightKg())
                .divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal first(List<FarmEvent> list, java.util.function.Function<FarmEvent, BigDecimal> f) {
        return list.stream().map(f).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private Animal animal(Long farmId, Long id) {
        return animals.findByIdAndFarmId(id, farmId).orElseThrow(() -> ApiException.notFound("Animal not found."));
    }

    private FarmEvent load(Long farmId, Long id) {
        return events.findByIdAndFarmIdAndEventType(id, farmId, EventType.MEASUREMENT)
                .orElseThrow(() -> ApiException.notFound("Measurement not found."));
    }
}
