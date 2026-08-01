package com.cognition.devinops.ingress;

import static org.assertj.core.api.Assertions.assertThat;

import com.cognition.devinops.config.GitHubProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class HmacVerifierTest {

    private static final String SECRET = "test-webhook-secret";
    private static final String BODY = "{\"action\":\"labeled\",\"issue\":{\"number\":47}}";

    private final HmacVerifier verifier = new HmacVerifier(
            new GitHubProperties(null, SECRET, "razachoudhary/devin-superset", "https://api.github.com"));

    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void acceptsAValidSignatureOverTheRawBody() throws Exception {
        assertThat(verifier.verify(BODY, sign(BODY, SECRET))).isTrue();
    }

    @Test
    void rejectsWrongSecretTamperedBodyAndMalformedHeaders() throws Exception {
        assertThat(verifier.verify(BODY, sign(BODY, "other-secret"))).isFalse();
        assertThat(verifier.verify(BODY + " ", sign(BODY, SECRET))).isFalse();
        assertThat(verifier.verify(BODY, null)).isFalse();
        assertThat(verifier.verify(BODY, "sha1=abcdef")).isFalse();
        assertThat(verifier.verify(BODY, "sha256=nothex")).isFalse();
    }
}
