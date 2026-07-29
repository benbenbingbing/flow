package com.workflow.openapi.application;

import java.util.Map;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class IntegrationSecretHasher implements PasswordEncoder {

    private static final String ENCODING_ID = "argon2";

    private final PasswordEncoder passwordEncoder =
            new DelegatingPasswordEncoder(
                    ENCODING_ID,
                    Map.of(
                            ENCODING_ID,
                            Argon2PasswordEncoder
                                    .defaultsForSpringSecurity_v5_8()));

    public String hash(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("凭据不能为空");
        }
        return encode(secret);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("凭据不能为空");
        }
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(
            CharSequence rawPassword,
            String encodedPassword) {
        return rawPassword != null
                && encodedPassword != null
                && passwordEncoder.matches(
                        rawPassword,
                        encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return passwordEncoder.upgradeEncoding(encodedPassword);
    }
}
