package com.dairyfarm.alert;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.dairyfarm.alert.AlertViews.AlertCategory;
import com.dairyfarm.alert.AlertViews.Rule;
import com.dairyfarm.alert.AlertViews.RuleUpdate;
import com.dairyfarm.alert.AlertViews.Severity;
import com.dairyfarm.breeding.BreedingSettings;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.farm.Farm;
import com.dairyfarm.farm.FarmSettings;
import com.dairyfarm.milk.MilkGradingRules;

import tools.jackson.databind.json.JsonMapper;

/**
 * The alert rules, their defaults and where each number lives. Numbers that already exist elsewhere
 * (breeding settings, milk grading, farm settings) are read from and written to that place, so they are stored once.
 */
@Component
public class AlertRuleBook {

    /** Where a parameter is stored. */
    enum Link { OWN, BREEDING, GRADING, FARM }

    record Param(String key, BigDecimal def, BigDecimal min, BigDecimal max, Link link) {
    }

    record Def(String code, AlertCategory category, Severity severity, String title, String description,
               List<Param> params, boolean alwaysOn, boolean ownerOnly, boolean workerVisible) {
    }

    /** A rule as it applies to one farm. */
    public record Resolved(Def def, boolean enabled, boolean workerVisible, Map<String, BigDecimal> params) {
        public BigDecimal p(String key) {
            return params.get(key);
        }

        public int i(String key) {
            return params.get(key).intValue();
        }
    }

    static final List<Def> DEFS = List.of(
            def("HEAT_EXPECTED", AlertCategory.REPRODUCTION, Severity.INFO, "Heat expected", "days after last heat",
                    p("heatCycleDays", "21", "15", "30", Link.BREEDING)),
            def("PD_DUE", AlertCategory.REPRODUCTION, Severity.MEDIUM, "Pregnancy check due", "days after AI",
                    p("pdAfterDays", "45", "20", "120", Link.BREEDING)),
            def("CALVING_SOON", AlertCategory.REPRODUCTION, Severity.MEDIUM, "Calving due soon", "days before due date",
                    p("daysBefore", "7", "1", "60", Link.OWN)),
            def("NO_HEAT_AFTER_CALVING", AlertCategory.REPRODUCTION, Severity.MEDIUM, "No heat after calving", "days after calving",
                    p("days", "60", "20", "200", Link.OWN)),
            def("LONG_CALVING_INTERVAL", AlertCategory.REPRODUCTION, Severity.INFO, "Long calving interval", "more than … days",
                    p("days", "400", "300", "700", Link.OWN)),
            def("COLOSTRUM_DUE", AlertCategory.CALF, Severity.HIGH, "Colostrum due", "within … hours of calving",
                    p("colostrumHours", "2", "1", "12", Link.BREEDING)),
            def("WEANING", AlertCategory.CALF, Severity.INFO, "Weaning reminder", "from … days, starter ≥ … kg/day",
                    p("weanMinDays", "90", "30", "240", Link.BREEDING), p("weanStarterKg", "1.0", "0.1", "5", Link.BREEDING)),
            def("HEIFER_FIRST_AI", AlertCategory.CALF, Severity.INFO, "Heifer first AI due", "… kg or … months",
                    p("heiferFirstServiceKg", "250", "50", "600", Link.BREEDING),
                    p("heiferFirstServiceMonths", "14", "6", "36", Link.BREEDING)),
            def("WEIGHT_OVERDUE", AlertCategory.CALF, Severity.MEDIUM, "Weight entry overdue", "calves & heifers every … days",
                    p("everyDays", "7", "1", "90", Link.OWN)),
            def("BCS_RANGE", AlertCategory.HEALTH, Severity.MEDIUM, "BCS out of range", "below … or above …",
                    p("min", "2.5", "1", "5", Link.OWN), p("max", "4.0", "1", "5", Link.OWN)),
            new Def("WITHDRAWAL", AlertCategory.HEALTH, Severity.HIGH, "Withdrawal active", "Always on — milk safety",
                    List.of(), true, false, true),
            def("HEALTH_FOLLOW_UP", AlertCategory.HEALTH, Severity.MEDIUM, "Health follow-up", "next actions on health records"),
            def("SCHEDULE_DUE", AlertCategory.HEALTH, Severity.MEDIUM, "Deworming & vaccination due", "from health schedules"),
            def("YIELD_DROP", AlertCategory.MILK, Severity.MEDIUM, "Sudden yield drop", "more than … % vs recent average",
                    p("dropAlertPct", "20", "5", "80", Link.GRADING)),
            def("LOW_FAT", AlertCategory.MILK, Severity.INFO, "Low fat %", "below … %",
                    p("minFatPct", "3.5", "1", "10", Link.OWN)),
            def("HIGH_SCC", AlertCategory.MILK, Severity.MEDIUM, "High SCC", "above … k cells/mL",
                    p("maxSccK", "200", "50", "2000", Link.OWN)),
            def("DRY_OFF_DUE", AlertCategory.MILK, Severity.MEDIUM, "Dry-off due", "… days before calving",
                    p("dryOffDaysBeforeDue", "60", "0", "120", Link.BREEDING)),
            def("FEED_REORDER", AlertCategory.FEED, Severity.MEDIUM, "Feed reorder", "stock below reorder level or lead time"),
            def("SEMEN_LOW", AlertCategory.FINANCE_OPS, Severity.MEDIUM, "Semen stock low", "fewer than … straws",
                    p("minStraws", "5", "1", "100", Link.OWN)),
            new Def("REPLACEMENT_AGE", AlertCategory.FINANCE_OPS, Severity.INFO, "Cow replacement due", "at age … years",
                    List.of(p("ageYears", "8", "3", "20", Link.OWN)), false, false, false),
            new Def("LOW_MARGIN", AlertCategory.FINANCE_OPS, Severity.HIGH, "Low margin", "weekly margin below … %",
                    List.of(p("marginAlertPct", "30", "0", "100", Link.FARM)), false, true, false),
            def("EQUIPMENT_SERVICE", AlertCategory.FINANCE_OPS, Severity.MEDIUM, "Equipment maintenance", "per equipment schedule"),
            new Def("PROCUREMENT_DUE", AlertCategory.FINANCE_OPS, Severity.INFO, "Procurement needed soon",
                    "planned or ordered items needed within 2 days", List.of(), false, false, false));

