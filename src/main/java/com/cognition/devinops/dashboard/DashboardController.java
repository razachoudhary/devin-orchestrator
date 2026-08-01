package com.cognition.devinops.dashboard;

import com.cognition.devinops.config.AppProperties;
import com.cognition.devinops.domain.Remediation;
import com.cognition.devinops.domain.RemediationState;
import com.cognition.devinops.domain.StateTransition;
import com.cognition.devinops.repo.RemediationRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final RemediationRepository remediations;
    private final AppProperties appProperties;
    private final boolean simulateMode;

    public DashboardController(RemediationRepository remediations, AppProperties appProperties,
                               Environment environment) {
        this.remediations = remediations;
        this.appProperties = appProperties;
        this.simulateMode = Arrays.asList(environment.getActiveProfiles()).contains("simulate");
    }

    @GetMapping("/api/dashboard")
    @Transactional(readOnly = true)
    public DashboardStats dashboard() {
        List<Remediation> all = remediations.findAll();

        long received = all.size();
        long merged = countByState(all, RemediationState.MERGED);
        long escalated = countByState(all, RemediationState.ESCALATED);
        long failed = countByState(all, RemediationState.FAILED);
        long inFlight = all.stream().filter(r -> !r.getState().isTerminal()).count();

        long settled = merged + escalated + failed;
        Double successRate = settled == 0 ? null : (double) merged / settled;

        List<Double> cycleTimes = all.stream()
                .filter(r -> r.getState() == RemediationState.MERGED)
                .map(DashboardController::cycleTimeMinutes)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
        Double medianCycleTime = median(cycleTimes);

        BigDecimal totalAcus = all.stream()
                .map(Remediation::getAcusConsumed)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Double acusPerMergedPr = merged == 0 ? null : totalAcus.doubleValue() / merged;
        Double estimatedCostPerMergedPr = acusPerMergedPr == null
                ? null : acusPerMergedPr * appProperties.acuCostUsd().doubleValue();

        long mergedFirstPass = all.stream()
                .filter(r -> r.getState() == RemediationState.MERGED && r.getRepairAttempts() == 0)
                .count();
        Double firstPassCiSuccessRate = merged == 0 ? null : (double) mergedFirstPass / merged;

        long repairLoopRecoveries = all.stream()
                .filter(r -> r.getState() == RemediationState.MERGED && r.getRepairAttempts() > 0)
                .count();

        Map<String, Long> escalationsByReason = new LinkedHashMap<>();
        all.stream()
                .filter(r -> r.getState() == RemediationState.ESCALATED)
                .forEach(r -> escalationsByReason.merge(
                        r.getEscalationReason() == null ? "unknown" : r.getEscalationReason(), 1L, Long::sum));

        List<DashboardStats.Row> rows = new ArrayList<>();
        all.stream()
                .sorted(Comparator.comparing(Remediation::getId).reversed())
                .forEach(r -> rows.add(new DashboardStats.Row(
                        r.getIssueNumber(),
                        r.getIssueTitle(),
                        r.getState().name(),
                        r.getState() == RemediationState.ESCALATED ? r.getEscalationReason() : null,
                        r.getPrUrl(),
                        r.getDevinSessionUrl(),
                        r.getAcusConsumed(),
                        r.getRepairAttempts(),
                        cycleTimeMinutes(r))));

        return new DashboardStats(
                new DashboardStats.Totals(received, merged, escalated, failed, inFlight),
                successRate,
                medianCycleTime,
                totalAcus,
                acusPerMergedPr,
                estimatedCostPerMergedPr,
                firstPassCiSuccessRate,
                repairLoopRecoveries,
                escalationsByReason,
                rows,
                simulateMode);
    }

    private static long countByState(List<Remediation> all, RemediationState state) {
        return all.stream().filter(r -> r.getState() == state).count();
    }

    private static Double cycleTimeMinutes(Remediation remediation) {
        if (remediation.getState() != RemediationState.MERGED) {
            return null;
        }
        List<StateTransition> transitions = remediation.getTransitions();
        StateTransition first = transitions.get(0);
        StateTransition last = transitions.get(transitions.size() - 1);
        double minutes = Duration.between(first.getOccurredAt(), last.getOccurredAt()).toSeconds() / 60.0;
        return Math.round(minutes * 10.0) / 10.0;
    }

    private static Double median(List<Double> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        int middle = sorted.size() / 2;
        double median = sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        return Math.round(median * 10.0) / 10.0;
    }
}
