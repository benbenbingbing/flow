package com.workflow.embed.management.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource;
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
    void derivesListProjectionPoliciesFromImmutableReleaseSnapshots()
            throws Exception {
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
                 "form":{"id":"form-1","entityId":"entity-1"},
                 "legacyFields":[
                   {"fieldCode":"title","fieldType":"TEXT","isReadonly":0},
                   {"fieldCode":"secret_token","fieldType":"STRING","isReadonly":0}]}
                """;
        ListTargetRow list = new ListTargetRow(
                "list-1", "entity-1", "work_order", "open", "lr-1", 3L,
                listDocument, hash(listDocument));
        FormTargetRow form = new FormTargetRow(
                "form-1", "entity-1", "fr-1", 5L,
                formDocument, hash(formDocument));

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
        assertTrue(result.actionKeys().containsAll(
                List.of("view", "create", "save", "edit")));
        assertFalse(result.actionKeys().contains("delete"));
        assertFalse(result.actionKeys().contains("custom"));
        assertTrue(result.trustedComponentsOnly());
    }

    @Test
    void formAcceptsFutureComponentsPropertiesAndLayoutWithoutEmbedChanges()
            throws Exception {
        ObjectNode document = formDocument();
        document.withArray("legacyFields").addObject()
                .put("fieldCode", "future_value")
                .put("fieldType", "FUTURE_PLATFORM_TYPE")
                .put("componentType", "future-cascading-calendar")
                .put("componentProps", "{\"popup\":{\"engine\":\"flow-vNext\"},"
                        + "\"events\":{\"change\":\"native-handler\"}}")
                .put("isReadonly", 0);
        document.withArray("nodes").addObject()
                .put("id", "future-node")
                .put("nodeKey", "future_node")
                .put("nodeType", "FUTURE_CONTAINER")
                .put("propsDocument", "{\"newLayoutRule\":true}");
        document.withArray("eventBindings").addObject()
                .put("event", "future-event")
                .put("handler", "native-flow-runtime");
        document.withArray("viewCompositions").addObject()
                .put("type", "future-composition");

        var result = parseForm(document, List.of());

        assertTrue(result.trustedComponentsOnly(),
                "FORM 由 Flow 原生运行时解释，Embed 不维护组件/props/布局白名单");
        assertEquals(List.of("future_value"), result.fields());
        assertEquals(List.of("future_value"), result.writableFields());
    }

    @Test
    void formHasNoEmbedSpecificFieldOrNodeCountLimit() throws Exception {
        ObjectNode document = formDocument();
        for (int index = 0; index < 501; index++) {
            document.withArray("legacyFields").addObject()
                    .put("fieldCode", "future_field_" + index)
                    .put("fieldType", "FUTURE_TYPE_" + index)
                    .put("componentType", "future_component_" + index)
                    .put("isReadonly", 0);
            document.withArray("nodes").addObject()
                    .put("id", "node-" + index)
                    .put("nodeType", "FUTURE_NODE_" + index);
        }

        var result = parseForm(document, List.of());

        assertTrue(result.trustedComponentsOnly());
        assertEquals(501, result.fields().size());
    }

    @Test
    void formStillEnforcesSnapshotHashTypeAndPublishedOwnership()
            throws Exception {
        ObjectNode document = formDocument();
        String serialized = objectMapper.writeValueAsString(document);
        FormTargetRow tampered = new FormTargetRow(
                "form-1", "entity-1", "fr-1", 1L,
                serialized.replace("form-1", "form-2"), hash(serialized));
        FormTargetRow wrongOwner = new FormTargetRow(
                "form-other", "entity-1", "fr-1", 1L,
                serialized, hash(serialized));
        ObjectNode wrongTypeDocument = document.deepCopy();
        wrongTypeDocument.put("configType", "LIST");
        String wrongType = objectMapper.writeValueAsString(wrongTypeDocument);
        FormTargetRow wrongTypeTarget = new FormTargetRow(
                "form-1", "entity-1", "fr-1", 1L,
                wrongType, hash(wrongType));

        assertThrows(IllegalStateException.class, () -> parser.parse(
                SurfaceType.FORM, "work_order", null, "form-1",
                null, tampered, List.of()));
        assertThrows(IllegalStateException.class, () -> parser.parse(
                SurfaceType.FORM, "work_order", null, "form-1",
                null, wrongOwner, List.of()));
        assertThrows(IllegalStateException.class, () -> parser.parse(
                SurfaceType.FORM, "work_order", null, "form-1",
                null, wrongTypeTarget, List.of()));
    }

    @Test
    void nativeListAcceptsFlowRenderersProvidersEventsAndCompositions()
            throws Exception {
        String document = """
                {"schemaVersion":1,"configType":"LIST",
                 "list":{"id":"list-1","entityId":"entity-1",
                   "entityCode":"work_order","listKey":"open",
                   "queryProviderCode":"project-custom",
                   "fields":[{"fieldCode":"title",
                     "dataSourceType":"REMOTE",
                     "renderComponent":"remote-grid"}]},
                 "eventBindings":[{"event":"load","handler":"flow"}],
                 "viewCompositions":[{"type":"future-composition"}]}
                """;
        ListTargetRow target = new ListTargetRow(
                "list-1", "entity-1", "work_order", "open", "lr-1", 1L,
                document, hash(document));

        var result = parser.parse(
                SurfaceType.LIST, "work_order", "open", null,
                target, null, List.of(new FieldRow("title", false, "TEXT")));

        assertTrue(result.trustedComponentsOnly(),
                "LIST 由 Flow 原生运行时解释，Embed 不维护组件或数据源白名单");
    }

    @Test
    void credentialLikeFieldsRemainSensitivePolicyMetadata()
            throws Exception {
        ObjectNode document = formDocument();
        document.withArray("legacyFields").addObject()
                .put("fieldCode", "private_key")
                .put("fieldType", "FUTURE_SECRET_EDITOR")
                .put("componentType", "future-secret-editor")
                .put("isReadonly", 0);

        var result = parseForm(document, List.of());

        assertEquals(List.of("private_key"), result.sensitiveFields());
        assertFalse(result.queryableFields().contains("private_key"));
        assertTrue(result.trustedComponentsOnly());
    }

    private ObjectNode formDocument() {
        ObjectNode document = objectMapper.createObjectNode();
        document.put("schemaVersion", 1);
        document.put("configType", "FORM");
        document.putObject("form")
                .put("id", "form-1")
                .put("entityId", "entity-1");
        document.putArray("nodes");
        document.putArray("legacyFields");
        document.putArray("eventBindings");
        document.putArray("viewCompositions");
        return document;
    }

    private ResolvedResource parseForm(
            ObjectNode document,
            List<FieldRow> fields) throws Exception {
        String serialized = objectMapper.writeValueAsString(document);
        FormTargetRow form = new FormTargetRow(
                "form-1", "entity-1", "fr-1", 1L,
                serialized, hash(serialized));
        return parser.parse(
                SurfaceType.FORM, "work_order", null, "form-1",
                null, form, fields);
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
