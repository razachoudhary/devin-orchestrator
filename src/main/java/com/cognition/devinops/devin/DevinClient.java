package com.cognition.devinops.devin;

import com.cognition.devinops.devin.dto.CreateSessionRequest;
import com.cognition.devinops.devin.dto.DevinSelf;
import com.cognition.devinops.devin.dto.DevinSession;

public interface DevinClient {

    DevinSession createSession(CreateSessionRequest request);

    DevinSession getSession(String sessionId);

    void sendMessage(String sessionId, String message);

    DevinSelf whoAmI();
}
