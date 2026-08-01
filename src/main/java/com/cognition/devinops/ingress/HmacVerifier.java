package com.cognition.devinops.ingress;

import com.cognition.devinops.config.GitHubProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class HmacVerifier {

    private static final String SIGNATURE_PREFIX = "sha256=";

    private final GitHubProperties gitHubProperties;

    public HmacVerifier(GitHubProperties gitHubProperties) {
        this.gitHubProperties = gitHubProperties;
    }

    public boolean verify(String rawBody, String signatureHeader) {
        String secret = gitHubProperties.webhookSecret();
        if (secret == null || secret.isBlank()
                || signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        try {
            byte[] claimed = HexFormat.of().parseHex(signatureHeader.substring(SIGNATURE_PREFIX.length()));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(claimed, computed);
        } catch (Exception e) {
            return false;
        }
    }
}
