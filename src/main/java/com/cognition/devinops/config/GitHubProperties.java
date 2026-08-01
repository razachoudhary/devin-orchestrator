package com.cognition.devinops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github")
public record GitHubProperties(
        String token,
        String webhookSecret,
        String repo,
        String baseUrl
) {
}
