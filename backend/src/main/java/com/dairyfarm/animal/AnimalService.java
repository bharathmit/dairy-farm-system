package com.dairyfarm.animal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dairyfarm.animal.AnimalEnums.Acquisition;
import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.animal.AnimalEnums.Status;
import com.dairyfarm.animal.AnimalViews.AnimalDetail;
import com.dairyfarm.animal.AnimalViews.AnimalListItem;
import com.dairyfarm.animal.AnimalViews.HerdSummary;
import com.dairyfarm.animal.AnimalViews.ParentRef;
import com.dairyfarm.animal.AnimalViews.Photo;
import com.dairyfarm.animal.photo.PhotoStorage;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.common.PageResponse;
import com.dairyfarm.common.Sex;
import com.dairyfarm.farm.FarmService;
import com.dairyfarm.group.AnimalGroup;
import com.dairyfarm.group.AnimalGroupRepository;

@Service
public class AnimalService {

    static final int MAX_PAGE_SIZE = 200;

    private final AnimalRepository animals;
    private final AnimalGroupRepository groups;
    private final PhotoStorage photos;

    public AnimalService(AnimalRepository animals, AnimalGroupRepository groups, PhotoStorage photos) {
        this.animals = animals;
        this.groups = groups;
        this.photos = photos;
    }

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public PageResponse<AnimalListItem> list(Long farmId, AnimalQuery query, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("Use page 0 or more and size 1 to " + MAX_PAGE_SIZE + ".");
        }
        Page<Animal> result = animals.findAll(query.toSpecification(farmId), PageRequest.of(page, size, query.toSort()));
        Map<Long, String> groupNames = groupNames(farmId);
        List<String> firstPhotos = result.getContent().stream()
                .filter(a -> !a.getPhotoPaths().isEmpty()).map(a -> a.getPhotoPaths().get(0)).toList();
        Map<String, String> urls = photos.signedUrls(firstPhotos);
        return PageResponse.of(result.map(a -> new AnimalListItem(a.getId(), a.getTag(), a.getName(), a.getSpecies(),
                a.getBreed(), a.getSex(), a.getCategory(), a.getStatus(), a.getDateOfBirth(), a.getGroupId(),
                groupNames.get(a.getGroupId()), a.getLastCalvingDate(),
                a.getPhotoPaths().isEmpty() ? null : urls.get(a.getPhotoPaths().get(0)))));
    }

    /** Same filters and paging as {@link #list}, with every animal's full details (for the app's herd cache). */
    @Transactional(readOnly = true)
    public PageResponse<AnimalDetail> listDetails(Long farmId, AnimalQuery query, int page, int size, boolean showMoney) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("Use page 0 or more and size 1 to " + MAX_PAGE_SIZE + ".");
        }
        Page<Animal> result = animals.findAll(query.toSpecification(farmId), PageRequest.of(page, size, query.toSort()));
        Map<Long, String> groupNames = groupNames(farmId);
        List<Long> parentIds = result.getContent().stream()
                .flatMap(a -> java.util.stream.Stream.of(a.getDamId(), a.getSireId()))
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Animal> parents = parentIds.isEmpty() ? Map.of()
                : animals.findAllById(parentIds).stream().collect(Collectors.toMap(Animal::getId, Function.identity()));
        Map<String, String> urls = photos.signedUrls(
                result.getContent().stream().flatMap(a -> a.getPhotoPaths().stream()).toList());
        return PageResponse.of(result.map(a -> toDetail(a, showMoney, groupNames.get(a.getGroupId()), parents, urls)));
    }

    @Transactional(readOnly = true)
    public AnimalDetail get(Long farmId, Long id, boolean showMoney) {
        return detail(load(farmId, id), showMoney);
    }

    @Transactional(readOnly = true)
    public AnimalDetail getByTag(Long farmId, String tag, boolean showMoney) {
        Animal a = animals.findFirstByFarmIdAndTagIgnoreCase(farmId, tag.trim())
                .orElseThrow(() -> ApiException.notFound("No animal with tag " + tag.trim() + " on this farm."));
        return detail(a, showMoney);
    }

    @Transactional(readOnly = true)
    public HerdSummary summary(Long farmId) {
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (Category c : Category.values()) {
            byCategory.put(c.name(), 0L);
        }
        animals.countActiveByCategory(farmId).forEach(c -> byCategory.put(c.getKey(), c.getTotal()));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Status s : Status.values()) {
            byStatus.put(s.name(), 0L);
        }
        animals.countByStatus(farmId).forEach(c -> byStatus.put(c.getKey(), c.getTotal()));
        return new HerdSummary(byStatus.get(Status.ACTIVE.name()), byCategory, byStatus);
    }

    // ---------- writing ----------

    @Transactional
    public AnimalDetail create(Long farmId, AnimalRequest req) {
        Animal a = createEntity(farmId, req);
        relinkParents(farmId);
        return detail(load(farmId, a.getId()), true);
    }

    /** Validates and saves one animal (used by create and CSV import). */
    Animal createEntity(Long farmId, AnimalRequest req) {
        if (animals.existsByFarmIdAndTagIgnoreCase(farmId, req.tag().trim())) {
            throw ApiException.conflict("Tag " + req.tag().trim() + " is already used on this farm.");
        }
        Animal a = new Animal(farmId, req.species());
        apply(a, req);
        return animals.save(a);
    }

    @Transactional
    public AnimalDetail update(Long farmId, Long id, AnimalRequest req) {
        Animal a = load(farmId, id);
        if (req.species() != a.getSpecies()) {
            throw ApiException.badRequest("Species can't be changed after an animal is added.");
        }
        if (animals.existsByFarmIdAndTagIgnoreCaseAndIdNot(farmId, req.tag().trim(), id)) {
            throw ApiException.conflict("Tag " + req.tag().trim() + " is already used on this farm.");
        }
        if (req.sex() != a.getSex() && hasOffspring(a)) {
            throw ApiException.badRequest(a.getTag() + " is recorded as a parent, so the sex can't change.");
        }
        apply(a, req);
        animals.saveAndFlush(a);
        relinkParents(farmId);
        return detail(load(farmId, id), true);
    }

    @Transactional
    public AnimalDetail changeStatus(Long farmId, Long id, StatusRequest req) {
        Animal a = load(farmId, id);
        if (req.status() == Status.ACTIVE) {
            a.setStatus(Status.ACTIVE);
            a.setExitDate(null);
            a.setExitReason(null);
            a.setExitPrice(null);
            a.setExitParty(null);
        } else {
            LocalDate date = req.date() != null ? req.date() : LocalDate.now(FarmService.FARM_ZONE);
            if (a.getDateOfBirth() != null && date.isBefore(a.getDateOfBirth())) {
                throw ApiException.badRequest("The date can't be before the date of birth.");
            }
            a.setStatus(req.status());
            a.setExitDate(date);
            a.setExitReason(blankToNull(req.reason()));
            a.setExitPrice(req.status() == Status.SOLD ? req.price() : null);
            a.setExitParty(req.status() == Status.DEAD ? null : blankToNull(req.party()));
            a.setGroupId(null);
        }
        return detail(a, true);
    }

    @Transactional
    public AnimalDetail moveToGroup(Long farmId, Long id, Long groupId) {
        Animal a = load(farmId, id);
        if (groupId != null) {
            checkGroup(farmId, a, groupId);
        }
        a.setGroupId(groupId);
        return detail(a, true);
    }

    @Transactional
    public void delete(Long farmId, Long id) {
        deleteAll(List.of(load(farmId, id)));
    }

    @Transactional
    public int deleteMany(Long farmId, Collection<Long> ids) {
        List<Animal> found = animals.findByFarmIdAndIdIn(farmId, ids);
        if (found.size() != new java.util.HashSet<>(ids).size()) {
            throw ApiException.notFound("Some of those animals aren't on this farm. Refresh and try again.");
        }
        deleteAll(found);
        return found.size();
    }

    private void deleteAll(List<Animal> list) {
        List<String> paths = new ArrayList<>();
        for (Animal a : list) {
            paths.addAll(a.getPhotoPaths());
        }
        Collection<Long> ids = list.stream().map(Animal::getId).toList();
        for (Animal a : list) {
            animals.detachDam(a.getId(), a.getTag());
            animals.detachSire(a.getId(), a.getTag());
        }
        animals.deleteAllByIdInBatch(ids);
        if (!paths.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    photos.delete(paths);
                }
            });
        }
    }

    /** Connects tag-only parents to animals that now exist on the farm. */
    void relinkParents(Long farmId) {
        animals.linkDamsByTag(farmId);
        animals.linkSiresByTag(farmId);
    }

    // ---------- rules ----------

    private void apply(Animal a, AnimalRequest req) {
        Category category = req.category();
        if (category.femaleOnly() && req.sex() != Sex.FEMALE) {
            throw ApiException.badRequest(label(category) + " animals must be female.");
        }
        if (category.maleOnly() && req.sex() != Sex.MALE) {
            throw ApiException.badRequest("A bull must be male.");
        }
        if (req.acquisitionType() == Acquisition.BORN && req.dateOfBirth() == null) {
            throw ApiException.badRequest("Add the date of birth for an animal born on the farm.");
        }
        LocalDate dob = req.dateOfBirth();
        if (dob != null && req.acquisitionDate() != null && req.acquisitionDate().isBefore(dob)) {
            throw ApiException.badRequest("The purchase date can't be before the date of birth.");
        }
        if (req.lastCalvingDate() != null) {
            if (req.sex() != Sex.FEMALE) {
                throw ApiException.badRequest("Only female animals have a calving date.");
            }
            if (dob != null && !req.lastCalvingDate().isAfter(dob)) {
                throw ApiException.badRequest("The calving date must be after the date of birth.");
            }
        }

        a.setTag(req.tag().trim());
        a.setName(blankToNull(req.name()));
        a.setNationalId(blankToNull(req.nationalId()));
        a.setBreed(blankToNull(req.breed()));
        a.setSex(req.sex());
        a.setDateOfBirth(dob);
        a.setColour(blankToNull(req.colour()));
        a.setBirthWeightKg(req.birthWeightKg());
        a.setCategory(category);
        a.setAcquisitionType(req.acquisitionType());
        a.setAcquisitionDate(req.acquisitionDate());
        a.setPurchasePrice(req.acquisitionType() == Acquisition.PURCHASED ? req.purchasePrice() : null);
        a.setLastCalvingDate(req.lastCalvingDate());
        a.setCurrentWeightKg(req.currentWeightKg());
        a.setNotes(blankToNull(req.notes()));

        if (req.groupId() != null) {
            checkGroup(a.getFarmId(), a, req.groupId());
        }
        a.setGroupId(req.groupId());
        setParent(a, req.damId(), req.damTag(), Sex.FEMALE);
        setParent(a, req.sireId(), req.sireTag(), Sex.MALE);
    }

    private void setParent(Animal a, Long parentId, String parentTag, Sex parentSex) {
        String role = parentSex == Sex.FEMALE ? "mother" : "father";
        Animal parent = null;
        String tag = blankToNull(parentTag);
        if (parentId != null) {
            parent = animals.findByIdAndFarmId(parentId, a.getFarmId())
                    .orElseThrow(() -> ApiException.badRequest("The " + role + " isn't on this farm."));
        } else if (tag != null) {
            parent = animals.findFirstByFarmIdAndTagIgnoreCase(a.getFarmId(), tag).orElse(null);
        }
        if (parent != null) {
            if (Objects.equals(parent.getId(), a.getId())) {
                throw ApiException.badRequest("An animal can't be its own " + role + ".");
            }
            if (parent.getSex() != parentSex) {
                throw ApiException.badRequest(parent.getTag() + " is " + parent.getSex().name().toLowerCase()
                        + ", so it can't be the " + role + ".");
            }
            if (parent.getSpecies() != a.getSpecies()) {
                throw ApiException.badRequest("The " + role + " must be the same species.");
            }
            tag = null;
        }
        Long id = parent == null ? null : parent.getId();
        if (parentSex == Sex.FEMALE) {
            a.setDamId(id);
            a.setDamTag(tag);
        } else {
            a.setSireId(id);
            a.setSireTag(tag);
        }
    }

    private void checkGroup(Long farmId, Animal a, Long groupId) {
        AnimalGroup g = groups.findByIdAndFarmId(groupId, farmId)
                .orElseThrow(() -> ApiException.badRequest("That group isn't on this farm."));
        if (g.getSpecies() != a.getSpecies()) {
            throw ApiException.badRequest(g.getName() + " is a " + g.getSpecies().name().toLowerCase()
                    + " group; this animal is a " + a.getSpecies().name().toLowerCase() + ".");
        }
        if (!a.isActive()) {
            throw ApiException.badRequest("Only active animals can be in a group.");
        }
    }

    private boolean hasOffspring(Animal a) {
        return a.getId() != null && (animals.existsByDamId(a.getId()) || animals.existsBySireId(a.getId()));
    }

    // ---------- mapping ----------

    Animal load(Long farmId, Long id) {
        return animals.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> ApiException.notFound("Animal not found."));
    }

    AnimalDetail detail(Animal a, boolean showMoney) {
        String groupName = a.getGroupId() == null ? null
                : groups.findById(a.getGroupId()).map(AnimalGroup::getName).orElse(null);
        List<Long> parentIds = new ArrayList<>();
        if (a.getDamId() != null) {
            parentIds.add(a.getDamId());
        }
        if (a.getSireId() != null) {
            parentIds.add(a.getSireId());
        }
        Map<Long, Animal> parents = parentIds.isEmpty() ? Map.of()
                : animals.findAllById(parentIds).stream().collect(Collectors.toMap(Animal::getId, Function.identity()));
        return toDetail(a, showMoney, groupName, parents, photos.signedUrls(a.getPhotoPaths()));
    }

    private static AnimalDetail toDetail(Animal a, boolean showMoney, String groupName, Map<Long, Animal> parents,
                                         Map<String, String> urls) {
        List<Photo> photoList = a.getPhotoPaths().stream()
                .map(p -> new Photo(p.substring(p.lastIndexOf('/') + 1), urls.get(p))).toList();
        return new AnimalDetail(a.getId(), a.getTag(), a.getName(), a.getNationalId(), a.getSpecies(), a.getBreed(),
                a.getSex(), a.getDateOfBirth(), a.getColour(), a.getBirthWeightKg(), a.getCategory(), a.getStatus(),
                a.getGroupId(), groupName,
                parent(a.getDamId(), a.getDamTag(), parents), parent(a.getSireId(), a.getSireTag(), parents),
                a.getAcquisitionType(), a.getAcquisitionDate(), showMoney ? a.getPurchasePrice() : null,
                a.getExitDate(), a.getExitReason(), showMoney ? a.getExitPrice() : null, a.getExitParty(),
                a.getLastCalvingDate(), a.getCurrentWeightKg(), a.getNotes(), photoList,
                a.getCreatedAt(), a.getUpdatedAt());
    }

    private static ParentRef parent(Long id, String tag, Map<Long, Animal> parents) {
        if (id != null && parents.containsKey(id)) {
            Animal p = parents.get(id);
            return new ParentRef(p.getId(), p.getTag(), p.getName());
        }
        return tag == null ? null : new ParentRef(null, tag, null);
    }

    private Map<Long, String> groupNames(Long farmId) {
        Map<Long, String> names = new HashMap<>();
        groups.findByFarmIdOrderByNameAsc(farmId).forEach(g -> names.put(g.getId(), g.getName()));
        return names;
    }

    static String label(Category c) {
        return c.name().charAt(0) + c.name().substring(1).toLowerCase();
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
