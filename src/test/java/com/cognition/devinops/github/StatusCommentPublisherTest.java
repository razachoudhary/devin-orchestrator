package com.cognition.devinops.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cognition.devinops.domain.FindingSource;
import com.cognition.devinops.domain.Remediation;
import com.cognition.devinops.domain.RemediationState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StatusCommentPublisherTest {

    private final GitHubClient gitHubClient = mock(GitHubClient.class);
    private final StatusCommentPublisher publisher =
            new StatusCommentPublisher(gitHubClient, new ObjectMapper());

    @Test
    void createsOneCommentThenEditsItInPlace() {
        when(gitHubClient.createComment(eq(47), anyString())).thenReturn(555L);
        Remediation remediation = Remediation.receive(47, "flaky test", FindingSource.MANUAL);
        remediation.recordSession("sess-1", "https://app.devin.ai/sessions/sess-1");

        publisher.sync(remediation);
        assertThat(remediation.getStatusCommentId()).isEqualTo(555L);

        remediation.transitionTo(RemediationState.SESSION_RUNNING, "dispatched");
        publisher.sync(remediation);

        verify(gitHubClient, times(1)).createComment(anyInt(), anyString());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(gitHubClient).updateComment(eq(555L), body.capture());
        assertThat(body.getValue())
                .contains("Devin Remediation Status")
                .contains("SESSION_RUNNING")
                .contains("[session](https://app.devin.ai/sessions/sess-1)");
    }

    @Test
    void gitHubFailuresNeverPropagateAndScoutRowsAreSkipped() {
        when(gitHubClient.createComment(anyInt(), anyString())).thenThrow(new RuntimeException("github 502"));
        Remediation remediation = Remediation.receive(47, "flaky test", FindingSource.MANUAL);
        assertThatCode(() -> publisher.sync(remediation)).doesNotThrowAnyException();

        Remediation scout = Remediation.receive(0, "scout: maintenance debt scan", FindingSource.SCOUT);
        publisher.sync(scout);
        verify(gitHubClient, times(1)).createComment(anyInt(), anyString());
    }
}
