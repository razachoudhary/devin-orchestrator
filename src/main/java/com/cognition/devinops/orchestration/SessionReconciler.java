package com.cognition.devinops.orchestration;

import com.cognition.devinops.config.AppProperties;
import com.cognition.devinops.devin.DevinClient;
import com.cognition.devinops.devin.dto.DevinPullRequest;
import com.cognition.devinops.devin.dto.DevinSession;
import com.cognition.devinops.devin.dto.DevinSessionStatus;
import com.cognition.devinops.domain.FindingSource;
import com.cognition.devinops.domain.Remediation;
import com.cognition.devinops.domain.RemediationState;
import com.cognition.devinops.github.StatusCommentPublisher;
import com.cognition.devinops.repo.RemediationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SessionReconciler {

    private static final Logger log = LoggerFactory.getLogger(SessionReconciler.class);

    private final RemediationRepository remediations;
    private final DevinClient devinClient;
    private final StatusCommentPublisher statusCommentPublisher;
    private final AppProperties appProperties;

    public SessionReconciler(RemediationRepository remediations, DevinClient devinClient,
                             StatusCommentPublisher statusCommentPublisher, AppProperties appProperties) {
        this.remediations = remediations;
        this.devinClient = devinClient;
        this.statusCommentPublisher = statusCommentPublisher;
        this.appProperties = appProperties;
    }

    @Scheduled(fixedDelayString = "${app.reconcile-interval-ms:10000}")
    @Transactional
    public void reconcile() {
        for (Remediation remediation : remediations.findByStateIn(
                EnumSet.of(RemediationState.SESSION_RUNNING, RemediationState.REPAIR_DISPATCHED))) {
            try {
                reconcileOne(remediation);
            } catch (RuntimeException e) {
                log.warn("reconciliation failed for remediation {}", remediation.getId(), e);
            }
        }
    }

    private void reconcileOne(Remediation remediation) {
        DevinSession session = devinClient.getSession(remediation.getDevinSessionId());
        remediation.recordUsage(session.acusConsumed(),
                session.structuredOutput() == null ? null : session.structuredOutput().toString());
        DevinSessionStatus status = session.sessionStatus();
        if (status.isBlocked()) {
            transition(remediation, RemediationState.ESCALATED, status.reason());
            return;
        }
        if (status.isActive()) {
            remediations.save(remediation);
            escalateIfTimedOut(remediation);
            return;
        }
        if (status.isSettled()) {
            if (session.pullRequests().isEmpty()) {
                transition(remediation, RemediationState.FAILED,
                        remediation.getSource() == FindingSource.SCOUT
                                ? "scout run finished"
                                : "session ended without a pull request");
            } else {
                DevinPullRequest pullRequest = session.pullRequests().get(0);
                remediation.recordPullRequest(pullRequest.number(), pullRequest.url());
                transition(remediation, RemediationState.PR_OPEN,
                        "pull request #%d opened".formatted(pullRequest.number()));
            }
            return;
        }
        transition(remediation, RemediationState.FAILED, "session errored");
    }

    private void escalateIfTimedOut(Remediation remediation) {
        long elapsedMinutes = Duration.between(remediation.getCreatedAt(), Instant.now()).toMinutes();
        if (elapsedMinutes >= appProperties.sessionTimeoutMinutes()) {
            transition(remediation, RemediationState.ESCALATED, "session timeout");
        }
    }

    private void transition(Remediation remediation, RemediationState to, String reason) {
        remediation.transitionTo(to, reason);
        remediations.save(remediation);
        statusCommentPublisher.sync(remediation);
    }
}
