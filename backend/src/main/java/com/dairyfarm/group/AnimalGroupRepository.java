package com.dairyfarm.group;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnimalGroupRepository extends JpaRepository<AnimalGroup, Long> {

    Optional<AnimalGroup> findByIdAndFarmId(Long id, Long farmId);

    List<AnimalGroup> findByFarmIdOrderByNameAsc(Long farmId);

    List<AnimalGroup> findByFarmIdAndIdIn(Long farmId, Collection<Long> ids);

    Optional<AnimalGroup> findFirstByFarmIdAndNameIgnoreCase(Long farmId, String name);

    boolean existsByFarmIdAndNameIgnoreCase(Long farmId, String name);

    boolean existsByFarmIdAndNameIgnoreCaseAndIdNot(Long farmId, String name, Long id);
}
