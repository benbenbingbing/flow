package com.workflow.embed.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedNativeActorRuntimePort;
import com.workflow.contracts.embed.EmbedNativeFormRuntimePort;
import com.workflow.contracts.embed.EmbedNativeListRuntimePort;
import com.workflow.contracts.embed.EmbedRuntimeEntityPort;
import com.workflow.embed.api.web.EmbedRuntimeListFilterRequest;
import com.workflow.embed.api.web.EmbedRuntimeListQueryRequest;
import com.workflow.embed.config.EmbedProperties;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedNativeFormTarget;
import com.workflow.embed.domain.EmbedRuntimeReleaseSnapshot;
import com.workflow.embed.security.EmbedContextHolder;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class EmbedRuntimeReadFacadeTest {

    private final CapturingEntityPort entityPort = new CapturingEntityPort();
    private EmbedRuntimeReadFacade facade;

    @BeforeEach
    void setUp() {
        EmbedProperties properties = new EmbedProperties();
        facade = new EmbedRuntimeReadFacade(
                (sessionId, viewId, releaseId) -> release(),
                entityPort,
                properties,
                new ObjectMapper());
        EmbedContextHolder.set(session(Set.of(
                "LIST_QUERY", "SELECTION_RETURN", "RECORD_VIEW")));
    }

    @AfterEach
    void cleanUp() {
        EmbedContextHolder.clear();
    }

    @Test
    void schemaIsStrictExternalProjection() {
        var schema = facade.schema();

        assertEquals(List.of("title"), schema.list().columns().stream()
                .map(value -> value.code()).toList());
        assertEquals(List.of("title"), schema.list().filters().stream()
                .map(value -> value.code()).toList());
        assertEquals(List.of("view"), schema.actions().stream()
                .map(value -> value.key()).toList());
        assertEquals("LOCAL_FORM", schema.actions().get(0).transport());
        assertEquals("SINGLE", schema.list().selection().mode());
        assertEquals("title", schema.list().selection().valueField());
        assertEquals(List.of("title"), schema.list().selection().returnableFields());
        assertFalse(schema.list().pagination().allowTotal());
    }

    @Test
    void queryPinsReleaseAppliesContextAndProjectsFieldsAndActions() throws Exception {
        EmbedRuntimeListQueryRequest request = new EmbedRuntimeListQueryRequest();
        request.setPageNum(2);
        request.setPageSize(20);
        request.setFilters(List.of(valueFilter("title", "pump")));

        var result = facade.query(request);

        assertEquals("list-release-7", entityPort.releaseId);
        assertEquals(7, entityPort.releaseVersion);
        assertEquals(Map.of("title", "pump", "title_op", "LIKE"),
                entityPort.clientFilters);
        assertEquals(Map.of("supplier_id", "S-10086", "supplier_id_op", "EQ"),
                entityPort.contextFilters);
        assertEquals(Map.of("title", "Pump repair"), result.items().get(0).values());
        assertNull(result.items().get(0).recordVersion());
        assertEquals(Set.of("view"), result.items().get(0).actions().keySet());
        assertNull(result.total());
        assertTrue(result.hasMore());
        String serialized = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(result);
        assertFalse(serialized.contains("secret"));
        assertFalse(serialized.contains("supplier_id"));
        assertFalse(serialized.contains("\"total\""));
    }

    @Test
    void omittedPageSizeUsesImmutableViewDefault() {
        facade.query(new EmbedRuntimeListQueryRequest());

        assertEquals(1, entityPort.pageNum);
        assertEquals(25, entityPort.pageSize);
    }

    @Test
    void rejectsFilterNotPublishedInExternalSchema() {
        EmbedRuntimeListQueryRequest request = new EmbedRuntimeListQueryRequest();
        request.setFilters(List.of(valueFilter("secret", "steal")));

        EmbedException error = assertThrows(EmbedException.class, () -> facade.query(request));

        assertEquals(400, error.getStatus());
        assertEquals("INVALID_REQUEST", error.getErrorCode().name());
        assertNull(entityPort.clientFilters);
    }

    @Test
    void mapsEveryPublishedOperatorToFixedInternalConditionKeys() {
        record Mapping(
                String operator,
                EmbedRuntimeListFilterRequest filter,
                Map<String, Object> expected) {
        }
        List<Mapping> mappings = List.of(
                new Mapping("EQ", valueFilter("title", "P-100"),
                        Map.of("title", "P-100", "title_op", "EQ")),
                new Mapping("CONTAINS", valueFilter("title", "pump"),
                        Map.of("title", "pump", "title_op", "LIKE")),
                new Mapping("GT", valueFilter("title", 10),
                        Map.of("title", 10, "title_op", "GT")),
                new Mapping("GTE", valueFilter("title", 10),
                        Map.of("title_start", 10)),
                new Mapping("LT", valueFilter("title", 20),
                        Map.of("title", 20, "title_op", "LT")),
                new Mapping("LTE", valueFilter("title", 20),
                        Map.of("title_end", 20)),
                new Mapping("IN", valuesFilter("title", List.of("A", "B")),
                        Map.of("title", List.of("A", "B"), "title_op", "IN")),
                new Mapping("BETWEEN", rangeFilter("title", 10, 20),
                        Map.of("title_start", 10, "title_end", 20)));

        for (Mapping mapping : mappings) {
            entityPort.queryOperator = mapping.operator();
            EmbedRuntimeListQueryRequest request = new EmbedRuntimeListQueryRequest();
            request.setFilters(List.of(mapping.filter()));

            facade.query(request);

            assertEquals(mapping.expected(), entityPort.clientFilters,
                    "Unexpected internal encoding for " + mapping.operator());
        }
    }

    @Test
    void rejectsInternalSuffixDuplicateAndOperatorValueShapeMismatch() {
        EmbedRuntimeListQueryRequest suffix = new EmbedRuntimeListQueryRequest();
        suffix.setFilters(List.of(valueFilter("title_op", "EQ")));
        assertEquals(400, assertThrows(
                EmbedException.class, () -> facade.query(suffix)).getStatus());

        EmbedRuntimeListQueryRequest duplicate = new EmbedRuntimeListQueryRequest();
        duplicate.setFilters(List.of(
                valueFilter("title", "first"),
                valueFilter("title", "second")));
        assertEquals(400, assertThrows(
                EmbedException.class, () -> facade.query(duplicate)).getStatus());

        entityPort.queryOperator = "BETWEEN";
        EmbedRuntimeListQueryRequest wrongShape = new EmbedRuntimeListQueryRequest();
        wrongShape.setFilters(List.of(valueFilter("title", 10)));
        assertEquals(400, assertThrows(
                EmbedException.class, () -> facade.query(wrongShape)).getStatus());
        assertNull(entityPort.clientFilters);
    }

    @Test
    void bootstrapReturnsOnlyPresentationActorAndStablePolicies() {
        EmbedContextHolder.clear();
        EmbedContextHolder.set(session(Set.of(
                "LIST_QUERY", "RECORD_VIEW", "RECORD_CREATE", "ACTION_EXECUTE", "INTERNAL_PERMISSION")));

        var bootstrap = facade.bootstrap();

        assertEquals("供应商工单", bootstrap.view().name());
        assertEquals("张三", bootstrap.actor().displayName());
        assertEquals("dark", bootstrap.ui().theme());
        assertEquals(25, bootstrap.ui().pageSize());
        assertEquals(100, bootstrap.limits().maxPageSize());
        assertTrue(bootstrap.capabilities().contains("ACTION_EXECUTE"));
        assertFalse(bootstrap.capabilities().contains("INTERNAL_PERMISSION"));
    }

    @Test
    void nativeListBootstrapWithoutDefaultFormReturnsNullFormCoordinates()
            throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        var closure = new com.workflow.contracts.embed
                .EmbedNativeListDependencyClosure(
                1,
                List.of(new com.workflow.contracts.embed
                        .EmbedNativeListDependencyClosure.ListNode(
                        new com.workflow.contracts.embed
                                .EmbedNativeListDependencyClosure.ListCoordinate(
                                "work_order", "supplier_open", "list-1",
                                "list-release-7", 7),
                        true, null, List.of())));
        var configNode = objectMapper.createObjectNode();
        configNode.putArray("entryModes").add("LIST");
        configNode.putObject("resolved");
        var closureNode = objectMapper.valueToTree(closure);
        configNode.set(
                EmbedNativeListDependencyClosureCodec.CONFIG_FIELD,
                closureNode);
        configNode.put(
                EmbedNativeListDependencyClosureCodec.HASH_FIELD,
                EmbedNativeListDependencyClosureCodec.canonicalHash(
                        objectMapper, closureNode));
        EmbedRuntimeReleaseSnapshot release = new EmbedRuntimeReleaseSnapshot(
                "release_1", "view_1", "supplier-work-orders", "供应商工单", 7,
                "LIST", "work_order", "supplier_open", "list-release-7", 7,
                null, null,
                "[\"LIST_QUERY\"]",
                "{\"mode\":\"FLOW_PUBLISHED\",\"returnable\":[]}",
                "{\"allowed\":[]}", "[]",
                "{\"showSearch\":true,\"showPagination\":true,"
                        + "\"showToolbar\":true,\"pageSize\":25,"
                        + "\"heightMode\":\"AUTO\"}",
                objectMapper.writeValueAsString(configNode),
                "张三", "zh-CN", "light");
        EmbedNativeFormTargetResolver resolver = mock(
                EmbedNativeFormTargetResolver.class);
        when(resolver.resolveRoot(any())).thenReturn(
                new EmbedNativeFormTarget(
                        "work_order", null, null, null,
                        "supplier_open", "list-release-7", 7,
                        "LIST", null, null, Map.of(), Map.of(), Map.of()));
        EmbedNativeListRuntimePort listRuntime = mock(
                EmbedNativeListRuntimePort.class);
        when(listRuntime.issueReleaseResolutionToken(any()))
                .thenReturn("elr1.fixed.signature");
        facade = new EmbedRuntimeReadFacade(
                (sessionId, viewId, releaseId) -> release,
                entityPort,
                new EmbedProperties(),
                objectMapper,
                provider(resolver),
                provider((EmbedNativeFormRuntimePort) null),
                provider(listRuntime),
                provider((EmbedNativeActorRuntimePort) null));

        var target = facade.bootstrap().target();

        assertNull(target.formId());
        assertNull(target.formReleaseId());
        assertNull(target.formReleaseVersion());
        assertEquals("elr1.fixed.signature",
                target.listReleaseResolutionToken());
        String serialized = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(target);
        assertFalse(serialized.contains("formReleaseVersion"));
    }

    @Test
    void listOperationsRequireSessionCapability() {
        EmbedContextHolder.clear();
        EmbedContextHolder.set(session(Set.of("RECORD_VIEW")));

        EmbedException error = assertThrows(EmbedException.class, facade::schema);

        assertEquals(403, error.getStatus());
        assertEquals("EMBED_OPERATION_NOT_ALLOWED", error.getErrorCode().name());
    }

    @Test
    void rejectsContextTargetsWhoseEncodedOperatorKeysCollide() {
        EmbedRuntimeReleaseSnapshot release = release();
        EmbedRuntimeReleaseSnapshot colliding = new EmbedRuntimeReleaseSnapshot(
                release.releaseId(), release.viewId(), release.viewKey(), release.viewName(),
                release.revision(), release.surfaceType(), release.entityCode(),
                release.listKey(), release.listReleaseId(), release.listReleaseVersion(),
                release.formReleaseId(), release.formReleaseVersion(),
                release.capabilitiesJson(), release.fieldPolicyJson(),
                release.actionPolicyJson(),
                "[{\"source\":\"supplierId\",\"target\":\"supplier_id_op\","
                        + "\"usage\":\"FIXED_FILTER\"},"
                        + "{\"source\":\"supplierId\",\"target\":\"supplier_id\","
                        + "\"usage\":\"FIXED_FILTER\"}]",
                release.uiConfigJson(), release.configJson(),
                release.actorDisplayName(), release.uiLocale(), release.uiTheme());
        facade = new EmbedRuntimeReadFacade(
                (sessionId, viewId, releaseId) -> colliding,
                entityPort,
                new EmbedProperties(),
                new ObjectMapper());

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> facade.query(new EmbedRuntimeListQueryRequest()));

        assertEquals(503, error.getStatus());
        assertNull(entityPort.contextFilters);
    }

    private static AuthenticatedEmbedSession session(Set<String> capabilities) {
        return new AuthenticatedEmbedSession(
                "ems_1", "app_1", "grant_1", "view_1", "release_1",
                "user_1", "zhangsan", "https://partner.example", "channel_1",
                "LIST", null, Map.of("supplierId", "S-10086"), capabilities,
                Instant.parse("2026-08-27T09:05:00Z"),
                Instant.parse("2026-08-27T09:30:00Z"));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static EmbedRuntimeListFilterRequest valueFilter(String field, Object value) {
        EmbedRuntimeListFilterRequest filter = new EmbedRuntimeListFilterRequest();
        filter.setField(field);
        filter.setValue(value);
        return filter;
    }

    private static EmbedRuntimeListFilterRequest valuesFilter(
            String field,
            List<?> values) {
        EmbedRuntimeListFilterRequest filter = new EmbedRuntimeListFilterRequest();
        filter.setField(field);
        filter.setValues(values.stream().map(value -> (Object) value).toList());
        return filter;
    }

    private static EmbedRuntimeListFilterRequest rangeFilter(
            String field,
            Object start,
            Object end) {
        EmbedRuntimeListFilterRequest.Range range = new EmbedRuntimeListFilterRequest.Range();
        range.setStart(start);
        range.setEnd(end);
        EmbedRuntimeListFilterRequest filter = new EmbedRuntimeListFilterRequest();
        filter.setField(field);
        filter.setRange(range);
        return filter;
    }

    private static EmbedRuntimeReleaseSnapshot release() {
        return new EmbedRuntimeReleaseSnapshot(
                "release_1", "view_1", "supplier-work-orders", "供应商工单", 7,
                "LIST", "work_order", "supplier_open", "list-release-7", 7,
                "form-release-4", 4,
                "[\"LIST_QUERY\",\"SELECTION_RETURN\",\"RECORD_VIEW\",\"ACTION_EXECUTE\","
                        + "\"INTERNAL_PERMISSION\"]",
                "{\"visible\":[\"title\"],\"queryable\":[\"title\"],"
                        + "\"writable\":[],\"returnable\":[\"title\"]}",
                "{\"allowed\":[\"view\",\"delete\"]}",
                "[{\"source\":\"supplierId\",\"target\":\"supplier_id\","
                        + "\"usage\":\"FIXED_FILTER\"}]",
                "{\"showSearch\":true,\"showPagination\":true,\"showToolbar\":true,"
                        + "\"pageSize\":25,\"heightMode\":\"AUTO\"}",
                "{\"queryPolicy\":{\"allowTotal\":false,\"maxPageSize\":100}}",
                "张三", "zh-CN", "dark");
    }

    private static final class CapturingEntityPort implements EmbedRuntimeEntityPort {
        String releaseId;
        int releaseVersion;
        Map<String, Object> clientFilters;
        Map<String, Object> contextFilters;
        int pageNum;
        int pageSize;
        String queryOperator = "CONTAINS";

        @Override
        public ListSchema loadListSchema(
                String entityCode, String listKey, String listReleaseId,
                int listReleaseVersion) {
            return new ListSchema(
                    entityCode, "工单", listKey, "供应商工单",
                    Map.of("mode", "SINGLE", "valueField", "title"),
                    List.of(
                            new Field("title", "标题", "TEXT", 180,
                                    true, true, queryOperator, List.of()),
                            new Field("secret", "秘密", "TEXT", 120,
                                    true, true, "EQ", List.of())),
                    List.of(new Action("create", "新建", "TOOLBAR")),
                    List.of(new Action("view", "查看", "ROW"),
                            new Action("delete", "删除", "ROW")));
        }

        @Override
        public ListPage queryList(
                String entityCode, String listKey, String listReleaseId,
                int listReleaseVersion, int pageNum, int pageSize,
                Map<String, Object> clientFilters,
                Map<String, Object> trustedContextFilters) {
            this.releaseId = listReleaseId;
            this.releaseVersion = listReleaseVersion;
            this.pageNum = pageNum;
            this.pageSize = pageSize;
            this.clientFilters = new LinkedHashMap<>(clientFilters);
            this.contextFilters = new LinkedHashMap<>(trustedContextFilters);
            return new ListPage(
                    List.of(new Row(
                            "record-1",
                            Map.of("title", "Pump repair", "secret", "do-not-leak"),
                            Instant.parse("2026-08-27T08:20:00Z"),
                            Map.of(
                                    "view", new ActionCapability(true, true, null),
                                    "delete", new ActionCapability(true, true, null)))),
                    50,
                    pageNum,
                    pageSize);
        }
    }
}
