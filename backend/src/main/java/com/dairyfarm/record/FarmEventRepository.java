package com.dairyfarm.record;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dairyfarm.record.FarmEvent.EventType;

public interface FarmEventRepository extends JpaRepository<FarmEvent, Long>, JpaSpecificationExecutor<FarmEvent> {

    Optional<FarmEvent> findByIdAndFarmIdAndEventType(Long id, Long farmId, EventType type);

    List<FarmEvent> findByFarmIdAndAnimalIdAndEventTypeOrderByEventDateDescIdDesc(Long farmId, Long animalId, EventType type);

    List<FarmEvent> findByFarmIdAndBatchIdOrderByIdAsc(Long farmId, java.util.UUID batchId);

    @Query("""
            select e from FarmEvent e where e.farmId = :farmId and e.eventType = com.dairyfarm.record.FarmEvent.EventType.HEALTH
              and e.milkWithdrawalUntil > :from and e.milkWithdrawalUntil <= :to order by e.milkWithdrawalUntil""")
    List<FarmEvent> findMilkWithdrawalsEnding(@Param("farmId") Long farmId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select e from FarmEvent e where e.farmId = :farmId and e.eventType = com.dairyfarm.record.FarmEvent.EventType.HEALTH
              and e.nextActionDate is not null and e.nextActionDone = false and e.nextActionDate <= :until
            order by e.nextActionDate, e.id""")
    List<FarmEvent> findDueFollowUps(@Param("farmId") Long farmId, @Param("until") LocalDate until);

    @Query("""
            select count(e) from FarmEvent e where e.farmId = :farmId and e.eventType = com.dairyfarm.record.FarmEvent.EventType.HEALTH
              and e.nextActionDate is not null and e.nextActionDone = false""")
    long countOpenFollowUps(@Param("farmId") Long farmId);

    @Query("""
            select count(distinct e.animalId) from FarmEvent e where e.farmId = :farmId
              and e.eventType = com.dairyfarm.record.FarmEvent.EventType.HEALTH and e.milkWithdrawalUntil > :now""")
    long countAnimalsInWithdrawal(@Param("farmId") Long farmId, @Param("now") Instant now);

    @Query("""
            select count(e) from FarmEvent e where e.farmId = :farmId and e.eventType = com.dairyfarm.record.FarmEvent.EventType.HEALTH
              and e.eventDate between :from and :to""")
    long countHealthBetween(@Param("farmId") Long farmId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
