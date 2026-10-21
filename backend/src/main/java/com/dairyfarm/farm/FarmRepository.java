package com.dairyfarm.farm;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FarmRepository extends JpaRepository<Farm, Long> {

    /** Transaction-scoped lock so one person's farm creations run one at a time. */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext('farm-create:' || :userId))::text", nativeQuery = true)
    String lockForUser(@Param("userId") String userId);

    /** A farm the person is an active owner of, by name (ignoring case). */
    @Query("""
            select f from Farm f where lower(f.name) = lower(:name) and exists (
              select 1 from FarmMember m where m.farmId = f.id and m.userId = :userId
                and m.role = com.dairyfarm.member.Role.OWNER and m.active = true)
            order by f.id""")
    List<Farm> findOwnedByName(@Param("userId") UUID userId, @Param("name") String name);
}
