package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfigReadRow;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CurrentVersionDocumentProjectionTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void releaseOverridesCurrentAndOnlyRemovesRootEnvelopeFields() throws Exception {
        var row = row("{\"schemaVersion\":99,\"enabled\":true,\"status\":\"PUBLISHED\",\"migrationState\":\"DONE\","
                + "\"activeReleaseId\":\"r1\",\"activeReleaseVersion\":4,\"nested\":{\"status\":\"keep\"}}");
        row.setSourceContractVersion(2);
        var result = json.readTree(CurrentVersionDocumentProjection.resolve(row).getConfigDocument());
        assertEquals(2, result.get("schemaVersion").intValue());
        assertTrue(result.get("enabled").booleanValue());
        for (String key : List.of("status", "migrationState", "activeReleaseId", "activeReleaseVersion")) assertFalse(result.has(key));
        assertEquals("keep", result.get("nested").get("status").textValue());
        row.setSourceContractVersion(null);
        assertEquals(1, json.readTree(CurrentVersionDocumentProjection.resolve(row).getConfigDocument()).get("schemaVersion").intValue());
    }

    @Test
    void invalidReleaseFallsBackVerbatimAndDoesNotAcceptTrailingTokens() {
        for (String invalid : List.of("", " ", "{", "{} garbage", "{} {}", "// comment\n{}", "NaN", "1e999")) {
            var row = row(invalid);
            assertEquals(row.getConfigDocument(), CurrentVersionDocumentProjection.resolve(row).getConfigDocument(), invalid);
        }
        var row = row("{}");
        row.setSourceReleaseId(null);
        assertEquals(row.getConfigDocument(), CurrentVersionDocumentProjection.resolve(row).getConfigDocument());
    }

    @Test
    void overdeepReleaseRemainsAnExplicitFailure() {
        assertThrows(IllegalArgumentException.class, () -> CurrentVersionDocumentProjection.resolve(
                row("[".repeat(101) + "0" + "]".repeat(101))));
    }

    @Test
    void validJsonNullArraysAndScalarsRetainTheirExistingMeaning() throws Exception {
        for (String value : List.of("null", "true", "123", "\"文字\"", "[]", "[{\"status\":\"keep\"}]")) {
            assertEquals(json.readTree(value), json.readTree(CurrentVersionDocumentProjection.resolve(row(value)).getConfigDocument()));
        }
    }

    private EntityVersionConfigReadRow row(String document) {
        var row = new EntityVersionConfigReadRow();
        row.setConfigDocument(" {\"source\": \"current\"} ");
        row.setSourceReleaseId("release");
        row.setSourceReleaseDocument(document);
        return row;
    }
}
