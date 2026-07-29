package com.workflow.openapi.webhook.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class WebhookSignatureService {

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
