package com.dairyfarm.group;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.animal.Animal;
import com.dairyfarm.animal.AnimalRepository;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.group.GroupViews.GroupAnimal;
import com.dairyfarm.group.GroupViews.GroupDetail;
import com.dairyfarm.group.GroupViews.GroupSummary;
import com.dairyfarm.member.FarmMember;
import com.dairyfarm.member.FarmMemberRepository;

@Service
public class GroupService {

    private final AnimalGroupRepository groups;
    private final AnimalRepository animals;
    private final FarmMemberRepository members;

    public GroupService(AnimalGroupRepository groups, AnimalRepository animals, FarmMemberRepository members) {
        this.groups = groups;
        this.animals = animals;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> list(Long farmId) {
        Map<Long, Long> counts = new HashMap<>();
        animals.countByGroup(farmId).forEach(c -> counts.put(c.getGroupId(), c.getTotal()));
        Map<Long, String> managers = managerNames(farmId);
        return groups.findByFarmIdOrderByNameAsc(farmId).stream()
                .map(g -> new GroupSummary(g.getId(), g.getName(), g.getSpecies(), g.getLocation(), g.getPurpose(),
                        g.getManagerMemberId(), managers.get(g.getManagerMemberId()), g.getColour(), g.getNotes(),
                        counts.getOrDefault(g.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupDetail get(Long farmId, Long id) {
        return detail(load(farmId, id));
    }

    @Transactional
    public GroupDetail create(Long farmId, GroupRequest req) {
        String name = req.name().trim();
        if (groups.existsByFarmIdAndNameIgnoreCase(farmId, name)) {
            throw ApiException.conflict("There's already a group called " + name + ".");
        }
        AnimalGroup g = new AnimalGroup(farmId, name, req.species());
        apply(g, req);
        return detail(groups.save(g));
    }

    @Transactional
    public GroupDetail update(Long farmId, Long id, GroupRequest req) {
        AnimalGroup g = load(farmId, id);
        String name = req.name().trim();
        if (groups.existsByFarmIdAndNameIgnoreCaseAndIdNot(farmId, name, id)) {
            throw ApiException.conflict("There's already a group called " + name + ".");
        }
        if (req.species() != g.getSpecies()) {
            throw ApiException.badRequest("A group's species can't change. Create a new group instead.");
        }
        g.setName(name);
        apply(g, req);
        return detail(g);
    }

    /** Animals in the group leave it; they stay on the farm. */
    @Transactional
    public void delete(Long farmId, Long id) {
        AnimalGroup g = load(farmId, id);
        animals.clearGroup(farmId, g.getId());
        groups.delete(g);
    }

    /**
     * Sets exactly which animals are in the group. Listed animals move here (from any other group);
     * animals not listed leave it.
     */
    @Transactional
    public GroupDetail setAnimals(Long farmId, Long id, List<Long> animalIds) {
        AnimalGroup g = load(farmId, id);
        Set<Long> ids = new LinkedHashSet<>(animalIds);
        List<Animal> chosen = ids.isEmpty() ? List.of() : animals.findByFarmIdAndIdIn(farmId, ids);
        if (chosen.size() != ids.size()) {
            throw ApiException.badRequest("Some of those animals aren't on this farm. Refresh and try again.");
        }
        for (Animal a : chosen) {
            if (a.getSpecies() != g.getSpecies()) {
                throw ApiException.badRequest(a.getTag() + " is a " + a.getSpecies().name().toLowerCase()
                        + "; " + g.getName() + " is for " + g.getSpecies().name().toLowerCase() + " only.");
            }
            if (!a.isActive()) {
                throw ApiException.badRequest(a.getTag() + " is no longer active, so it can't join a group.");
            }
        }
        if (ids.isEmpty()) {
            animals.clearGroup(farmId, g.getId());
        } else {
            animals.clearGroupExcept(farmId, g.getId(), ids);
            animals.findByFarmIdAndIdIn(farmId, ids).forEach(a -> a.setGroupId(g.getId()));
        }
        animals.flush();
        return detail(load(farmId, id));
    }

    private void apply(AnimalGroup g, GroupRequest req) {
        if (req.managerMemberId() != null) {
            FarmMember m = members.findByIdAndFarmId(req.managerMemberId(), g.getFarmId())
                    .orElseThrow(() -> ApiException.badRequest("The manager must be on this farm's team."));
            if (!m.isActive()) {
                throw ApiException.badRequest(m.getName() + " is no longer active on this farm.");
            }
        }
        g.setLocation(blankToNull(req.location()));
        g.setPurpose(req.purpose() == null ? GroupPurpose.MAINTENANCE : req.purpose());
        g.setManagerMemberId(req.managerMemberId());
        g.setColour(req.colour() == null ? null : req.colour().toUpperCase());
        g.setNotes(blankToNull(req.notes()));
    }

    private AnimalGroup load(Long farmId, Long id) {
        return groups.findByIdAndFarmId(id, farmId).orElseThrow(() -> ApiException.notFound("Group not found."));
    }

    private GroupDetail detail(AnimalGroup g) {
        List<GroupAnimal> list = animals.findByFarmIdAndGroupIdOrderByTagAsc(g.getFarmId(), g.getId()).stream()
                .map(a -> new GroupAnimal(a.getId(), a.getTag(), a.getName(), a.getSex(), a.getCategory(),
                        a.getDateOfBirth()))
                .toList();
        String manager = g.getManagerMemberId() == null ? null
                : members.findById(g.getManagerMemberId()).map(FarmMember::getName).orElse(null);
        return new GroupDetail(g.getId(), g.getName(), g.getSpecies(), g.getLocation(), g.getPurpose(),
                g.getManagerMemberId(), manager, g.getColour(), g.getNotes(), list);
    }

    private Map<Long, String> managerNames(Long farmId) {
        Map<Long, String> names = new HashMap<>();
        members.findByFarmIdOrderByActiveDescRoleAscNameAsc(farmId).forEach(m -> names.put(m.getId(), m.getName()));
        return names;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
