package com.cognition.devinops.ingress;

import com.cognition.devinops.domain.Remediation;
import com.cognition.devinops.domain.RemediationState;
import com.cognition.devinops.orchestration.RemediationService;
import com.cognition.devinops.repo.RemediationRepository;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("simulate")
public class SimulateScenarioDriver {

    private static final String SIMULATED_CHECK = "pytest devin_gate_tests (simulated)";

    private static final String SIMULATED_FAILURE_LOG = """
            =================================== FAILURES ===================================
            ____________ test_format_currency_handles_missing_currency ____________
            AssertionError: assert '1,234.50' == '1 234,50'
            ========================= 1 failed, 11 passed in 4.21s =========================
            """;

    private final RemediationRepository remediations;
    private final RemediationService remediationService;

    public SimulateScenarioDriver(RemediationRepository remediations, RemediationService remediationService) {
        this.remediations = remediations;
        this.remediationService = remediationService;
    }

    @Scheduled(fixedDelayString = "${app.reconcile-interval-ms:2000}")
    @Transactional
    public void drive() {
        for (Remediation remediation : remediations.findByStateIn(Set.of(RemediationState.PR_OPEN))) {
            String scenario = scenarioOf(remediation);
            if (scenario == null || remediation.getPrNumber() == null) {
                continue;
            }
            switch (scenario) {
                case "happy-path" -> pass(remediation);
                case "repair-loop" -> {
                    if (remediation.getRepairAttempts() == 0) {
                        fail(remediation);
                    } else {
                        pass(remediation);
                    }
                }
                case "escalation" -> fail(remediation);
                default -> {
                }
            }
        }
    }

    private void pass(Remediation remediation) {
        remediationService.onCheckRunCompleted(remediation.getPrNumber(), SIMULATED_CHECK, "success", null);
        remediationService.onPullRequestMerged(remediation.getPrNumber());
    }

    private void fail(Remediation remediation) {
        remediationService.onCheckRunCompleted(remediation.getPrNumber(), SIMULATED_CHECK, "failure",
                SIMULATED_FAILURE_LOG);
    }

    private static String scenarioOf(Remediation remediation) {
        String title = remediation.getIssueTitle();
        if (title == null || !title.startsWith("[") || !title.contains("]")) {
            return null;
        }
        return title.substring(1, title.indexOf(']'));
    }
}
