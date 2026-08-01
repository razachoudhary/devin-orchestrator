package com.cognition.devinops.ingress;

import com.cognition.devinops.domain.FindingSource;
import com.cognition.devinops.domain.Remediation;
import com.cognition.devinops.orchestration.RemediationService;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("simulate")
public class SimulateController {

    private static final Set<String> SCENARIOS = Set.of("happy-path", "repair-loop", "escalation", "blocked");

    private final RemediationService remediationService;
    private final AtomicInteger issueCounter =
            new AtomicInteger((int) (Instant.now().getEpochSecond() % 100_000));

    public SimulateController(RemediationService remediationService) {
        this.remediationService = remediationService;
    }

    public record SimulateResponse(int issueNumber, String state, String sessionUrl) {
    }

    @PostMapping("/admin/simulate/{scenario}")
    public ResponseEntity<SimulateResponse> simulate(@PathVariable String scenario) {
        if (!SCENARIOS.contains(scenario)) {
            return ResponseEntity.badRequest().build();
        }
        int issueNumber = issueCounter.incrementAndGet();
        Remediation remediation = remediationService.onIssueLabeled(issueNumber,
                "[%s] simulated maintenance finding".formatted(scenario),
                "scenario " + scenario, FindingSource.MANUAL);
        return ResponseEntity.accepted().body(new SimulateResponse(
                remediation.getIssueNumber(), remediation.getState().name(), remediation.getDevinSessionUrl()));
    }
}
