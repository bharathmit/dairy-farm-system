package com.dairyfarm.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the alert rules for every farm each morning at 05:00 farm time. */
@Component
@EnableScheduling
public class AlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertScheduler.class);

    private final AlertRepository repo;
    private final AlertEngine engine;

    public AlertScheduler(AlertRepository repo, AlertEngine engine) {
        this.repo = repo;
        this.engine = engine;
    }

    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Kolkata")
    public void daily() {
        for (Long farmId : repo.farmIds()) {
            try {
                engine.refresh(farmId);
            } catch (RuntimeException e) {
                log.warn("Alert run failed for farm {}: {}", farmId, e.getMessage());
            }
        }
    }
}
