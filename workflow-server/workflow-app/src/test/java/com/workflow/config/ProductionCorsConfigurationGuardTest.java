package com.workflow.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionCorsConfigurationGuardTest {

    @Test
    void defaultHeadersAllowFrontendWriteHeaders() {
        List<String> headers = new CorsProperties().getAllowedHeaders();

        assertTrue(headers.contains("X-Business-Trace-Key"));
        assertTrue(headers.contains("Idempotency-Key"));
    }

    @Test
    void productionRejectsWildcardOrigins() {
        CorsProperties wildcard = new CorsProperties();
        wildcard.setAllowedOrigins(List.of("*"));

        assertThrows(
                IllegalStateException.class,
                () -> new ProductionCorsConfigurationGuard(
                        wildcard));
    }

    @Test
    void productionAcceptsExplicitOrigins() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(
                List.of("https://flow.example.com"));

        assertDoesNotThrow(
                () -> new ProductionCorsConfigurationGuard(
                        properties));
    }

    @Test
    void productionAcceptsDenyAllOrigins() {
        assertDoesNotThrow(
                () -> new ProductionCorsConfigurationGuard(
                        new CorsProperties()));
    }
}
