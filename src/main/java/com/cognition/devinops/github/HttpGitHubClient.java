package com.cognition.devinops.github;

import com.cognition.devinops.config.GitHubProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Profile("!simulate")
class HttpGitHubClient implements GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(HttpGitHubClient.class);

    private final RestClient restClient;
    private final String owner;
    private final String repo;

    HttpGitHubClient(@Qualifier("gitHubRestClient") RestClient restClient, GitHubProperties gitHubProperties) {
        this.restClient = restClient;
        String[] parts = gitHubProperties.repo().split("/", 2);
        this.owner = parts[0];
        this.repo = parts.length > 1 ? parts[1] : "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CommentResponse(long id) {
    }

    @Override
    @Retryable(retryFor = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public long createComment(int issueNumber, String body) {
        CommentResponse response = restClient.post()
                .uri("/repos/{owner}/{repo}/issues/{issueNumber}/comments", owner, repo, issueNumber)
                .body(Map.of("body", body))
                .retrieve()
                .body(CommentResponse.class);
        return response.id();
    }

    @Override
    @Retryable(retryFor = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void updateComment(long commentId, String body) {
        restClient.patch()
                .uri("/repos/{owner}/{repo}/issues/comments/{commentId}", owner, repo, commentId)
                .body(Map.of("body", body))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    @Retryable(retryFor = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void addLabel(int issueNumber, String label) {
        restClient.post()
                .uri("/repos/{owner}/{repo}/issues/{issueNumber}/labels", owner, repo, issueNumber)
                .body(Map.of("labels", List.of(label)))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    @Retryable(retryFor = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void removeLabel(int issueNumber, String label) {
        try {
            restClient.delete()
                    .uri("/repos/{owner}/{repo}/issues/{issueNumber}/labels/{label}",
                            owner, repo, issueNumber, label)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
        }
    }

    @Override
    public String getCheckRunLogs(long checkRunId) {
        try {
            String logs = restClient.get()
                    .uri("/repos/{owner}/{repo}/actions/jobs/{jobId}/logs", owner, repo, checkRunId)
                    .retrieve()
                    .body(String.class);
            return logs == null ? "" : logs;
        } catch (RestClientException e) {
            log.warn("could not fetch logs for check run {}", checkRunId, e);
            return "";
        }
    }
}
