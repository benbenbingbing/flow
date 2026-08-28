package com.workflow.embed.application.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedRuntimeFormPort;
import com.workflow.embed.api.web.EmbedRuntimeLookupQueryRequest;
import com.workflow.embed.api.web.EmbedRuntimeOptionQueryRequest;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedRuntimeReleaseSnapshot;
import com.workflow.embed.security.EmbedContextHolder;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbedRuntimeFormFacadeTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final CapturingFormPort formPort = new CapturingFormPort();
    private EmbedRuntimeReleaseSnapshot release;
    private EmbedRuntimeFormFacade facade;

    @BeforeEach
    void setUp() {
        release = release(
                "VIEW", "[\"RECORD_VIEW\"]",
                "{\"visible\":[\"title\",\"supplierId\",\"conditional\"],"
                        + "\"writable\":[\"title\",\"supplierId\"]}",
                "{\"allowed\":[\"view\"]}",
                "[{\"source\":\"supplierId\",\"target\":\"supplier_id\","
                        + "\"usage\":\"FIXED_FILTER\"},"
                        + "{\"source\":\"supplierId\",\"target\":\"supplierId\","
                        + "\"usage\":\"FORCED_FORM_VALUE\"}]");
        facade = new EmbedRuntimeFormFacade(
                (sessionId, viewId, releaseId) -> release,
                formPort, objectMapper);
        EmbedContextHolder.set(session(
                "VIEW", "record-1", Set.of("RECORD_VIEW")));
        formPort.snapshot = new EmbedRuntimeFormPort.FormSnapshot(
                "工单详情", "grid",
                List.of(
                        field("title", "TEXT", true, true),
                        field("supplierId", "TEXT", true, true),
                        new EmbedRuntimeFormPort.Field(
                                "conditional", "条件字段", "TEXT", false,
                                false, false, false, true, null, Map.of(),
                                List.of(), false, List.of(), false,
                                List.of(), List.of(), 24),
                        field("secret", "TEXT", true, true)),
                List.of(new EmbedRuntimeFormPort.Action(
                        "view", "查看", true, true, null)));
        formPort.record = Optional.of(new EmbedRuntimeFormPort.RecordSnapshot(
                "record-1", 19L,
                Map.of("title", "泵站维修", "supplierId", "S-10086",
                        "conditional", "must-not-leak", "secret", "root-secret"),
                Instant.parse("2026-08-26T08:00:00Z"),
                Instant.parse("2026-08-27T08:00:00Z")));
    }

    @AfterEach
    void cleanUp() {
        EmbedContextHolder.clear();
    }

    @Test
    void boundViewMayOmitRecordIdAndProjectsOnlyExternalFields() throws Exception {
        var result = facade.form("VIEW", null);

        assertEquals("record-1", formPort.recordQuery.recordId());
        assertEquals(Map.of("supplier_id", "S-10086", "supplier_id_op", "EQ"),
                formPort.recordQuery.trustedContextFilters());
        assertEquals("record-1", result.record().id());
        assertNull(result.record().recordVersion());
        assertEquals(Map.of("title", "泵站维修", "supplierId", "S-10086"),
                result.record().values());
        assertEquals("S-10086", result.form().fields().stream()
                .filter(field -> "supplierId".equals(field.code()))
                .findFirst().orElseThrow().defaultValue());
        assertFalse(result.form().fields().stream()
                .filter(field -> "supplierId".equals(field.code()))
                .findFirst().orElseThrow().fieldState().writable());
        assertTrue(result.form().fields().stream()
                .filter(field -> "conditional".equals(field.code()))
                .findFirst().orElseThrow().hidden());

        String json = objectMapper.writeValueAsString(result);
        assertFalse(json.contains("root-secret"));
        assertFalse(json.contains("must-not-leak"));
        assertFalse(json.contains("form-release-4"));
        assertFalse(json.contains("provider"));
        assertFalse(json.contains("serviceId"));
        assertFalse(json.contains("operationCode"));
        assertFalse(json.contains("script"));
        assertFalse(json.contains("eventCode"));
    }

    @Test
    void boundRecordMismatchAndUnauthorizedRecordHaveSame404Contract() {
        EmbedException mismatch = assertThrows(
                EmbedException.class,
                () -> facade.form("VIEW", "record-2"));

        formPort.record = Optional.empty();
        EmbedException denied = assertThrows(
                EmbedException.class,
                () -> facade.form("VIEW", null));

        assertEquals(404, mismatch.getStatus());
        assertEquals("EMBED_RESOURCE_NOT_FOUND", mismatch.getErrorCode().name());
        assertEquals(404, denied.getStatus());
        assertEquals(mismatch.getMessage(), denied.getMessage());
    }

    @Test
    void editModeIsRejectedEvenWhenLegacySessionAndReleaseContainUpdateCapability() {
        EmbedContextHolder.clear();
        EmbedContextHolder.set(session(
                "EDIT", "record-1", Set.of("RECORD_VIEW", "RECORD_UPDATE")));
        release = release(
                "VIEW", "[\"RECORD_VIEW\",\"RECORD_UPDATE\"]",
                "{\"visible\":[\"title\"],\"writable\":[\"title\"]}",
                "{\"allowed\":[\"save\"]}", "[]");
        formPort.snapshot = new EmbedRuntimeFormPort.FormSnapshot(
                "编辑工单", "grid", List.of(field("title", "TEXT", true, true)),
                List.of(new EmbedRuntimeFormPort.Action(
                        "save", "保存", true, true, null)));

        EmbedException formError = assertThrows(
                EmbedException.class,
                () -> facade.form("EDIT", null));
        EmbedException recordError = assertThrows(
                EmbedException.class,
                () -> facade.record("record-1"));

        assertEquals(403, formError.getStatus());
        assertEquals("EMBED_OPERATION_NOT_ALLOWED", formError.getErrorCode().name());
        assertEquals(403, recordError.getStatus());
        assertEquals("EMBED_OPERATION_NOT_ALLOWED", recordError.getErrorCode().name());
        assertNull(formPort.resolveQuery);
    }

    @Test
    void createAuthorizationIntersectsWritableFieldsAndForcedContextWins() {
        EmbedContextHolder.clear();
        EmbedContextHolder.set(session(
                "CREATE", null, Set.of("RECORD_CREATE")));
        release = release(
                "CREATE", "[\"RECORD_CREATE\"]",
                "{\"visible\":[\"title\",\"supplierId\",\"readOnlyCode\"],"
                        + "\"writable\":[\"title\",\"supplierId\",\"readOnlyCode\"],"
                        + "\"returnable\":[\"title\",\"notInForm\"]}",
                "{\"allowed\":[\"save\"]}",
                "[{\"source\":\"supplierId\",\"target\":\"supplier_id\","
                        + "\"usage\":\"FIXED_FILTER\"},"
                        + "{\"source\":\"supplierId\",\"target\":\"supplierId\","
                        + "\"usage\":\"FORCED_FORM_VALUE\"}]");
        formPort.snapshot = new EmbedRuntimeFormPort.FormSnapshot(
                "新建工单", "grid",
                List.of(
                        field("title", "TEXT", true, true),
                        field("supplierId", "TEXT", true, true),
                        field("readOnlyCode", "TEXT", true, false)),
                List.of(new EmbedRuntimeFormPort.Action(
                        "save", "保存", true, true, null)));

        assertEquals(List.of("title"),
                facade.form("CREATE", null).form().returnableFields());

        var authorization = facade.authorizeCreate(Map.of(
                "title", "泵站维修",
                "supplierId", "browser-cannot-override"));

        assertEquals(Map.of(
                "title", "泵站维修",
                "supplierId", "browser-cannot-override"),
                authorization.clientData());
        assertEquals(Map.of(
                "title", "泵站维修",
                "supplierId", "S-10086"),
                authorization.effectiveData());
        assertEquals(Map.of(
                "supplier_id", "S-10086", "supplier_id_op", "EQ"),
                authorization.contextFilters());

        assertEquals(400, assertThrows(
                EmbedException.class,
                () -> facade.authorizeCreate(Map.of(
                        "readOnlyCode", "must-not-write"))).getStatus());
        assertEquals(400, assertThrows(
                EmbedException.class,
                () -> facade.authorizeCreate(Map.of(
                        "unknownField", "must-not-write"))).getStatus());
    }

    @Test
    void createValidatesPinnedLengthFormatAndStaticOptionsBeforeWrite() {
        useCreateSession();
        release = release(
                "CREATE", "[\"RECORD_CREATE\"]",
                "{\"visible\":[\"title\",\"category\"],"
                        + "\"writable\":[\"title\",\"category\"]}",
                "{\"allowed\":[\"save\"]}", "[]");
        formPort.snapshot = new EmbedRuntimeFormPort.FormSnapshot(
                "新建工单", "grid",
                List.of(
                        new EmbedRuntimeFormPort.Field(
                                "title", "邮箱", "TEXT", true,
                                false, false, true, true, null,
                                Map.of("minLength", 5, "format", "EMAIL"),
                                List.of(), false, List.of(), false,
                                List.of(), List.of(), 24),
                        new EmbedRuntimeFormPort.Field(
                                "category", "类型", "SELECT", false,
                                false, false, true, true, null, Map.of(),
                                List.of(
                                        new EmbedRuntimeFormPort.Option(
                                                "设备", "EQUIPMENT", false),
                                        new EmbedRuntimeFormPort.Option(
                                                "停用", "DISABLED", true)),
                                false, List.of(), false,
                                List.of(), List.of(), 24)),
                List.of(new EmbedRuntimeFormPort.Action(
                        "save", "保存", true, true, null)));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> facade.authorizeCreate(Map.of(
                        "title", "bad",
                        "category", "DISABLED")));

        assertEquals(422, error.getStatus());
        assertEquals("FORM_VALIDATION_FAILED", error.getErrorCode().name());
        assertTrue(error.getData().toString().contains("MIN_LENGTH"));
        assertTrue(error.getData().toString().contains("FORMAT"));
        assertTrue(error.getData().toString().contains("ENUM"));
    }

    @Test
    void createReevaluatesLinkageAndRejectsFieldThatBecameHidden() {
        EmbedContextHolder.clear();
        EmbedContextHolder.set(session(
                "CREATE", null, Set.of("RECORD_CREATE")));
        release = release(
                "CREATE", "[\"RECORD_CREATE\"]",
                "{\"visible\":[\"status\",\"details\"],"
                        + "\"writable\":[\"status\",\"details\"]}",
                "{\"allowed\":[\"save\"]}", "[]");
        formPort.snapshotResolver = query -> {
            boolean closed = "CLOSED".equals(
                    query.trustedRecordValues().get("status"));
            return new EmbedRuntimeFormPort.FormSnapshot(
                    "新建工单", "grid",
                    List.of(
                            new EmbedRuntimeFormPort.Field(
                                    "status", "状态", "TEXT", true,
                                    false, false, true, true, null, Map.of(),
                                    List.of(), false, List.of(), false,
                                    List.of(), List.of(), 24),
                            new EmbedRuntimeFormPort.Field(
                                    "details", "详情", "TEXT", false,
                                    false, false, !closed, true, null, Map.of(),
                                    List.of(), false, List.of(), false,
                                    List.of(), List.of(), 24)),
                    List.of(new EmbedRuntimeFormPort.Action(
                            "save", "保存", true, true, null)));
        };

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> facade.authorizeCreate(Map.of(
                        "status", "CLOSED",
                        "details", "must-not-write")));

        assertEquals(422, error.getStatus());
        assertTrue(error.getData().toString().contains("NOT_WRITABLE"));
        assertEquals(2, formPort.resolveCount);
    }

    @Test
    void createEvaluationRecomputesPartialDraftAndForcedContextWithoutFinalValidation() {
        EmbedContextHolder.clear();
        EmbedContextHolder.set(session(
                "CREATE", null, Set.of("RECORD_CREATE")));
        release = release(
                "CREATE", "[\"RECORD_CREATE\"]",
                "{\"visible\":[\"title\",\"status\",\"details\",\"supplierId\"],"
                        + "\"writable\":[\"title\",\"status\",\"details\",\"supplierId\"]}",
                "{\"allowed\":[\"save\"]}",
                "[{\"source\":\"supplierId\",\"target\":\"supplierId\","
                        + "\"usage\":\"FORCED_FORM_VALUE\"}]");
        formPort.snapshotResolver = query -> {
            boolean closed = "CLOSED".equals(
                    query.trustedRecordValues().get("status"));
            return new EmbedRuntimeFormPort.FormSnapshot(
                    "新建工单", "grid",
                    List.of(
                            field("title", "TEXT", true, true),
                            field("status", "TEXT", true, true),
                            new EmbedRuntimeFormPort.Field(
                                    "details", "详情", "TEXT", true,
                                    false, false, !closed, true, null, Map.of(),
                                    List.of(), false, List.of(), false,
                                    List.of(), List.of(), 24),
                            field("supplierId", "TEXT", true, true)),
                    List.of(new EmbedRuntimeFormPort.Action(
                            "save", "保存", true, true, null)));
        };

        var result = facade.evaluateCreate(Map.of(
                "status", "CLOSED",
                "supplierId", "browser-cannot-override"));

        assertEquals("CREATE", result.mode());
        assertTrue(result.form().fields().stream()
                .filter(field -> "details".equals(field.code()))
                .findFirst().orElseThrow().hidden());
        assertEquals(Map.of(
                        "status", "CLOSED",
                        "supplierId", "S-10086"),
                formPort.resolveQuery.trustedRecordValues());
        assertEquals(2, formPort.resolveCount);
        // title/details 都是 required，但联动重算允许部分草稿，不执行最终提交校验。
        assertFalse(formPort.resolveQuery.trustedRecordValues()
                .containsKey("title"));
    }

    @Test
    void createEvaluationRejectsFieldsOutsideFixedWritablePolicyAndUnsafeValues() {
        EmbedContextHolder.clear();
        EmbedContextHolder.set(session(
                "CREATE", null, Set.of("RECORD_CREATE")));
        release = release(
                "CREATE", "[\"RECORD_CREATE\"]",
                "{\"visible\":[\"status\",\"readOnlyCode\"],"
                        + "\"writable\":[\"status\"]}",
                "{\"allowed\":[\"save\"]}", "[]");
        formPort.snapshot = new EmbedRuntimeFormPort.FormSnapshot(
                "新建工单", "grid",
                List.of(
                        field("status", "TEXT", true, true),
                        field("readOnlyCode", "TEXT", true, true)),
                List.of(new EmbedRuntimeFormPort.Action(
                        "save", "保存", true, true, null)));

        assertEquals(400, assertThrows(
                EmbedException.class,
                () -> facade.evaluateCreate(Map.of(
                        "readOnlyCode", "must-not-evaluate"))).getStatus());
        assertEquals(400, assertThrows(
                EmbedException.class,
                () -> facade.evaluateCreate(Map.of(
                        "status", Map.of("nested", "unsafe")))).getStatus());

        EmbedContextHolder.clear();
        EmbedContextHolder.set(session(
                "VIEW", "record-1", Set.of("RECORD_VIEW")));
        assertEquals(403, assertThrows(
                EmbedException.class,
                () -> facade.evaluateCreate(Map.of())).getStatus());
    }

    @Test
    void createdRecordIsRereadAndProjectedThroughCurrentVisibleFields() {
        EmbedContextHolder.clear();
        EmbedContextHolder.set(session(
                "CREATE", null, Set.of("RECORD_CREATE")));
        release = release(
                "CREATE", "[\"RECORD_CREATE\"]",
                "{\"visible\":[\"title\"],\"writable\":[\"title\"]}",
                "{\"allowed\":[\"save\"]}", "[]");
        formPort.snapshot = new EmbedRuntimeFormPort.FormSnapshot(
                "新建工单", "grid", List.of(field("title", "TEXT", true, true)),
                List.of(new EmbedRuntimeFormPort.Action(
                        "save", "保存", true, true, null)));
        var authorization = facade.authorizeCreate(Map.of("title", "新工单"));
        formPort.record = Optional.of(new EmbedRuntimeFormPort.RecordSnapshot(
                "record-created", 2L,
                Map.of("title", "新工单", "systemSecret", "must-not-leak"),
                null, null));

        var result = facade.projectCreatedRecord(
                authorization, "record-created");

        assertEquals(Map.of("title", "新工单"), result.values());
        assertEquals(authorization.target(), formPort.recordQuery.target());
    }

    @Test
    void dynamicOptionQueryAcceptsOnlyCurrentWritableDependencies() {
        useCreateSession();
        formPort.snapshot = optionForm();
        formPort.optionPage = new EmbedRuntimeFormPort.OptionPage(
                List.of(new EmbedRuntimeFormPort.Option(
                        "设备维修", "EQUIPMENT_REPAIR", false)),
                false, 1, 20);
        EmbedRuntimeOptionQueryRequest request = new EmbedRuntimeOptionQueryRequest();
        request.setMode("CREATE");
        request.setKeyword("维修");
        request.setDependencies(Map.of("title", "泵站"));
        request.setPageSize(20);

        var result = facade.queryOptions("category", request);

        assertEquals("category", formPort.optionQuery.fieldCode());
        assertEquals(Map.of("title", "泵站"),
                formPort.optionQuery.clientDependencies());
        assertEquals(Map.of("supplierId", "S-10086"),
                formPort.optionQuery.trustedContext());
        assertEquals("EQUIPMENT_REPAIR", result.items().get(0).value());

        request.setDependencies(Map.of("supplierId", "attacker"));
        EmbedException error = assertThrows(
                EmbedException.class,
                () -> facade.queryOptions("category", request));
        assertEquals(400, error.getStatus());
    }

    @Test
    void listSessionQueriesPinnedFormOptionsUsingValidatedLocalNavigationMode() {
        EmbedContextHolder.clear();
        EmbedContextHolder.set(session(
                "LIST", null, Set.of("LIST_QUERY", "RECORD_CREATE")));
        release = new EmbedRuntimeReleaseSnapshot(
                "release-1", "view-1", "work-order-list", "工单列表", 4,
                "LIST", "work_order", "open", "list-release-4", 4,
                "form-release-4", 4,
                "[\"LIST_QUERY\",\"RECORD_CREATE\"]",
                "{\"visible\":[\"title\",\"category\",\"lineId\"],"
                        + "\"writable\":[\"title\",\"category\",\"lineId\"]}",
                "{\"allowed\":[]}", "[]",
                "{\"showSearch\":true,\"showPagination\":true,"
                        + "\"showToolbar\":true,\"pageSize\":20,"
                        + "\"heightMode\":\"AUTO\"}",
                "{\"resolved\":{\"defaultFormId\":\"form-1\"},"
                        + "\"entryModes\":[\"LIST\",\"CREATE\"]}",
                "张三", "zh-CN", "light");
        formPort.snapshot = optionForm();
        EmbedRuntimeOptionQueryRequest request = new EmbedRuntimeOptionQueryRequest();
        request.setMode("CREATE");
        request.setDependencies(Map.of("title", "泵站"));

        facade.queryOptions("category", request);

        assertEquals(EmbedRuntimeFormPort.RuntimeMode.CREATE,
                formPort.resolveQuery.mode());
        assertEquals("form-release-4",
                formPort.resolveQuery.target().formReleaseId());
    }

    @Test
    void writableLookupWithoutPinnedCandidateSourceFailsClosed() {
        EmbedContextHolder.clear();
        EmbedContextHolder.set(session(
                "CREATE", null, Set.of("RECORD_CREATE")));
        release = release(
                "CREATE", "[\"RECORD_CREATE\"]",
                "{\"visible\":[\"assignee\"],\"writable\":[\"assignee\"]}",
                "{\"allowed\":[\"save\"]}", "[]");
        formPort.snapshot = new EmbedRuntimeFormPort.FormSnapshot(
                "新建工单", "vertical",
                List.of(field("assignee", "REFERENCE", true, true)),
                List.of(new EmbedRuntimeFormPort.Action(
                        "save", "保存", true, true, null)));

        EmbedException error = assertThrows(
                EmbedException.class, () -> facade.form("CREATE", null));

        assertEquals(503, error.getStatus());
        assertEquals("EMBED_RUNTIME_UNAVAILABLE", error.getErrorCode().name());
    }

    @Test
    void lookupQueryIsHardClosedBeforeReleaseResolutionOrPortCall() {
        EmbedRuntimeLookupQueryRequest request = new EmbedRuntimeLookupQueryRequest();
        request.setMode("CREATE");
        request.setFilters(Map.of("status", "ACTIVE"));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> facade.queryLookups("lineId", request));

        assertEquals(403, error.getStatus());
        assertEquals("EMBED_OPERATION_NOT_ALLOWED", error.getErrorCode().name());
        assertEquals(0, formPort.resolveCount);
        assertNull(formPort.lookupQuery);
    }

    @Test
    void unknownModeAndCorruptRecordValueFailClosed() {
        assertEquals(400, assertThrows(
                EmbedException.class,
                () -> facade.form("APPROVE", null)).getStatus());

        formPort.record = Optional.of(new EmbedRuntimeFormPort.RecordSnapshot(
                "record-1", null,
                Map.of("title", Map.of("internal", "object")), null, null));
        EmbedException corrupt = assertThrows(
                EmbedException.class,
                () -> facade.form("VIEW", null));
        assertEquals(503, corrupt.getStatus());
        assertEquals("EMBED_RUNTIME_UNAVAILABLE", corrupt.getErrorCode().name());
    }

    private void useCreateSession() {
        EmbedContextHolder.clear();
        EmbedContextHolder.set(session(
                "CREATE", null, Set.of("RECORD_CREATE")));
        release = release(
                "CREATE", "[\"RECORD_CREATE\"]",
                "{\"visible\":[\"title\",\"category\",\"lineId\"],"
                        + "\"writable\":[\"title\",\"category\",\"lineId\"]}",
                "{\"allowed\":[]}", "[]");
    }

    private EmbedRuntimeFormPort.FormSnapshot optionForm() {
        return new EmbedRuntimeFormPort.FormSnapshot(
                "新建工单", "vertical",
                List.of(
                        field("title", "TEXT", true, true),
                        new EmbedRuntimeFormPort.Field(
                                "category", "类型", "SELECT", true,
                                false, false, true, true, null, Map.of(),
                                List.of(), true,
                                List.of(
                                        new EmbedRuntimeFormPort.DependencyRule(
                                                "title", EmbedRuntimeFormPort.PolicySource.CLIENT_WRITABLE),
                                        new EmbedRuntimeFormPort.DependencyRule(
                                                "supplierId", EmbedRuntimeFormPort.PolicySource.CONTEXT)),
                                false, List.of(), List.of(), 24),
                        field("lineId", "TEXT", true, true)),
                List.of());
    }

    private static EmbedRuntimeFormPort.Field field(
            String code, String type, boolean visible, boolean editable) {
        return new EmbedRuntimeFormPort.Field(
                code, code, type, "title".equals(code),
                false, false, visible, editable, null, Map.of(),
                List.of(), false, List.of(), false,
                List.of(), List.of(), 24);
    }

    private static AuthenticatedEmbedSession session(
            String entryMode,
            String recordId,
            Set<String> capabilities) {
        return new AuthenticatedEmbedSession(
                "ems-1", "app-1", "grant-1", "view-1", "release-1",
                "user-1", "zhangsan", "https://partner.example", "channel-1",
                entryMode, recordId, Map.of("supplierId", "S-10086"),
                capabilities, Instant.parse("2026-08-27T09:05:00Z"),
                Instant.parse("2026-08-27T09:30:00Z"));
    }

    private static EmbedRuntimeReleaseSnapshot release(
            String entryMode,
            String capabilities,
            String fieldPolicy,
            String actionPolicy,
            String contextBindings) {
        return new EmbedRuntimeReleaseSnapshot(
                "release-1", "view-1", "work-order-form", "工单表单", 4,
                "FORM", "work_order", null, null, null,
                "form-release-4", 4,
                capabilities, fieldPolicy, actionPolicy, contextBindings,
                "{\"showSearch\":false,\"showPagination\":false,"
                        + "\"showToolbar\":false,\"pageSize\":20,"
                        + "\"heightMode\":\"AUTO\"}",
                "{\"resolved\":{\"defaultFormId\":\"form-1\"},"
                        + "\"entryModes\":[\"" + entryMode + "\"]}",
                "张三", "zh-CN", "light");
    }

    private static final class CapturingFormPort implements EmbedRuntimeFormPort {
        FormSnapshot snapshot;
        Optional<RecordSnapshot> record = Optional.empty();
        OptionPage optionPage = new OptionPage(List.of(), false, 1, 50);
        LookupPage lookupPage = new LookupPage(List.of(), false, 1, 20);
        java.util.function.Function<ResolveQuery, FormSnapshot> snapshotResolver;
        int resolveCount;
        RecordQuery recordQuery;
        ResolveQuery resolveQuery;
        OptionQuery optionQuery;
        LookupQuery lookupQuery;

        @Override
        public FormSnapshot resolveForm(ResolveQuery query) {
            resolveCount++;
            this.resolveQuery = query;
            return snapshotResolver == null ? snapshot : snapshotResolver.apply(query);
        }

        @Override
        public Optional<RecordSnapshot> findRecord(RecordQuery query) {
            this.recordQuery = query;
            return record;
        }

        @Override
        public OptionPage queryOptions(OptionQuery query) {
            this.optionQuery = query;
            return optionPage;
        }

        @Override
        public LookupPage queryLookups(LookupQuery query) {
            this.lookupQuery = query;
            return lookupPage;
        }
    }
}
