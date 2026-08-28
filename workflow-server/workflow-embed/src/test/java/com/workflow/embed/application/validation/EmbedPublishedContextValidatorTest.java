package com.workflow.embed.application.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmbedPublishedContextValidatorTest {

    private final EmbedPublishedContextValidator validator =
            new EmbedPublishedContextValidator(new ObjectMapper());

    @Test
    void validatesRequiredPublishedProperty() {
        String schema = """
                {"type":"object","additionalProperties":false,
                 "required":["supplierId"],
                 "properties":{"supplierId":{"type":"string","minLength":1,"maxLength":8}}}
                """;

        assertDoesNotThrow(() -> validator.validate(Map.of("supplierId", "S-1"), schema));
        EmbedException missing = assertThrows(
                EmbedException.class,
                () -> validator.validate(Map.of(), schema));
        assertEquals(EmbedErrorCode.EMBED_CONTEXT_INVALID, missing.getErrorCode());
        assertThrows(EmbedException.class,
                () -> validator.validate(Map.of("supplierId", "S-1", "admin", true), schema));
    }

    @Test
    void rejectsRemoteOrLocalReferencesInsteadOfResolvingThem() {
        EmbedException error = assertThrows(
                EmbedException.class,
                () -> validator.validate(Map.of(), "{\"$ref\":\"https://evil.test/schema\"}"));
        assertEquals(EmbedErrorCode.EMBED_CONTEXT_INVALID, error.getErrorCode());
    }

    @Test
    void rejectsPublishedJavaRegexInsteadOfExecutingBacktrackingPatterns() {
        String schema = """
                {"type":"object","properties":{
                  "value":{"type":"string","pattern":"(a+)+$"}}}
                """;

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> validator.validate(Map.of("value", "aaaaaaaaaaaaaaaa!"), schema));

        assertEquals(EmbedErrorCode.EMBED_CONTEXT_INVALID, error.getErrorCode());
    }
}
