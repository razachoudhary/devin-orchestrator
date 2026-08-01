package com.cognition.devinops.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RemediationStateMachineTest {

    private static final int MAX_REPAIR_ATTEMPTS = 2;

    private Remediation fresh() {
        return Remediation.receive(47, "flaky rolling window", FindingSource.MANUAL);
    }

    /** Drives a remediation to the given state through legal transitions only. */
    private Remediation at(RemediationState target) {
        Remediation r = fresh();
        switch (target) {
            case RECEIVED -> { }
            case GATED_REJECTED -> r.transitionTo(RemediationState.GATED_REJECTED, "duplicate");
            case SESSION_RUNNING -> r.transitionTo(RemediationState.SESSION_RUNNING, "dispatched");
            case PR_OPEN -> {
                r.transitionTo(RemediationState.SESSION_RUNNING, "dispatched");
                r.transitionTo(RemediationState.PR_OPEN, "pr opened");
            }
            case CI_FAILED -> {
                r.transitionTo(RemediationState.SESSION_RUNNING, "dispatched");
                r.transitionTo(RemediationState.PR_OPEN, "pr opened");
                r.transitionTo(RemediationState.CI_FAILED, "ci red");
            }
            case REPAIR_DISPATCHED -> {
                r.transitionTo(RemediationState.SESSION_RUNNING, "dispatched");
                r.transitionTo(RemediationState.PR_OPEN, "pr opened");
                r.transitionTo(RemediationState.CI_FAILED, "ci red");
                r.dispatchRepair("attempt 1", MAX_REPAIR_ATTEMPTS);
            }
            case MERGED -> {
                r.transitionTo(RemediationState.SESSION_RUNNING, "dispatched");
                r.transitionTo(RemediationState.PR_OPEN, "pr opened");
                r.transitionTo(RemediationState.MERGED, "merged");
            }
            case ESCALATED -> {
                r.transitionTo(RemediationState.SESSION_RUNNING, "dispatched");
                r.transitionTo(RemediationState.ESCALATED, "needs a human");
            }
            case FAILED -> {
                r.transitionTo(RemediationState.SESSION_RUNNING, "dispatched");
                r.transitionTo(RemediationState.FAILED, "session errored");
            }
        }
        assertThat(r.getState()).isEqualTo(target);
        return r;
    }

    @Nested
    class LegalTransitions {

        @ParameterizedTest
        @EnumSource(RemediationState.class)
        void everyLegalTargetIsAccepted(RemediationState from) {
            for (RemediationState to : RemediationState.values()) {
                if (!from.canTransitionTo(to)) {
                    continue;
                }
                Remediation r = at(from);
                if (to == RemediationState.REPAIR_DISPATCHED) {
                    assertThatCode(() -> r.dispatchRepair("repair", MAX_REPAIR_ATTEMPTS))
                            .doesNotThrowAnyException();
                } else {
                    assertThatCode(() -> r.transitionTo(to, "test"))
                            .doesNotThrowAnyException();
                }
                assertThat(r.getState()).isEqualTo(to);
            }
        }

        @Test
        void repairLoopCanCycleBackToPrOpenAndMerge() {
            Remediation r = at(RemediationState.REPAIR_DISPATCHED);
            r.transitionTo(RemediationState.PR_OPEN, "pr updated");
            r.transitionTo(RemediationState.MERGED, "merged");
            assertThat(r.getState()).isEqualTo(RemediationState.MERGED);
        }
    }

    @Nested
    class IllegalTransitions {

        @ParameterizedTest
        @EnumSource(RemediationState.class)
        void everyIllegalTargetThrows(RemediationState from) {
            for (RemediationState to : RemediationState.values()) {
                if (from.canTransitionTo(to)) {
                    continue;
                }
                Remediation r = at(from);
                if (to == RemediationState.REPAIR_DISPATCHED) {
                    assertThatThrownBy(() -> r.dispatchRepair("repair", MAX_REPAIR_ATTEMPTS))
                            .isInstanceOf(IllegalStateTransitionException.class);
                } else {
                    assertThatThrownBy(() -> r.transitionTo(to, "test"))
                            .isInstanceOf(IllegalStateTransitionException.class);
                }
                assertThat(r.getState()).isEqualTo(from);
            }
        }

        @ParameterizedTest
        @EnumSource(value = RemediationState.class,
                names = {"GATED_REJECTED", "MERGED", "ESCALATED", "FAILED"})
        void terminalStatesRejectEverything(RemediationState terminal) {
            assertThat(terminal.isTerminal()).isTrue();
            for (RemediationState to : RemediationState.values()) {
                Remediation r = at(terminal);
                assertThatThrownBy(() -> r.transitionTo(to, "test"))
                        .isInstanceOf(IllegalStateTransitionException.class);
            }
        }

        @Test
        void repairDispatchedIsNotReachableThroughTransitionTo() {
            Remediation r = at(RemediationState.CI_FAILED);
            assertThatThrownBy(() -> r.transitionTo(RemediationState.REPAIR_DISPATCHED, "repair"))
                    .isInstanceOf(IllegalStateTransitionException.class)
                    .hasMessageContaining("dispatchRepair");
        }
    }

    @Nested
    class RepairCap {

        @Test
        void repairIsAllowedUpToTheCapThenThrows() {
            Remediation r = at(RemediationState.CI_FAILED);

            r.dispatchRepair("attempt 1", MAX_REPAIR_ATTEMPTS);
            assertThat(r.getRepairAttempts()).isEqualTo(1);

            r.transitionTo(RemediationState.PR_OPEN, "pr updated");
            r.transitionTo(RemediationState.CI_FAILED, "ci red again");
            r.dispatchRepair("attempt 2", MAX_REPAIR_ATTEMPTS);
            assertThat(r.getRepairAttempts()).isEqualTo(2);

            r.transitionTo(RemediationState.PR_OPEN, "pr updated");
            r.transitionTo(RemediationState.CI_FAILED, "ci red again");
            assertThat(r.canDispatchRepair(MAX_REPAIR_ATTEMPTS)).isFalse();
            assertThatThrownBy(() -> r.dispatchRepair("attempt 3", MAX_REPAIR_ATTEMPTS))
                    .isInstanceOf(IllegalStateTransitionException.class)
                    .hasMessageContaining("repair attempts exhausted");
            assertThat(r.getState()).isEqualTo(RemediationState.CI_FAILED);
            assertThat(r.getRepairAttempts()).isEqualTo(2);
        }
    }

    @Nested
    class Bookkeeping {

        @Test
        void receiveRecordsTheInitialTransition() {
            Remediation r = fresh();
            assertThat(r.getState()).isEqualTo(RemediationState.RECEIVED);
            List<StateTransition> transitions = r.getTransitions();
            assertThat(transitions).hasSize(1);
            assertThat(transitions.get(0).getFromState()).isNull();
            assertThat(transitions.get(0).getToState()).isEqualTo(RemediationState.RECEIVED);
        }

        @Test
        void everyTransitionIsAppendedWithFromAndTo() {
            Remediation r = at(RemediationState.CI_FAILED);
            r.dispatchRepair("attempt 1", MAX_REPAIR_ATTEMPTS);

            List<StateTransition> transitions = r.getTransitions();
            assertThat(transitions).extracting(StateTransition::getToState).containsExactly(
                    RemediationState.RECEIVED,
                    RemediationState.SESSION_RUNNING,
                    RemediationState.PR_OPEN,
                    RemediationState.CI_FAILED,
                    RemediationState.REPAIR_DISPATCHED);
            assertThat(transitions.get(4).getFromState()).isEqualTo(RemediationState.CI_FAILED);
            assertThat(transitions.get(4).getReason()).isEqualTo("attempt 1");
        }

        @Test
        void escalationReasonIsCapturedOnEscalate() {
            Remediation r = at(RemediationState.SESSION_RUNNING);
            r.transitionTo(RemediationState.ESCALATED, "session timeout");
            assertThat(r.getEscalationReason()).isEqualTo("session timeout");
        }

        @Test
        void updatedAtIsBumpedByTransitions() {
            Remediation r = fresh();
            var before = r.getUpdatedAt();
            r.transitionTo(RemediationState.SESSION_RUNNING, "dispatched");
            assertThat(r.getUpdatedAt()).isAfterOrEqualTo(before);
        }
    }
}
