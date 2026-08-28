package com.workflow.embed.management.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.FieldRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.FormTargetRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ListTargetRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublishedUiResourceSnapshotParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PublishedUiResourceSnapshotParser parser =
            new PublishedUiResourceSnapshotParser(objectMapper);

    @Test
    void derivesPoliciesOnlyFromImmutableReleaseSnapshots() throws Exception {
        String listDocument = """
                {"schemaVersion":1,"configType":"LIST",
                 "list":{"id":"list-1","entityId":"entity-1",
                   "entityCode":"work_order","listKey":"open",
                   "fields":[
                     {"fieldCode":"id","isQuery":false,"dataSourceType":"ENTITY_FIELD"},
                     {"fieldCode":"title","isQuery":true,"dataSourceType":"ENTITY_FIELD"}],
                   "toolbarConfig":[
                     {"key":"create","type":"built-in","enabled":true},
                     {"key":"delete","type":"built-in","enabled":true}],
                   "rowActionConfig":[
                     {"key":"edit","type":"built-in","enabled":true},
                     {"key":"custom","type":"custom","enabled":true}]},
                 "eventBindings":[],"viewCompositions":[]}
                """;
        String formDocument = """
                {"schemaVersion":1,"configType":"FORM",
                 "form":{"id":"form-1","entityId":"entity-1"},"nodes":[],
                 "legacyFields":[
                   {"fieldCode":"title","fieldType":"TEXT","isReadonly":0},
                   {"fieldCode":"secret_token","fieldType":"SECRET","isReadonly":0}],
                 "eventBindings":[],"viewCompositions":[]}
                """;
        ListTargetRow list = new ListTargetRow(
                "list-1", "entity-1", "work_order", "open", "lr-1", 3L,
                listDocument, hash(listDocument));
        FormTargetRow form = new FormTargetRow(
                "form-1", "entity-1", "fr-1", 5L, formDocument, hash(formDocument));

        var result = parser.parse(
                SurfaceType.LIST, "work_order", "open", "form-1", list, form,
                List.of(
                        new FieldRow("id", false, "TEXT"),
                        new FieldRow("title", true, "TEXT"),
                        new FieldRow("secret_token", true, "SECRET")));

        assertEquals(List.of("id", "title", "secret_token"), result.fields());
        assertEquals(List.of("title"), result.queryableFields());
        assertEquals(List.of("title", "secret_token"), result.writableFields());
        assertEquals(List.of("secret_token"), result.sensitiveFields());
        assertTrue(result.actionKeys().containsAll(List.of("view", "create", "save", "edit")));
        assertFalse(result.actionKeys().contains("delete"));
        assertFalse(result.actionKeys().contains("custom"));
        assertTrue(result.trustedComponentsOnly());
    }

    @Test
    void failsClosedWhenSnapshotHashDoesNotMatch() throws Exception {
        String document = """
                {"schemaVersion":1,"configType":"LIST",
                 "list":{"id":"list-1","entityId":"entity-1",
                   "entityCode":"work_order","listKey":"open",
                   "fields":[]},"eventBindings":[],"viewCompositions":[]}
                """;
        ListTargetRow target = new ListTargetRow(
                "list-1", "entity-1", "work_order", "open", "lr-1", 1L,
                document.replace("open", "closed"), hash(document));

        assertThrows(IllegalStateException.class, () -> parser.parse(
                SurfaceType.LIST, "work_order", "open", null,
                target, null, List.of()));
    }

    @Test
    void marksCustomComponentsAndExternalProvidersAsUntrusted() throws Exception {
        String document = """
                {"schemaVersion":1,"configType":"LIST",
                 "list":{"id":"list-1","entityId":"entity-1",
                   "entityCode":"work_order","listKey":"open",
                   "customComponent":"remote-grid","queryProviderCode":"project-custom",
                   "fields":[]},"eventBindings":[],"viewCompositions":[]}
                """;
        ListTargetRow target = new ListTargetRow(
                "list-1", "entity-1", "work_order", "open", "lr-1", 1L,
                document, hash(document));

        var result = parser.parse(SurfaceType.LIST, "work_order", "open", null,
                target, null, List.of());

        assertFalse(result.trustedComponentsOnly());
        assertFalse(result.actionKeys().contains("view"));
    }

    @Test
    void marksBacktrackingFormPatternAsUntrustedForEmbedV1() throws Exception {
        String document = """
                {"schemaVersion":1,"configType":"FORM",
                 "form":{"id":"form-1","entityId":"entity-1"},"nodes":[],
                 "legacyFields":[
                   {"fieldCode":"title","fieldType":"TEXT","isReadonly":0,
                    "validationRules":"{\\\"pattern\\\":\\\"(a+)+$\\\"}"}],
                 "eventBindings":[],"viewCompositions":[]}
                """;
        FormTargetRow form = new FormTargetRow(
                "form-1", "entity-1", "fr-1", 1L, document, hash(document));

        var result = parser.parse(
                SurfaceType.FORM, "work_order", null, "form-1",
                null, form, List.of(new FieldRow("title", true, "TEXT")));

        assertFalse(result.trustedComponentsOnly());
    }

    @Test
    void marksLookupFieldUntrustedUntilCandidateReleaseRegistryExists() throws Exception {
        String document = """
                {"schemaVersion":1,"configType":"FORM",
                 "form":{"id":"form-1","entityId":"entity-1"},"nodes":[],
                 "legacyFields":[
                   {"fieldCode":"assignee_id","fieldType":"REFERENCE","isReadonly":0}],
                 "eventBindings":[],"viewCompositions":[]}
                """;
        FormTargetRow form = new FormTargetRow(
                "form-1", "entity-1", "fr-1", 1L, document, hash(document));

        var result = parser.parse(
                SurfaceType.FORM, "work_order", null, "form-1",
                null, form,
                List.of(new FieldRow("assignee_id", true, "REFERENCE")));

        assertFalse(result.trustedComponentsOnly());
    }

    @Test
    void classifiesCredentialLikeFieldsAsSensitiveAndNeverQueryable() throws Exception {
        String document = """
                {"schemaVersion":1,"configType":"LIST",
                 "list":{"id":"list-1","entityId":"entity-1",
                   "entityCode":"work_order","listKey":"open",
                   "fields":[
                     {"fieldCode":"private_key","isQuery":true,
                      "dataSourceType":"ENTITY_FIELD"},
                     {"fieldCode":"api_credential","isQuery":true,
                      "dataSourceType":"ENTITY_FIELD"}]},
                 "eventBindings":[],"viewCompositions":[]}
                """;
        ListTargetRow target = new ListTargetRow(
                "list-1", "entity-1", "work_order", "open", "lr-1", 1L,
                document, hash(document));

        var result = parser.parse(SurfaceType.LIST, "work_order", "open", null,
                target, null, List.of(
                        new FieldRow("private_key", false, "TEXT"),
                        new FieldRow("api_credential", false, "TEXT")));

        assertEquals(List.of("private_key", "api_credential"), result.sensitiveFields());
        assertTrue(result.queryableFields().isEmpty());
    }

    private String hash(String document) throws Exception {
        JsonNode node = objectMapper.readTree(document);
        Object value = objectMapper.convertValue(node, Object.class);
        ObjectMapper canonical = objectMapper.copy()
                .configure(com.fasterxml.jackson.databind.SerializationFeature
                        .ORDER_MAP_ENTRIES_BY_KEYS, true);
        String normalized = canonical.writeValueAsString(value);
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(normalized.getBytes(StandardCharsets.UTF_8)));
    }
}
