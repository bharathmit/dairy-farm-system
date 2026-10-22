package com.dairyfarm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.dairyfarm.alert.AlertsStaleInterceptor;
import com.dairyfarm.security.FarmAccessInterceptor;
import com.dairyfarm.security.UserProvisioningInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UserProvisioningInterceptor provisioning;
    private final FarmAccessInterceptor farmAccess;
    private final AlertsStaleInterceptor alertsStale;

    public WebConfig(UserProvisioningInterceptor provisioning, FarmAccessInterceptor farmAccess,
                     AlertsStaleInterceptor alertsStale) {
        this.provisioning = provisioning;
        this.farmAccess = farmAccess;
        this.alertsStale = alertsStale;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(provisioning).addPathPatterns("/api/**").order(0);
        registry.addInterceptor(farmAccess).addPathPatterns("/api/v1/farms/*/**", "/api/v1/farms/*").order(1);
        registry.addInterceptor(alertsStale).addPathPatterns("/api/v1/farms/*/**").order(2);
    }
}
