package com.workflow.embed.management.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.ValidationResult;
import com.workflow.embed.management.support.InMemoryEmbedManagementRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbedViewConfigurationValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InMemoryEmbedManagementRepository repository =
            new InMemoryEmbedManagementRepository();
    private EmbedViewConfigurationValidator validator;

    @BeforeEach
    void setUp() {
        repository.resolvedResource = new ResolvedResource(
                "work_order", "supplier_open", "form-1",
                "list-release-1", 5L, "form-release-1", 7L,
                List.of("id", "title", "supplier_id", "secret_token"),
                List.of("id", "title", "supplier_id"),
                List.of("title", "supplier_id"),
                List.of("secret_token"), List.of("view", "create", "save"), true);
        validator = new EmbedViewConfigurationValidator(objectMapper, repository);
    }

    @Test
    void canonicalHashIsStableAcrossObjectPropertyOrder() throws Exception {
        JsonNode first = objectMapper.readTree(validDraft());
        JsonNode second = objectMapper.readTree("""
                {
                  "ui":{},"contextBindings":[],"contextSchema":{},
                  "actionPolicy":{"allowed":["view"]},
                  "fieldPolicy":{"returnable":["id"],"writable":["title"],
                    "queryable":["title"],"visible":["id","title"]},
                  "capabilities":["LIST_QUERY","RECORD_VIEW"],
                  "releasePolicy":{"listReleaseId":"list-release-1",
                    "formReleaseId":"form-release-1","strategy":"PINNED"},
                  "entryModes":["LIST","VIEW"],
                  "target":{"defaultFormId":"form-1","listKey":"supplier_open",
                    "entityCode":"work_order"}
                }
                """);

        ValidationResult firstResult = validator.validate(SurfaceType.LIST, first);
        ValidationResult secondResult = validator.validate(SurfaceType.LIST, second);

        assertTrue(firstResult.valid());
        assertEquals(firstResult.canonicalConfig(), secondResult.canonicalConfig());
        assertEquals(firstResult.configHash(), secondResult.configHash());
        assertEquals(64, firstResult.configHash().length());
    }

    @Test
    void rejectsRemoteSchemaRefSensitiveReturnAndBlockedCapability() throws Exception {
        JsonNode draft = objectMapper.readTree(validDraft());
        ((com.fasterxml.jackson.databind.node.ArrayNode) draft.path("capabilities"))
                .add("EXPORT");
        ((com.fasterxml.jackson.databind.node.ArrayNode) draft.path("fieldPolicy")
                .path("visible")).add("secret_token");
        ((com.fasterxml.jackson.databind.node.ArrayNode) draft.path("fieldPolicy")
                .path("returnable")).add("secret_token");
        ((com.fasterxml.jackson.databind.node.ObjectNode) draft.path("contextSchema"))
                .put("$ref", "https://attacker.example/schema.json");

        ValidationResult result = validator.validate(SurfaceType.LIST, draft);

        assertFalse(result.valid());
        assertTrue(result.violations().stream()
                .anyMatch(item -> item.code().equals("CAPABILITY_NOT_SUPPORTED")));
        assertTrue(result.violations().stream()
                .anyMatch(item -> item.code().equals("REMOTE_REF_FORBIDDEN")));
        assertTrue(result.violations().stream()
                .anyMatch(item -> item.code().equals("SENSITIVE_FIELD_NOT_RETURNABLE")));
    }

    @Test
    void rejectsCapabilitiesWithoutV1ConcurrencyOrTypedActionContracts() throws Exception {
        for (String blocked : List.of("RECORD_UPDATE", "ACTION_EXECUTE", "PROCESS_START")) {
            JsonNode draft = objectMapper.readTree(validDraft());
            ((com.fasterxml.jackson.databind.node.ArrayNode) draft.path("capabilities"))
                    .add(blocked);

            ValidationResult result = validator.validate(SurfaceType.LIST, draft);

            assertFalse(result.valid(), blocked);
            assertTrue(result.violations().stream()
                    .anyMatch(item -> item.code().equals("CAPABILITY_NOT_SUPPORTED")), blocked);
        }
    }

    @Test
    void rejectsEditEntryEvenWhenLegacyDraftAlsoRequestsUpdateCapability() throws Exception {
        JsonNode draft = objectMapper.readTree(validDraft());
        ((com.fasterxml.jackson.databind.node.ArrayNode) draft.path("entryModes"))
                .add("EDIT");
        ((com.fasterxml.jackson.databind.node.ArrayNode) draft.path("capabilities"))
                .add("RECORD_UPDATE");

        ValidationResult result = validator.validate(SurfaceType.LIST, draft);

        assertFalse(result.valid());
        assertTrue(result.violations().stream()
                .anyMatch(item -> item.code().equals("ENTRY_MODE_NOT_SUPPORTED")));
        assertTrue(result.violations().stream()
                .anyMatch(item -> item.code().equals("CAPABILITY_NOT_SUPPORTED")));
    }

    @Test
    void rejectsLocalReferencesBecauseLaunchV1HasNoSchemaResolver() throws Exception {
        JsonNode draft = objectMapper.readTree(validDraft());
        ((com.fasterxml.jackson.databind.node.ObjectNode) draft.path("contextSchema"))
                .put("$ref", "#/definitions/tenant");

        ValidationResult result = validator.validate(SurfaceType.LIST, draft);

        assertFalse(result.valid());
        assertTrue(result.violations().stream()
                .anyMatch(item -> item.code().equals("REMOTE_REF_FORBIDDEN")));
    }

    @Test
    void rejectsEntryModeWithoutMatchingRuntimeCapability() throws Exception {
        JsonNode draft = objectMapper.readTree(validDraft());
        ((com.fasterxml.jackson.databind.node.ArrayNode) draft.path("capabilities"))
                .removeAll().add("LIST_QUERY");

        ValidationResult result = validator.validate(SurfaceType.LIST, draft);

        assertFalse(result.valid());
        assertTrue(result.violations().stream()
                .anyMatch(item -> item.code().equals("ENTRY_CAPABILITY_REQUIRED")));
    }

    @Test
    void requiresExplicitContextSchemaBecauseRuntimeAlwaysParsesIt() throws Exception {
        JsonNode draft = objectMapper.readTree(validDraft());
        ((com.fasterxml.jackson.databind.node.ObjectNode) draft).remove("contextSchema");

        ValidationResult result = validator.validate(SurfaceType.LIST, draft);

        assertFalse(result.valid());
        assertTrue(result.violations().stream()
                .anyMatch(item -> item.code().equals("INVALID_CONTEXT_SCHEMA")));
    }

    @Test
    void rejectsUnsupportedSchemaConstraintsAndOptionalBindingSources() throws Exception {
        JsonNode draft = objectMapper.readTree(validDraft());
        ((com.fasterxml.jackson.databind.node.ObjectNode) draft).set(
                "contextSchema", objectMapper.readTree("""
                        {"type":"object","additionalProperties":false,
                         "properties":{"supplierId":{"type":"string",
                           "format":"uuid","pattern":"["}},"required":[]}
                        """));
        ((com.fasterxml.jackson.databind.node.ArrayNode) draft.path("contextBindings"))
                .add(objectMapper.readTree("""
                        {"source":"supplierId","target":"supplier_id",
                         "usage":"FIXED_FILTER"}
                        """));

        ValidationResult result = validator.validate(SurfaceType.LIST, draft);

        assertFalse(result.valid());
        assertTrue(result.violations().stream()
                .anyMatch(item -> item.code().equals("UNSUPPORTED_SCHEMA_KEYWORD")));
        assertTrue(result.violations().stream()
                .anyMatch(item -> item.code().equals("SCHEMA_PATTERN_NOT_SUPPORTED")));
        assertTrue(result.violations().stream()
                .anyMatch(item -> item.code().equals("CONTEXT_SOURCE_MUST_BE_REQUIRED")));
    }

    @Test
    void acceptsRequiredScalarContextBinding() throws Exception {
        JsonNode draft = objectMapper.readTree(validDraft());
        ((com.fasterxml.jackson.databind.node.ObjectNode) draft).set(
                "contextSchema", objectMapper.readTree("""
                        {"type":"object","additionalProperties":false,
                         "properties":{"supplierId":{"type":"string","minLength":1,
                           "maxLength":64}},"required":["supplierId"]}
                        """));
        ((com.fasterxml.jackson.databind.node.ArrayNode) draft.path("contextBindings"))
                .add(objectMapper.readTree("""
                        {"source":"supplierId","target":"supplier_id",
                         "usage":"FIXED_FILTER"}
                        """));

        ValidationResult result = validator.validate(SurfaceType.LIST, draft);

        assertTrue(result.valid());
    }

    @Test
    void rejectsFixedFilterTargetsWhoseOperatorKeysCollide() throws Exception {
        repository.resolvedResource = new ResolvedResource(
                "work_order", "supplier_open", "form-1",
                "list-release-1", 5L, "form-release-1", 7L,
                List.of("id", "title", "supplier_id", "supplier_id_op"),
                List.of("id", "title", "supplier_id", "supplier_id_op"),
                List.of("title", "supplier_id", "supplier_id_op"),
                List.of(), List.of("view", "create", "save"), true);
        JsonNode draft = objectMapper.readTree(validDraft());
        ((com.fasterxml.jackson.databind.node.ObjectNode) draft).set(
                "contextSchema", objectMapper.readTree("""
                        {"type":"object","additionalProperties":false,
                         "properties":{"supplierId":{"type":"string"}},
                         "required":["supplierId"]}
                        """));
        var bindings = (com.fasterxml.jackson.databind.node.ArrayNode)
                draft.path("contextBindings");
        bindings.add(objectMapper.readTree("""
                {"source":"supplierId","target":"supplier_id_op",
                 "usage":"FIXED_FILTER"}
                """));
        bindings.add(objectMapper.readTree("""
                {"source":"supplierId","target":"supplier_id",
                 "usage":"FIXED_FILTER"}
                """));

        ValidationResult result = validator.validate(SurfaceType.LIST, draft);

        assertFalse(result.valid());
        assertTrue(result.violations().stream()
                .anyMatch(item -> item.code().equals("FILTER_KEY_COLLISION")));
    }

    @Test
    void rechecksSizeAfterInjectingResolvedReleaseCoordinates() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode draft =
                (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(validDraft());
        draft.put("padding", "");
        int baseBytes = objectMapper.writeValueAsBytes(draft).length;
        draft.put("padding", "x".repeat(262_144 - baseBytes));
        assertEquals(262_144, objectMapper.writeValueAsBytes(draft).length);

        ValidationResult result = validator.validate(SurfaceType.LIST, draft);

        assertFalse(result.valid());
        assertTrue(result.violations().stream()
                .anyMatch(item -> item.code().equals("DOCUMENT_TOO_LARGE")));
    }

    private static String validDraft() {
        return """
                {
                  "target":{"entityCode":"work_order","listKey":"supplier_open",
                    "defaultFormId":"form-1"},
                  "entryModes":["LIST","VIEW"],
                  "releasePolicy":{"strategy":"PINNED","listReleaseId":"list-release-1",
                    "formReleaseId":"form-release-1"},
                  "capabilities":["LIST_QUERY","RECORD_VIEW"],
                  "fieldPolicy":{"visible":["id","title"],"queryable":["title"],
                    "writable":["title"],"returnable":["id"]},
                  "actionPolicy":{"allowed":["view"]},
                  "contextSchema":{},"contextBindings":[],"ui":{}
                }
                """;
    }
}
