package com.cognition.devinops.devin;

import com.cognition.devinops.devin.dto.CreateSessionRequest;
import com.cognition.devinops.devin.dto.DevinSelf;
import com.cognition.devinops.devin.dto.DevinSession;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!simulate")
class UnconfiguredDevinClient implements DevinClient {

    @Override
    public DevinSession createSession(CreateSessionRequest request) {
        throw notConfigured();
    }

    @Override
    public DevinSession getSession(String sessionId) {
        throw notConfigured();
    }

    @Override
    public void sendMessage(String sessionId, String message) {
        throw notConfigured();
    }

    @Override
    public DevinSelf whoAmI() {
        throw notConfigured();
    }

    private static IllegalStateException notConfigured() {
        return new IllegalStateException(
                "Devin HTTP client is not implemented yet; run with the simulate profile");
    }
}
