package com.cognition.devinops.devin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DevinPullRequest(
        @JsonProperty("url") String url,
        @JsonProperty("number") Integer number
) {
}
