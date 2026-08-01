package com.cognition.devinops.ingress;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cognition.devinops.config.GitHubProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = GitHubWebhookController.class,
        properties = "github.webhook-secret=test-webhook-secret")
@Import(HmacVerifier.class)
@EnableConfigurationProperties(GitHubProperties.class)
class GitHubWebhookControllerTest {

    private static final String SECRET = "test-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookDispatcher dispatcher;

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsInvalidSignatureWith401() throws Exception {
        mockMvc.perform(post("/webhooks/github")
                        .header("X-GitHub-Event", "issues")
                        .header("X-Hub-Signature-256", "sha256=deadbeef")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dispatcher);
    }

    @Test
    void routesDevinFixLabelToDispatcherAndIgnoresOtherLabels() throws Exception {
        String labeled = """
                {"action":"labeled","label":{"name":"devin-fix"},
                 "issue":{"number":47,"title":"flaky test","body":"details"}}
                """;
        mockMvc.perform(post("/webhooks/github")
                        .header("X-GitHub-Event", "issues")
                        .header("X-Hub-Signature-256", sign(labeled))
                        .content(labeled))
                .andExpect(status().isAccepted());
        verify(dispatcher).issueLabeled(47, "flaky test", "details");

        String otherLabel = """
                {"action":"labeled","label":{"name":"bug"},
                 "issue":{"number":48,"title":"other","body":""}}
                """;
        mockMvc.perform(post("/webhooks/github")
                        .header("X-GitHub-Event", "issues")
                        .header("X-Hub-Signature-256", sign(otherLabel))
                        .content(otherLabel))
                .andExpect(status().isAccepted());
        verify(dispatcher, org.mockito.Mockito.never()).issueLabeled(anyInt(), eq("other"), eq(""));
    }
}
