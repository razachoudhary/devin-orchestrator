package com.cognition.devinops.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        int dailySessionBudget,
        int maxRepairAttempts,
        int sessionTimeoutMinutes,
        BigDecimal acuCostUsd,
        String ciCommand,
        long reconcileIntervalMs
) {
}
