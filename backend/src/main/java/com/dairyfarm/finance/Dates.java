package com.dairyfarm.finance;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Short date labels: "12–18 Oct", "28 Sep – 4 Oct". */
public final class Dates {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private Dates() {
    }

    public static String range(LocalDate from, LocalDate to) {
        if (to == null || to.equals(from)) {
            return DAY_MONTH.format(from);
        }
        if (from.getMonth() == to.getMonth() && from.getYear() == to.getYear()) {
            return from.getDayOfMonth() + "–" + DAY_MONTH.format(to);
        }
        return DAY_MONTH.format(from) + " – " + DAY_MONTH.format(to);
    }
}
