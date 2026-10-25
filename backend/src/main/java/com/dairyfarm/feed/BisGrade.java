package com.dairyfarm.feed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * IS 2052:2023 compounded cattle feed grades (Table 1). Type III was removed by Amendment 4 to IS 2052:2009.
 * Every limit except moisture is on a moisture-free (dry matter) basis, so the as-fed mix values are divided by
 * the mix DM before comparing. Aflatoxin B1 is in ppb (µg/kg).
 */
public enum BisGrade {
    TYPE_I("Type I", 22, 4.0, 10, 2.5),
    TYPE_II("Type II", 20, 3.0, 12, 3.0);

    static final double MOISTURE_MAX = 11, CA_MIN = 0.8, P_MIN = 0.5, AFLATOXIN_MAX = 20;

    public final String label;
    final double cpMin, fatMin, fibreMax, aiaMax;

    BisGrade(String label, double cpMin, double fatMin, double fibreMax, double aiaMax) {
        this.label = label;
        this.cpMin = cpMin;
        this.fatMin = fatMin;
        this.fibreMax = fibreMax;
        this.aiaMax = aiaMax;
    }

    /** As-fed mix values (weighted average of the ingredients); null = some ingredient has no value. */
    public record Mix(Double dmPct, Double cpPct, Double fatPct, Double fibrePct, Double aiaPct, Double caPct, Double pPct,
                      Double aflatoxinPpb) {
    }

    /** One checked parameter. value is dry basis except moisture; status PASS | FAIL | UNKNOWN. */
    public record Param(String name, BigDecimal value, BigDecimal limit, String kind, String unit, String status) {
    }

    /** status PASS (all pass) | FAIL (any fails) | INCOMPLETE (none fail, some unknown). */
    public record Check(BisGrade grade, String label, String status, List<Param> params) {
    }

    public Check check(Mix m) {
        Double dm = m.dmPct();
        boolean dmOk = dm != null && dm > 0;
        List<Param> p = new ArrayList<>();
        p.add(param("Moisture", dm == null ? null : 100 - dm, MOISTURE_MAX, false, "%"));
        p.add(param("Crude protein", dry(m.cpPct(), dm, dmOk), cpMin, true, "%"));
        p.add(param("Crude fat", dry(m.fatPct(), dm, dmOk), fatMin, true, "%"));
        p.add(param("Crude fibre", dry(m.fibrePct(), dm, dmOk), fibreMax, false, "%"));
        p.add(param("Acid insoluble ash", dry(m.aiaPct(), dm, dmOk), aiaMax, false, "%"));
        p.add(param("Calcium", dry(m.caPct(), dm, dmOk), CA_MIN, true, "%"));
        p.add(param("Total phosphorus", dry(m.pPct(), dm, dmOk), P_MIN, true, "%"));
        p.add(param("Aflatoxin B1", dry(m.aflatoxinPpb(), dm, dmOk), AFLATOXIN_MAX, false, "ppb"));
        boolean fail = p.stream().anyMatch(x -> x.status().equals("FAIL"));
        boolean unknown = p.stream().anyMatch(x -> x.status().equals("UNKNOWN"));
        return new Check(this, label, fail ? "FAIL" : unknown ? "INCOMPLETE" : "PASS", p);
    }

    private static Double dry(Double asFed, Double dm, boolean dmOk) {
        return asFed == null || !dmOk ? null : asFed * 100 / dm;
    }

    private static Param param(String name, Double value, double limit, boolean min, String unit) {
        BigDecimal v = value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
        String status = v == null ? "UNKNOWN"
                : (min ? v.doubleValue() >= limit : v.doubleValue() <= limit) ? "PASS" : "FAIL";
        return new Param(name, v, BigDecimal.valueOf(limit), min ? "MIN" : "MAX", unit, status);
    }
}
