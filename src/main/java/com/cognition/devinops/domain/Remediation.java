package com.cognition.devinops.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Aggregate root for a single remediation attempt. All state changes go
 * through {@link #transitionTo} (or {@link #dispatchRepair} for the capped
 * repair transition) so that every change is validated against the legal
 * transition table and recorded as a {@link StateTransition}.
 */
@Entity
@Table(name = "remediations")
public class Remediation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_number", nullable = false)
    private int issueNumber;

    @Column(name = "issue_title", nullable = false)
    private String issueTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RemediationState state;

    @Column(name = "devin_session_id")
    private String devinSessionId;

    @Column(name = "devin_session_url")
    private String devinSessionUrl;

    @Column(name = "pr_url")
    private String prUrl;

    @Column(name = "pr_number")
    private Integer prNumber;

    @Column(name = "status_comment_id")
    private Long statusCommentId;

    @Column(name = "acus_consumed")
    private BigDecimal acusConsumed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_output")
    private String structuredOutput;

    @Column(name = "repair_attempts", nullable = false)
    private int repairAttempts;

    @Column(name = "escalation_reason")
    private String escalationReason;

    // The webhook thread and the reconciler race on the same row; optimistic
    // locking makes the loser retry instead of silently overwriting.
    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "remediation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt asc, id asc")
    private List<StateTransition> transitions = new ArrayList<>();

    protected Remediation() {
        // JPA
    }

    private Remediation(int issueNumber, String issueTitle, FindingSource source) {
        this.issueNumber = issueNumber;
        this.issueTitle = issueTitle;
        this.source = source;
        this.state = RemediationState.RECEIVED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.transitions.add(new StateTransition(this, null, RemediationState.RECEIVED, "webhook accepted"));
    }

    public static Remediation receive(int issueNumber, String issueTitle, FindingSource source) {
        return new Remediation(issueNumber, issueTitle, source);
    }

    /**
     * The single guarded state mutation. Validates the transition against the
     * legal transition table, records it, and bumps {@code updatedAt}.
     *
     * <p>{@code REPAIR_DISPATCHED} is deliberately not reachable through this
     * method — use {@link #dispatchRepair} so the attempt cap is enforced.
     */
    public void transitionTo(RemediationState to, String reason) {
        if (to == RemediationState.REPAIR_DISPATCHED) {
            throw new IllegalStateTransitionException(
                    "REPAIR_DISPATCHED must go through dispatchRepair() so the attempt cap is enforced");
        }
        doTransition(to, reason);
    }

    /**
     * CI_FAILED -> REPAIR_DISPATCHED, permitted only while
     * {@code repairAttempts < maxRepairAttempts}. Increments the attempt
     * counter as part of the transition.
     */
    public void dispatchRepair(String reason, int maxRepairAttempts) {
        if (repairAttempts >= maxRepairAttempts) {
            throw new IllegalStateTransitionException(
                    "repair attempts exhausted: %d of %d used".formatted(repairAttempts, maxRepairAttempts));
        }
        doTransition(RemediationState.REPAIR_DISPATCHED, reason);
        repairAttempts++;
    }

    private void doTransition(RemediationState to, String reason) {
        if (!state.canTransitionTo(to)) {
            throw new IllegalStateTransitionException(
                    "illegal transition %s -> %s".formatted(state, to));
        }
        RemediationState from = state;
        state = to;
        updatedAt = Instant.now();
        if (to == RemediationState.ESCALATED) {
            escalationReason = reason;
        }
        transitions.add(new StateTransition(this, from, to, reason));
    }

    public boolean canDispatchRepair(int maxRepairAttempts) {
        return repairAttempts < maxRepairAttempts;
    }

    public void recordSession(String sessionId, String sessionUrl) {
        this.devinSessionId = sessionId;
        this.devinSessionUrl = sessionUrl;
    }

    public void recordPullRequest(Integer prNumber, String prUrl) {
        this.prNumber = prNumber;
        this.prUrl = prUrl;
    }

    public void recordUsage(BigDecimal acusConsumed, String structuredOutput) {
        if (acusConsumed != null) {
            this.acusConsumed = acusConsumed;
        }
        if (structuredOutput != null) {
            this.structuredOutput = structuredOutput;
        }
    }

    public void recordStatusCommentId(Long statusCommentId) {
        this.statusCommentId = statusCommentId;
    }

    public Long getId() {
        return id;
    }

    public int getIssueNumber() {
        return issueNumber;
    }

    public String getIssueTitle() {
        return issueTitle;
    }

    public FindingSource getSource() {
        return source;
    }

    public RemediationState getState() {
        return state;
    }

    public String getDevinSessionId() {
        return devinSessionId;
    }

    public String getDevinSessionUrl() {
        return devinSessionUrl;
    }

    public String getPrUrl() {
        return prUrl;
    }

    public Integer getPrNumber() {
        return prNumber;
    }

    public Long getStatusCommentId() {
        return statusCommentId;
    }

    public BigDecimal getAcusConsumed() {
        return acusConsumed;
    }

    public String getStructuredOutput() {
        return structuredOutput;
    }

    public int getRepairAttempts() {
        return repairAttempts;
    }

    public String getEscalationReason() {
        return escalationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<StateTransition> getTransitions() {
        return Collections.unmodifiableList(transitions);
    }
}
