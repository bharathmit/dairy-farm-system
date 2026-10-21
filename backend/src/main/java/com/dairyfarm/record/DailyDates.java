package com.dairyfarm.record;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.farm.FarmService;

/** Date checks shared by the daily records. */
public final class DailyDates {

    private DailyDates() {
    }

    public static LocalDate today() {
        return LocalDate.now(FarmService.FARM_ZONE);
    }

    /** Records are for today or earlier. */
    public static void notFuture(LocalDate date) {
        if (date.isAfter(today())) {
            throw ApiException.badRequest("The date can't be in the future.");
        }
    }

    public static void range(LocalDate from, LocalDate to, int maxDays) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("'to' must be on or after 'from'.");
        }
        if (ChronoUnit.DAYS.between(from, to) > maxDays) {
            throw ApiException.badRequest("Pick a range of at most " + maxDays + " days.");
        }
    }
}
