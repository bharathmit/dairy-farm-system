package com.dairyfarm.breeding;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.animal.Animal;
import com.dairyfarm.animal.AnimalEnums.Status;
import com.dairyfarm.animal.AnimalRepository;
import com.dairyfarm.breeding.BreedingRecord.Outcome;
import com.dairyfarm.breeding.BreedingViews.Family;
import com.dairyfarm.breeding.BreedingViews.FamilyAnimal;
import com.dairyfarm.breeding.BreedingViews.Grandparents;
import com.dairyfarm.breeding.BreedingViews.MateCheck;
import com.dairyfarm.breeding.BreedingViews.NextTag;
import com.dairyfarm.breeding.BreedingViews.Node;
import com.dairyfarm.breeding.BreedingViews.Offspring;
import com.dairyfarm.breeding.BreedingViews.Parents;
import com.dairyfarm.breeding.BreedingViews.Sibling;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.common.Sex;
import com.dairyfarm.stock.Item;
import com.dairyfarm.stock.ItemRepository;

/** Pedigree (2 generations), siblings, offspring, the mate-risk check and the next free tag. */
@Service
public class FamilyService {

    private static final Pattern NUMBERED = Pattern.compile("^(.*?)(\\d+)$");

    private final AnimalRepository animals;
    private final BreedingRecordRepository records;
    private final ItemRepository items;
    private final BreedingSql sql;

    public FamilyService(AnimalRepository animals, BreedingRecordRepository records, ItemRepository items, BreedingSql sql) {
        this.animals = animals;
        this.records = records;
        this.items = items;
        this.sql = sql;
    }

