package com.dairyfarm.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FarmMemberRepository extends JpaRepository<FarmMember, Long> {

    Optional<FarmMember> findByFarmIdAndUserIdAndActiveTrue(Long farmId, UUID userId);

    Optional<FarmMember> findByIdAndFarmId(Long id, Long farmId);

    List<FarmMember> findByFarmIdOrderByActiveDescRoleAscNameAsc(Long farmId);

    List<FarmMember> findByUserIdAndActiveTrue(UUID userId);

    boolean existsByFarmIdAndEmailIgnoreCase(Long farmId, String email);

    boolean existsByFarmIdAndEmailIgnoreCaseAndIdNot(Long farmId, String email, Long id);

    boolean existsByFarmIdAndUserId(Long farmId, UUID userId);

    long countByFarmIdAndRoleAndActiveTrue(Long farmId, Role role);

    /** Links memberships that were added by email before this person had an account. */
    @Modifying
    @Query(value = """
            UPDATE farm_member m SET user_id = :userId, updated_at = now()
            WHERE m.user_id IS NULL AND m.email IS NOT NULL AND lower(m.email) = lower(:email)
              AND m.login_type <> 'NONE'
              AND NOT EXISTS (SELECT 1 FROM farm_member o WHERE o.farm_id = m.farm_id AND o.user_id = :userId)
            """, nativeQuery = true)
    int linkPendingByEmail(@Param("userId") UUID userId, @Param("email") String email);
}
