package com.cognition.devinops.orchestration;

import com.cognition.devinops.config.AppProperties;
import com.cognition.devinops.domain.RemediationState;
import com.cognition.devinops.repo.RemediationRepository;
import com.cognition.devinops.repo.StateTransitionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class PolicyGate {

    private final RemediationRepository remediations;
    private final StateTransitionRepository transitions;
    private final AppProperties appProperties;

    public PolicyGate(RemediationRepository remediations, StateTransitionRepository transitions,
                      AppProperties appProperties) {
        this.remediations = remediations;
        this.transitions = transitions;
        this.appProperties = appProperties;
    }

    public GateDecision evaluate(int issueNumber) {
        if (remediations.existsByIssueNumberAndStateIn(issueNumber, RemediationState.liveStates())) {
            return GateDecision.reject("duplicate: issue #%d already has a live remediation".formatted(issueNumber));
        }
        Instant midnightUtc = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        long dispatchedToday = transitions.countByToStateAndOccurredAtGreaterThanEqual(
                RemediationState.SESSION_RUNNING, midnightUtc);
        if (dispatchedToday >= appProperties.dailySessionBudget()) {
            return GateDecision.reject("daily session budget of %d exhausted".formatted(
                    appProperties.dailySessionBudget()));
        }
        return GateDecision.allow();
    }
}
