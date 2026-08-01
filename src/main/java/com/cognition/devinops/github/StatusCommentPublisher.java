package com.cognition.devinops.github;

import com.cognition.devinops.domain.Remediation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StatusCommentPublisher {

    private static final Logger log = LoggerFactory.getLogger(StatusCommentPublisher.class);

    public void sync(Remediation remediation) {
        try {
            log.info("remediation {} issue #{} -> {}",
                    remediation.getId(), remediation.getIssueNumber(), remediation.getState());
        } catch (RuntimeException e) {
            log.warn("status comment sync failed for remediation {}", remediation.getId(), e);
        }
    }
}