    @Transactional(readOnly = true)
    public Family family(Long farmId, Long id) {
        List<Animal> herd = animals.findByFarmIdOrderByTagAsc(farmId);
        Map<Long, Animal> byId = herd.stream().collect(Collectors.toMap(Animal::getId, Function.identity()));
        Animal a = byId.get(id);
        if (a == null) {
            throw ApiException.notFound("Animal not found.");
        }
        Node dam = node(byId, a.getDamId(), a.getDamTag());
        Node sire = node(byId, a.getSireId(), a.getSireTag());
        Animal damA = a.getDamId() == null ? null : byId.get(a.getDamId());
        Animal sireA = a.getSireId() == null ? null : byId.get(a.getSireId());
        Grandparents gp = new Grandparents(
                damA == null ? null : node(byId, damA.getDamId(), damA.getDamTag()),
                damA == null ? null : node(byId, damA.getSireId(), damA.getSireTag()),
                sireA == null ? null : node(byId, sireA.getDamId(), sireA.getDamTag()),
                sireA == null ? null : node(byId, sireA.getSireId(), sireA.getSireTag()));

        List<Sibling> siblings = new ArrayList<>();
        for (Animal x : herd) {
            if (x.getId().equals(a.getId())) {
                continue;
            }
            boolean sameDam = a.getDamId() != null && a.getDamId().equals(x.getDamId());
            boolean sameSire = a.getSireId() != null ? a.getSireId().equals(x.getSireId())
                    : a.getSireTag() != null && x.getSireId() == null && a.getSireTag().equalsIgnoreCase(Objects.toString(x.getSireTag(), ""));
            if (sameDam || sameSire) {
                siblings.add(new Sibling(BreedingService.ref(x), sameDam && sameSire ? "FULL" : sameDam ? "HALF_DAM" : "HALF_SIRE"));
            }
        }

        List<BreedingRecord> recs = a.getSex() == Sex.FEMALE ? records.findByFarmIdAndDamId(farmId, id) : List.of();
        Map<Long, BreedingRecord> birthOf = new HashMap<>();
        recs.forEach(r -> r.getCalves().forEach(c -> {
            if (c.animalId() != null) {
                birthOf.put(c.animalId(), r);
            }
        }));
        Map<Long, Item> semen = items.findByFarmIdAndIdIn(farmId, recs.stream().map(BreedingRecord::getSemenItemId)
                .filter(Objects::nonNull).toList()).stream().collect(Collectors.toMap(Item::getId, Function.identity()));
        List<Offspring> offspring = new ArrayList<>();
        for (Animal x : herd) {
            boolean child = a.getSex() == Sex.FEMALE ? id.equals(x.getDamId()) : id.equals(x.getSireId());
            if (!child) {
                continue;
            }
            BreedingRecord br = birthOf.get(x.getId());
            String sireLabel = a.getSex() == Sex.FEMALE
                    ? (x.getSireId() != null && byId.containsKey(x.getSireId()) ? byId.get(x.getSireId()).getTag() : x.getSireTag())
                    : (x.getDamId() != null && byId.containsKey(x.getDamId()) ? "dam " + byId.get(x.getDamId()).getTag() : x.getDamTag());
            offspring.add(new Offspring(BreedingService.ref(x), x.getSex(), x.getDateOfBirth(), sireLabel,
                    br == null || br.getCalvingEase() == null ? null : br.getCalvingEase().name(), x.getBirthWeightKg(),
                    x.getStatus() == Status.ACTIVE ? x.getCategory().name() : x.getStatus().name()));
        }
        for (BreedingRecord r : recs) {
            for (BreedingRecord.Calf c : r.getCalves()) {
                if (c.animalId() == null) {
                    Item s = r.getSemenItemId() == null ? null : semen.get(r.getSemenItemId());
                    String sireLabel = s != null ? s.getName() : r.getSireId() != null && byId.containsKey(r.getSireId())
                            ? byId.get(r.getSireId()).getTag() : r.getSireTag();
                    offspring.add(new Offspring(null, c.sex(), r.getCalvingDate(), sireLabel,
                            r.getCalvingEase() == null ? null : r.getCalvingEase().name(), c.birthWeightKg(),
                            r.getCalvingOutcome() == null ? null : r.getCalvingOutcome().name()));
                }
            }
        }
        offspring.sort(Comparator.comparing(Offspring::birthDate, Comparator.nullsLast(Comparator.reverseOrder())));

        List<LocalDate> calvings = recs.stream().filter(r -> r.getCalvingDate() != null && r.getCalvingOutcome() != Outcome.ABORTED)
                .map(BreedingRecord::getCalvingDate).sorted().toList();
        Integer interval = null;
        if (calvings.size() >= 2) {
            interval = (int) Math.round((double) ChronoUnit.DAYS.between(calvings.get(0), calvings.get(calvings.size() - 1))
                    / (calvings.size() - 1));
        }
        return new Family(new FamilyAnimal(a.getId(), a.getTag(), a.getName(), a.getSpecies(), a.getBreed(), a.getSex(),
                a.getDateOfBirth(), a.getCategory()), knownPct(dam, sire, gp), new Parents(dam, sire), gp, siblings,
                offspring, interval);
    }

