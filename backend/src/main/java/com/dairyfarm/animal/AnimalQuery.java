package com.dairyfarm.animal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.animal.AnimalEnums.Status;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.common.Sex;
import com.dairyfarm.common.Species;

import jakarta.persistence.criteria.Predicate;

/**
 * Filters for the animal list. Empty lists mean "any".
 * sort: tag (default), name, newest (recently added), oldest (by date of birth).
 */
public record AnimalQuery(String q, List<Species> species, List<Category> category, List<Status> status,
                          Sex sex, Long groupId, Boolean noGroup, String sort) {

    Specification<Animal> toSpecification(Long farmId) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("farmId"), farmId));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.get("tag")), like, '\\'),
                        cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(root.get("nationalId")), like, '\\')));
            }
            if (species != null && !species.isEmpty()) {
                where.add(root.get("species").in(species));
            }
            if (category != null && !category.isEmpty()) {
                where.add(root.get("category").in(category));
            }
            if (status != null && !status.isEmpty()) {
                where.add(root.get("status").in(status));
            }
            if (sex != null) {
                where.add(cb.equal(root.get("sex"), sex));
            }
            if (groupId != null) {
                where.add(cb.equal(root.get("groupId"), groupId));
            }
            if (Boolean.TRUE.equals(noGroup)) {
                where.add(cb.isNull(root.get("groupId")));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    Sort toSort() {
        String s = sort == null ? "tag" : sort.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "tag" -> Sort.by("tag");
            case "name" -> Sort.by(Sort.Order.asc("name").nullsLast(), Sort.Order.asc("tag"));
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case "oldest" -> Sort.by(Sort.Order.asc("dateOfBirth").nullsLast(), Sort.Order.asc("tag"));
            default -> throw ApiException.badRequest("Sort by tag, name, newest or oldest.");
        };
    }
}
