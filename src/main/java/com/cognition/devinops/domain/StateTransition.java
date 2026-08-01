package com.cognition.devinops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "state_transitions")
public class StateTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "remediation_id")
    private Remediation remediation;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state")
    private RemediationState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false)
    private RemediationState toState;

    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected StateTransition() {
        // JPA
    }

    StateTransition(Remediation remediation, RemediationState fromState, RemediationState toState, String reason) {
        this.remediation = remediation;
        this.fromState = fromState;
        this.toState = toState;
        this.reason = reason;
        this.occurredAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Remediation getRemediation() {
        return remediation;
    }

    public RemediationState getFromState() {
        return fromState;
    }

    public RemediationState getToState() {
        return toState;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
