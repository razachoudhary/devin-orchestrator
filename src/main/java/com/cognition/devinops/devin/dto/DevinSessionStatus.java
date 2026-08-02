package com.cognition.devinops.devin.dto;

import java.util.Locale;
import java.util.Set;

public record DevinSessionStatus(SessionStatus status, String reason) {

    public enum SessionStatus {
        NEW,        // session created, no machine assigned yet
        CLAIMED,    // machine assigned, boot in progress
        RUNNING,    // actively working
        EXIT,       // process ended
        ERROR,      // session crashed
        SUSPENDED,  // normal resting state after Devin finishes work and waits; not a failure
        RESUMING    // transient state right after a message auto-resumes a suspended session; treat as active
    }

    private static final Set<String> BLOCKED_REASONS = Set.of(
            "usage_limit_exceeded",
            "out_of_credits",
            "out_of_quota",
            "no_quota_allocation",
            "payment_declined",
            "org_usage_limit_exceeded",
            "total_session_limit_exceeded");

    private static final Set<String> RESTING_REASONS = Set.of(
            "waiting_for_user",
            "finished");

    public static DevinSessionStatus of(String status, String reason) {
        return new DevinSessionStatus(SessionStatus.valueOf(status.toUpperCase(Locale.ROOT)), reason);
    }

    public boolean isActive() {
        return !isSettled()
                && (status == SessionStatus.NEW
                || status == SessionStatus.CLAIMED
                || status == SessionStatus.RUNNING
                || status == SessionStatus.RESUMING);
    }

    public boolean isSettled() {
        return status == SessionStatus.SUSPENDED
                || status == SessionStatus.EXIT
                || (reason != null && RESTING_REASONS.contains(reason));
    }

    public boolean isBlocked() {
        return reason != null && BLOCKED_REASONS.contains(reason);
    }
}
