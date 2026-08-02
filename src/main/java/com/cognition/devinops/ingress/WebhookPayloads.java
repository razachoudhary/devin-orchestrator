package com.cognition.devinops.ingress;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class WebhookPayloads {

    private WebhookPayloads() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IssuesEvent(String action, Label label, Issue issue) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Label(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Issue(int number, String title, String body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckRunEvent(String action, @JsonProperty("check_run") CheckRun checkRun) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckRun(long id, String name, String conclusion, Output output,
                           @JsonProperty("pull_requests") List<PullRequestRef> pullRequests) {

        public List<PullRequestRef> pullRequests() {
            return pullRequests == null ? List.of() : pullRequests;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output(String title, String summary, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequestRef(int number) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequestEvent(String action, @JsonProperty("pull_request") PullRequest pullRequest) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequest(int number, boolean merged) {
    }
}
