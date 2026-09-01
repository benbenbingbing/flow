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
import java.util.stream.IntStream;
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
    void flowPublishedFormFollowsReleaseFieldsWithoutMaintainingFieldArrays()
            throws Exception {
        JsonNode draft = objectMapper.readTree(flowPublishedFormDraft());

        ValidationResult result = validator.validate(SurfaceType.FORM, draft);

        assertTrue(result.valid());
        JsonNode canonical = objectMapper.readTree(result.canonicalConfig());
        assertEquals("FLOW_PUBLISHED",
                canonical.path("fieldPolicy").path("mode").asText());
        assertFalse(canonical.path("fieldPolicy").has("visible"));
        assertEquals(List.of("title"), objectMapper.convertValue(
                canonical.path("fieldPolicy").path("returnable"), List.class));
        // FORM 操作栏完全引用 Published Form；即使旧客户端仍提交 allowed，
        // Runtime Snapshot 也必须清空，不能让该参数形成按钮覆盖或求交语义。
        assertTrue(canonical.path("actionPolicy").path("allowed").isEmpty());
    }

    @Test
    void flowPublishedFormHasNoEmbedSpecificFieldCountLimit()
            throws Exception {
        List<String> futurePlatformFields = IntStream.range(0, 501)
                .mapToObj(index -> index == 0 ? "title" : "field_" + index)
                .toList();
        repository.resolvedResource = new ResolvedResource(
                "work_order", null, "form-1",
                null, null, "form-release-501", 501L,
                futurePlatformFields, List.of(), futurePlatformFields,
                List.of(), List.of("view", "create", "save"), true);

        ValidationResult result = validator.validate(
                SurfaceType.FORM,
                objectMapper.readTree(flowPublishedFormDraft()));

        assertTrue(result.valid(),
                "字段规模由 Flow Published Form 自身约束，Embed 不维护第二套上限");
    }

    @Test
    void canonicalizesCaseInsensitiveFormPolicyModeForFollowerDiscovery()
            throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode draft =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        objectMapper.readTree(flowPublishedFormDraft());
        draft.withObject("/fieldPolicy").put("mode", "flow_published");

        ValidationResult result = validator.validate(SurfaceType.FORM, draft);

        assertTrue(result.valid());
        assertEquals("FLOW_PUBLISHED", objectMapper.readTree(
                result.canonicalConfig()).path("fieldPolicy")
                .path("mode").asText());
    }

    @Test
    void formRejectsExplicitOrMissingFieldPolicyMode() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode explicitDraft =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        objectMapper.readTree(flowPublishedFormDraft());
        explicitDraft.withObject("/fieldPolicy").put("mode", "EXPLICIT");
        explicitDraft.withObject("/fieldPolicy").set(
                "visible", objectMapper.readTree("[\"title\"]"));
        explicitDraft.withObject("/fieldPolicy").set(
                "queryable", objectMapper.readTree("[]"));
        explicitDraft.withObject("/fieldPolicy").set(
                "writable", objectMapper.readTree("[\"title\"]"));
        com.fasterxml.jackson.databind.node.ObjectNode missingModeDraft =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        objectMapper.readTree(flowPublishedFormDraft());
        missingModeDraft.withObject("/fieldPolicy").remove("mode");

        ValidationResult explicitResult = validator.validate(
                SurfaceType.FORM, explicitDraft);
        ValidationResult missingModeResult = validator.validate(
                SurfaceType.FORM, missingModeDraft);

        assertFalse(explicitResult.valid());
        assertTrue(explicitResult.violations().stream()
                .anyMatch(item -> item.code().equals(
                        "FLOW_PUBLISHED_FIELD_POLICY_REQUIRED")));
        assertFalse(missingModeResult.valid());
        assertTrue(missingModeResult.violations().stream()
                .anyMatch(item -> item.code().equals(
                        "FLOW_PUBLISHED_FIELD_POLICY_REQUIRED")));
    }

    @Test
    void nativeSurfacesDoNotMaintainAnEmbedComponentTrustGate()
            throws Exception {
        repository.resolvedResource = new ResolvedResource(
                "work_order", null, "form-1",
                null, null, "form-release-2", 2L,
                List.of("title"), List.of("title"), List.of("title"),
                List.of(), List.of("view", "create", "save"), false);

        ValidationResult formResult = validator.validate(
                SurfaceType.FORM,
                objectMapper.readTree(flowPublishedFormDraft()));

        assertTrue(formResult.valid(),
                "FORM 由 Flow 原生运行时解释，不能被 Embed 组件白名单拒绝");

        repository.resolvedResource = new ResolvedResource(
                "work_order", "supplier_open", "form-1",
                "list-release-2", 2L, "form-release-2", 2L,
                List.of("id", "title", "supplier_id", "secret_token"),
                List.of("id", "title", "supplier_id"),
                List.of("title", "supplier_id"),
                List.of("secret_token"), List.of("view", "create"), false);
        ValidationResult listResult = validator.validate(
                SurfaceType.LIST, objectMapper.readTree(validDraft()));

        assertTrue(listResult.valid(),
                "LIST 与 FORM 都交给 Flow 原生运行时解释，新组件不得要求 Embed 同步白名单");
    }

    @Test
    void nativePolicyRejectsFormOverridesAndSensitiveReturnButAllowsListMode()
            throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode draft =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        objectMapper.readTree(flowPublishedFormDraft());
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                draft.path("fieldPolicy")).set(
                "visible", objectMapper.readTree("[\"title\"]"));
        ((com.fasterxml.jackson.databind.node.ArrayNode)
                draft.path("fieldPolicy").path("returnable"))
                .add("secret_token");

        ValidationResult formResult = validator.validate(SurfaceType.FORM, draft);
        ValidationResult listResult = validator.validate(
                SurfaceType.LIST,
                objectMapper.readTree(flowPublishedFormDraft()));

        assertFalse(formResult.valid());
        assertTrue(formResult.violations().stream()
                .anyMatch(item -> item.code().equals(
                        "FIELD_POLICY_ARRAY_NOT_ALLOWED")));
        assertTrue(formResult.violations().stream()
                .anyMatch(item -> item.code().equals(
                        "SENSITIVE_FIELD_NOT_RETURNABLE")));
        assertTrue(listResult.valid(), () -> listResult.violations().toString());
    }

    @Test
    void flowPublishedRejectsUnknownFieldPolicyOverrides() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode draft =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        objectMapper.readTree(flowPublishedFormDraft());
        draft.withObject("/fieldPolicy").put("controlType", "input");

        ValidationResult result = validator.validate(SurfaceType.FORM, draft);

        assertFalse(result.valid());
        assertTrue(result.violations().stream()
                .anyMatch(item -> item.path().equals("fieldPolicy.controlType")
                        && item.code().equals("FIELD_POLICY_KEY_NOT_ALLOWED")));
    }

    @Test
    void flowPublishedFormAcceptsServerSideFixedFilterFromPublishedRelease()
            throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode draft =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        objectMapper.readTree(flowPublishedFormDraft());
        draft.set("contextSchema", objectMapper.readTree("""
                {"type":"object","additionalProperties":false,
                 "properties":{"supplierId":{"type":"string",
                   "minLength":1,"maxLength":64}},
                 "required":["supplierId"]}
                """));
        ((com.fasterxml.jackson.databind.node.ArrayNode)
                draft.path("contextBindings")).add(objectMapper.readTree("""
                {"source":"supplierId","target":"supplier_id",
                 "usage":"FIXED_FILTER"}
                """));

        ValidationResult result = validator.validate(SurfaceType.FORM, draft);

        assertTrue(result.valid());
        JsonNode canonical = objectMapper.readTree(result.canonicalConfig());
        assertFalse(canonical.path("fieldPolicy").has("queryable"));
    }

    @Test
    void flowPublishedFormRejectsAbsentOrSensitiveFixedFilterTargetAsNotQueryable()
            throws Exception {
        for (String target : List.of("missing_field", "secret_token")) {
            com.fasterxml.jackson.databind.node.ObjectNode draft =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                            objectMapper.readTree(flowPublishedFormDraft());
            draft.set("contextSchema", objectMapper.readTree("""
                    {"type":"object","additionalProperties":false,
                     "properties":{"supplierId":{"type":"string"}},
                     "required":["supplierId"]}
                    """));
            ((com.fasterxml.jackson.databind.node.ArrayNode)
                    draft.path("contextBindings")).add(objectMapper.readTree("""
                    {"source":"supplierId","target":"%s",
                     "usage":"FIXED_FILTER"}
                    """.formatted(target)));

            ValidationResult result = validator.validate(SurfaceType.FORM, draft);

            assertFalse(result.valid(), target);
            assertTrue(result.violations().stream().anyMatch(item ->
                    item.path().equals("contextBindings[0].target")
                            && item.code().equals("FIELD_NOT_QUERYABLE")), target);
            assertFalse(result.violations().stream().anyMatch(item ->
                    item.path().equals("contextBindings[0].target")
                            && item.code().equals("FIELD_NOT_FOUND")), target);
        }
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
    void rejectsCapabilitiesWithoutV1ConcurrencyContracts() throws Exception {
        for (String blocked : List.of("RECORD_UPDATE", "PROCESS_START")) {
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
    void nativeFormAndListCanDeclareActionExecuteCapability() throws Exception {
        JsonNode formDraft = objectMapper.readTree(flowPublishedFormDraft());
        ((com.fasterxml.jackson.databind.node.ArrayNode) formDraft
                .path("capabilities")).add("ACTION_EXECUTE");

        ValidationResult form = validator.validate(
                SurfaceType.FORM, formDraft);

        assertTrue(form.valid(), () -> form.violations().toString());
        assertTrue(objectMapper.readTree(form.canonicalConfig())
                .path("capabilities").toString()
                .contains("ACTION_EXECUTE"));

        JsonNode listDraft = objectMapper.readTree(validDraft());
        ((com.fasterxml.jackson.databind.node.ArrayNode) listDraft
                .path("capabilities")).add("ACTION_EXECUTE");
        ValidationResult list = validator.validate(
                SurfaceType.LIST, listDraft);
        assertTrue(list.valid(), () -> list.violations().toString());
        assertTrue(objectMapper.readTree(list.canonicalConfig())
                .path("capabilities").toString()
                .contains("ACTION_EXECUTE"));
    }

    @Test
    void nativeListRecordNavigationDoesNotRequireEmbedDefaultFormRelease()
            throws Exception {
        repository.resolvedResource = new ResolvedResource(
                "work_order", "supplier_open", null,
                "list-release-2", 2L, null, null,
                List.of("id", "title", "supplier_id", "secret_token"),
                List.of("id", "title", "supplier_id"),
                List.of("title", "supplier_id"), List.of("secret_token"),
                List.of("view", "create"), false);
        JsonNode draft = objectMapper.readTree(validDraft());
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                draft.path("target")).remove("defaultFormId");
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                draft.path("releasePolicy")).remove("formReleaseId");

        ValidationResult result = validator.validate(
                SurfaceType.LIST, draft);

        assertTrue(result.valid(), () -> result.violations().toString());
        JsonNode resolved = objectMapper.readTree(
                result.canonicalConfig()).path("resolved");
        assertTrue(resolved.path("defaultFormId").isNull());
        assertTrue(resolved.path("formReleaseId").isNull());
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

    private static String flowPublishedFormDraft() {
        return """
                {
                  "target":{"entityCode":"work_order","listKey":"supplier_open",
                    "defaultFormId":"form-1"},
                  "entryModes":["CREATE","VIEW"],
                  "releasePolicy":{"strategy":"PINNED","listReleaseId":"list-release-1",
                    "formReleaseId":"form-release-1"},
                  "capabilities":["RECORD_CREATE","RECORD_VIEW"],
                  "fieldPolicy":{"mode":"FLOW_PUBLISHED","returnable":["title"]},
                  "actionPolicy":{"allowed":["view","save"]},
                  "contextSchema":{},"contextBindings":[],"ui":{}
                }
                """;
    }
}
