package com.dairyfarm.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findFirstByEmailIgnoreCase(String email);

    /** Returns 1 when a new row was created, 0 when the user already existed. */
    @Modifying
    @Query(value = """
            INSERT INTO app_user (id, email, full_name) VALUES (:id, :email, :name)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("email") String email, @Param("name") String name);
}
