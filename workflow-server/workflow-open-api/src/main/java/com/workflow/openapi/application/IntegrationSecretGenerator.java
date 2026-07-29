package com.workflow.openapi.application;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class IntegrationSecretGenerator {

    private static final int CLIENT_ID_RANDOM_BYTES = 18;
    private static final int CLIENT_SECRET_RANDOM_BYTES = 32;

    private final SecureRandom secureRandom;

    public IntegrationSecretGenerator() {
        this(new SecureRandom());
    }

    IntegrationSecretGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String newClientId() {
        return "flow_" + randomUrlSafe(CLIENT_ID_RANDOM_BYTES);
    }

    public String newClientSecret() {
        return randomUrlSafe(CLIENT_SECRET_RANDOM_BYTES);
    }

    private String randomUrlSafe(int byteCount) {
        byte[] value = new byte[byteCount];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
