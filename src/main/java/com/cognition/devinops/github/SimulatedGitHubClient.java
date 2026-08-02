package com.cognition.devinops.github;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("simulate")
class SimulatedGitHubClient implements GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(SimulatedGitHubClient.class);

    private final AtomicLong commentIds = new AtomicLong(1000);

    @Override
    public long createComment(int issueNumber, String body) {
        long id = commentIds.incrementAndGet();
        log.info("[simulated github] create comment {} on issue #{}:\n{}", id, issueNumber, body);
        return id;
    }

    @Override
    public void updateComment(long commentId, String body) {
        log.info("[simulated github] update comment {}:\n{}", commentId, body);
    }

    @Override
    public void addLabel(int issueNumber, String label) {
        log.info("[simulated github] add label {} to issue #{}", label, issueNumber);
    }

    @Override
    public void removeLabel(int issueNumber, String label) {
        log.info("[simulated github] remove label {} from issue #{}", label, issueNumber);
    }

    @Override
    public String getCheckRunLogs(long checkRunId) {
        return "";
    }
}
