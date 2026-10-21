package com.dairyfarm.farm;

import java.util.Collection;
import java.util.Map;

/** Counts active animals per farm for the farm picker. Implemented by the animal module. */
public interface HerdCounter {

    Map<Long, Long> activeAnimalsByFarm(Collection<Long> farmIds);
}
