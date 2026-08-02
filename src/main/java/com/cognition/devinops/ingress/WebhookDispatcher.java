package com.cognition.devinops.ingress;

import com.cognition.devinops.domain.FindingSource;
import com.cognition.devinops.github.GitHubClient;
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
    private final GitHubClient gitHubClient;

    public WebhookDispatcher(RemediationService remediationService, GitHubClient gitHubClient) {
        this.remediationService = remediationService;
        this.gitHubClient = gitHubClient;
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
    public void checkRunCompleted(int prNumber, long checkRunId, String checkName, String conclusion,
                                  String logExcerpt) {
        try {
            String excerpt = logExcerpt;
            if (excerpt == null || excerpt.isBlank()) {
                excerpt = gitHubClient.getCheckRunLogs(checkRunId);
            }
            remediationService.onCheckRunCompleted(prNumber, checkName, conclusion, excerpt);
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