    @Transactional(readOnly = true)
    public MateCheck mateCheck(Long farmId, Long id, Long sireAnimalId, Long semenItemId) {
        if ((sireAnimalId == null) == (semenItemId == null)) {
            throw ApiException.badRequest("Pick either a bull from the herd or a semen batch.");
        }
        List<Animal> herd = animals.findByFarmIdOrderByTagAsc(farmId);
        Map<Long, Animal> byId = herd.stream().collect(Collectors.toMap(Animal::getId, Function.identity()));
        Animal cow = byId.get(id);
        if (cow == null) {
            throw ApiException.notFound("Animal not found.");
        }
        Family fam = family(farmId, id);
        int known = fam.pedigreeKnownPct();

        // The candidate bull and his known ancestors.
        String bullLabel;
        List<String> bullSelf = new ArrayList<>();
        Map<String, String> bullAnc = new LinkedHashMap<>();
        Animal bull = null;
        if (sireAnimalId != null) {
            bull = byId.get(sireAnimalId);
            if (bull == null || bull.getSex() != Sex.MALE) {
                throw ApiException.badRequest("Pick a male from this farm.");
            }
            bullLabel = bull.getTag();
            bullSelf.addAll(keys(bull.getId(), bull.getTag()));
            addAnc(bullAnc, byId, bull, 2);
        } else {
            Item s = items.findByIdAndFarmIdAndItemType(semenItemId, farmId, Item.Type.SEMEN)
                    .orElseThrow(() -> ApiException.badRequest("That semen batch isn't on this farm."));
            bullLabel = s.getName() + (s.getDetails().bullCode() == null ? "" : " · " + s.getDetails().bullCode());
            bullSelf.addAll(keys(null, s.getDetails().bullCode()));
            bullSelf.addAll(keys(null, s.getName()));
        }
        Map<String, String> cowParents = new LinkedHashMap<>();
        Map<String, String> cowGrand = new LinkedHashMap<>();
        put(cowParents, keys(cow.getDamId(), label(byId, cow.getDamId(), cow.getDamTag())), label(byId, cow.getDamId(), cow.getDamTag()));
        put(cowParents, keys(cow.getSireId(), label(byId, cow.getSireId(), cow.getSireTag())), label(byId, cow.getSireId(), cow.getSireTag()));
        for (Long pid : new Long[] {cow.getDamId(), cow.getSireId()}) {
            Animal p = pid == null ? null : byId.get(pid);
            if (p != null) {
                put(cowGrand, keys(p.getDamId(), label(byId, p.getDamId(), p.getDamTag())), label(byId, p.getDamId(), p.getDamTag()));
                put(cowGrand, keys(p.getSireId(), label(byId, p.getSireId(), p.getSireTag())), label(byId, p.getSireId(), p.getSireTag()));
            }
        }
        String name = cow.getName() != null ? cow.getName() : cow.getTag();
        if (bullSelf.stream().anyMatch(k -> keys(cow.getSireId(), label(byId, cow.getSireId(), cow.getSireTag())).contains(k))) {
            return new MateCheck("HIGH", "FATHER_DAUGHTER", List.of(bullLabel), known,
                    "High risk — do not use. " + bullLabel + " is " + name + "'s own sire (father × daughter).");
        }
        if (bull != null && cow.getId().equals(bull.getDamId())) {
            return new MateCheck("HIGH", "MOTHER_SON", List.of(name), known,
                    "High risk — do not use. " + bullLabel + " is " + name + "'s own son (mother × son).");
        }
        if (bullSelf.stream().anyMatch(cowGrand::containsKey)) {
            return new MateCheck("HIGH", "GRANDSIRE", List.of(bullLabel), known,
                    "High risk — do not use. " + bullLabel + " is " + name + "'s grandsire.");
        }
        if (bull != null) {
            Map<String, String> bullParents = new LinkedHashMap<>();
            put(bullParents, keys(bull.getDamId(), label(byId, bull.getDamId(), bull.getDamTag())), label(byId, bull.getDamId(), bull.getDamTag()));
            put(bullParents, keys(bull.getSireId(), label(byId, bull.getSireId(), bull.getSireTag())), label(byId, bull.getSireId(), bull.getSireTag()));
            List<String> sharedParents = bullParents.entrySet().stream().filter(e -> cowParents.containsKey(e.getKey()))
                    .map(Map.Entry::getValue).distinct().toList();
            if (!sharedParents.isEmpty()) {
                return new MateCheck("HIGH", "SIBLINGS", sharedParents, known,
                        "High risk — do not use. " + bullLabel + " and " + name + " share a parent: " + String.join(", ", sharedParents) + ".");
            }
        }
        Map<String, String> cowAll = new LinkedHashMap<>(cowParents);
        cowAll.putAll(cowGrand);
        List<String> shared = bullAnc.entrySet().stream().filter(e -> cowAll.containsKey(e.getKey()))
                .map(Map.Entry::getValue).distinct().toList();
        if (!shared.isEmpty()) {
            return new MateCheck("MEDIUM", "RELATED", shared, known,
                    "Related — shared ancestors: " + String.join(", ", shared) + ". Use only if no other bull is available.");
        }
        return new MateCheck("NONE", "NONE", List.of(), known, bullLabel + " — no shared ancestors in known pedigree (2 generations)."
                + (known < 100 ? " Missing links (" + (100 - known) + " %) can hide risk." : ""));
    }

