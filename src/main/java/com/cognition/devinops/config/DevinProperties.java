package com.cognition.devinops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devin")
public record DevinProperties(
        String baseUrl,
        String apiKey,
        String orgId,
        String remediationPlaybookId,
        String scoutPlaybookId,
        String forkKnowledgeId,
        boolean tagsEnabled,
        int maxAcuLimit
) {
}
