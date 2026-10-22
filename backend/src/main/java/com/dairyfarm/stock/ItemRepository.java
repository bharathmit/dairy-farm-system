package com.dairyfarm.stock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByIdAndFarmIdAndItemType(Long id, Long farmId, Item.Type type);

    List<Item> findByFarmIdAndItemTypeOrderByNameAsc(Long farmId, Item.Type type);

    List<Item> findByFarmIdAndIdIn(Long farmId, Collection<Long> ids);
}
