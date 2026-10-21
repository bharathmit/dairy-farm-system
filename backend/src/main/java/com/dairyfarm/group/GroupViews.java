package com.dairyfarm.group;

import java.time.LocalDate;
import java.util.List;

import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.common.Sex;
import com.dairyfarm.common.Species;

public final class GroupViews {

    private GroupViews() {
    }

    public record GroupSummary(Long id, String name, Species species, String location, GroupPurpose purpose,
                               Long managerMemberId, String managerName, String colour, String notes,
                               long animalCount) {
    }

    public record GroupAnimal(Long id, String tag, String name, Sex sex, Category category, LocalDate dateOfBirth) {
    }

    public record GroupDetail(Long id, String name, Species species, String location, GroupPurpose purpose,
                              Long managerMemberId, String managerName, String colour, String notes,
                              List<GroupAnimal> animals) {
    }
}
