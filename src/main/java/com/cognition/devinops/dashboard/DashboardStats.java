package com.cognition.devinops.dashboard;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardStats(
        Totals totals,
        Double successRate,
        Double medianCycleTimeMinutes,
        BigDecimal totalAcus,
        Double acusPerMergedPr,
        Double estimatedCostPerMergedPr,
        Double firstPassCiSuccessRate,
        long repairLoopRecoveries,
        Map<String, Long> escalationsByReason,
        List<Row> remediations,
        boolean simulateMode
) {

    public record Totals(long received, long merged, long escalated, long failed, long inFlight) {
    }

    public record Row(
            int issueNumber,
            String issueTitle,
            String state,
            String stateReason,
            String prUrl,
            String sessionUrl,
            BigDecimal acus,
            int repairAttempts,
            Double cycleTimeMinutes
    ) {
    }
}
