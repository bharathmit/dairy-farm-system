package com.dairyfarm.alert;

import java.math.BigDecimal;
import java.util.Map;

/** A farm's choice for one alert rule (JSONB in farm.alert_rules). Null values use the rule's defaults. */
public record RuleSetting(Boolean enabled, Boolean workerVisible, Map<String, BigDecimal> params) {
}
