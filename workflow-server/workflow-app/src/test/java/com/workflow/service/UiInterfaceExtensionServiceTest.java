package com.workflow.service;

import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.ui.application.validation.UiExtensionDefinitionValidator;
import com.workflow.entity.ui.application.UiDataSourceExecutionAccessService;
import com.workflow.entity.ui.application.model.UiDataSourceExecutionAuthorization;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import com.workflow.entity.ui.application.UiInvocationContextFactory;

import com.workflow.admin.dictionary.application.SysDictItemService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.entity.list.model.DataScopePlan;
import com.workflow.contracts.entity.ui.context.CommonInvocationContext;
import com.workflow.contracts.entity.ui.model.EntityDescriptor;
import com.workflow.contracts.entity.ui.context.ListInvocationContext;
import com.workflow.contracts.entity.ui.spi.UiDataSourceProvider;
import com.workflow.contracts.entity.ui.model.UiActionCommandPlan;
import com.workflow.contracts.entity.ui.spi.UiActionCommandPlanProvider;
import com.workflow.contracts.entity.ui.model.UiActionMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;
import com.workflow.entity.ui.api.request.UiExtensionExecuteRequest;
import com.workflow.entity.ui.api.request.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.ui.api.request.UiBoundExtensionExecuteRequest;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UI 接口扩展服务测试。
 *
 * <p>被测对象：{@link UiInterfaceExtensionService}，覆盖接口扩展保存的 URL/schema 校验、执行前的必填输入与类型校验、
 * provider 输出类型校验、权限拒绝时不执行 Provider、
 * 缓存按权限计划/发布版本/表单发布/列表发布隔离、
 * 预览走草稿授权而非发布授权等场景。
 */
class UiInterfaceExtensionServiceTest {

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
        UiExtensionDefinitionSaveRequest request = saveRequest();
        request.setImplementationConfig(Map.of(
                "url",
                "https://example.invalid/data"));

