package com.cognition.devinops.devin.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionStatusMappingTest {

    @Test
    void activeStatusesKeepThePollingLoopAlive() {
        for (String status : List.of("new", "claimed", "running", "resuming")) {
            assertThat(DevinSessionStatus.of(status, "working").isActive()).isTrue();
        }
        for (String status : List.of("exit", "error", "suspended")) {
            assertThat(DevinSessionStatus.of(status, "finished").isActive()).isFalse();
        }
    }

    @Test
    void suspendedWithFinishedReasonIsNeitherActiveNorBlocked() {
        DevinSessionStatus status = DevinSessionStatus.of("suspended", "finished");
        assertThat(status.isActive()).isFalse();
        assertThat(status.isBlocked()).isFalse();
    }

    @Test
    void exhaustionReasonsAreBlockedAndOrdinaryReasonsAreNot() {
        for (String reason : List.of(
                "usage_limit_exceeded", "out_of_credits", "out_of_quota", "no_quota_allocation",
                "payment_declined", "org_usage_limit_exceeded", "total_session_limit_exceeded")) {
            assertThat(DevinSessionStatus.of("suspended", reason).isBlocked()).isTrue();
        }
        for (String reason : List.of("working", "waiting_for_user", "finished", "inactivity", "error")) {
            assertThat(DevinSessionStatus.of("suspended", reason).isBlocked()).isFalse();
        }
        assertThat(DevinSessionStatus.of("running", null).isBlocked()).isFalse();
    }

    @Test
    void unknownStatusThrows() {
        assertThatThrownBy(() -> DevinSessionStatus.of("hibernating", "working"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runningSessionWaitingForUserIsSettledNotActive() {
        DevinSessionStatus status = DevinSessionStatus.of("running", "waiting_for_user");
        assertThat(status.isActive()).isFalse();
        assertThat(status.isSettled()).isTrue();
        assertThat(status.isBlocked()).isFalse();
    }

    @Test
    void realApiPayloadShapeMapsToStatusDetailAndPrUrl() throws Exception {
        DevinSession session = new ObjectMapper().readValue("""
                {
                  "session_id": "4ea9af71",
                  "url": "https://app.devin.ai/sessions/4ea9af71",
                  "status": "running",
                  "status_detail": "waiting_for_user",
                  "pull_requests": [
                    {"pr_url": "https://github.com/razachoudhary/devin-superset/pull/10", "pr_state": "open"}
                  ],
                  "acus_consumed": 0.0
                }
                """, DevinSession.class);
        assertThat(session.reason()).isEqualTo("waiting_for_user");
        assertThat(session.sessionStatus().isSettled()).isTrue();
        assertThat(session.pullRequests().get(0).number()).isEqualTo(10);
        assertThat(session.pullRequests().get(0).url())
                .isEqualTo("https://github.com/razachoudhary/devin-superset/pull/10");
    }
}
