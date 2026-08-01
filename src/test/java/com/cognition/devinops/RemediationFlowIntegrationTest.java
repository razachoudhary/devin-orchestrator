package com.cognition.devinops;

import static org.assertj.core.api.Assertions.assertThat;

import com.cognition.devinops.devin.ReplayDevinClient;
import com.cognition.devinops.domain.FindingSource;
import com.cognition.devinops.domain.Remediation;
import com.cognition.devinops.domain.RemediationState;
import com.cognition.devinops.domain.StateTransition;
import com.cognition.devinops.orchestration.RemediationService;
import com.cognition.devinops.orchestration.SessionReconciler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "app.reconcile-interval-ms=3600000",
        "app.daily-session-budget=2"
})
@ActiveProfiles("simulate")
@Testcontainers
@Transactional
class RemediationFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @TestConfiguration
    static class ReplayClockConfiguration {

        @Bean
        MutableClock mutableClock() {
            return new MutableClock();
        }

        @Bean
        @Primary
        ReplayDevinClient replayDevinClient(ObjectMapper objectMapper, MutableClock clock) {
            return new ReplayDevinClient(objectMapper, clock);
        }
    }

    @Autowired
    private RemediationService service;

    @Autowired
    private SessionReconciler reconciler;

    @Autowired
    private ReplayDevinClient replayDevinClient;

    @Autowired
    private MutableClock clock;

    private int issueNumber = 100;

    @BeforeEach
    void nextIssueNumber() {
        issueNumber++;
    }

    private Remediation label(String scenario) {
        return service.onIssueLabeled(issueNumber, "seeded issue", "scenario " + scenario, FindingSource.MANUAL);
    }

    @Test
    void happyPathReachesMergedThroughTheFullLifecycle() {
        Remediation remediation = label("happy-path");
        assertThat(remediation.getState()).isEqualTo(RemediationState.SESSION_RUNNING);

        clock.advanceSeconds(13);
        reconciler.reconcile();
        assertThat(remediation.getState()).isEqualTo(RemediationState.PR_OPEN);
        assertThat(remediation.getPrNumber()).isEqualTo(482);
        assertThat(remediation.getAcusConsumed()).isEqualByComparingTo(new BigDecimal("3.4"));
        assertThat(remediation.getStructuredOutput()).contains("\"confidence\":\"high\"");

        service.onCheckRunCompleted(482, "pytest", "success", null);
        assertThat(remediation.getState()).isEqualTo(RemediationState.PR_OPEN);

        service.onPullRequestMerged(482);
        assertThat(remediation.getTransitions()).extracting(StateTransition::getToState).containsExactly(
                RemediationState.RECEIVED,
                RemediationState.SESSION_RUNNING,
                RemediationState.PR_OPEN,
                RemediationState.MERGED);
    }

    @Test
    void ciFailureSendsRepairIntoTheSameSessionAndRecoversToMerged() {
        Remediation remediation = label("repair-loop");
        String sessionId = remediation.getDevinSessionId();

        clock.advanceSeconds(11);
        reconciler.reconcile();
        assertThat(remediation.getState()).isEqualTo(RemediationState.PR_OPEN);

        service.onCheckRunCompleted(483, "pytest", "failure", "AssertionError: separator mismatch");
        assertThat(remediation.getState()).isEqualTo(RemediationState.REPAIR_DISPATCHED);
        assertThat(remediation.getRepairAttempts()).isEqualTo(1);
        assertThat(replayDevinClient.messagesFor(sessionId)).hasSize(1);
        assertThat(replayDevinClient.messagesFor(sessionId).get(0))
                .contains("CI failed on the pull request you opened for issue #" + issueNumber)
                .contains("AssertionError: separator mismatch")
                .contains("Do not open a new pull request");

        clock.advanceSeconds(8);
        reconciler.reconcile();
        assertThat(remediation.getState()).isEqualTo(RemediationState.REPAIR_DISPATCHED);

        clock.advanceSeconds(12);
        reconciler.reconcile();
        assertThat(remediation.getState()).isEqualTo(RemediationState.PR_OPEN);

        service.onPullRequestMerged(483);
        assertThat(remediation.getState()).isEqualTo(RemediationState.MERGED);
        assertThat(remediation.getTransitions()).extracting(StateTransition::getToState).containsExactly(
                RemediationState.RECEIVED,
                RemediationState.SESSION_RUNNING,
                RemediationState.PR_OPEN,
                RemediationState.CI_FAILED,
                RemediationState.REPAIR_DISPATCHED,
                RemediationState.PR_OPEN,
                RemediationState.MERGED);
    }

    @Test
    void exhaustedRepairAttemptsEscalate() {
        Remediation remediation = label("escalation");

        clock.advanceSeconds(9);
        reconciler.reconcile();
        service.onCheckRunCompleted(484, "pytest", "failure", "flaky ordering");
        assertThat(remediation.getRepairAttempts()).isEqualTo(1);

        clock.advanceSeconds(12);
        reconciler.reconcile();
        service.onCheckRunCompleted(484, "pytest", "failure", "flaky ordering");
        assertThat(remediation.getRepairAttempts()).isEqualTo(2);

        clock.advanceSeconds(12);
        reconciler.reconcile();
        service.onCheckRunCompleted(484, "pytest", "failure", "flaky ordering");

        assertThat(remediation.getState()).isEqualTo(RemediationState.ESCALATED);
        assertThat(remediation.getEscalationReason()).isEqualTo("repair attempts exhausted");
    }

    @Test
    void blockedSessionEscalatesWithoutAttemptingRepair() {
        Remediation remediation = label("blocked");
        String sessionId = remediation.getDevinSessionId();

        clock.advanceSeconds(13);
        reconciler.reconcile();

        assertThat(remediation.getState()).isEqualTo(RemediationState.ESCALATED);
        assertThat(remediation.getEscalationReason()).isEqualTo("usage_limit_exceeded");
        assertThat(remediation.getRepairAttempts()).isZero();
        assertThat(replayDevinClient.messagesFor(sessionId)).isEmpty();
    }

    @Test
    void policyGateRejectsDuplicatesAndBudgetOverruns() {
        label("happy-path");
        Remediation duplicate = service.onIssueLabeled(issueNumber, "seeded issue", "scenario happy-path",
                FindingSource.MANUAL);
        assertThat(duplicate.getState()).isEqualTo(RemediationState.GATED_REJECTED);
        assertThat(duplicate.getTransitions().get(1).getReason()).contains("duplicate");

        Remediation second = service.onIssueLabeled(issueNumber + 1, "seeded issue", "scenario happy-path",
                FindingSource.MANUAL);
        assertThat(second.getState()).isEqualTo(RemediationState.SESSION_RUNNING);

        Remediation overBudget = service.onIssueLabeled(issueNumber + 2, "seeded issue", "scenario happy-path",
                FindingSource.MANUAL);
        assertThat(overBudget.getState()).isEqualTo(RemediationState.GATED_REJECTED);
        assertThat(overBudget.getTransitions().get(1).getReason()).contains("budget");
    }
}
