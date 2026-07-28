package com.workflow.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionCorsConfigurationGuardTest {

    @Test
    void productionRejectsWildcardAndEmptyOrigins() {
        CorsProperties wildcard = new CorsProperties();
        wildcard.setAllowedOrigins(List.of("*"));
        CorsProperties empty = new CorsProperties();

        assertThrows(
                IllegalStateException.class,
                () -> new ProductionCorsConfigurationGuard(
                        wildcard));
        assertThrows(
                IllegalStateException.class,
                () -> new ProductionCorsConfigurationGuard(
                        empty));
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
}
