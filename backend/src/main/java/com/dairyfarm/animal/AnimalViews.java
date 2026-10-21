package com.dairyfarm.animal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.dairyfarm.animal.AnimalEnums.Acquisition;
import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.animal.AnimalEnums.Status;
import com.dairyfarm.common.Sex;
import com.dairyfarm.common.Species;

/** Response shapes for animals. Prices are left out for workers. */
public final class AnimalViews {

    private AnimalViews() {
    }

    /** A parent: an animal on the farm (id set) or only a tag / AI bull code. */
    public record ParentRef(Long id, String tag, String name) {
    }

    /** id is the file name; url is a short-lived signed link (absent when storage isn't set up). */
    public record Photo(String id, String url) {
    }

    public record AnimalDetail(
            Long id, String tag, String name, String nationalId, Species species, String breed, Sex sex,
            LocalDate dateOfBirth, String colour, BigDecimal birthWeightKg, Category category, Status status,
            Long groupId, String groupName, ParentRef dam, ParentRef sire,
            Acquisition acquisitionType, LocalDate acquisitionDate, BigDecimal purchasePrice,
            LocalDate exitDate, String exitReason, BigDecimal exitPrice, String exitParty,
            LocalDate lastCalvingDate, BigDecimal currentWeightKg, String notes,
            List<Photo> photos, Instant createdAt, Instant updatedAt) {
    }

    public record AnimalListItem(
            Long id, String tag, String name, Species species, String breed, Sex sex, Category category,
            Status status, LocalDate dateOfBirth, Long groupId, String groupName, LocalDate lastCalvingDate,
            String photoUrl) {
    }

    /** Counts for the list header chips. */
    public record HerdSummary(long active, Map<String, Long> activeByCategory, Map<String, Long> byStatus) {
    }
}
