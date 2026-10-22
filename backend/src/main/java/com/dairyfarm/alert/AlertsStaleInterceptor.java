package com.dairyfarm.alert;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * After any successful change to a farm's records (POST/PUT/PATCH/DELETE), its alerts are marked out of date,
 * so logging e.g. a pregnancy check closes the "pregnancy check due" alert on the next look at Alerts or Home.
 * A refresh takes well under a second for a farm, so running it on the next read is cheap.
 */
@Component
public class AlertsStaleInterceptor implements HandlerInterceptor {

    private final AlertRepository alerts;

    public AlertsStaleInterceptor(AlertRepository alerts) {
        this.alerts = alerts;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        if (ex != null || res.getStatus() >= 400 || "GET".equals(req.getMethod()) || "HEAD".equals(req.getMethod())
                || "OPTIONS".equals(req.getMethod()) || req.getRequestURI().endsWith("/alerts/refresh")) {
            return;
        }
        Object vars = req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars instanceof java.util.Map<?, ?> m && m.get("farmId") instanceof String id) {
            try {
                alerts.markStale(Long.valueOf(id));
            } catch (NumberFormatException ignored) {
                // not a farm path
            }
        }
    }
}
