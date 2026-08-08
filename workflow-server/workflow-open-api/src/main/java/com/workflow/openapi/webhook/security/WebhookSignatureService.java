package com.workflow.openapi.webhook.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.time.Clock;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class WebhookSignatureService {

    private static final int SIGNATURE_LENGTH = 47;

    public boolean verify(
            String eventId,
            long unixTimestamp,
            byte[] body,
            String signingSecret,
            String signature,
            Clock clock,
            long allowedSkewSeconds) {
        if (eventId == null || eventId.isBlank()
                || body == null
                || signingSecret == null || signingSecret.isBlank()
                || clock == null || allowedSkewSeconds < 0
                || signature == null
                || signature.length() != SIGNATURE_LENGTH
                || !signature.startsWith("v1=")
                || !withinTimeWindow(
                        clock.instant().getEpochSecond(),
                        unixTimestamp,
                        allowedSkewSeconds)) {
            return false;
        }
        String expected = sign(
                eventId, unixTimestamp, body, signingSecret);
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private boolean withinTimeWindow(
            long currentTimestamp,
            long receivedTimestamp,
            long allowedSkewSeconds) {
        try {
            long difference = Math.subtractExact(
                    currentTimestamp, receivedTimestamp);
            return Math.absExact(difference) <= allowedSkewSeconds;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    public String sign(
            String eventId,
            long unixTimestamp,
            byte[] body,
            String signingSecret) {
        if (eventId == null
                || eventId.isBlank()
                || body == null
                || signingSecret == null
                || signingSecret.isBlank()) {
            throw new IllegalArgumentException(
                    "Webhook 签名参数不完整");
        }
        byte[] prefix = (eventId
                + "."
                + unixTimestamp
                + ".").getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    signingSecret.getBytes(
                            StandardCharsets.UTF_8),
                    "HmacSHA256"));
            mac.update(prefix);
            mac.update(body);
            return "v1=" + Base64.getEncoder()
                    .encodeToString(mac.doFinal());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Webhook 签名失败",
                    exception);
        }
    }
}
