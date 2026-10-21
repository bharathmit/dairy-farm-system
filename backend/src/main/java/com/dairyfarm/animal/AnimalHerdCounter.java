package com.dairyfarm.animal;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.dairyfarm.farm.HerdCounter;

@Component
class AnimalHerdCounter implements HerdCounter {

    private final AnimalRepository animals;

    AnimalHerdCounter(AnimalRepository animals) {
        this.animals = animals;
    }

    @Override
    public Map<Long, Long> activeAnimalsByFarm(Collection<Long> farmIds) {
        Map<Long, Long> counts = new HashMap<>();
        animals.countActiveByFarm(farmIds).forEach(c -> counts.put(c.getFarmId(), c.getTotal()));
        return counts;
    }
}
