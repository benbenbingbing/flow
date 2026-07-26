package com.workflow.system.audit.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditPayloadSanitizerTest {

    @Test
    void masksSecretsAndPersonalInformation() {
        AuditPayloadSanitizer sanitizer =
                new AuditPayloadSanitizer(new ObjectMapper(), 32768);

        String json = sanitizer.sanitize(Map.of(
                "password", "secret-password",
                "accessToken", "token-value",
                "email", "alice@example.com",
                "phone", "13800138000")).json();

        assertFalse(json.contains("secret-password"));
        assertFalse(json.contains("token-value"));
        assertFalse(json.contains("alice@example.com"));
        assertFalse(json.contains("13800138000"));
        assertTrue(json.contains("******"));
    }

    @Test
    void truncatesOversizedPayload() {
        AuditPayloadSanitizer sanitizer =
                new AuditPayloadSanitizer(new ObjectMapper(), 1024);

        AuditPayloadSanitizer.SanitizedPayload payload =
                sanitizer.sanitize(Map.of("description", "x".repeat(5000)));

        assertTrue(payload.truncated());
        assertTrue(payload.json().length() <= 1024);
    }

    @Test
    void masksSecretsAndPersonalInformationInText() {
        AuditPayloadSanitizer sanitizer =
                new AuditPayloadSanitizer(new ObjectMapper(), 32768);

        String text = sanitizer.sanitizeText(
                "password=plain Bearer abc.def token: xyz alice@example.com 13800138000 6222021234567890123",
                1000);

        assertFalse(text.contains("plain"));
        assertFalse(text.contains("abc.def"));
        assertFalse(text.contains("xyz"));
        assertFalse(text.contains("alice@example.com"));
        assertFalse(text.contains("13800138000"));
        assertFalse(text.contains("6222021234567890123"));
        assertTrue(text.contains("******"));
    }
}
