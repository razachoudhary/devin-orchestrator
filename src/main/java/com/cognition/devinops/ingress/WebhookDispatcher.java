package com.cognition.devinops.ingress;

import com.cognition.devinops.domain.FindingSource;
import com.cognition.devinops.orchestration.RemediationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final RemediationService remediationService;

    public WebhookDispatcher(RemediationService remediationService) {
        this.remediationService = remediationService;
    }

    @Async("webhookExecutor")
    public void issueLabeled(int issueNumber, String title, String body) {
        try {
            remediationService.onIssueLabeled(issueNumber, title, body, FindingSource.MANUAL);
        } catch (DataIntegrityViolationException e) {
            remediationService.recordGatedRejection(issueNumber, title, FindingSource.MANUAL,
                    "duplicate: concurrent remediation for issue #%d".formatted(issueNumber));
        } catch (RuntimeException e) {
            log.warn("failed to handle labeled issue #{}", issueNumber, e);
        }
    }

    @Async("webhookExecutor")
    public void checkRunCompleted(int prNumber, String checkName, String conclusion, String logExcerpt) {
        try {
            remediationService.onCheckRunCompleted(prNumber, checkName, conclusion, logExcerpt);
        } catch (RuntimeException e) {
            log.warn("failed to handle check run for pr #{}", prNumber, e);
        }
    }

    @Async("webhookExecutor")
    public void pullRequestMerged(int prNumber) {
        try {
            remediationService.onPullRequestMerged(prNumber);
        } catch (RuntimeException e) {
            log.warn("failed to handle merge of pr #{}", prNumber, e);
        }
    }
}
