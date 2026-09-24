package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlaUserReferenceRewriterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void nestedExportImportRoundTripPreservesOtherReferencesAndDocumentShape() throws Exception {
        var original = mapper.readTree("""
                {"rules":[{"userId":"alice","userIds":["alice","bob"],"groupId":"finance"}],
                 "nested":{"rules":[{"userId":"bob"}]},"description":"alice","enabled":true}
                """);
        var document = original.deepCopy();
        SlaUserReferenceRewriter.rewrite(document, mapper, user -> "portable:" + user);
        assertEquals("portable:bob", document.at("/nested/rules/0/userId").asText());
        assertEquals("portable:alice", document.at("/rules/0/userIds/0").asText());
        assertEquals("finance", document.at("/rules/0/groupId").asText());
        assertEquals("alice", document.get("description").asText());
        SlaUserReferenceRewriter.rewrite(document, mapper, user -> user.substring("portable:".length()));
        assertEquals(original, document);
    }

    @Test
    void converterValidationFailureIsPropagatedToAbortImport() throws Exception {
        var document = mapper.readTree("{\"userId\":\"missing\"}");
        var failure = new IllegalArgumentException("目标用户不存在");
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> SlaUserReferenceRewriter.rewrite(document, mapper, user -> { throw failure; })));
        assertEquals("missing", document.get("userId").asText());
    }
}
