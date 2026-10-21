package com.dairyfarm.animal;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.dairyfarm.animal.AnimalEnums.Status;

/** Lets the CSV import add animals with the same rules as the app's form. */
@Component
public class AnimalImporter {

    private final AnimalService service;

    AnimalImporter(AnimalService service) {
        this.service = service;
    }

    /** Adds one animal, already sold/dead/transferred when the file says so. */
    public void add(Long farmId, AnimalRequest req, Status status, LocalDate exitDate) {
        if (status != Status.ACTIVE && req.dateOfBirth() != null && exitDate.isBefore(req.dateOfBirth())) {
            throw com.dairyfarm.common.ApiException.badRequest("exit_date is before dob");
        }
        Animal a = service.createEntity(farmId, req);
        if (status != Status.ACTIVE) {
            a.setStatus(status);
            a.setExitDate(exitDate);
            a.setGroupId(null);
        }
    }

    public void linkParents(Long farmId) {
        service.relinkParents(farmId);
    }
}
