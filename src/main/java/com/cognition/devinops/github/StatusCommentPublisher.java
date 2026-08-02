package com.cognition.devinops.github;

import com.cognition.devinops.domain.Remediation;
import com.cognition.devinops.domain.RemediationState;
import com.cognition.devinops.domain.StateTransition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StatusCommentPublisher {

    private static final Logger log = LoggerFactory.getLogger(StatusCommentPublisher.class);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    private static final List<String> MANAGED_LABELS = List.of("devin:running", "devin:pr-open", "devin:escalated");

    private final GitHubClient gitHubClient;
    private final ObjectMapper objectMapper;

    public StatusCommentPublisher(GitHubClient gitHubClient, ObjectMapper objectMapper) {
        this.gitHubClient = gitHubClient;
        this.objectMapper = objectMapper;
    }

    public void sync(Remediation remediation) {
        try {
            if (remediation.getIssueNumber() <= 0) {
                return;
            }
            String body = render(remediation);
            if (remediation.getStatusCommentId() == null) {
                remediation.recordStatusCommentId(
                        gitHubClient.createComment(remediation.getIssueNumber(), body));
            } else {
                gitHubClient.updateComment(remediation.getStatusCommentId(), body);
            }
            syncLabels(remediation);
        } catch (RuntimeException e) {
            log.warn("status comment sync failed for remediation {} (issue #{})",
                    remediation.getId(), remediation.getIssueNumber(), e);
        }
    }

    private String render(Remediation remediation) {
        StringBuilder body = new StringBuilder("### 🤖 Devin Remediation Status\n\n");
        body.append("| Time | State | Detail |\n|---|---|---|\n");
        for (StateTransition transition : remediation.getTransitions()) {
            body.append("| ").append(TIME.format(transition.getOccurredAt()))
                    .append(" | `").append(transition.getToState()).append("`")
                    .append(" | ").append(detail(remediation, transition)).append(" |\n");
        }
        body.append("\n**Session:** `").append(orDash(remediation.getDevinSessionId())).append("`");
        if (remediation.getAcusConsumed() != null) {
            body.append(" · **ACUs:** ").append(remediation.getAcusConsumed());
        }
        body.append(" · **Elapsed:** ").append(elapsedMinutes(remediation)).append("m\n");
        appendStructuredOutput(body, remediation);
        return body.toString();
    }

    private String detail(Remediation remediation, StateTransition transition) {
        if (transition.getToState() == RemediationState.SESSION_RUNNING
                && remediation.getDevinSessionUrl() != null) {
            return "[session](%s)".formatted(remediation.getDevinSessionUrl());
        }
        if (transition.getToState() == RemediationState.PR_OPEN && remediation.getPrUrl() != null) {
            return "[#%d](%s)".formatted(remediation.getPrNumber(), remediation.getPrUrl());
        }
        if (transition.getToState() == RemediationState.MERGED) {
            return "✅";
        }
        return transition.getReason() == null ? "" : transition.getReason();
    }

    private void appendStructuredOutput(StringBuilder body, Remediation remediation) {
        if (remediation.getStructuredOutput() == null) {
            return;
        }
        try {
            JsonNode output = objectMapper.readTree(remediation.getStructuredOutput());
            if (output.hasNonNull("summary")) {
                body.append("\n**Summary:** ").append(output.get("summary").asText()).append('\n');
            }
            if (output.hasNonNull("confidence")) {
                body.append("**Confidence:** ").append(output.get("confidence").asText()).append('\n');
            }
        } catch (Exception e) {
            log.warn("could not parse structured output for remediation {}", remediation.getId(), e);
        }
    }

    private void syncLabels(Remediation remediation) {
        String desired = switch (remediation.getState()) {
            case RECEIVED, SESSION_RUNNING, CI_FAILED, REPAIR_DISPATCHED -> "devin:running";
            case PR_OPEN -> "devin:pr-open";
            case ESCALATED -> "devin:escalated";
            default -> null;
        };
        for (String label : MANAGED_LABELS) {
            if (label.equals(desired)) {
                gitHubClient.addLabel(remediation.getIssueNumber(), label);
            } else {
                gitHubClient.removeLabel(remediation.getIssueNumber(), label);
            }
        }
    }

    private long elapsedMinutes(Remediation remediation) {
        Instant end = remediation.getState().isTerminal() ? remediation.getUpdatedAt() : Instant.now();
        return Duration.between(remediation.getCreatedAt(), end).toMinutes();
    }

    private static String orDash(String value) {
        return value == null ? "-" : value;
    }
}
