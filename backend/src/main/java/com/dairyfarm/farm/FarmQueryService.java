package com.dairyfarm.farm;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.member.FarmMember;
import com.dairyfarm.member.FarmMemberRepository;

@Service
public class FarmQueryService {

    private final FarmRepository farms;
    private final FarmMemberRepository members;
    private final HerdCounter herdCounter;

    public FarmQueryService(FarmRepository farms, FarmMemberRepository members, HerdCounter herdCounter) {
        this.farms = farms;
        this.members = members;
        this.herdCounter = herdCounter;
    }

    /** Every farm the person is an active member of, by name. */
    @Transactional(readOnly = true)
    public List<FarmSummary> farmsOf(UUID userId) {
        List<FarmMember> memberships = members.findByUserIdAndActiveTrue(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<Long> ids = memberships.stream().map(FarmMember::getFarmId).toList();
        Map<Long, Farm> byId = farms.findAllById(ids).stream().collect(Collectors.toMap(Farm::getId, Function.identity()));
        Map<Long, Long> counts = herdCounter.activeAnimalsByFarm(ids);
        return memberships.stream()
                .filter(m -> byId.containsKey(m.getFarmId()))
                .map(m -> summary(byId.get(m.getFarmId()), m, counts.getOrDefault(m.getFarmId(), 0L)))
                .sorted(Comparator.comparing(FarmSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static FarmSummary summary(Farm f, FarmMember m, long animalCount) {
        return new FarmSummary(f.getId(), f.getName(), f.getVillage(), f.getDistrict(), f.getState(),
                m.getRole(), m.getId(), animalCount);
    }
}