        assertThrows(
                IllegalArgumentException.class,
                () -> context(List.of()).service().save(request));
    }

    @Test
    void boundOperationCannotBypassFormButtonRuntime() {
        UiBoundExtensionExecuteRequest request =
                new UiBoundExtensionExecuteRequest();
        request.setOwnerType("FORM");
        request.setOwnerId("form-1");
        request.setBindingCode("FORM_BUTTON_CLICK");
        request.setTargetType("BUTTON");
        request.setTargetKey("generate-report");
        request.setExtensionId("service-1");

        BusinessForbiddenException error = assertThrows(
                BusinessForbiddenException.class,
                () -> context(List.of()).service()
                        .executeBoundOperation(request));

        assertEquals("UI_EVENT_RUNTIME_REQUIRED", error.getErrorCode());
    }

    /** 历史 pair 只负责恢复接口 ID，执行仍必须通过发布绑定授权。 */
    @Test
    void legacyBoundOperationResolvesPairAndStillRequiresAuthorization() {
        TestContext context = context(List.of());
        UiExtensionDefinition definition = definition(context.codec(), "STATIC_OPTIONS", null,
                Map.of(), Map.of(), Map.of("options", List.of("ok")));
        definition.setLegacyServiceId("legacy-service");
        when(context.mapper().selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(definition);
        UiBoundExtensionExecuteRequest request = new UiBoundExtensionExecuteRequest();
        request.setOwnerType("LIST");
        request.setOwnerId("list-1");
        request.setBindingCode("LIST_QUERY");
        request.setTargetType("OWNER");
        request.setExtensionId("legacy-service");
        request.setLegacyOperationCode("query");
        request.setInput(Map.of("filters", Map.of("deptId", "business-dept")));
        authorize(context, plan("1=1", 7));

        assertEquals(List.of("ok"), context.service().executeBoundOperation(request));
        ArgumentCaptor<UiExtensionExecuteRequest> internal =
                ArgumentCaptor.forClass(UiExtensionExecuteRequest.class);
        verify(context.executionAccessService()).authorizePublished(eq(definition), internal.capture());
        assertEquals("query", internal.getValue().getOperationCode());
        assertEquals(request.getInput(), internal.getValue().getInput());

        when(context.executionAccessService().authorizePublished(any(), any()))
                .thenThrow(new BusinessForbiddenException(
                        "UI_DATA_SOURCE_PUBLISHED_BINDING_REQUIRED", "未绑定该接口"));
        BusinessForbiddenException error = assertThrows(BusinessForbiddenException.class,
                () -> context.service().executeBoundOperation(request));
        assertEquals("UI_DATA_SOURCE_PUBLISHED_BINDING_REQUIRED", error.getErrorCode());
    }

    /** 新接口 ID 已唯一确定操作，历史客户端字段不能更改它的 Provider 路由。 */
    @Test
    void modernBoundOperationIgnoresLegacyOperationClaim() {
        TestContext context = context(List.of());
        UiExtensionDefinition definition = definition(context.codec(), "STATIC_OPTIONS", null,
                Map.of(), Map.of(), Map.of("options", List.of("ok")));
        when(context.mapper().selectById("source-1")).thenReturn(definition);
        authorize(context, plan("1=1", 7));
        UiBoundExtensionExecuteRequest request = new UiBoundExtensionExecuteRequest();
        request.setOwnerType("LIST");
        request.setOwnerId("list-1");
        request.setBindingCode("LIST_QUERY");
        request.setTargetType("OWNER");
        request.setExtensionId("source-1");
        request.setLegacyOperationCode("arbitrary-method");

        assertEquals(List.of("ok"), context.service().executeBoundOperation(request));
        ArgumentCaptor<UiExtensionExecuteRequest> internal =
                ArgumentCaptor.forClass(UiExtensionExecuteRequest.class);
        verify(context.executionAccessService()).authorizePublished(eq(definition), internal.capture());
        assertEquals("query", internal.getValue().getOperationCode());
        verify(context.mapper(), never()).selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    /** 测试保存时拒绝畸形 schema：验证 required 非字符串数组时抛出 IllegalArgumentException */
    @Test
    void rejectsMalformedSchemaWhenSaving() {
        UiExtensionDefinitionSaveRequest request = saveRequest();
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

    /**
     * 当管理端后续修改同一接口服务时，旧宿主必须继续执行发布时
     * 固定的配置，而不是仅比对 revision 后报错。
     */
    @Test
    void pinnedOperationExecutesFrozenDefinitionAfterDraftChanges() {
        UiDataSourceProvider provider = mockProvider();
        when(provider.getCode()).thenReturn("safe-provider");
        when(provider.getVersion()).thenReturn(1);
        when(provider.getArtifactDigest()).thenReturn("a".repeat(64));
        when(provider.execute(any(), any(), anyMap(), anyMap()))
                .thenAnswer(invocation -> ((Map<?, ?>)
                        invocation.getArgument(2)).get("marker"));
        TestContext context = context(List.of(provider));
        UiExtensionDefinition definition = definition(
                context.codec(),
                "REGISTERED_PROVIDER",
                "safe-provider",
                Map.of(),
                Map.of(),
                Map.of("marker", "published"),
                Map.of(
                        "timeoutMs", 3000,
                        "failurePolicy", "FAIL"));
        definition.setSourceCode("expense-query");
        definition.setSourceName("费用查询");
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);
        authorize(context, plan("1=1", 3));

        UiInterfaceExtensionService.PublishedOperationSnapshot pinned =
                context.service().freezeOperation("source-1", "query");
        Map<String, Object> snapshot = context.codec().readObject(
                pinned.document(), "test interface snapshot");
        assertEquals(3, snapshot.get("schemaVersion"));
        assertEquals("INTERFACE", snapshot.get("extensionType"));
        assertEquals("source-1", snapshot.get("extensionId"));
        assertEquals("expense-query", snapshot.get("extensionKey"));
        assertEquals("REGISTERED_PROVIDER",
                snapshot.get("implementationType"));
        assertEquals("query", snapshot.get("providerOperationCode"));
        assertFalse(snapshot.containsKey("serviceId"));
        assertFalse(snapshot.containsKey("sourceCode"));
        assertFalse(snapshot.containsKey("operationCode"));
        definition.setRevision(4);
        definition.setConfigDocument(context.codec().write(
                Map.of("marker", "changed"), "changed definition"));

        Object result = context.service().executePinnedOperation(
                pinned.document(),
                pinned.hash(),
                executeRequest(Map.of(), Map.of()));

        assertEquals("published", result);
    }

    /** schema v2 的原始文档和哈希必须继续按旧字段执行，不能在读取时重写。 */
    @Test
    void pinnedOperationReadsLegacyV2SnapshotWithoutRewritingHash()
            throws Exception {
        TestContext context = context(List.of());
        authorize(context, plan("1=1", 3));
        Map<String, Object> legacy = new java.util.LinkedHashMap<>();
        legacy.put("schemaVersion", 2);
        legacy.put("id", "legacy-interface");
        legacy.put("sourceCode", "legacy-options");
        legacy.put("sourceName", "历史选项");
        legacy.put("sourceType", "STATIC_OPTIONS");
        legacy.put("providerCode", null);
        legacy.put("scopeType", "GLOBAL");
        legacy.put("scopeId", null);
        legacy.put("revision", 2);
        legacy.put("operationCode", "query");
        legacy.put("operationContextType", "LIST");
        legacy.put("operationKind", "READ");
        legacy.put("configDocument", context.codec().canonicalize(
                context.codec().write(Map.of(
                        "options", List.of("legacy")), "legacy config"),
                "legacy config"));
        legacy.put("executionPolicyDocument", "{}");
        legacy.put("inputSchemaDocument", "{}");
        legacy.put("outputSchemaDocument", "{}");
        String document = context.codec().canonicalize(
                context.codec().write(legacy, "legacy snapshot"),
                "legacy snapshot");
        String hash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(document.getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)));

        Object result = context.service().executePinnedOperation(
                document,
                hash,
                executeRequest(Map.of(), Map.of()));

        assertEquals(List.of("legacy"), result);
    }

    /** schema v1 没有 Provider 制品身份，仍须按原始文档和哈希执行。 */
    @Test
    void pinnedOperationReadsLegacyV1SnapshotWithoutRewritingHash()
            throws Exception {
        TestContext context = context(List.of());
        authorize(context, plan("1=1", 3));
        Map<String, Object> legacy = new java.util.LinkedHashMap<>();
        legacy.put("schemaVersion", 1);
        legacy.put("id", "legacy-interface-v1");
        legacy.put("sourceCode", "legacy-options-v1");
        legacy.put("sourceName", "历史选项 v1");
        legacy.put("sourceType", "STATIC_OPTIONS");
        legacy.put("providerCode", null);
        legacy.put("scopeType", "GLOBAL");
        legacy.put("scopeId", null);
        legacy.put("revision", 1);
        legacy.put("operationCode", "query");
        legacy.put("operationContextType", "LIST");
        legacy.put("operationKind", "READ");
        legacy.put("configDocument", context.codec().canonicalize(
                context.codec().write(Map.of(
                        "options", List.of("legacy-v1")), "legacy v1 config"),
                "legacy v1 config"));
        legacy.put("executionPolicyDocument", "{}");
        legacy.put("inputSchemaDocument", "{}");
        legacy.put("outputSchemaDocument", "{}");
        String document = context.codec().canonicalize(
                context.codec().write(legacy, "legacy v1 snapshot"),
                "legacy v1 snapshot");
        String hash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(document.getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)));

        Object result = context.service().executePinnedOperation(
                document,
                hash,
                executeRequest(Map.of(), Map.of()));

        assertEquals(List.of("legacy-v1"), result);
    }

    @Test
    void pinnedOperationRejectsDefinitionHashTampering() {
        TestContext context = context(List.of());
        UiExtensionDefinition definition = definition(
                context.codec(),
                "STATIC_OPTIONS",
                null,
                Map.of(),
                Map.of(),
                Map.of("options", List.of("A")),
                Map.of("failurePolicy", "FAIL"));
        definition.setSourceCode("static-options");
        definition.setSourceName("静态选项");
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);
        UiInterfaceExtensionService.PublishedOperationSnapshot pinned =
                context.service().freezeOperation("source-1", "query");

        assertThrows(
                com.workflow.core.error.BusinessConflictException.class,
                () -> context.service().executePinnedOperation(
                        pinned.document().replace(
                                "static-options", "changed-options"),
                        pinned.hash(),
                        executeRequest(Map.of(), Map.of())));
    }

    /** 同编码、同版本但制品摘要变化时，旧宿主必须 fail-closed。 */
    @Test
    void pinnedOperationRejectsProviderArtifactDrift() {
        AtomicInteger publishedCalls = new AtomicInteger();
        UiDataSourceProvider publishedProvider = provider(
                publishedCalls, "published");
        TestContext publishContext = context(List.of(publishedProvider));
        UiExtensionDefinition definition = definition(
                publishContext.codec(),
                "REGISTERED_PROVIDER",
                "safe-provider",
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("failurePolicy", "FAIL"));
        definition.setSourceCode("artifact-pinned-query");
        definition.setSourceName("制品钉定查询");
        when(publishContext.mapper().selectById("source-1"))
                .thenReturn(definition);
        UiInterfaceExtensionService.PublishedOperationSnapshot pinned =
                publishContext.service().freezeOperation(
                        "source-1", "query");

        UiDataSourceProvider driftedProvider =
                mockProvider();
        when(driftedProvider.getCode()).thenReturn("safe-provider");
        when(driftedProvider.getVersion()).thenReturn(1);
        when(driftedProvider.getArtifactDigest())
                .thenReturn("b".repeat(64));
        TestContext runtimeContext = context(List.of(driftedProvider));
        authorize(runtimeContext, plan("1=1", 3));

        com.workflow.core.error.BusinessConflictException error =
                assertThrows(
                        com.workflow.core.error.BusinessConflictException.class,
                        () -> runtimeContext.service()
                                .executePinnedOperation(
                                        pinned.document(),
                                        pinned.hash(),
                                        executeRequest(Map.of(), Map.of())));

        assertEquals(
                "UI_INTERFACE_PINNED_PROVIDER_MISSING",
                error.getErrorCode());
        verify(driftedProvider, never()).execute(
                any(), any(), anyMap(), anyMap());
        assertEquals(0, publishedCalls.get());
    }

    /** 关联内容真实数据测试必须在同一次定义解析中拒绝 WRITE 操作。 */
    @Test
    void relatedContentPreviewRejectsWriteOperationBeforeAuthorization() {
        TestContext context = context(List.of());
        UiExtensionDefinition definition = definition(
                context.codec(),
                "STATIC_OPTIONS",
                null,
                Map.of(),
                Map.of(),
                Map.of("options", List.of("A")));
        setOperation(definition, context.codec(), "query", "LIST",
                "WRITE", Map.of(), Map.of());
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);

        assertThrows(
                BusinessForbiddenException.class,
                () -> context.service()
                        .previewRelatedContentReadOperation(
                                "source-1",
                                "query",
                                executeRequest(Map.of(), Map.of())));

        verify(context.executionAccessService(), never())
                .authorizeManagementPreview(any(), any());
    }

    @Test
    void pinnedLocalWriteOnlyReturnsTypedCommandPlan() {
        // 用存量 Provider fixture 验证新 SPI Registry 的兼容消费。
        UiActionCommandPlanProvider provider =
                new com.workflow.contracts.entity.ui.spi.UiActionCommandPlanProvider() {
                    @Override
                    public String getCode() {
                        return "controlled-writer";
                    }

                    @Override
                    public String getDisplayName() {
                        return "受控本地写入";
                    }

                    @Override
                    public UiActionCommandPlan plan(
                            com.workflow.contracts.entity.ui.context.UiInvocationContext call,
                            Map<String, Object> configuration,
                            Map<String, Object> input) {
                        return new UiActionCommandPlan(
                                List.of(new UiActionMutationCommand(
                                        "requirement",
                                        "req-1",
                                        EntityMutationOperationType.UPDATE,
                                        Map.of("status", input.get("status")))),
                                Map.of("message", "已生成计划"));
                    }
                };
        TestContext context = context(List.of());
        context.service().setActionCommandPlanProviders(List.of(provider));
        UiExtensionDefinition definition = definition(
                context.codec(),
                "REGISTERED_PROVIDER",
                "controlled-writer",
                Map.of(
                        "type", "object",
                        "required", List.of("status"),
                        "properties", Map.of(
                                "status", Map.of("type", "string"))),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "message", Map.of("type", "string"))),
                Map.of(),
                Map.of("failurePolicy", "FAIL"));
        definition.setSourceCode("controlled-writer");
        setOperation(definition, context.codec(), "batchUpdate", "LIST",
                "WRITE",
                Map.of(
                        "type", "object",
                        "required", List.of("status"),
                        "properties", Map.of(
                                "status", Map.of("type", "string"))),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "message", Map.of("type", "string"))));
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);
        authorize(context, plan("1=1", 3));
        UiInterfaceExtensionService.PublishedOperationSnapshot pinned =
                context.service().freezeActionOperation(
                        "source-1", "batchUpdate");
        UiExtensionExecuteRequest request = executeRequest(
                Map.of("status", "DONE"), Map.of());
        request.setUsage("RELATED_CONTENT_ACTION");

        UiActionCommandPlan result = context.service()
                .planPinnedActionOperation(
                        pinned.document(), pinned.hash(), request);

        assertEquals(1, result.commands().size());
        assertEquals("requirement", result.commands().get(0).entityCode());
        assertEquals("已生成计划", result.result().get("message"));

        UiActionCommandPlanProvider driftedProvider =
                mock(UiActionCommandPlanProvider.class);
        when(driftedProvider.getCode()).thenReturn("controlled-writer");
        when(driftedProvider.getVersion()).thenReturn(provider.getVersion());
        when(driftedProvider.getArtifactDigest())
                .thenReturn("f".repeat(64));
        context.service().setActionCommandPlanProviders(
                List.of(driftedProvider));

        com.workflow.core.error.BusinessConflictException error =
                assertThrows(
                        com.workflow.core.error.BusinessConflictException.class,
                        () -> context.service().planPinnedActionOperation(
                                pinned.document(), pinned.hash(), request));
        assertEquals(
                "UI_INTERFACE_PINNED_PROVIDER_MISSING",
                error.getErrorCode());
        verify(driftedProvider, never()).plan(
                any(), anyMap(), anyMap());
    }

    /** 测试执行前拒绝缺失必填映射输入：验证缺少 customerId 时抛出 IllegalArgumentException */
    @Test
    void rejectsMissingRequiredMappedInputBeforeExecution() {
        TestContext context = context(List.of());
        authorize(context, plan("1=1", 1));
        UiExtensionDefinition definition = definition(
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
        authorize(context, plan("1=1", 1));
        UiExtensionDefinition definition = definition(
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
        UiExtensionDefinition definition = definition(
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

    /** 测试拒绝的 provider 不执行：验证权限拒绝时抛出业务禁止异常且 provider 未被调用 */
    @Test
    void deniedProviderDoesNotExecute() {
        AtomicInteger calls = new AtomicInteger();
        UiDataSourceProvider provider = provider(
                calls,
                Map.of("value", "secret"));
        TestContext context = context(List.of(provider));
        authorize(context, denyPlan());
        UiExtensionDefinition definition = definition(
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

    @Test
    void isolatesCacheForTwoInterfaceExtensionsOnSameRevision() {
        AtomicInteger calls = new AtomicInteger();
        UiDataSourceProvider provider = mockProvider();
        when(provider.getCode()).thenReturn("safe-provider");
        when(provider.getVersion()).thenReturn(1);
        when(provider.getArtifactDigest()).thenReturn("a".repeat(64));
        when(provider.execute(any(), any(), anyMap(), anyMap()))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    return ((Map<?, ?>) invocation.getArgument(2))
                            .get("marker");
                });
        TestContext context = context(List.of(provider));
        authorize(context, plan("1=1", 7));
        UiExtensionDefinition firstDefinition = cachedProvider(
                context.codec());
        firstDefinition.setId("source-first");
        firstDefinition.setExtensionKey("source-first");
        firstDefinition.setConfigDocument(context.codec().write(
                Map.of("marker", "first"), "第一条接口配置"));
        UiExtensionDefinition secondDefinition = cachedProvider(
                context.codec());
        secondDefinition.setId("source-second");
        secondDefinition.setExtensionKey("source-second");
        secondDefinition.setConfigDocument(context.codec().write(
                Map.of("marker", "second"), "第二条接口配置"));
        when(context.mapper().selectById("source-first"))
                .thenReturn(firstDefinition);
        when(context.mapper().selectById("source-second"))
                .thenReturn(secondDefinition);
        UiExtensionExecuteRequest request = request(Map.of(), null);

        Object first = context.service().execute("source-first", request);
        Object second = context.service().execute("source-second", request);

        assertEquals("first", first);
        assertEquals("second", second);
        assertEquals(2, calls.get());
    }

    @Test
    void isolatesPinnedCacheForDifferentFrozenDefinitions() {
        AtomicInteger calls = new AtomicInteger();
        UiDataSourceProvider provider = mockProvider();
        when(provider.getCode()).thenReturn("safe-provider");
        when(provider.getVersion()).thenReturn(1);
        when(provider.getArtifactDigest()).thenReturn("a".repeat(64));
        when(provider.execute(any(), any(), anyMap(), anyMap()))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    return ((Map<?, ?>) invocation.getArgument(2))
                            .get("marker");
                });
        TestContext context = context(List.of(provider));
        authorize(context, plan("1=1", 7));
        UiExtensionDefinition definition = cachedProvider(
                context.codec());
        definition.setSourceCode("cached-source");
        definition.setSourceName("缓存接口");
        definition.setConfigDocument(context.codec().write(
                Map.of("marker", "published-a"),
                "第一份钉版配置"));
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);
        UiInterfaceExtensionService.PublishedOperationSnapshot first =
                context.service().freezeOperation(
                        "source-1", "query");
        definition.setConfigDocument(context.codec().write(
                Map.of("marker", "published-b"),
                "第二份钉版配置"));
        UiInterfaceExtensionService.PublishedOperationSnapshot second =
                context.service().freezeOperation(
                        "source-1", "query");

        Object firstResult = context.service().executePinnedOperation(
                first.document(), first.hash(),
                executeRequest(Map.of(), Map.of()));
        Object secondResult = context.service().executePinnedOperation(
                second.document(), second.hash(),
                executeRequest(Map.of(), Map.of()));

        assertEquals("published-a", firstResult);
        assertEquals("published-b", secondResult);
        assertEquals(2, calls.get());
    }

    @Test
    void isolatesCacheForTrustedFormButtonContext() {
        AtomicInteger calls = new AtomicInteger();
        UiDataSourceProvider provider = provider(
                calls,
                Map.of("value", "ok"));
        TestContext context = context(List.of(provider));
        when(context.executionAccessService().authorizePublished(
                any(), any()))
                .thenReturn(new UiDataSourceExecutionAuthorization(
                        false,
                        "FORM",
                        "form-1",
                        "release-1",
                        3,
                        "$.release.eventBindings[0].steps",
                        "FORM_BUTTON_CLICK",
                        "entity-1",
                        "expense",
                        null,
                        context.user(),
                        plan("1=1", 7),
                        Map.of(),
                        "trusted-seed"));
        UiExtensionDefinition definition = cachedProvider(
                context.codec());
        setOperation(definition, context.codec(), "query", "FORM",
                "READ", Map.of(), Map.of());
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);

        UiExtensionExecuteRequest base = cachedFormButtonRequest(
                "record-1", "approve", "task-1", "process-1");
        context.service().executeOperation("source-1", "query", base);
        context.service().executeOperation(
                "source-1",
                "query",
                cachedFormButtonRequest(
                        "record-1", "approve", "task-1", "process-1"));
        context.service().executeOperation(
                "source-1",
                "query",
                cachedFormButtonRequest(
                        "record-2", "approve", "task-1", "process-1"));
        context.service().executeOperation(
                "source-1",
                "query",
                cachedFormButtonRequest(
                        "record-1", "view", "task-1", "process-1"));
        context.service().executeOperation(
                "source-1",
                "query",
                cachedFormButtonRequest(
                        "record-1", "approve", "task-2", "process-1"));
        context.service().executeOperation(
                "source-1",
                "query",
                cachedFormButtonRequest(
                        "record-1", "approve", "task-1", "process-2"));

        // 只有完全相同的可信上下文命中缓存；四种服务端身份变化均重新执行。
        assertEquals(5, calls.get());
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
        UiExtensionDefinition definition = cachedProvider(
                context.codec());
        when(context.mapper().selectById("source-1"))
                .thenReturn(definition);
        UiExtensionExecuteRequest request = request(Map.of(), null);

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
        UiExtensionExecuteRequest request = request(Map.of(), null);

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
        UiExtensionExecuteRequest request = request(
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
        UiExtensionExecuteRequest request = request(Map.of(), null);

        context.service().execute("source-1", request);
        context.service().execute("source-1", request);

        assertEquals(2, calls.get());
    }

    /** 测试预览走草稿授权而非发布授权：验证调用 authorizePreview 且不调用 authorizePublished */
    @Test
    void previewUsesDraftAuthorizationInsteadOfPublishedAuthorization() {
        TestContext context = context(List.of());
        UiExtensionDefinition definition = definition(
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
        UiExtensionExecuteRequest request =
                request(Map.of(), Map.of("formId", "form-1"));
        request.setUsage("FIELD_OPTIONS");
        setOperation(definition, context.codec(), "query", "FORM",
                "READ", Map.of(), Map.of());

        Object result = context.service().preview(
                "source-1",
                request);

        assertEquals(List.of("A"), result);
        verify(context.executionAccessService())
                .authorizePreview(any(), eq(request));
        verify(context.executionAccessService(), never())
                .authorizePublished(any(), any());
    }

    /** 构造基础数据源保存请求 */
    private UiExtensionDefinitionSaveRequest saveRequest() {
        UiExtensionDefinitionSaveRequest request = new UiExtensionDefinitionSaveRequest();
        request.setExtensionType("INTERFACE");
        request.setExtensionKey("safe_source");
        request.setDisplayName("安全接口");
        request.setImplementationType("STATIC_OPTIONS");
        request.setScopeType("GLOBAL");
        request.setInterfaceKind("READ");
        request.setInterfaceContextType("LIST");
        request.setInputSchema(Map.of());
        request.setOutputSchema(Map.of());
        request.setStatus("ACTIVE");
        return request;
    }

    /** 构造带输入与上下文的数据源执行请求 */
    private UiExtensionExecuteRequest request(
            Map<String, Object> input,
            Map<String, Object> context) {
        UiExtensionExecuteRequest request =
                new UiExtensionExecuteRequest();
        request.setUsage("LIST_QUERY");
        request.setOperationCode("query");
        request.setEntityCode("expense");
        request.setListKey("default");
        request.setInput(input);
        request.setContext(context);
        request.setPageNum(1);
        request.setPageSize(20);
        return request;
    }

    /** 构造关联内容从已发布宿主执行钉版接口操作的请求。 */
    private UiExtensionExecuteRequest executeRequest(
            Map<String, Object> input,
            Map<String, Object> context) {
        UiExtensionExecuteRequest request = request(input, context);
        request.setUsage("RELATED_CONTENT_RESOLVE");
        request.setConfigType("LIST");
        request.setConfigId("list-1");
        request.setReleaseId("release-1");
        request.setReleaseVersion(3);
        return request;
    }

    /** 构造仅强类型可信上下文不同、业务输入相同的表单按钮请求。 */
    private UiExtensionExecuteRequest cachedFormButtonRequest(
            String recordId,
            String mode,
            String taskId,
            String processInstanceId) {
        UiExtensionExecuteRequest request = request(Map.of(), Map.of());
        request.setUsage("FORM_BUTTON_CLICK");
        request.setConfigType("FORM");
        request.setConfigId("form-1");
        request.setReleaseId("release-1");
        request.setReleaseVersion(3);
        request.setServerRecordId(recordId);
        request.setServerFormMode(mode);
        request.setServerTaskId(taskId);
        request.setServerProcessInstanceId(processInstanceId);
        return request;
    }

    /** 构造启用缓存策略的注册 provider 数据源定义 */
    private UiExtensionDefinition cachedProvider(
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
    private UiExtensionDefinition definition(
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
    private UiExtensionDefinition definition(
            JsonDocumentCodec codec,
            String sourceType,
            String providerCode,
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema,
            Map<String, Object> config,
            Map<String, Object> executionPolicy) {
        UiExtensionDefinition definition =
                new UiExtensionDefinition();
        definition.setId("source-1");
        definition.setExtensionType("INTERFACE");
        definition.setExtensionKey("source-1");
        definition.setDisplayName("测试接口");
        definition.setSourceType(sourceType);
        definition.setProviderCode(providerCode);
        definition.setScopeType("GLOBAL");
        definition.setRevision(3);
        definition.setEnabled(true);
        definition.setConfigDocument(document(
                codec,
                config,
                "测试数据源配置"));
        definition.setExecutionPolicyDocument(document(
                codec,
                executionPolicy,
                "测试执行策略"));
        setOperation(definition, codec, "query", "LIST", "READ",
                inputSchema, outputSchema);
        return definition;
    }

    /** 为测试定义填充单一接口的调用契约。 */
    private void setOperation(
            UiExtensionDefinition definition,
            JsonDocumentCodec codec,
            String operationCode,
            String contextType,
            String kind,
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema) {
        definition.setProviderOperationCode(operationCode);
        definition.setInterfaceContextType(contextType);
        definition.setInterfaceKind(kind);
        definition.setInputSchemaDocument(codec.write(
                inputSchema == null ? Map.of() : inputSchema,
                "测试接口输入 Schema"));
        definition.setOutputSchemaDocument(codec.write(
                outputSchema == null ? Map.of() : outputSchema,
                "测试接口输出 Schema"));
    }

    /** 将 Map 序列化为 JSON 文档，空值返回 null */
    private String document(
            JsonDocumentCodec codec,
            Map<String, Object> value,
            String label) {
        return value == null || value.isEmpty()
                ? null : codec.write(value, label);
    }

    private UiDataSourceProvider mockProvider() {
        UiDataSourceProvider provider = mock(UiDataSourceProvider.class);
        when(provider.execute(any(), any(), anyMap(), anyMap(), any())).thenCallRealMethod();
        return provider;
    }

    /** 超时必须中断实际工作，并允许同一个有界执行器继续服务下一次请求。 */
    @Test
    void timedOutProviderIsCancelledAndWorkerCapacityRecovers() throws Exception {
        var pool = new com.workflow.entity.ui.infrastructure.config.UiExtensionExecutionConfiguration()
                .uiExtensionTaskExecutor(1, 1);
        pool.initialize();
        var stopped = new java.util.concurrent.CountDownLatch(1);
        var calls = new AtomicInteger();
        UiDataSourceProvider provider = mockProvider();
        when(provider.getCode()).thenReturn("safe-provider");
        when(provider.getVersion()).thenReturn(1);
        when(provider.getArtifactDigest()).thenReturn("a".repeat(64));
        org.mockito.Mockito.doAnswer(call -> {
            var control = call.getArgument(4, com.workflow.contracts.execution.port.ExecutionControlPort.class);
            assertTrue(control.remainingMillis() <= 200);
            assertEquals("user-1", UserContext.getUserId());
            if (calls.incrementAndGet() == 1) {
                try { new java.util.concurrent.CountDownLatch(1).await(); }
                catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); }
                finally { stopped.countDown(); }
                control.check();
            }
            return Map.of("value", "ok");
        }).when(provider).execute(any(), any(), anyMap(), anyMap(), any());
        TestContext context = context(List.of(provider), pool);
        authorize(context, plan("1=1", 7));
        when(context.mapper().selectById("source-1")).thenReturn(definition(context.codec(),
                "REGISTERED_PROVIDER", "safe-provider", Map.of(), Map.of(), Map.of(),
                Map.of("timeoutMs", 200, "failurePolicy", "FAIL")));
        try {
            assertThrows(RuntimeException.class, () -> context.service().execute("source-1", request(Map.of(), null)));
            assertTrue(stopped.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(Map.of("value", "ok"), context.service().execute("source-1", request(Map.of(), null)));
            assertEquals(2, calls.get());
            assertEquals(0, pool.getQueueSize());
            assertEquals("user-1", UserContext.getUserId());
            pool.submit(() -> {
                org.junit.jupiter.api.Assertions.assertNull(UserContext.getUserId());
                org.junit.jupiter.api.Assertions.assertNull(com.workflow.core.concurrent.ExecutionDeadline.current());
            }).get(2, java.util.concurrent.TimeUnit.SECONDS);
        } finally { pool.shutdown(); }
    }

    /** 已过期的排队任务不能在忙线程释放后补执行，也不能继续占用有限队列。 */
    @Test
    void expiredQueuedProviderNeverRunsAndQueueSlotIsReleased() throws Exception {
        var pool = new com.workflow.entity.ui.infrastructure.config.UiExtensionExecutionConfiguration()
                .uiExtensionTaskExecutor(1, 1);
        pool.initialize();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var unblock = new java.util.concurrent.CountDownLatch(1);
        pool.execute(() -> {
            entered.countDown();
            try { unblock.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        AtomicInteger calls = new AtomicInteger();
        TestContext context = context(List.of(provider(calls, Map.of("ok", true))), pool);
        authorize(context, plan("1=1", 7));
        when(context.mapper().selectById("source-1")).thenReturn(definition(context.codec(),
                "REGISTERED_PROVIDER", "safe-provider", Map.of(), Map.of(), Map.of(),
                Map.of("timeoutMs", 100, "failurePolicy", "FAIL")));
        try {
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertThrows(RuntimeException.class, () -> context.service().execute("source-1", request(Map.of(), null)));
            assertEquals(0, pool.getQueueSize());
            unblock.countDown();
            pool.submit(() -> {}).get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(0, calls.get());
        } finally { unblock.countDown(); pool.shutdown(); }
    }

    /** 构造带调用计数与固定返回值的 Mock provider */
    private UiDataSourceProvider provider(
            AtomicInteger calls,
            Object result) {
        UiDataSourceProvider provider =
                mockProvider();
        when(provider.getCode()).thenReturn("safe-provider");
        when(provider.getVersion()).thenReturn(1);
        when(provider.getArtifactDigest()).thenReturn("a".repeat(64));
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
                "$.release.eventBindings[0].steps",
                "LIST_QUERY",
                "entity-1",
                "expense",
                "default",
                user,
                plan,
                Map.of(),
                null);
    }

    /** 装配测试上下文。 */
    private TestContext context(List<UiDataSourceProvider> providers) {
        return context(providers, new SimpleAsyncTaskExecutor("ui-data-source-test-"));
    }

    private TestContext context(List<UiDataSourceProvider> providers, org.springframework.core.task.TaskExecutor executor) {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        EntityFormMapper formMapper =
                mock(EntityFormMapper.class);
        EntityListConfigMapper listMapper =
                mock(EntityListConfigMapper.class);
        UiDataSourceExecutionAccessService executionAccessService =
                mock(UiDataSourceExecutionAccessService.class);
        UiInvocationContextFactory invocationContextFactory =
                mock(UiInvocationContextFactory.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(
                        new ObjectMapper().findAndRegisterModules());
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setUsername("tester");
        user.setStatus(SysUser.Status.ENABLED.getValue());
        when(invocationContextFactory.create(
                any(),
                any(),
                any()))
                .thenAnswer(invocation -> {
                    UiExtensionDefinition definition =
                            invocation.getArgument(0);
                    UiDataSourceExecutionAuthorization authorization =
                            invocation.getArgument(1);
                    return new ListInvocationContext(
                            new CommonInvocationContext(
                                    definition.getId(),
                                    definition.getOperationCode(),
                                    authorization.usage(),
                                    authorization.configType(),
                                    authorization.configId(),
                                    "OWNER",
                                    null,
                                    authorization.user().getId(),
                                    authorization.user().getUsername(),
                                    null,
                                    authorization.user().getOrgId(),
                                    authorization.user().getDeptId(),
                                    authorization.releaseId(),
                                    authorization.releaseVersion(),
                                    "request-1"),
                            new EntityDescriptor(
                                    authorization.entityId(),
                                    authorization.entityCode(),
                                    "费用",
                                    "DYNAMIC",
                                    authorization.releaseVersion()),
                            authorization.configId(),
                            authorization.listKey(),
                            "默认列表",
                            1,
                            20,
                            null,
                            "PAGE");
                });
        UiInterfaceExtensionService service = new UiInterfaceExtensionService(
                mapper,
                formMapper,
                listMapper,
                mock(EntityDefinitionAccessPolicy.class),
                mock(EntityUiConfigurationPolicy.class),
                mock(SysDictItemService.class),
                executionAccessService,
                invocationContextFactory,
                new UiExtensionDefinitionValidator(codec),
                providers,
                codec,
                executor);
        return new TestContext(
                service,
                mapper,
                executionAccessService,
                codec,
                user);
    }

    /** 测试上下文记录，聚合被测服务与各 Mock 依赖 */
    private record TestContext(
            UiInterfaceExtensionService service,
            UiExtensionDefinitionMapper mapper,
            UiDataSourceExecutionAccessService executionAccessService,
            JsonDocumentCodec codec,
            SysUser user) {
    }
}
