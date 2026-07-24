package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.BusinessConflictException;
import com.workflow.common.BusinessForbiddenException;
import com.workflow.common.PageResult;
import com.workflow.common.UserContext;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.contracts.integration.IntegrationConnector;
import com.workflow.contracts.integration.IntegrationRequest;
import com.workflow.contracts.integration.IntegrationResult;
import com.workflow.contracts.ui.UiDataSourceProvider;
import com.workflow.dto.EntityDataDTO;
import com.workflow.dto.UiDataSourceExecuteRequest;
import com.workflow.dto.UiDataSourceSaveRequest;
import com.workflow.entity.SysUser;
import com.workflow.entity.UiDataSourceDefinition;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.UiDataSourceDefinitionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UI 数据源服务测试。
 *
 * <p>被测对象：{@link UiDataSourceService}，覆盖数据源保存的 URL/schema 校验、执行前的必填输入与类型校验、
 * provider 输出类型校验、权限计划不可构建/拒绝时不触达数据层、实体查询分页 JSON 校验、
 * 缓存按权限计划/发布版本/表单发布/列表发布隔离、集成连接器仅接收服务端可信安全上下文、
 * 预览走草稿授权而非发布授权等场景。
 */
class UiDataSourceServiceTest {

    /** 设置当前用户上下文 */
    @BeforeEach
    void setCurrentUser() {
        UserContext.setCurrentUser("user-1", "tester");
    }

    /** 清理当前用户上下文，避免用例间污染 */
    @AfterEach
    void clearCurrentUser() {
        UserContext.clear();
    }

    /** 测试拒绝任意 URL 配置：验证配置含 url 字段时保存抛出 IllegalArgumentException */
    @Test
    void rejectsArbitraryUrlConfiguration() {
        UiDataSourceSaveRequest request = saveRequest();
        request.setConfig(Map.of(
                "url",
                "https://example.invalid/data"));

        assertThrows(
                IllegalArgumentException.class,
                () -> context(List.of()).service().save(request));
    }

    /** 测试保存时拒绝畸形 schema：验证 required 非字符串数组时抛出 IllegalArgumentException */
    @Test
    void rejectsMalformedSchemaWhenSaving() {
        UiDataSourceSaveRequest request = saveRequest();
        request.setInputSchema(Map.of(
                "type",
                "object",
                "required",
                "customerId"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context(List.of()).service().save(request));

        assertTrue(exception.getMessage().contains(
                "required 必须为字符串数组"));
    }

    /** 测试执行前拒绝缺失必填映射输入：验证缺少 customerId 时抛出 IllegalArgumentException */
    @Test
    void rejectsMissingRequiredMappedInputBeforeExecution() {
        TestContext context = context(List.of());
        UiDataSourceDefinition definition = definition(
                context.codec(),
                "STATIC_OPTIONS",
                null,
                Map.of(
                        "type", "object",
                        "required", List.of("customerId"),
                        "properties", Map.of(
                                "customerId",
                                Map.of("type", "string"))),
                Map.of(),
                Map.of());
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().execute(
                        "source-1",
                        request(Map.of(), null)));

        assertTrue(exception.getMessage().contains(
                "$.customerId 为必填字段"));
    }

