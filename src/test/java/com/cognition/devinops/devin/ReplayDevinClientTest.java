package com.cognition.devinops.devin;

import static org.assertj.core.api.Assertions.assertThat;

import com.cognition.devinops.MutableClock;
import com.cognition.devinops.devin.dto.CreateSessionRequest;
import com.cognition.devinops.devin.dto.DevinPullRequest;
import com.cognition.devinops.devin.dto.DevinSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplayDevinClientTest {

    private MutableClock clock;
    private ReplayDevinClient client;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        client = new ReplayDevinClient(new ObjectMapper(), clock);
    }

    private DevinSession create(String scenarioToken) {
        return client.createSession(new CreateSessionRequest(
                "scenario " + scenarioToken + " for issue #47", null, null, 10, true, null, null));
    }

    @Test
    void happyPathStartsActiveAndEndsSuspendedWithPullRequest() {
        DevinSession start = create("happy-path");
        assertThat(start.sessionStatus().isActive()).isTrue();
        assertThat(start.structuredOutput()).isNull();
        clock.advanceSeconds(60);
        DevinSession end = client.getSession(start.sessionId());
        assertThat(end.status()).isEqualTo("suspended");
        assertThat(end.sessionStatus().isActive()).isFalse();
        assertThat(end.sessionStatus().isBlocked()).isFalse();
        assertThat(end.pullRequests()).extracting(DevinPullRequest::number).containsExactly(482);
        assertThat(end.structuredOutput().get("confidence").asText()).isEqualTo("high");
    }

    @Test
    void repairLoopPassesThroughResumingAndRecordsMessagesOnTheSameSession() {
        DevinSession session = create("repair-loop");
        clock.advanceSeconds(11);
        assertThat(client.getSession(session.sessionId()).status()).isEqualTo("suspended");
        client.sendMessage(session.sessionId(), "CI failed on the pull request you opened for issue #47");
        DevinSession resuming = client.getSession(session.sessionId());
        assertThat(resuming.status()).isEqualTo("resuming");
        assertThat(resuming.sessionStatus().isActive()).isTrue();
        clock.advanceSeconds(41);
        DevinSession end = client.getSession(session.sessionId());
        assertThat(end.status()).isEqualTo("suspended");
        assertThat(end.pullRequests()).extracting(DevinPullRequest::number).containsExactly(483);
        assertThat(client.messagesFor(session.sessionId()))
                .containsExactly("CI failed on the pull request you opened for issue #47");
    }

    @Test
    void blockedScenarioEndsWithUsageLimitReasonAndNoPullRequest() {
        DevinSession session = create("blocked");
        clock.advanceSeconds(60);
        DevinSession end = client.getSession(session.sessionId());
        assertThat(end.reason()).isEqualTo("usage_limit_exceeded");
        assertThat(end.sessionStatus().isBlocked()).isTrue();
        assertThat(end.pullRequests()).isEmpty();
    }
}
