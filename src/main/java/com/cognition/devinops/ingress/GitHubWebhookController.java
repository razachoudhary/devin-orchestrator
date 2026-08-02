package com.cognition.devinops.ingress;

import com.cognition.devinops.ingress.WebhookPayloads.CheckRunEvent;
import com.cognition.devinops.ingress.WebhookPayloads.IssuesEvent;
import com.cognition.devinops.ingress.WebhookPayloads.PullRequestEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private static final String TRIGGER_LABEL = "devin-fix";

    private final HmacVerifier hmacVerifier;
    private final WebhookDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public GitHubWebhookController(HmacVerifier hmacVerifier, WebhookDispatcher dispatcher,
                                   ObjectMapper objectMapper) {
        this.hmacVerifier = hmacVerifier;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhooks/github")
    public ResponseEntity<Void> receive(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event) {
        if (!hmacVerifier.verify(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            route(event, rawBody);
        } catch (JsonProcessingException e) {
            log.warn("unparseable {} webhook payload", event, e);
        }
        return ResponseEntity.accepted().build();
    }

    private void route(String event, String rawBody) throws JsonProcessingException {
        switch (event == null ? "" : event) {
            case "issues" -> {
                IssuesEvent payload = objectMapper.readValue(rawBody, IssuesEvent.class);
                if ("labeled".equals(payload.action())
                        && payload.label() != null
                        && TRIGGER_LABEL.equals(payload.label().name())
                        && payload.issue() != null) {
                    dispatcher.issueLabeled(payload.issue().number(), payload.issue().title(),
                            payload.issue().body());
                }
            }
            case "check_run" -> {
                CheckRunEvent payload = objectMapper.readValue(rawBody, CheckRunEvent.class);
                if ("completed".equals(payload.action())
                        && payload.checkRun() != null
                        && !payload.checkRun().pullRequests().isEmpty()) {
                    dispatcher.checkRunCompleted(
                            payload.checkRun().pullRequests().get(0).number(),
                            payload.checkRun().id(),
                            payload.checkRun().name(),
                            payload.checkRun().conclusion(),
                            excerpt(payload.checkRun()));
                }
            }
            case "pull_request" -> {
                PullRequestEvent payload = objectMapper.readValue(rawBody, PullRequestEvent.class);
                if ("closed".equals(payload.action())
                        && payload.pullRequest() != null
                        && payload.pullRequest().merged()) {
                    dispatcher.pullRequestMerged(payload.pullRequest().number());
                }
            }
            default -> {
            }
        }
    }

    private static String excerpt(WebhookPayloads.CheckRun checkRun) {
        if (checkRun.output() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        if (checkRun.output().summary() != null) {
            text.append(checkRun.output().summary());
        }
        if (checkRun.output().text() != null) {
            text.append('\n').append(checkRun.output().text());
        }
        return text.toString();
    }
}
