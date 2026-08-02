package com.cognition.devinops.devin.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DevinPullRequest(
        @JsonProperty("url") @JsonAlias("pr_url") String url,
        @JsonProperty("number") Integer number
) {

    @Override
    public Integer number() {
        if (number != null) {
            return number;
        }
        if (url == null) {
            return null;
        }
        String tail = url.substring(url.lastIndexOf('/') + 1);
        try {
            return Integer.parseInt(tail);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
