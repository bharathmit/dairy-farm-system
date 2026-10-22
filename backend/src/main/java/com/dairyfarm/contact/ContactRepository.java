package com.dairyfarm.contact;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByFarmIdOrderByNameAsc(Long farmId);

    Optional<Contact> findByIdAndFarmId(Long id, Long farmId);

    List<Contact> findByFarmIdAndIdIn(Long farmId, Collection<Long> ids);

    boolean existsByFarmIdAndNameIgnoreCaseAndIdNot(Long farmId, String name, Long id);

    boolean existsByFarmIdAndNameIgnoreCase(Long farmId, String name);

    /** How many milk sales and farm events point at the contact. */
    @Query(value = """
            select (select count(*) from production_record where buyer_contact_id = :id)
                 + (select count(*) from farm_event where contact_id = :id)
                 + (select count(*) from breeding_record where :id in (technician_contact_id, bull_owner_contact_id,
                        preg_checked_by_contact_id))
                 + (select count(*) from item where supplier_contact_id = :id)
                 + (select count(*) from item_txn where supplier_contact_id = :id)""", nativeQuery = true)
    long countUses(@Param("id") Long id);
}
