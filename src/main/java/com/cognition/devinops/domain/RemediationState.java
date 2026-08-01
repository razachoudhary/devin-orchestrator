package com.cognition.devinops.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public enum RemediationState {
    RECEIVED,           // webhook accepted, not yet gated
    GATED_REJECTED,     // terminal — duplicate or over budget
    SESSION_RUNNING,    // Devin session dispatched
    PR_OPEN,            // Devin opened a PR, CI pending
    CI_FAILED,          // CI red
    REPAIR_DISPATCHED,  // failure log sent back into the same session
    MERGED,             // terminal — success
    ESCALATED,          // terminal — needs a human
    FAILED;             // terminal — session errored with no PR

    private static final Map<RemediationState, Set<RemediationState>> LEGAL_TRANSITIONS =
            new EnumMap<>(Map.of(
                    RECEIVED, Set.of(GATED_REJECTED, SESSION_RUNNING),
                    SESSION_RUNNING, Set.of(PR_OPEN, FAILED, ESCALATED),
                    PR_OPEN, Set.of(MERGED, CI_FAILED, ESCALATED),
                    CI_FAILED, Set.of(REPAIR_DISPATCHED, ESCALATED),
                    REPAIR_DISPATCHED, Set.of(PR_OPEN, FAILED, ESCALATED),
                    GATED_REJECTED, Set.of(),
                    MERGED, Set.of(),
                    ESCALATED, Set.of(),
                    FAILED, Set.of()
            ));

    public boolean isTerminal() {
        return LEGAL_TRANSITIONS.get(this).isEmpty();
    }

    public boolean canTransitionTo(RemediationState to) {
        return LEGAL_TRANSITIONS.get(this).contains(to);
    }
}
