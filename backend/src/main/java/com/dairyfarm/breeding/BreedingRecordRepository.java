package com.dairyfarm.breeding;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BreedingRecordRepository extends JpaRepository<BreedingRecord, Long> {

    Optional<BreedingRecord> findByIdAndFarmId(Long id, Long farmId);

    List<BreedingRecord> findByFarmId(Long farmId);

    List<BreedingRecord> findByFarmIdAndDamId(Long farmId, Long damId);

    List<BreedingRecord> findByFarmIdAndDamIdIn(Long farmId, Collection<Long> damIds);

    List<BreedingRecord> findByFarmIdAndHeatEventIdIn(Long farmId, Collection<Long> heatIds);

    @Query(value = "select * from breeding_record where farm_id = :farmId and calves @> cast(:json as jsonb)", nativeQuery = true)
    List<BreedingRecord> findByCalf(@Param("farmId") Long farmId, @Param("json") String json);
}
