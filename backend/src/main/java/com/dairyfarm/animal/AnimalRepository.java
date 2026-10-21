package com.dairyfarm.animal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnimalRepository extends JpaRepository<Animal, Long>, JpaSpecificationExecutor<Animal> {

    Optional<Animal> findByIdAndFarmId(Long id, Long farmId);

    Optional<Animal> findFirstByFarmIdAndTagIgnoreCase(Long farmId, String tag);

    boolean existsByFarmIdAndTagIgnoreCase(Long farmId, String tag);

    boolean existsByFarmIdAndTagIgnoreCaseAndIdNot(Long farmId, String tag, Long id);

    boolean existsByDamId(Long damId);

    boolean existsBySireId(Long sireId);

    List<Animal> findByFarmIdAndIdIn(Long farmId, Collection<Long> ids);

    List<Animal> findByFarmIdOrderByTagAsc(Long farmId);

    List<Animal> findByFarmIdAndGroupIdOrderByTagAsc(Long farmId, Long groupId);

    @Query("select lower(a.tag) from Animal a where a.farmId = :farmId")
    List<String> findTagsLowerCase(@Param("farmId") Long farmId);

    interface Count {
        String getKey();

        long getTotal();
    }

    @Query("select cast(a.category as string) as key, count(a) as total from Animal a "
            + "where a.farmId = :farmId and a.status = com.dairyfarm.animal.AnimalEnums.Status.ACTIVE group by a.category")
    List<Count> countActiveByCategory(@Param("farmId") Long farmId);

    @Query("select cast(a.status as string) as key, count(a) as total from Animal a where a.farmId = :farmId group by a.status")
    List<Count> countByStatus(@Param("farmId") Long farmId);

    interface FarmCount {
        Long getFarmId();

        long getTotal();
    }

    @Query("select a.farmId as farmId, count(a) as total from Animal a "
            + "where a.farmId in :farmIds and a.status = com.dairyfarm.animal.AnimalEnums.Status.ACTIVE group by a.farmId")
    List<FarmCount> countActiveByFarm(@Param("farmIds") Collection<Long> farmIds);

    interface GroupCount {
        Long getGroupId();

        long getTotal();
    }

    @Query("select a.groupId as groupId, count(a) as total from Animal a "
            + "where a.farmId = :farmId and a.groupId is not null group by a.groupId")
    List<GroupCount> countByGroup(@Param("farmId") Long farmId);

    /**
     * Links tag-only parents to animals that now exist on the farm (same species; dam female, sire male).
     * Runs after adding or importing animals so calves recorded before their mother are connected.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE animal c SET dam_id = d.id, dam_tag = NULL, updated_at = now()
            FROM animal d
            WHERE c.farm_id = :farmId AND c.dam_id IS NULL AND c.dam_tag IS NOT NULL
              AND d.farm_id = c.farm_id AND lower(d.tag) = lower(c.dam_tag) AND d.id <> c.id
              AND d.sex = 'FEMALE' AND d.species = c.species
            """, nativeQuery = true)
    int linkDamsByTag(@Param("farmId") Long farmId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE animal c SET sire_id = s.id, sire_tag = NULL, updated_at = now()
            FROM animal s
            WHERE c.farm_id = :farmId AND c.sire_id IS NULL AND c.sire_tag IS NOT NULL
              AND s.farm_id = c.farm_id AND lower(s.tag) = lower(c.sire_tag) AND s.id <> c.id
              AND s.sex = 'MALE' AND s.species = c.species
            """, nativeQuery = true)
    int linkSiresByTag(@Param("farmId") Long farmId);

    /** Before a parent is deleted, its offspring keep the parent's tag as text. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE animal SET dam_tag = :tag, dam_id = NULL, updated_at = now() WHERE dam_id = :id
            """, nativeQuery = true)
    int detachDam(@Param("id") Long id, @Param("tag") String tag);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE animal SET sire_tag = :tag, sire_id = NULL, updated_at = now() WHERE sire_id = :id
            """, nativeQuery = true)
    int detachSire(@Param("id") Long id, @Param("tag") String tag);

    /** Removes everyone from a group except the given animals. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Animal a set a.groupId = null where a.farmId = :farmId and a.groupId = :groupId and a.id not in :keep")
    int clearGroupExcept(@Param("farmId") Long farmId, @Param("groupId") Long groupId, @Param("keep") Collection<Long> keep);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Animal a set a.groupId = null where a.farmId = :farmId and a.groupId = :groupId")
    int clearGroup(@Param("farmId") Long farmId, @Param("groupId") Long groupId);
}