    private final JsonMapper json;

    public AlertRuleBook(JsonMapper json) {
        this.json = json;
    }

    private static Def def(String code, AlertCategory cat, Severity sev, String title, String desc, Param... params) {
        return new Def(code, cat, sev, title, desc, List.of(params), false, false, true);
    }

    private static Param p(String key, String def, String min, String max, Link link) {
        return new Param(key, new BigDecimal(def), new BigDecimal(min), new BigDecimal(max), link);
    }

    static Def def(String code) {
        return DEFS.stream().filter(d -> d.code().equals(code)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Unknown alert rule " + code + "."));
    }

    /** Every rule with the farm's choices and linked numbers filled in. */
    public Map<String, Resolved> resolve(Farm farm) {
        Map<String, RuleSetting> saved = farm.getAlertRules();
        Map<String, Object> breeding = map(farm.getBreedingSettings().resolved());
        Map<String, Object> grading = map(farm.getMilkGrading().resolved());
        Map<String, Object> farmSettings = map(farm.getSettings().resolved());
        Map<String, Resolved> out = new LinkedHashMap<>();
        for (Def d : DEFS) {
            RuleSetting s = saved.get(d.code());
            Map<String, BigDecimal> params = new LinkedHashMap<>();
            for (Param p : d.params()) {
                BigDecimal v = switch (p.link()) {
                    case BREEDING -> num(breeding.get(p.key()));
                    case GRADING -> num(grading.get(p.key()));
                    case FARM -> num(farmSettings.get(p.key()));
                    case OWN -> s != null && s.params() != null ? s.params().get(p.key()) : null;
                };
                params.put(p.key(), v != null ? v : p.def());
            }
            boolean enabled = d.alwaysOn() || s == null || s.enabled() == null || s.enabled();
            boolean visible = !d.ownerOnly() && (s == null || s.workerVisible() == null ? d.workerVisible() : s.workerVisible());
            out.put(d.code(), new Resolved(d, enabled, visible, params));
        }
        return out;
    }

    public List<Rule> view(Farm farm) {
        List<Rule> out = new ArrayList<>();
        resolve(farm).values().forEach(r -> out.add(new Rule(r.def().code(), r.def().category(), r.def().title(),
                r.def().description(), r.enabled(), r.workerVisible(), r.params(), r.def().alwaysOn(), r.def().ownerOnly())));
        return out;
    }

    /** Applies rule changes; linked numbers go to their own settings. */
    public void apply(Farm farm, List<RuleUpdate> updates) {
        Map<String, RuleSetting> saved = farm.getAlertRules();
        Map<String, Object> breeding = map(farm.getBreedingSettings().resolved());
        Map<String, Object> grading = map(farm.getMilkGrading().resolved());
        Map<String, Object> farmSettings = map(farm.getSettings().resolved());
        boolean bChanged = false, gChanged = false, fChanged = false;
        for (RuleUpdate u : updates) {
            Def d = def(u.code());
            RuleSetting old = saved.get(d.code());
            Map<String, BigDecimal> own = new LinkedHashMap<>(old != null && old.params() != null ? old.params() : Map.of());
            if (u.params() != null) {
                for (Map.Entry<String, BigDecimal> e : u.params().entrySet()) {
                    Param p = d.params().stream().filter(x -> x.key().equals(e.getKey())).findFirst()
                            .orElseThrow(() -> ApiException.badRequest(d.title() + " has no setting " + e.getKey() + "."));
                    BigDecimal v = e.getValue();
                    if (v == null) {
                        continue;
                    }
                    if (v.compareTo(p.min()) < 0 || v.compareTo(p.max()) > 0) {
                        throw ApiException.badRequest(d.title() + ": use " + p.min().stripTrailingZeros().toPlainString()
                                + " to " + p.max().stripTrailingZeros().toPlainString() + ".");
                    }
                    switch (p.link()) {
                        case BREEDING -> {
                            breeding.put(p.key(), p.key().endsWith("Kg") ? v : (Object) v.intValue());
                            bChanged = true;
                        }
                        case GRADING -> { grading.put(p.key(), v.intValue()); gChanged = true; }
                        case FARM -> { farmSettings.put(p.key(), v.intValue()); fChanged = true; }
                        case OWN -> own.put(p.key(), v);
                    }
                }
            }
            if (d.code().equals("BCS_RANGE")) {
                BigDecimal min = own.getOrDefault("min", d.params().get(0).def());
                BigDecimal max = own.getOrDefault("max", d.params().get(1).def());
                if (min.compareTo(max) >= 0) {
                    throw ApiException.badRequest("BCS out of range: the lower limit must be below the upper one.");
                }
            }
            Boolean enabled = d.alwaysOn() ? Boolean.TRUE : u.enabled() != null ? u.enabled() : old == null ? null : old.enabled();
            Boolean visible = d.ownerOnly() ? Boolean.FALSE
                    : u.workerVisible() != null ? u.workerVisible() : old == null ? null : old.workerVisible();
            saved.put(d.code(), new RuleSetting(enabled, visible, own.isEmpty() ? null : own));
        }
        farm.setAlertRules(saved);
        if (bChanged) {
            farm.setBreedingSettings(json.convertValue(breeding, BreedingSettings.class).resolved());
        }
        if (gChanged) {
            farm.setMilkGrading(json.convertValue(grading, MilkGradingRules.class).resolved());
        }
        if (fChanged) {
            farm.setSettings(json.convertValue(farmSettings, FarmSettings.class).resolved());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object o) {
        return new LinkedHashMap<>(json.convertValue(o, Map.class));
    }

    private static BigDecimal num(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }
}