    @Transactional(readOnly = true)
    public NextTag nextTag(Long farmId, String prefix) {
        List<String> tags = sql.tags(farmId);
        Map<String, Integer> count = new HashMap<>();
        Map<String, Long> max = new HashMap<>();
        Map<String, Integer> width = new HashMap<>();
        for (String t : tags) {
            Matcher m = NUMBERED.matcher(t.trim());
            if (!m.matches() || m.group(2).length() > 12) {
                continue;
            }
            String p = m.group(1).toUpperCase();
            count.merge(p, 1, Integer::sum);
            max.merge(p, Long.parseLong(m.group(2)), Math::max);
            width.merge(p, m.group(2).length(), Math::max);
        }
        String p;
        if (prefix != null && !prefix.isBlank()) {
            p = prefix.trim().toUpperCase();
        } else {
            p = count.entrySet().stream().max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey).orElse("");
        }
        String original = prefix != null && !prefix.isBlank() ? prefix.trim()
                : tags.stream().map(String::trim).filter(t -> t.toUpperCase().startsWith(p)).findFirst()
                .map(t -> t.substring(0, p.length())).orElse(p);
        long next = max.getOrDefault(p, 0L) + 1;
        int w = Math.max(width.getOrDefault(p, 3), 1);
        String candidate = original + String.format("%0" + w + "d", next);
        while (tags.stream().anyMatch(candidate::equalsIgnoreCase)) {
            next++;
            candidate = original + String.format("%0" + w + "d", next);
        }
        return new NextTag(candidate);
    }

    // ---------- helpers ----------

    private static Node node(Map<Long, Animal> byId, Long id, String tag) {
        if (id != null && byId.containsKey(id)) {
            return new Node(BreedingService.ref(byId.get(id)), byId.get(id).getTag());
        }
        return tag == null || tag.isBlank() ? null : new Node(null, tag);
    }

    private static int knownPct(Node dam, Node sire, Grandparents gp) {
        int known = 0;
        for (Node n : new Node[] {dam, sire, gp.damDam(), gp.damSire(), gp.sireDam(), gp.sireSire()}) {
            if (n != null) {
                known++;
            }
        }
        return (int) Math.round(known * 100.0 / 6);
    }

    private static List<String> keys(Long id, String tag) {
        List<String> k = new ArrayList<>();
        if (id != null) {
            k.add("id:" + id);
        }
        if (tag != null && !tag.isBlank()) {
            k.add("tag:" + tag.trim().toLowerCase());
        }
        return k;
    }

    private static String label(Map<Long, Animal> byId, Long id, String tag) {
        return id != null && byId.containsKey(id) ? byId.get(id).getTag() : tag;
    }

    private static void put(Map<String, String> m, List<String> keys, String label) {
        keys.forEach(k -> m.put(k, label));
    }

    /** Parents and (depth 2) grandparents of an animal, as keys → labels. Tags of farm animals are keys too. */
    private static void addAnc(Map<String, String> out, Map<Long, Animal> byId, Animal a, int depth) {
        if (a == null || depth == 0) {
            return;
        }
        put(out, keys(a.getDamId(), label(byId, a.getDamId(), a.getDamTag())), label(byId, a.getDamId(), a.getDamTag()));
        put(out, keys(a.getSireId(), label(byId, a.getSireId(), a.getSireTag())), label(byId, a.getSireId(), a.getSireTag()));
        if (a.getDamId() != null) {
            Animal d = byId.get(a.getDamId());
            if (d != null) {
                out.put("tag:" + d.getTag().toLowerCase(), d.getTag());
                addAnc(out, byId, d, depth - 1);
            }
        }
        if (a.getSireId() != null) {
            Animal s = byId.get(a.getSireId());
            if (s != null) {
                out.put("tag:" + s.getTag().toLowerCase(), s.getTag());
                addAnc(out, byId, s, depth - 1);
            }
        }
    }
}
