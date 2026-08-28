package com.workflow.embed.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EmbedPropertiesValidationTest {

    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsHttpsOriginAndExplicitLoopbackHttpOrigins() {
        for (String value : new String[]{
                "https://embed.flow.example",
                "https://embed.flow.example:8443",
                "http://localhost:8080",
                "http://127.0.0.1:8080",
                "http://[::1]:8080"}) {
            EmbedProperties properties = validProperties();
            properties.setPublicBaseUrl(value);
            assertTrue(validator.validate(properties).isEmpty(), value);
        }
    }

    @Test
    void rejectsNonLoopbackHttpAndAnythingBeyondAnOrigin() {
        for (String value : new String[]{
                "http://embed.flow.example",
                "https://user@embed.flow.example",
                "https://embed.flow.example/",
                "https://embed.flow.example/path",
                "https://embed.flow.example?query=1",
                "https://embed.flow.example#fragment",
                "https://embed.flow.example:",
                "https://embed.flow.example:99999",
                "//embed.flow.example",
                "javascript:alert(1)"}) {
            EmbedProperties properties = validProperties();
            properties.setPublicBaseUrl(value);
            assertFalse(validator.validate(properties).isEmpty(), value);
        }
    }

    @Test
    void assetPathsCannotEscapeOrAliasTheDedicatedDirectory() {
        for (String value : new String[]{
                "/embed-main.js",
                "/embed-assets/../admin.js",
                "/embed-assets/./embed-main.js",
                "/embed-assets//embed-main.js",
                "/embed-assets\\embed-main.js",
                "/embed-assets/"}) {
            EmbedProperties properties = validProperties();
            properties.setEntryAssetPath(value);
            assertFalse(validator.validate(properties).isEmpty(), value);
        }

        EmbedProperties nested = validProperties();
        nested.setEntryAssetPath("/embed-assets/v1/embed-main.abc123.js");
        nested.setEntryStylePath("/embed-assets/v1/embed-main.abc123.css");
        assertTrue(validator.validate(nested).isEmpty());
    }

    @Test
    void terminalContextMustBeErasedBeforeWholeSessionRetentionEnds() {
        EmbedProperties properties = validProperties();
        properties.setTerminalContextRetentionSeconds(7200);
        properties.setTerminalSessionRetentionSeconds(3600);

        assertFalse(validator.validate(properties).isEmpty());

        properties.setTerminalContextRetentionSeconds(3600);
        assertTrue(validator.validate(properties).isEmpty());
    }

    @Test
    void maintenanceCannotBeConfiguredAsATightLoopOrUnboundedBatch() {
        EmbedProperties properties = validProperties();
        properties.setMaintenanceScanMs(999);
        properties.setMaintenanceBatchSize(1001);

        assertFalse(validator.validate(properties).isEmpty());

        properties.setMaintenanceScanMs(1000);
        properties.setMaintenanceBatchSize(1000);
        assertTrue(validator.validate(properties).isEmpty());
    }

    private static EmbedProperties validProperties() {
        return new EmbedProperties();
    }
}