    /** 测试拒绝嵌套输入类型不匹配：验证数组元素字段类型不符时抛出 IllegalArgumentException */
    @Test
    void rejectsNestedInputTypeMismatch() {
        TestContext context = context(List.of());
        UiDataSourceDefinition definition = definition(
                context.codec(),
                "STATIC_OPTIONS",
                null,
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "rows", Map.of(
                                        "type", "array",
                                        "items", Map.of(
                                                "type", "object",
                                                "required", List.of("quantity"),
                                                "properties", Map.of(
                                                        "quantity",
                                                        Map.of("type", "integer")))))),
                Map.of(),
                Map.of());
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().execute(
                        "source-1",
                        request(
                                Map.of(
                                        "rows",
                                        List.of(Map.of(
                                                "quantity",
                                                "not-a-number"))),
                                null)));

        assertTrue(exception.getMessage().contains(
                "$.rows[0].quantity 类型应为 integer"));
    }

    /** 测试拒绝 provider 输出类型不匹配：验证 total 字段类型不符时抛出 IllegalArgumentException */
    @Test
    void rejectsProviderOutputTypeMismatch() {
        UiDataSourceProvider provider = provider(
                new AtomicInteger(),
                Map.of("total", "wrong-type"));
        TestContext context = context(List.of(provider));
        authorize(context, plan("owner_id = 'user-1'", 7));
        UiDataSourceDefinition definition = definition(
                context.codec(),
                "REGISTERED_PROVIDER",
                "safe-provider",
                Map.of(),
                Map.of(
                        "type", "object",
                        "required", List.of("total"),
                        "properties", Map.of(
                                "total",
                                Map.of("type", "number"))),
                Map.of());
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().execute(
                        "source-1",
                        request(Map.of(), null)));

        assertTrue(exception.getMessage().contains(
                "$.total 类型应为 number"));
    }

    /** 测试权限计划无法构建时拒绝执行：验证抛出业务冲突异常且不触达动态数据服务 */
    @Test
    void rejectsExecutionWhenPermissionPlanCannotBeBuilt() {
        TestContext context = context(List.of());
        when(context.executionAccessService().authorizePublished(
                any(),
                any()))
                .thenThrow(new BusinessConflictException(
                        "UI_DATA_SOURCE_PERMISSION_PLAN_UNAVAILABLE",
                        "数据权限引擎未返回可验证的权限计划"));
        UiDataSourceDefinition definition = definition(
                context.codec(),
                "ENTITY_QUERY",
                null,
                Map.of(),
                Map.of(),
                Map.of(
                        "entityCode", "expense",
                        "listKey", "default"));
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> context.service().execute(
                        "source-1",
                        request(Map.of("filters", Map.of()), null)));

        assertEquals(
                "UI_DATA_SOURCE_PERMISSION_PLAN_UNAVAILABLE",
                exception.getErrorCode());
        verifyNoInteractions(context.dynamicService());
    }

    /** 测试拒绝的实体查询不触达数据库：验证权限拒绝时返回空分页且不与动态数据服务交互 */
    @Test
    void deniedEntityQueryDoesNotReachDatabase() {
        TestContext context = context(List.of());
        authorize(context, denyPlan());
        UiDataSourceDefinition definition = definition(
                context.codec(),
                "ENTITY_QUERY",
                null,
                Map.of(),
                Map.of(),
                Map.of(
                        "entityCode", "expense",
                        "listKey", "default"));
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);

        Object result = context.service().execute(
                "source-1",
                request(Map.of("filters", Map.of()), null));

        PageResult<?> page = assertInstanceOf(
                PageResult.class,
                result);
        assertEquals(0, page.getTotal());
        verifyNoInteractions(context.dynamicService());
    }

    /** 测试校验实体查询分页为 JSON 对象：验证按权限计划查询并返回分页结果 */
    @Test
    void validatesEntityQueryPageAsJsonObject() {
        TestContext context = context(List.of());
        DataScopePlan plan = plan("owner_id = 'user-1'", 7);
        authorize(context, plan);
        UiDataSourceDefinition definition = definition(
                context.codec(),
                "ENTITY_QUERY",
                null,
                Map.of(),
                Map.of(
                        "type", "object",
                        "required", List.of(
                                "records",
                                "total",
                                "pageNum",
                                "pageSize"),
                        "properties", Map.of(
                                "records", Map.of(
                                        "type", "array",
                                        "items", Map.of("type", "object")),
                                "total", Map.of("type", "integer"),
                                "pageNum", Map.of("type", "integer"),
                                "pageSize", Map.of("type", "integer"))),
                Map.of(
                        "entityCode", "expense",
                        "listKey", "default"));
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);
        PageResult<EntityDataDTO> page =
                new PageResult<>(List.of(), 0, 1, 20);
        when(context.dynamicService().findPageWithDataScopePlan(
                eq("expense"),
                anyMap(),
                eq(1L),
                eq(20L),
                eq(plan)))
                .thenReturn(page);

        Object result = context.service().execute(
                "source-1",
                request(Map.of("filters", Map.of()), null));

        assertEquals(page, result);
    }

    /** 测试拒绝的 provider 不执行：验证权限拒绝时抛出业务禁止异常且 provider 未被调用 */
    @Test
    void deniedProviderDoesNotExecute() {
        AtomicInteger calls = new AtomicInteger();
        UiDataSourceProvider provider = provider(
                calls,
                Map.of("value", "secret"));
        TestContext context = context(List.of(provider));
        authorize(context, denyPlan());
        UiDataSourceDefinition definition = definition(
                context.codec(),
                "REGISTERED_PROVIDER",
                "safe-provider",
                Map.of(),
                Map.of(),
                Map.of());
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().execute(
                        "source-1",
                        request(Map.of(), null)));

        assertEquals(
                "UI_DATA_SOURCE_DATA_SCOPE_DENIED",
                exception.getErrorCode());
        assertEquals(0, calls.get());
        verify(provider, never()).execute(
                any(),
                any(),
                anyMap(),
                anyMap());
    }

    /** 测试权限计划变化时缓存隔离：验证两次不同计划各执行一次 provider */
    @Test
    void isolatesCacheWhenPermissionPlanChanges() {
        AtomicInteger calls = new AtomicInteger();
        UiDataSourceProvider provider = provider(
                calls,
                Map.of("value", "ok"));
        TestContext context = context(List.of(provider));
        when(context.executionAccessService().authorizePublished(
                any(),
                any()))
                .thenReturn(
                        authorization(
                                context.user(),
                                plan("owner_id = 'user-1'", 7),
                                "release-1"),
                        authorization(
                                context.user(),
                                plan("department_id = 'dept-2'", 7),
                                "release-1"));
        UiDataSourceDefinition definition = cachedProvider(
                context.codec());
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);
        UiDataSourceExecuteRequest request = request(Map.of(), null);

        context.service().execute("source-1", request);
        context.service().execute("source-1", request);

        assertEquals(2, calls.get());
    }

    /** 测试权限发布版本变化时缓存隔离：验证发布版本不同时各执行一次 provider */
    @Test
    void isolatesCacheWhenPermissionReleaseVersionChanges() {
        AtomicInteger calls = new AtomicInteger();
        UiDataSourceProvider provider = provider(
                calls,
                Map.of("value", "ok"));
        TestContext context = context(List.of(provider));
        when(context.executionAccessService().authorizePublished(
                any(),
                any()))
                .thenReturn(
                        authorization(
                                context.user(),
                                plan("owner_id = 'user-1'", 7),
                                "release-1"),
                        authorization(
                                context.user(),
                                plan("owner_id = 'user-1'", 8),
                                "release-1"));
        when(context.mapper().selectById("source-1"))
                .thenReturn(cachedProvider(context.codec()));
        UiDataSourceExecuteRequest request = request(Map.of(), null);

        context.service().execute("source-1", request);
        context.service().execute("source-1", request);

        assertEquals(2, calls.get());
    }

    /** 测试表单发布变化时缓存隔离：验证表单 releaseId 不同时各执行一次 provider */
    @Test
    void isolatesCacheWhenFormReleaseChanges() {
        AtomicInteger calls = new AtomicInteger();
        UiDataSourceProvider provider = provider(
                calls,
                Map.of("value", "ok"));
        TestContext context = context(List.of(provider));
        when(context.executionAccessService().authorizePublished(
                any(),
                any()))
                .thenReturn(
                        authorization(
                                context.user(),
                                plan("owner_id = 'user-1'", 7),
                                "form-release-1"),
                        authorization(
                                context.user(),
                                plan("owner_id = 'user-1'", 7),
                                "form-release-2"));
        when(context.mapper().selectById("source-1"))
                .thenReturn(cachedProvider(context.codec()));
        UiDataSourceExecuteRequest request = request(
                Map.of(),
                Map.of("formId", "form-1"));

        context.service().execute("source-1", request);
        context.service().execute("source-1", request);

        assertEquals(2, calls.get());
    }

    /** 测试列表发布变化时缓存隔离：验证列表 releaseId 不同时各执行一次 provider */
    @Test
    void isolatesCacheWhenListReleaseChanges() {
        AtomicInteger calls = new AtomicInteger();
        UiDataSourceProvider provider = provider(
                calls,
                Map.of("value", "ok"));
        TestContext context = context(List.of(provider));
        when(context.executionAccessService().authorizePublished(
                any(),
                any()))
                .thenReturn(
                        authorization(
                                context.user(),
                                plan("owner_id = 'user-1'", 7),
                                "list-release-1"),
                        authorization(
                                context.user(),
                                plan("owner_id = 'user-1'", 7),
                                "list-release-2"));
        when(context.mapper().selectById("source-1"))
                .thenReturn(cachedProvider(context.codec()));
        UiDataSourceExecuteRequest request = request(Map.of(), null);

        context.service().execute("source-1", request);
        context.service().execute("source-1", request);

        assertEquals(2, calls.get());
    }

    /** 测试集成连接器仅接收服务端可信安全上下文：验证幂等键、参数、用户、租户、release、权限摘要符合预期且不含 SQL 片段 */
    @Test
    void connectorReceivesOnlyServerTrustedSecurityContext() {
        IntegrationConnector connector =
                mock(IntegrationConnector.class);
        when(connector.code()).thenReturn("safe-connector");
        when(connector.execute(any()))
                .thenReturn(IntegrationResult.builder()
                        .success(true)
                        .data(Map.of("ok", true))
                        .build());
        TestContext context = context(
                List.of(),
                List.of(connector));
        context.user().setOrgId("org-1");
        context.user().setDeptId("dept-1");
        DataScopePlan plan =
                plan("owner_id = 'user-1'", 9);
        authorize(context, plan);
        UiDataSourceDefinition definition = definition(
                context.codec(),
                "INTEGRATION_CONNECTOR",
                "safe-connector",
                Map.of(),
                Map.of(),
                Map.of("operation", "sync-order"));
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);
        UiDataSourceExecuteRequest request = request(
                Map.of("orderId", "order-1"),
                Map.of("formId", "form-1"));

        Object result = context.service().execute(
                "source-1",
                request);

        assertEquals(Map.of("ok", true), result);
        ArgumentCaptor<IntegrationRequest> captor =
                ArgumentCaptor.forClass(
                        IntegrationRequest.class);
        verify(connector).execute(captor.capture());
        IntegrationRequest integrationRequest =
                captor.getValue();
        assertTrue(integrationRequest.getIdempotencyKey()
                .startsWith("ui-ds-"));
        assertEquals(
                Map.of("orderId", "order-1"),
                integrationRequest.getParameters());
        assertEquals(
                "user-1",
                integrationRequest.getRuntimeContext().userId());
        assertEquals(
                "org-1",
                integrationRequest.getRuntimeContext().tenantId());
        assertEquals(
                "release-1",
                integrationRequest.getRuntimeContext().releaseId());
        assertEquals(
                plan,
                integrationRequest.getDataScopePlan());
        assertEquals(
                9,
                integrationRequest.getPermissionSummary()
                        .get("releaseVersion"));
        assertTrue(!integrationRequest.getPermissionSummary()
                .containsKey("sqlFragment"));
    }

    /** 测试预览走草稿授权而非发布授权：验证调用 authorizePreview 且不调用 authorizePublished */
    @Test
    void previewUsesDraftAuthorizationInsteadOfPublishedAuthorization() {
        TestContext context = context(List.of());
        UiDataSourceDefinition definition = definition(
                context.codec(),
                "STATIC_OPTIONS",
                null,
                Map.of(),
                Map.of(),
                Map.of("options", List.of("A")));
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);
        when(context.executionAccessService().authorizePreview(
                any(),
                any()))
                .thenReturn(new UiDataSourceExecutionAuthorization(
                        true,
                        "FORM",
                        "form-1",
                        null,
                        null,
                        "$.draft.form[0].dataSourceBindings.FIELD_OPTIONS",
                        "FIELD_OPTIONS",
                        "entity-1",
                        "expense",
                        null,
                        context.user(),
                        plan("1=1", 9),
                        Map.of(),
                        null));
        UiDataSourceExecuteRequest request =
                request(Map.of(), Map.of("formId", "form-1"));
        request.setUsage("FIELD_OPTIONS");

        Object result = context.service().preview(
                "source-1",
                request);

        assertEquals(List.of("A"), result);
        verify(context.executionAccessService())
                .authorizePreview(eq(definition), eq(request));
        verify(context.executionAccessService(), never())
                .authorizePublished(any(), any());
    }

    /** 构造基础数据源保存请求 */
    private UiDataSourceSaveRequest saveRequest() {
        UiDataSourceSaveRequest request = new UiDataSourceSaveRequest();
        request.setSourceCode("safe_source");
        request.setSourceName("安全数据源");
        request.setSourceType("STATIC_OPTIONS");
        request.setScopeType("GLOBAL");
        return request;
    }

    /** 构造带输入与上下文的数据源执行请求 */
    private UiDataSourceExecuteRequest request(
            Map<String, Object> input,
            Map<String, Object> context) {
        UiDataSourceExecuteRequest request =
                new UiDataSourceExecuteRequest();
        request.setUsage("LIST_QUERY");
        request.setEntityCode("expense");
        request.setListKey("default");
        request.setInput(input);
        request.setContext(context);
        request.setPageNum(1);
        request.setPageSize(20);
        return request;
    }

    /** 构造启用缓存策略的注册 provider 数据源定义 */
    private UiDataSourceDefinition cachedProvider(
            JsonDocumentCodec codec) {
        return definition(
                codec,
                "REGISTERED_PROVIDER",
                "safe-provider",
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "cacheSeconds", 60,
                        "timeoutMs", 3000,
                        "failurePolicy", "FAIL"));
    }

    /** 构造不含执行策略的数据源定义（重载，默认空执行策略） */
    private UiDataSourceDefinition definition(
            JsonDocumentCodec codec,
            String sourceType,
            String providerCode,
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema,
            Map<String, Object> config) {
        return definition(
                codec,
                sourceType,
                providerCode,
                inputSchema,
                outputSchema,
                config,
                Map.of());
    }

    /** 构造含执行策略的完整数据源定义 */
    private UiDataSourceDefinition definition(
            JsonDocumentCodec codec,
            String sourceType,
            String providerCode,
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema,
            Map<String, Object> config,
            Map<String, Object> executionPolicy) {
        UiDataSourceDefinition definition =
                new UiDataSourceDefinition();
        definition.setId("source-1");
        definition.setSourceType(sourceType);
        definition.setProviderCode(providerCode);
        definition.setScopeType("GLOBAL");
        definition.setRevision(3);
        definition.setEnabled(true);
        definition.setConfigDocument(document(
                codec,
                config,
                "测试数据源配置"));
        definition.setInputSchemaDocument(document(
                codec,
                inputSchema,
                "测试输入Schema"));
        definition.setOutputSchemaDocument(document(
                codec,
                outputSchema,
                "测试输出Schema"));
        definition.setExecutionPolicyDocument(document(
                codec,
                executionPolicy,
                "测试执行策略"));
        return definition;
    }

    /** 将 Map 序列化为 JSON 文档，空值返回 null */
    private String document(
            JsonDocumentCodec codec,
            Map<String, Object> value,
            String label) {
        return value == null || value.isEmpty()
                ? null : codec.write(value, label);
    }

    /** 构造带调用计数与固定返回值的 Mock provider */
    private UiDataSourceProvider provider(
            AtomicInteger calls,
            Object result) {
        UiDataSourceProvider provider =
                mock(UiDataSourceProvider.class);
        when(provider.getCode()).thenReturn("safe-provider");
        when(provider.execute(
                any(),
                any(),
                anyMap(),
                anyMap()))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    return result;
                });
        return provider;
    }

    /** 构造指定 SQL 与发布版本的权限计划 */
    private DataScopePlan plan(
            String sql,
            int releaseVersion) {
        return new DataScopePlan(
                true,
                sql,
                Map.of(),
                List.of(),
                List.of("test-rule"),
                "test",
                releaseVersion);
    }

    /** 构造拒绝访问的权限计划 */
    private DataScopePlan denyPlan() {
        return new DataScopePlan(
                false,
                "1=0",
                Map.of(),
                List.of(),
                List.of("deny-rule"),
                "denied",
                7);
    }

    /** 预置发布授权 Mock，返回带指定计划的授权对象 */
    private void authorize(
            TestContext context,
            DataScopePlan plan) {
        when(context.executionAccessService().authorizePublished(
                any(),
                any()))
                .thenReturn(authorization(
                        context.user(),
                        plan,
                        "release-1"));
    }

    /** 构造数据源执行授权对象，含用户、权限计划与 releaseId */
    private static UiDataSourceExecutionAuthorization authorization(
            SysUser user,
            DataScopePlan plan,
            String releaseId) {
        return new UiDataSourceExecutionAuthorization(
                false,
                "LIST",
                "list-1",
                releaseId,
                3,
                "$.release.list.queryDataSourceId",
                "LIST_QUERY",
                "entity-1",
                "expense",
                "default",
                user,
                plan,
                Map.of(),
                null);
    }

    /** 装配不含集成连接器的测试上下文（重载） */
    private TestContext context(
            List<UiDataSourceProvider> providers) {
        return context(providers, List.of());
    }

    /** 装配含 provider 与集成连接器的完整测试上下文 */
    private TestContext context(
            List<UiDataSourceProvider> providers,
            List<IntegrationConnector> connectors) {
        UiDataSourceDefinitionMapper mapper =
                mock(UiDataSourceDefinitionMapper.class);
        EntityFormMapper formMapper =
                mock(EntityFormMapper.class);
        EntityListConfigMapper listMapper =
                mock(EntityListConfigMapper.class);
        EntityDataDynamicService dynamicService =
                mock(EntityDataDynamicService.class);
        UiDataSourceExecutionAccessService executionAccessService =
                mock(UiDataSourceExecutionAccessService.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(
                        new ObjectMapper().findAndRegisterModules());
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setUsername("tester");
        user.setStatus(SysUser.Status.ENABLED.getValue());
        UiDataSourceService service = new UiDataSourceService(
                mapper,
                formMapper,
                listMapper,
                mock(EntityDefinitionAccessPolicy.class),
                dynamicService,
                mock(SysDictItemService.class),
                executionAccessService,
                providers,
                connectors,
                codec,
                new SimpleAsyncTaskExecutor(
                        "ui-data-source-test-"));
        return new TestContext(
                service,
                mapper,
                dynamicService,
                executionAccessService,
                codec,
                user);
    }

    /** 测试上下文记录，聚合被测服务与各 Mock 依赖 */
    private record TestContext(
            UiDataSourceService service,
            UiDataSourceDefinitionMapper mapper,
            EntityDataDynamicService dynamicService,
            UiDataSourceExecutionAccessService executionAccessService,
            JsonDocumentCodec codec,
            SysUser user) {
    }
}
