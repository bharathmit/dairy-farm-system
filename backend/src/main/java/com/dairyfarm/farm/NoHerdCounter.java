package com.dairyfarm.farm;

import java.util.Collection;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Placeholder until the animal module exists: every farm shows 0 animals. */
@Component
class NoHerdCounter implements HerdCounter {

    @Override
    public Map<Long, Long> activeAnimalsByFarm(Collection<Long> farmIds) {
        return Map.of();
    }
}
