package com.cognition.devinops.devin;

import com.cognition.devinops.config.DevinProperties;
import com.cognition.devinops.devin.dto.DevinSelf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

@Component
@Profile("!simulate")
class StartupIdentityCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupIdentityCheck.class);

    private final DevinClient devinClient;
    private final DevinProperties devinProperties;

    StartupIdentityCheck(DevinClient devinClient, DevinProperties devinProperties) {
        this.devinClient = devinClient;
        this.devinProperties = devinProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (devinProperties.apiKey() == null || devinProperties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "DEVIN_API_KEY is not set. Provide a service-user key (cog_ prefix) "
                            + "or run with SPRING_PROFILES_ACTIVE=simulate.");
        }
        try {
            DevinSelf self = devinClient.whoAmI();
            log.info("authenticated against Devin API as {} ({})", self.name(), self.id());
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "Devin API rejected the configured credentials (HTTP %d). Check DEVIN_API_KEY and DEVIN_ORG_ID."
                            .formatted(e.getStatusCode().value()), e);
        }
    }
}
