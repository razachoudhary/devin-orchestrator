package com.cognition.devinops.devin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DevinSession(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("url") String url,
        @JsonProperty("status") String status,
        @JsonProperty("reason") String reason,
        @JsonProperty("pull_requests") List<DevinPullRequest> pullRequests,
        @JsonProperty("acus_consumed") BigDecimal acusConsumed,
        @JsonProperty("structured_output") JsonNode structuredOutput
) {

    @Override
    public List<DevinPullRequest> pullRequests() {
        return pullRequests == null ? List.of() : pullRequests;
    }

    @Override
    public JsonNode structuredOutput() {
        return structuredOutput == null || structuredOutput.isNull() ? null : structuredOutput;
    }

    public DevinSessionStatus sessionStatus() {
        return DevinSessionStatus.of(status, reason);
    }
}
