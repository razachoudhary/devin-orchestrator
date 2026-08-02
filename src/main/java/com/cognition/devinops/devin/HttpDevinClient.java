package com.cognition.devinops.devin;

import com.cognition.devinops.config.DevinProperties;
import com.cognition.devinops.devin.dto.CreateSessionRequest;
import com.cognition.devinops.devin.dto.DevinSelf;
import com.cognition.devinops.devin.dto.DevinSession;
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

@Component
@Profile("!simulate")
class HttpDevinClient implements DevinClient {

    private static final Logger log = LoggerFactory.getLogger(HttpDevinClient.class);

    private final RestClient restClient;
    private final DevinProperties devinProperties;

    HttpDevinClient(@Qualifier("devinRestClient") RestClient restClient, DevinProperties devinProperties) {
        this.restClient = restClient;
        this.devinProperties = devinProperties;
    }

    @Override
    @Retryable(retryFor = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public DevinSession createSession(CreateSessionRequest request) {
        try {
            return postSession(request);
        } catch (HttpClientErrorException.BadRequest e) {
            if (request.tags() == null) {
                throw e;
            }
            log.warn("session creation rejected with 400, retrying once without tags: {}",
                    e.getResponseBodyAsString());
            return postSession(request.withoutTags());
        }
    }

    private DevinSession postSession(CreateSessionRequest request) {
        return restClient.post()
                .uri("/v3/organizations/{orgId}/sessions", devinProperties.orgId())
                .body(request)
                .retrieve()
                .body(DevinSession.class);
    }

    @Override
    @Retryable(retryFor = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public DevinSession getSession(String sessionId) {
        return restClient.get()
                .uri("/v3/organizations/{orgId}/sessions/{sessionId}", devinProperties.orgId(), sessionId)
                .retrieve()
                .body(DevinSession.class);
    }

    @Override
    @Retryable(retryFor = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void sendMessage(String sessionId, String message) {
        restClient.post()
                .uri("/v3/organizations/{orgId}/sessions/{sessionId}/messages",
                        devinProperties.orgId(), sessionId)
                .body(Map.of("message", message))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    @Retryable(retryFor = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public DevinSelf whoAmI() {
        return restClient.get()
                .uri("/v3/self")
                .retrieve()
                .body(DevinSelf.class);
    }
}
