package com.workflow.openapi.webhook.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class WebhookSignatureServiceTest {

    private final WebhookSignatureService service =
            new WebhookSignatureService();

    @Test
    void signsTheDocumentedFixedVector() {
        String signature = service.sign(
                "event-123",
                1_785_316_500L,
                "{\"hello\":\"world\"}".getBytes(
                        StandardCharsets.UTF_8),
                "test-secret");

        assertEquals(
                "v1=eJWXcDGa0uFAUQlpLxdiERYfDSpocCAzQi8t0IoKtO8=",
                signature);
    }

    @Test
    void rejectsIncompleteInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.sign(
                        "",
                        1,
                        new byte[0],
                        "secret"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.sign(
                        "event",
                        1,
                        null,
                        "secret"));
    }

    @Test
    void verifiesSignatureAndRejectsReplayOutsideTimeWindow() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        String signature = service.sign(
                "event-123", 1_785_316_500L, body, "test-secret");
        Clock clock = Clock.fixed(
                Instant.ofEpochSecond(1_785_316_501L), ZoneOffset.UTC);
        assertEquals(true, service.verify(
                "event-123", 1_785_316_500L, body, "test-secret",
                signature, clock, 30));
        assertEquals(false, service.verify(
                "event-123", 1_785_316_500L, body, "test-secret",
                signature, Clock.fixed(
                        Instant.ofEpochSecond(1_785_316_600L), ZoneOffset.UTC),
                30));
    }
}
