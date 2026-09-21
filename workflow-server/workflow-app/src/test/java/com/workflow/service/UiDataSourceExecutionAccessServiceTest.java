package com.workflow.service;

import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import com.workflow.entity.ui.application.UiDataSourceBindingMatcher;
import com.workflow.entity.ui.application.UiDataSourceExecutionAccessService;
import com.workflow.entity.ui.application.UiDataSourceExecutionAuthorization;
import com.workflow.entity.ui.application.EntitySelectionRuntimeService;
import com.workflow.entity.ui.application.UiConfigSnapshotSupport;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.entity.ui.application.UiEventBindingSnapshotService;
import com.workflow.entity.ui.application.UiEventExecutionReceiptService;
import com.workflow.entity.ui.application.UiEventRuntimeService;
import com.workflow.entity.ui.application.UiEventValueMapper;
import com.workflow.entity.ui.application.UiExtensionDefinitionValidator;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import com.workflow.entity.ui.application.UiInvocationContextFactory;
import com.workflow.entity.ui.application.UiReleaseResolutionTokenService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityService;
import com.workflow.entity.form.application.EntityFormActionService;
import com.workflow.entity.form.application.ResolvedEntityFormRelease;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.api.response.UiEventExecutionResult;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigHotfixTargetMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigHotfixTarget;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.entity.ui.spi.UiDataSourceProvider;
import com.workflow.contracts.ui.UiInvocationContext;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.api.request.UiExtensionExecuteRequest;
import com.workflow.entity.permission.api.response.DataPermissionResult;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.api.response.EntityListRuntimeContextDTO;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.permission.application.DataPermissionEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UI 数据源执行访问控制服务测试。
 *
 * <p>被测对象：{@link UiDataSourceExecutionAccessService}，覆盖发布执行需可验证来源、绑定校验、
 * 缺失激活发布拒绝、客户端过期发布声明拒绝、可信上下文防伪造、服务端幂等种子校验、
 * 历史钉版发布可信执行、草稿预览权限、列表直接访问权限、权限计划构建与运行时上下文净化、
 * 数据源作用域不匹配拒绝等场景。
 */
class UiDataSourceExecutionAccessServiceTest {

    /** 测试上下文，聚合被测服务与各 Mock 依赖 */
    private TestContext context;

    /** 设置当前用户并装配测试上下文 */
    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("user-1", "tester");
        context = context();
    }

    /** 清理用户上下文，避免用例间污染 */
    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 测试发布执行缺少可验证来源时拒绝：验证抛出业务禁止异常且不读取 release */
    @Test
    void rejectsPublishedExecutionWithoutVerifiableOrigin() {
        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().authorizePublished(
                        definition("STATIC_OPTIONS", "GLOBAL", null),
                        request("FIELD_OPTIONS", null, null)));

        assertEquals(
                "UI_DATA_SOURCE_EXECUTION_ORIGIN_REQUIRED",
                exception.getErrorCode());
        verifyNoInteractions(context.releaseMapper());
    }

    /** 测试拒绝未在激活发布中绑定的任意启用数据源：验证抛出 UI_DATA_SOURCE_PUBLISHED_BINDING_REQUIRED */
    @Test
    void rejectsArbitraryEnabledSourceNotBoundInActiveRelease() {
        allowPublishedForm(
                "release-1",
                """
                {"FIELD_OPTIONS":{
                  "serviceId":"different-source",
                  "operationCode":"query"
                }}
                """);

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().authorizePublished(
                        definition("STATIC_OPTIONS", "GLOBAL", null),
                        request("FIELD_OPTIONS", "form-1", null)));

        assertEquals(
                "UI_DATA_SOURCE_PUBLISHED_BINDING_REQUIRED",
                exception.getErrorCode());
    }

    /** 测试配置无激活发布时拒绝运行时执行：验证抛出 UI_DATA_SOURCE_RELEASE_REQUIRED */
    @Test
    void rejectsRuntimeExecutionWhenConfigurationHasNoActiveRelease() {
        allowFormTarget();
        when(context.releaseMapper().findActive("FORM", "form-1"))
                .thenReturn(null);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> context.service().authorizePublished(
                        definition("STATIC_OPTIONS", "GLOBAL", null),
                        request("FIELD_OPTIONS", "form-1", null)));

        assertEquals(
                "UI_DATA_SOURCE_RELEASE_REQUIRED",
                exception.getErrorCode());
    }

    /** 测试拒绝客户端过期的发布声明：验证客户端声明 release-1 而激活为 release-2 时抛出冲突 */
    @Test
    void rejectsStaleClientReleaseClaim() {
        allowPublishedForm(
                "release-2",
                """
                {"FIELD_OPTIONS":{
                  "serviceId":"source-1",
                  "operationCode":"query"
                }}
                """);
        UiExtensionExecuteRequest request =
                request("FIELD_OPTIONS", "form-1", "release-1");

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> context.service().authorizePublished(
                        definition("STATIC_OPTIONS", "GLOBAL", null),
                        request));

        assertEquals(
                "UI_DATA_SOURCE_RELEASE_CONFLICT",
                exception.getErrorCode());
    }

    /** 测试拒绝连接器客户端伪造租户元数据：验证 context 含 orgId 时抛出上下文伪造异常 */
    @Test
    void rejectsConnectorClientSpoofingTenantMetadata() {
        UiExtensionExecuteRequest request =
                request("BEFORE_SUBMIT", "form-1", null);
        request.setContext(Map.of(
                "orgId",
                "forged-tenant"));

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().authorizePublished(
                        definition(
                                "REGISTERED_PROVIDER",
                                "GLOBAL",
                                null),
                        request));

        assertEquals(
                "UI_DATA_SOURCE_EXECUTION_CONTEXT_SPOOFED",
                exception.getErrorCode());
        verifyNoInteractions(context.releaseMapper());
    }

    /** 嵌套上下文也不能绕过可信元数据检查，包含数组中的对象同样应被拒绝。 */
    @Test
    void rejectsNestedTrustedMetadataInMappedPayload() {
        UiExtensionExecuteRequest request =
                request("BEFORE_SUBMIT", "form-1", null);
        request.setContext(Map.of(
                "payload", Map.of(
                        "rows", List.of(Map.of(
                                "tenantId", "forged-tenant")))));

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().authorizePublished(
                        definition(
                                "REGISTERED_PROVIDER",
                                "GLOBAL",
                                null),
                        request));

        assertEquals(
                "UI_DATA_SOURCE_EXECUTION_CONTEXT_SPOOFED",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("tenantId"));
        verifyNoInteractions(context.releaseMapper());
    }

    /** 来源记录身份不能藏在嵌套上下文中交由 Provider 当作授权依据。 */
    @Test
    void rejectsNestedSourceRecordIdentity() {
        UiExtensionExecuteRequest request =
                request("FIELD_OPTIONS", "form-1", null);
        request.setContext(Map.of(
                "payload", Map.of(
                        "source_record_id", "forged-record")));

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().authorizePublished(
                        definition("STATIC_OPTIONS", "GLOBAL", null),
                        request));

        assertEquals(
                "UI_DATA_SOURCE_EXECUTION_CONTEXT_SPOOFED",
                exception.getErrorCode());
        verifyNoInteractions(context.releaseMapper());
    }

    /** 测试拒绝连接器客户端伪造幂等键：验证 context 含 idempotencyKey 时抛出上下文伪造异常 */
    @Test
    void rejectsConnectorClientSpoofingIdempotencyKey() {
        UiExtensionExecuteRequest request =
                request("BEFORE_SUBMIT", "form-1", null);
        request.setContext(Map.of(
                "idempotencyKey",
                "client-forged"));

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().authorizePublished(
                        definition(
                                "REGISTERED_PROVIDER",
                                "GLOBAL",
                                null),
                        request));

        assertEquals(
                "UI_DATA_SOURCE_EXECUTION_CONTEXT_SPOOFED",
                exception.getErrorCode());
    }

    /** 测试仅接受与服务端种子匹配的幂等键：验证授权返回的幂等种子为服务端 seed */
    @Test
    void acceptsOnlyMatchingServerIdempotencySeed() {
        allowPublishedForm(
                "release-1",
                """
                {"BEFORE_SUBMIT":{
                  "serviceId":"source-1",
                  "operationCode":"query"
                }}
                """);
        UiExtensionExecuteRequest request =
                request("BEFORE_SUBMIT", "form-1", "release-1");
        request.setServerIdempotencyKey("server-seed");
        request.setContext(Map.of(
                "idempotencyKey",
                "server-seed"));

        UiDataSourceExecutionAuthorization authorization =
                context.service().authorizePublished(
                        definition(
                                "REGISTERED_PROVIDER",
                                "GLOBAL",
                                null),
                        request);

        assertEquals(
                "server-seed",
                authorization.idempotencySeed());
    }

    /** 非表单按钮事件继续允许 task/process 作为普通业务字段，避免扩大契约。 */
    @Test
    void nonFormButtonWriteKeepsTaskAndProcessBusinessFields() {
        allowPublishedForm(
                "release-1",
                """
                {"BEFORE_SUBMIT":{
                  "serviceId":"source-1",
                  "operationCode":"query"
                }}
                """);
        UiExtensionExecuteRequest request =
                request("BEFORE_SUBMIT", "form-1", "release-1");
        request.setInput(Map.of(
                "taskId", "business-task",
                "processInstanceId", "business-process"));
        request.setContext(Map.of(
                "taskId", "business-task",
                "processInstanceId", "business-process"));

        UiDataSourceExecutionAuthorization authorization =
                context.service().authorizePublished(
                        definition(
                                "REGISTERED_PROVIDER",
                                "GLOBAL",
                                null),
                        request);

        assertEquals("business-task",
                authorization.requestContext().get("taskId"));
        assertEquals("business-process",
                authorization.requestContext().get(
                        "processInstanceId"));
    }

    /** 测试忽略客户端写入服务端幂等种子的尝试：验证反序列化后 serverIdempotencyKey 为 null */
    @Test
    void ignoresClientAttemptToWriteServerIdempotencySeed() throws Exception {
        UiExtensionExecuteRequest request =
                new ObjectMapper().readValue(
                        """
                        {
                          "usage":"BEFORE_SUBMIT",
                          "serverIdempotencyKey":"client-forged"
                        }
                        """,
                        UiExtensionExecuteRequest.class);

        assertNull(request.getServerIdempotencyKey());
    }

    /** 测试忽略客户端启用钉版发布的尝试：验证反序列化后 serverPinnedRelease 为 false */
    @Test
    void ignoresClientAttemptToEnablePinnedReleaseExecution()
            throws Exception {
        UiExtensionExecuteRequest request =
                new ObjectMapper().readValue(
                        """
                        {
                          "usage":"BEFORE_SUBMIT",
                          "serverPinnedRelease":true
                        }
                        """,
                        UiExtensionExecuteRequest.class);

        assertFalse(request.isServerPinnedRelease());
    }

    /** 测试允许可信服务端执行历史钉版发布：验证授权返回历史 releaseId 与版本 */
    @Test
    void allowsTrustedServerExecutionOfHistoricalPinnedRelease() {
        allowFormTarget("active-release");
        allowPermissionPlan();
        UiConfigRelease historical = release(
                "release-3",
                3,
                """
                {"BEFORE_SUBMIT":{
                  "serviceId":"source-1",
                  "operationCode":"query"
                }}
                """);
        when(context.releaseMapper().selectById("release-3"))
                .thenReturn(historical);
        UiExtensionExecuteRequest request =
                request(
                        "BEFORE_SUBMIT",
                        "form-1",
                        "release-3");
        request.setReleaseVersion(3);
        request.setServerPinnedRelease(true);
        request.setServerIdempotencyKey("server-seed");
        request.setInput(Map.of(
                "idempotencyKey",
                "server-seed"));

        UiDataSourceExecutionAuthorization authorization =
                context.service().authorizePublished(
                        definition(
                                "REGISTERED_PROVIDER",
                                "GLOBAL",
                                null),
                        request);

        assertEquals(
                "release-3",
                authorization.releaseId());
        assertEquals(
                3,
                authorization.releaseVersion());
    }

    /** 测试无可信服务端种子时拒绝钉版发布：验证抛出 UI_DATA_SOURCE_TRUSTED_EXECUTION_REQUIRED */
    @Test
    void rejectsPinnedReleaseWithoutTrustedServerSeed() {
        allowFormTarget("active-release");
        UiConfigRelease historical = release(
                "release-3",
                3,
                """
                {"BEFORE_SUBMIT":{
                  "serviceId":"source-1",
                  "operationCode":"query"
                }}
                """);
        when(context.releaseMapper().selectById("release-3"))
                .thenReturn(historical);
        UiExtensionExecuteRequest request =
                request(
                        "BEFORE_SUBMIT",
                        "form-1",
                        "release-3");
        request.setReleaseVersion(3);
        request.setServerPinnedRelease(true);

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().authorizePublished(
                        definition(
                                "REGISTERED_PROVIDER",
                                "GLOBAL",
                                null),
                        request));

        assertEquals(
                "UI_DATA_SOURCE_TRUSTED_EXECUTION_REQUIRED",
                exception.getErrorCode());
    }

    /** 测试管理员访问校验失败时在读取绑定前拒绝草稿预览：验证抛出 UI_CONFIG_ADMIN_REQUIRED 且不读节点 */
    @Test
    void rejectsDraftPreviewBeforeReadingBindingWhenAdminAccessFails() {
        doThrow(new BusinessForbiddenException(
                "UI_CONFIG_ADMIN_REQUIRED",
                "只有管理员可以预览数据源"))
                .when(context.configurationAccessService())
                .requireFormAccess("form-1");

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().authorizePreview(
                        definition("STATIC_OPTIONS", "GLOBAL", null),
                        request("FIELD_OPTIONS", "form-1", null)));

        assertEquals(
                "UI_CONFIG_ADMIN_REQUIRED",
                exception.getErrorCode());
        verifyNoInteractions(context.formNodeMapper());
    }

    /** 测试仅允许绑定该数据源的管理员草稿预览：验证授权为预览模式且 bindingPath 指向草稿绑定 */
    @Test
    void allowsOnlyBoundAdministratorDraftPreview() {
        allowFormTarget();
        EntityFormNode node = new EntityFormNode();
        node.setId("node-1");
        node.setNodeKey("field-1");
        node.setDataSourceBindingsDocument(
                """
                {"FIELD_OPTIONS":{
                  "serviceId":"source-1",
                  "operationCode":"query"
                }}
                """);
        when(context.formNodeMapper().findByFormId("form-1"))
                .thenReturn(List.of(node));
        allowPermissionPlan();

        UiDataSourceExecutionAuthorization authorization =
                context.service().authorizePreview(
                        definition("STATIC_OPTIONS", "GLOBAL", null),
                        request("FIELD_OPTIONS", "form-1", null));

        assertTrue(authorization.preview());
        assertNull(authorization.releaseId());
        assertEquals("expense", authorization.entityCode());
        assertEquals(
                "$.draft.form[1].dataSourceBindings.FIELD_OPTIONS",
                authorization.bindingPath());
    }

    /** 测试无列表访问权限时拒绝直接列表执行：验证抛出 UI_DATA_SOURCE_LIST_ACCESS_DENIED 且不读 release */
    @Test
    void rejectsDirectListExecutionWithoutListAccessPermission() {
        EntityListConfig list = list();
        when(context.listMapper().selectById("list-1"))
                .thenReturn(list);
        when(context.definitionMapper().selectById("entity-1"))
                .thenReturn(entity());
        when(context.menuMapper().selectPermsByUserId("user-1"))
                .thenReturn(Set.of());

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().authorizePublished(
                        definition("STATIC_OPTIONS", "GLOBAL", null),
                        listRequest()));

        assertEquals(
                "UI_DATA_SOURCE_LIST_ACCESS_DENIED",
                exception.getErrorCode());
        verifyNoInteractions(context.releaseMapper());
    }

    /** 测试构建权限计划：验证用户与数据权限计划来自服务端，普通业务上下文可以继续传递 */
    @Test
    void buildsPermissionPlanFromTrustedServerState() {
        allowPublishedForm(
                "release-1",
                """
                {"FIELD_OPTIONS":{
                  "serviceId":"source-1",
                  "operationCode":"query"
                }}
                """);
        allowPermissionPlan();
        UiExtensionExecuteRequest request =
                request("FIELD_OPTIONS", "form-1", "release-1");
        request.setEntityCode("expense");
        request.setContext(Map.of(
                "mode", "edit"));

        UiDataSourceExecutionAuthorization authorization =
                context.service().authorizePublished(
                        definition("STATIC_OPTIONS", "GLOBAL", null),
                        request);

        assertFalse(authorization.preview());
        assertEquals("release-1", authorization.releaseId());
        assertEquals("user-1", authorization.user().getId());
        assertEquals("org-1", authorization.user().getOrgId());
        assertEquals(
                "owner_id = #{permissionParameters.ownerId}",
                authorization.dataScopePlan().sqlFragment());
        assertEquals(Map.of("ownerId", "user-1"), authorization.dataScopePlan().parameters());
        assertEquals("edit", authorization.requestContext().get("mode"));
    }

    /** 测试保留事件运行时的可选空值：验证列表加载等场景不会在上下文净化时抛出空指针 */
    @Test
    void preservesNullableOptionalRuntimeContextAsReadOnly() {
        allowPublishedForm(
                "release-1",
                """
                {"FIELD_OPTIONS":{
                  "serviceId":"source-1",
                  "operationCode":"query"
                }}
                """);
        UiExtensionExecuteRequest request =
                request("FIELD_OPTIONS", "form-1", "release-1");
        Map<String, Object> runtimeContext = new LinkedHashMap<>();
        runtimeContext.put("mode", "edit");
        runtimeContext.put("targetKey", null);
        runtimeContext.put("recordId", null);
        runtimeContext.put(null, "ignored");
        request.setContext(runtimeContext);

        UiDataSourceExecutionAuthorization authorization =
                context.service().authorizePublished(
                        definition("STATIC_OPTIONS", "GLOBAL", null),
                        request);

        assertEquals("edit", authorization.requestContext().get("mode"));
        assertTrue(authorization.requestContext()
                .containsKey("targetKey"));
        assertNull(authorization.requestContext().get("targetKey"));
        assertTrue(authorization.requestContext()
                .containsKey("recordId"));
        assertNull(authorization.requestContext().get("recordId"));
        assertFalse(authorization.requestContext().containsKey(null));
        assertThrows(
                UnsupportedOperationException.class,
                () -> authorization.requestContext()
                        .put("unexpected", "value"));
    }

    /** 列表上下文 DTO 的来源身份不能泄漏到接口请求，前置接口仍应正常进入平台查询。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"parent-record-1"})
    void listLoadContextPassesRealAuthorizationBeforeDefaultQuery(String sourceRecordId) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> parameters = Map.of("keyword", "待处理");
        EntityListRuntimeContextDTO listContext = new EntityListRuntimeContextDTO();
        listContext.setSourceEntityCode("project");
        listContext.setSourceRecordId(sourceRecordId);
        listContext.setRelationKey("project_expenses");
        listContext.setParameters(parameters);
        Map<String, Object> eventContext = mapper.convertValue(listContext,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        eventContext.put("params", parameters);
        // 通用事件入口还会携带前端展示身份，包含大小写和分隔符变体。
        eventContext.put("LIST_KEY", "display-only");
        eventContext.put("Source-Record-Id", sourceRecordId);

        UiEventExecuteRequest event = new UiEventExecuteRequest();
        event.setConfigType("LIST");
        event.setConfigId("list-1");
        event.setEventCode("LIST_LOAD");
        event.setContext(eventContext);
        event.setInput(Map.of("params", parameters, "filters", Map.of(), "pageNum", 2, "pageSize", 20));
        List<Map<String, Object>> steps = List.of(Map.of(
                "strategy", "BEFORE", "extensionId", "source-1", "operationCode", "query"));
        Map<String, Object> snapshot = Map.of(
                "configType", "LIST", "list", Map.of("id", "list-1", "entityId", "entity-1"),
                "eventBindings", List.of(Map.of(
                        "ownerType", "LIST", "ownerId", "list-1", "targetType", "OWNER",
                        "eventCode", "LIST_LOAD", "steps", steps)));
        UiConfigRelease release = new UiConfigRelease();
        release.setId("list-release-1");
        release.setConfigType("LIST");
        release.setConfigId("list-1");
        release.setVersion(3);
        release.setSnapshotDocument(context.codec().write(snapshot, "列表事件测试发布快照"));
        when(context.listMapper().selectById("list-1")).thenReturn(list());
        when(context.definitionMapper().selectById("entity-1")).thenReturn(entity());
        when(context.releaseMapper().findActive("LIST", "list-1")).thenReturn(release);
        when(context.menuMapper().selectPermsByUserId("user-1")).thenReturn(Set.of("entity:expense:list"));
        when(context.userService().getById("user-1")).thenReturn(user());
        when(context.dataPermissionEngine().calculatePermission("expense", "default", user()))
                .thenReturn(DataPermissionResult.allowAll());
        UiEventBindingService bindings = mock(UiEventBindingService.class);
        when(bindings.resolvePublished(event)).thenReturn(new UiEventBindingService.ResolvedEventChain(
                steps, "list-release-1", 3, "entity-1", "expense", "default", snapshot));
        UiInterfaceExtensionService interfaces = mock(UiInterfaceExtensionService.class);
        when(interfaces.executeOperation(eq("source-1"), eq("query"), any())).thenAnswer(invocation -> {
            UiExtensionExecuteRequest request = invocation.getArgument(2);
            UiDataSourceExecutionAuthorization authorization = context.service().authorizePublished(
                    definition("REGISTERED_PROVIDER", "GLOBAL", null), request);
            Map<String, Object> providerContext = authorization.requestContext();
            assertFalse(providerContext.containsKey("sourceRecordId"));
            assertFalse(providerContext.containsKey("Source-Record-Id"));
            assertFalse(providerContext.containsKey("LIST_KEY"));
            assertFalse(providerContext.containsKey("params"));
            assertFalse(providerContext.containsKey("parameters"));
            assertFalse(providerContext.containsKey("relationKey"));
            Map<?, ?> state = (Map<?, ?>) providerContext.get("eventState");
            assertFalse(state.containsKey("context"));
            assertFalse(state.containsKey("input"));
            assertEquals("user-1", authorization.user().getId());
            return Map.of("filters", Map.of("name", "待处理"));
        });
        UiEventRuntimeService runtime = new UiEventRuntimeService(
                bindings, interfaces, new UiEventValueMapper(), mock(EntitySelectionRuntimeService.class),
                mock(SystemAuditPort.class), mock(EntityActionCapabilityService.class), mock(EntityFormActionService.class),
                mock(UiEventExecutionReceiptService.class), mock(EntityDataDynamicService.class), mapper);

        UiEventExecutionResult result = runtime.execute(event, input -> {
            assertEquals(parameters, input.get("params"));
            assertEquals(Map.of("name", "待处理"), input.get("filters"));
            assertEquals(2, input.get("pageNum"));
            assertEquals(20, input.get("pageSize"));
            return Map.of("records", List.of(), "total", 0);
        });

        assertTrue(result.isDefaultExecuted());
        assertEquals(Map.of("records", List.of(), "total", 0), result.getData());
        assertTrue(eventContext.containsKey("sourceRecordId"));
    }

    /** 测试所有接口类型都拒绝客户端伪造用户身份：验证静态数据源也不能提交 userId */
    @Test
    void rejectsTrustedIdentityMetadataForEverySourceType() {
        UiExtensionExecuteRequest request =
                request("FIELD_OPTIONS", "form-1", null);
        request.setContext(Map.of(
                "userId", "forged-user"));

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().authorizePublished(
                        definition(
                                "STATIC_OPTIONS",
                                "GLOBAL",
                                null),
                        request));

        assertEquals(
                "UI_DATA_SOURCE_EXECUTION_CONTEXT_SPOOFED",
                exception.getErrorCode());
        verifyNoInteractions(context.releaseMapper());
    }

    /** 测试即使绑定存在也拒绝数据源作用域不匹配：验证抛出 UI_DATA_SOURCE_SCOPE_MISMATCH */
    @Test
    void rejectsDataSourceScopeMismatchEvenWhenBindingExists() {
        allowPublishedForm(
                "release-1",
                """
                {"FIELD_OPTIONS":{
                  "serviceId":"source-1",
                  "operationCode":"query"
                }}
                """);

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().authorizePublished(
                        definition(
                                "STATIC_OPTIONS",
                                "FORM",
                                "different-form"),
                        request("FIELD_OPTIONS", "form-1", null)));

        assertEquals(
                "UI_DATA_SOURCE_SCOPE_MISMATCH",
                exception.getErrorCode());
    }

    @Test
    void resolvedFormButtonAuthorizesExactInheritedOwnerWithoutReadingActive() {
        allowFormTarget("active-release-changed");
        allowPermissionPlan();
        Map<String, Object> snapshot = resolvedButtonSnapshot();
        UiExtensionExecuteRequest request = resolvedButtonRequest();

        UiDataSourceExecutionAuthorization authorization =
                context.service().authorizeResolvedFormButton(
                        definition("REGISTERED_PROVIDER", "GLOBAL", null),
                        request,
                        snapshot,
                        "effective-hash");

        assertEquals("base-release", authorization.releaseId());
        assertEquals(
                "$.release.eventBindings[0].steps",
                authorization.bindingPath());
        verify(context.releaseService()).verifyResolvedEventSnapshot(
                snapshot, "effective-hash");
        verifyNoInteractions(context.releaseMapper());
    }

    @Test
    void formButtonAllowsReservedNamesInsideBusinessFormValues() {
        allowFormTarget("active-release-changed");
        allowPermissionPlan();
        UiExtensionExecuteRequest request = resolvedButtonRequest();
        request.setInput(Map.of(
                "form", Map.of(
                        "userId", "business-field-value",
                        "entityCode", "business-field-value",
                        "formId", "business-field-value")));
        request.setContext(Map.of("mode", "approve"));

        UiDataSourceExecutionAuthorization authorization =
                context.service().authorizeResolvedFormButton(
                        definition("REGISTERED_PROVIDER", "GLOBAL", null),
                        request,
                        resolvedButtonSnapshot(),
                        "effective-hash");

        assertEquals("approve",
                authorization.requestContext().get("mode"));
        assertFalse(authorization.requestContext()
                .containsKey("taskId"));
    }

    @Test
    void formButtonRejectsClientTaskAndProcessIdentityFromProviderPayload() {
        for (Map<String, Object> forged : List.<Map<String, Object>>of(
                Map.of("taskId", "task-forged"),
                Map.of("nested", Map.of(
                        "processInstanceId", "process-forged")))) {
            UiExtensionExecuteRequest request = resolvedButtonRequest();
            request.setContext(forged);

            BusinessForbiddenException error = assertThrows(
                    BusinessForbiddenException.class,
                    () -> context.service().authorizeResolvedFormButton(
                            definition("REGISTERED_PROVIDER", "GLOBAL", null),
                            request,
                            resolvedButtonSnapshot(),
                            "effective-hash"));

            assertEquals("UI_DATA_SOURCE_EXECUTION_CONTEXT_SPOOFED",
                    error.getErrorCode());
        }
    }

    @Test
    void formButtonStillRejectsNestedContextIdentity() {
        UiExtensionExecuteRequest request = resolvedButtonRequest();
        request.setContext(Map.of("nested", Map.of("userId", "forged-user")));
        BusinessForbiddenException error = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().authorizeResolvedFormButton(
                        definition("REGISTERED_PROVIDER", "GLOBAL", null),
                        request, resolvedButtonSnapshot(), "effective-hash"));
        assertEquals("UI_DATA_SOURCE_EXECUTION_CONTEXT_SPOOFED", error.getErrorCode());
    }

    /** 业务部门/用户字段可传给 Provider，但认证身份仍取自服务端当前用户。 */
    @ParameterizedTest
    @ValueSource(strings = {"ENTITY_SELECTED", "FIELD_CHANGE", "FIELD_BUTTON_CLICK"})
    void formFieldEventsAllowBusinessIdentityNames(String eventCode) {
        allowFormTarget();
        allowPermissionPlan();
        UiConfigRelease release = release("release-1", 3, "{}");
        release.setSnapshotDocument(context.codec().write(Map.of(
                "configType", "FORM",
                "form", Map.of("id", "form-1", "entityId", "entity-1"),
                "eventBindings", List.of(Map.of(
                        "ownerType", "FORM", "ownerId", "form-1",
                        "targetType", "FIELD", "targetKey", "field-1",
                        "eventCode", eventCode,
                        "steps", List.of(Map.of("extensionId", "source-1"))))),
                "字段事件发布快照"));
        when(context.releaseMapper().findActive("FORM", "form-1"))
                .thenReturn(release);
        UiExtensionExecuteRequest request = fieldEventRequest(eventCode);
        Map<String, Object> businessValues = Map.of(
                "deptId", "selected-dept", "userId", "selected-user",
                "userName", "selected-name", "formId", "business-form");
        request.setInput(Map.of(
                "form", businessValues,
                "selection", List.of(Map.of("data", businessValues)),
                "value", businessValues));
        request.setContext(Map.of("eventState", Map.of("selectionPresent", true)));

        UiDataSourceExecutionAuthorization authorization = context.service()
                .authorizePublished(definition("REGISTERED_PROVIDER", "GLOBAL", null), request);

        assertEquals("user-1", authorization.user().getId());
        assertEquals("dept-1", authorization.user().getDeptId());
        assertEquals(businessValues, request.getInput().get("form"));
        assertEquals("$.release.eventBindings[0].steps", authorization.bindingPath());
    }

    @Test
    void formFieldEventsStillRejectIdentityInsideClientContext() {
        UiExtensionExecuteRequest request = fieldEventRequest("ENTITY_SELECTED");
        request.setContext(Map.of("eventState", Map.of(
                "input", Map.of("form", Map.of("deptId", "forged-dept")))));
        assertSpoofedFieldEvent(request);
        verifyNoInteractions(context.releaseMapper());
    }

    /** 多个业务容器共用结构限制，允许字段名不能成为超深/循环输入的绕过路径。 */
    @Test
    void formFieldBusinessContainersStillEnforceStructureLimits() {
        Map<String, Object> cycle = new LinkedHashMap<>();
        cycle.put("self", cycle);
        Map<String, Object> deep = Map.of("deptId", "dept");
        for (int index = 0; index < 13; index++) {
            deep = Map.of("nested", deep);
        }
        List<String> values = new ArrayList<>();
        for (int index = 0; index < 2100; index++) {
            values.add("value");
        }
        for (Map<String, Object> input : List.<Map<String, Object>>of(
                Map.of("form", cycle), Map.of("selection", deep),
                Map.of("form", values, "selection", values))) {
            UiExtensionExecuteRequest request = fieldEventRequest("ENTITY_SELECTED");
            request.setInput(input);
            BusinessForbiddenException error = assertThrows(BusinessForbiddenException.class,
                    () -> context.service().authorizePublished(
                            definition("REGISTERED_PROVIDER", "GLOBAL", null), request));
            assertEquals("UI_DATA_SOURCE_INPUT_STRUCTURE_INVALID", error.getErrorCode());
        }
    }

    /** 原生数据源和各类事件共用业务输入契约，不依赖特定容器名或事件白名单。 */
    @ParameterizedTest
    @ValueSource(strings = {"FORM_OPEN", "FORM_SAVE", "FORM_RESET", "FORM_INIT",
            "AFTER_LOAD", "BEFORE_SUBMIT", "FIELD_OPTIONS", "ENTITY_SELECTED"})
    void allUsagesAllowBusinessFieldsWithoutChangingAuthorization(String usage) {
        allowPublishedForm("release-1", context.codec().write(
                Map.of(usage, Map.of("serviceId", "source-1", "operationCode", "query")),
                "测试绑定"));
        UiExtensionExecuteRequest request = request(usage, "form-1", "release-1");
        Map<String, Object> business = Map.of(
                "deptId", "", "userId", "selected-user", "tenantId", "business-tenant",
                "idempotencyKey", "business-key", "dataScopePlan", Map.of("allowed", true));
        request.setInput(Map.of("deptId", "selected-dept", "userId", "selected-user",
                "form", business, "formData", business, "rows", List.of(business)));
        request.setServerIdempotencyKey("server-seed");

        UiDataSourceExecutionAuthorization authorization = context.service().authorizePublished(
                definition("REGISTERED_PROVIDER", "GLOBAL", null), request);

        assertEquals("user-1", authorization.user().getId());
        assertEquals("dept-1", authorization.user().getDeptId());
        assertEquals("server-seed", authorization.idempotencySeed());
        assertEquals(business, request.getInput().get("form"));
        assertEquals("owner_id = #{permissionParameters.ownerId}", authorization.dataScopePlan().sqlFragment());
        assertEquals(Map.of("ownerId", "user-1"), authorization.dataScopePlan().parameters());
    }

    private UiExtensionExecuteRequest fieldEventRequest(String eventCode) {
        UiExtensionExecuteRequest request = request(eventCode, "form-1", "release-1");
        request.setTargetType("FIELD");
        return request;
    }

    /** 串联真实令牌校验、事件解析、接口授权和 Provider 回填，覆盖普通及固定版本页面。 */
    @ParameterizedTest
    @CsvSource({
            "ENTITY_SELECTED, false, false",
            "ENTITY_SELECTED, true, false",
            "ENTITY_SELECTED, true, true",
            "FIELD_CHANGE, true, true",
            "FIELD_BUTTON_CLICK, true, true"
    })
    void fieldEventExecutesProviderThroughReleaseAuthorization(
            String eventCode, boolean signed, boolean historical) throws Exception {
        try (FieldEventFlow flow = fieldEventFlow(eventCode, signed, historical)) {
            UiEventExecutionResult result = flow.runtime().execute(flow.request());

            ArgumentCaptor<UiInvocationContext> invocation =
                    ArgumentCaptor.forClass(UiInvocationContext.class);
            verify(flow.provider()).execute(invocation.capture(), any(), any(),
                    eq(flow.request().getInput()));
            assertEquals("user-1", invocation.getValue().common().userId());
            assertEquals("dept-1", invocation.getValue().common().departmentId());
            assertEquals("release-1", invocation.getValue().common().releaseId());
            assertEquals(3, invocation.getValue().common().releaseVersion());
            assertEquals(1, result.getEffects().size());
            assertEquals("FIELD_MAPPING", result.getEffects().get(0).get("type"));
            assertEquals(Map.of("form", Map.of("name", "周大伟", "myText", "ZDW")),
                    result.getEffects().get(0).get("data"));
            if (signed) {
                assertNotNull(flow.request().getServerIdempotencyKey());
                assertFalse(flow.request().getServerIdempotencyKey().equals(
                        flow.request().getRequestId()));
                verify(context.releaseMapper(), org.mockito.Mockito.never())
                        .findActive(anyString(), anyString());
            }
        }
    }

    /** 原始发布没有接口绑定，只有热修复有效快照包含绑定；不能回查原始发布授权。 */
    @ParameterizedTest
    @CsvSource({
            "ENTITY_SELECTED, FIELD",
            "ENTITY_SELECTED, FORM_OWNER",
            "ENTITY_SELECTED, ENTITY_OWNER",
            "FIELD_CHANGE, FIELD",
            "FIELD_BUTTON_CLICK, FIELD"
    })
    void fieldEventUsesEffectiveHotfixBinding(String eventCode, String bindingScope) throws Exception {
        try (FieldEventFlow flow = fieldEventFlow(eventCode, true, true, true, bindingScope)) {
            UiEventExecutionResult result = flow.runtime().execute(flow.request());

            verify(flow.provider()).execute(any(), any(), any(), eq(flow.request().getInput()));
            assertEquals(Map.of("form", Map.of("name", "周大伟", "myText", "ZDW")),
                    result.getEffects().get(0).get("data"));
            // 有效快照已由事件解析器验真，后续不能再读取基础版或 ACTIVE 的事件绑定。
            verify(context.releaseMapper(), org.mockito.Mockito.never()).selectById(anyString());
            verify(context.releaseMapper(), org.mockito.Mockito.never()).findActive(anyString(), anyString());
        }
    }

    /** 使用有效快照不降低授权要求：来源、完整性、表单身份和绑定都必须匹配。 */
    @ParameterizedTest
    @CsvSource({
            "missing-seed, UI_DATA_SOURCE_TRUSTED_EXECUTION_REQUIRED",
            "missing-hash, UI_EVENT_EFFECTIVE_SNAPSHOT_REQUIRED",
            "tampered, UI_EVENT_EFFECTIVE_SNAPSHOT_TAMPERED",
            "wrong-form, UI_EVENT_EFFECTIVE_SNAPSHOT_CONFLICT",
            "missing-binding, UI_DATA_SOURCE_PUBLISHED_BINDING_REQUIRED",
            "wrong-owner, UI_DATA_SOURCE_PUBLISHED_BINDING_REQUIRED"
    })
    void resolvedFieldEventStillRejectsInvalidAuthorization(String problem, String expectedCode) {
        allowFormTarget();
        UiConfigSnapshotSupport snapshots = new UiConfigSnapshotSupport(context.codec(), new ObjectMapper());
        ReflectionTestUtils.setField(context.releaseService(), "snapshotSupport", snapshots);
        doCallRealMethod().when(context.releaseService()).verifyResolvedEventSnapshot(any(), any());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("configType", "FORM");
        snapshot.put("form", Map.of("id", "form-1", "entityId", "entity-1"));
        snapshot.put("eventBindings", List.of(Map.of(
                "ownerType", "FORM", "ownerId", "form-1", "targetType", "FIELD",
                "targetKey", "field-1", "eventCode", "ENTITY_SELECTED",
                "steps", List.of(Map.of("extensionId", "source-1")))));
        if ("wrong-form".equals(problem)) {
            snapshot.put("form", Map.of("id", "different-form", "entityId", "entity-1"));
        } else if ("missing-binding".equals(problem)) {
            snapshot.put("eventBindings", List.of());
        }
        String expectedHash = "missing-hash".equals(problem) ? null
                : snapshots.hash(snapshots.canonical(snapshot));
        if ("tampered".equals(problem)) {
            snapshot.put("eventBindings", List.of());
        }
        UiExtensionExecuteRequest request = fieldEventRequest("ENTITY_SELECTED");
        request.setReleaseVersion(3);
        request.setServerPinnedRelease(true);
        request.setServerIdempotencyKey("missing-seed".equals(problem) ? null : "server-seed");
        request.setServerBindingOwnerType("FORM");
        request.setServerBindingOwnerId("wrong-owner".equals(problem) ? "different-form" : "form-1");
        request.setServerBindingTargetType("FIELD");
        request.setServerBindingTargetKey("field-1");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> context.service().authorizeResolvedFormFieldEvent(
                        definition("REGISTERED_PROVIDER", "GLOBAL", null), request, snapshot, expectedHash));
        String actualCode = error instanceof BusinessForbiddenException forbidden ? forbidden.getErrorCode()
                : error instanceof BusinessConflictException conflict ? conflict.getErrorCode() : null;
        assertEquals(expectedCode, actualCode);
        verifyNoInteractions(context.releaseMapper());
    }

    /** 无效/越界令牌与权限拒绝必须在生成内部凭证和执行 Provider 之前终止。 */
    @ParameterizedTest
    @ValueSource(strings = {"invalid-token", "wrong-release", "wrong-version", "denied"})
    void fieldEventRejectsUntrustedReleaseBeforeProviderExecution(String rejection) throws Exception {
        try (FieldEventFlow flow = fieldEventFlow("ENTITY_SELECTED", true, true)) {
            switch (rejection) {
                case "invalid-token" -> flow.request().setReleaseResolutionToken("forged-token");
                case "wrong-release" -> flow.request().setReleaseId("different-release");
                case "wrong-version" -> flow.request().setReleaseVersion(99);
                case "denied" -> doThrow(new BusinessForbiddenException("DENIED", "禁止访问"))
                        .when(flow.permissions()).requireStandardPermission(
                                "expense", EntityPermissionAction.LIST);
            }

            assertThrows(BusinessForbiddenException.class,
                    () -> flow.runtime().execute(flow.request()));
            assertNull(flow.request().getServerIdempotencyKey());
            verify(flow.provider(), org.mockito.Mockito.never()).execute(any(), any(), any(), any());
        }
    }

    /**
     * 仅替换持久化、权限外部依赖及流程版本查找；签名验证、快照验哈希、事件解析、
     * 接口授权、调用上下文和结果映射都使用生产实现，避免服务间契约被 Mock 掩盖。
     */
    private FieldEventFlow fieldEventFlow(String eventCode, boolean signed, boolean historical) {
        return fieldEventFlow(eventCode, signed, historical, false, "FIELD");
    }

    private FieldEventFlow fieldEventFlow(String eventCode, boolean signed, boolean historical,
                                         boolean hotfix, String bindingScope) {
        allowFormTarget(historical ? "new-active-release" : "release-1");
        allowPermissionPlan();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        UiConfigSnapshotSupport snapshots = new UiConfigSnapshotSupport(context.codec(), objectMapper);
        UiReleaseResolutionTokenService tokens = new UiReleaseResolutionTokenService(objectMapper);
        ReflectionTestUtils.setField(tokens, "secret", "field-event-integration-test-secret");
        Map<String, Object> snapshot = Map.of(
                "configType", "FORM",
                "form", Map.of("id", "form-1", "entityId", "entity-1"),
                "eventBindings", List.of(Map.of(
                        "ownerType", "ENTITY_OWNER".equals(bindingScope) ? "ENTITY" : "FORM",
                        "ownerId", "ENTITY_OWNER".equals(bindingScope) ? "entity-1" : "form-1",
                        "targetType", "FIELD".equals(bindingScope) ? "FIELD" : "OWNER",
                        "targetKey", "FIELD".equals(bindingScope) ? "field-1" : "", "eventCode", eventCode,
                        "steps", List.of(Map.of(
                                "strategy", "AFTER", "extensionId", "source-1",
                                "outputMapping", List.of(
                                        Map.of("sourcePath", "data.userName", "targetPath", "form.name"),
                                        Map.of("sourcePath", "data.userCode", "targetPath", "form.myText")))))));
        UiConfigRelease release = release("release-1", 3, "{}");
        Map<String, Object> baseSnapshot = new LinkedHashMap<>(snapshot);
        if (hotfix) {
            baseSnapshot.put("eventBindings", List.of());
        }
        release.setSnapshotDocument(snapshots.canonical(baseSnapshot));
        release.setContentHash(snapshots.hash(release.getSnapshotDocument()));
        when(context.releaseMapper().selectById("release-1")).thenReturn(release);
        when(context.releaseMapper().findActive("FORM", "form-1"))
                .thenReturn(historical ? release("new-active-release", 4, "{}") : release);
        ReflectionTestUtils.setField(context.releaseService(), "releaseMapper", context.releaseMapper());
        ReflectionTestUtils.setField(context.releaseService(), "codec", context.codec());
        ReflectionTestUtils.setField(context.releaseService(), "snapshotSupport", snapshots);
        ReflectionTestUtils.setField(context.releaseService(), "resolutionTokenService", tokens);
        doCallRealMethod().when(context.releaseService())
                .resolveRuntimeEventSnapshot(any(), any(), any(), any());
        doCallRealMethod().when(context.releaseService()).verifiedReleaseSnapshot(any());
        doCallRealMethod().when(context.releaseService()).verifyResolvedEventSnapshot(any(), any());
        String effectiveHash = snapshots.hash(snapshots.canonical(snapshot));
        if (hotfix) {
            UiConfigHotfixTarget target = new UiConfigHotfixTarget();
            target.setId("hotfix-target-1");
            target.setStatus("ACTIVE");
            target.setEffectiveSnapshotDocument(snapshots.canonical(snapshot));
            target.setEffectiveContentHash(effectiveHash);
            UiConfigHotfixTargetMapper hotfixes = mock(UiConfigHotfixTargetMapper.class);
            when(hotfixes.selectById("hotfix-target-1")).thenReturn(target);
            ReflectionTestUtils.setField(context.releaseService(), "hotfixTargetMapper", hotfixes);
        }
        EntityForm form = context.formMapper().selectById("form-1");
        when(context.releaseService().resolveRuntimeFormRelease(
                anyString(), anyString(), anyInt(), any(UiRuntimeResolutionContext.class)))
                .thenReturn(new ResolvedEntityFormRelease(
                        form, "release-1", 3, true,
                        hotfix ? "hotfix-release-1" : "release-1", effectiveHash,
                        hotfix ? "hotfix-target-1" : null,
                        com.workflow.contracts.ui.runtime.UiRuntimePurpose.NEW_INSTANCE));

        UiExtensionDefinition extension = definition("REGISTERED_PROVIDER", "GLOBAL", null);
        extension.setExtensionType("INTERFACE");
        extension.setExtensionKey("backfill-user");
        extension.setDisplayName("用户回填");
        extension.setProviderCode("backfill-provider");
        extension.setInterfaceContextType("FORM");
        extension.setInterfaceKind("READ");
        UiExtensionDefinitionMapper extensions = mock(UiExtensionDefinitionMapper.class);
        when(extensions.selectById("source-1")).thenReturn(extension);
        UiDataSourceProvider provider = mock(UiDataSourceProvider.class);
        when(provider.getCode()).thenReturn("backfill-provider");
        when(provider.getVersion()).thenReturn(1);
        when(provider.getArtifactDigest()).thenReturn("a".repeat(64));
        when(provider.execute(any(), any(), any(), any()))
                .thenReturn(Map.of("userName", "周大伟", "userCode", "ZDW"));
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        UiInterfaceExtensionService interfaces = new UiInterfaceExtensionService(
                extensions, context.formMapper(), context.listMapper(),
                mock(EntityDefinitionAccessPolicy.class), mock(EntityUiConfigurationPolicy.class),
                mock(SysDictItemService.class), context.service(),
                new UiInvocationContextFactory(context.definitionMapper(), context.formMapper(), context.listMapper()),
                new UiExtensionDefinitionValidator(context.codec()), List.of(provider), context.codec(), executor);
        UiEventBindingService bindings = new UiEventBindingService(
                mock(UiEventBindingMapper.class), context.releaseMapper(), context.definitionMapper(),
                context.formMapper(), context.listMapper(), mock(EntityDefinitionAccessPolicy.class),
                context.configurationAccessService(), interfaces, mock(UiEventBindingSnapshotService.class),
                context.releaseService(), context.codec(), objectMapper);
        EntityDataDynamicService entityData = mock(EntityDataDynamicService.class);
        EntityActionCapabilityService permissions = mock(EntityActionCapabilityService.class);
        UiEventRuntimeService runtime = new UiEventRuntimeService(
                bindings, interfaces, new UiEventValueMapper(),
                new EntitySelectionRuntimeService(entityData, mock(SystemEntityService.class),
                        mock(SystemEntityReadService.class), context.definitionMapper(), objectMapper),
                mock(SystemAuditPort.class), permissions, mock(EntityFormActionService.class),
                mock(UiEventExecutionReceiptService.class), entityData, objectMapper);
        UiEventExecuteRequest request = new UiEventExecuteRequest();
        request.setConfigType("FORM");
        request.setConfigId("form-1");
        request.setTargetType("FIELD");
        request.setTargetKey("field-1");
        request.setEventCode(eventCode);
        request.setReleaseId("release-1");
        request.setReleaseVersion(3);
        if (signed) {
            request.setReleaseResolutionToken(tokens.issue(
                    UiRuntimeResolutionContext.standalone(), "form-1", "release-1", 3, 0));
        }
        request.setInput(Map.of("form", Map.of("deptId", "business-dept"),
                "selection", Map.of("id", "selected-user", "userName", "selected-name"),
                "value", "selected-user"));
        request.setSelection(Map.of("id", "selected-user"));
        request.setContext(Map.of("mode", "create"));
        return new FieldEventFlow(runtime, request, provider, permissions, executor);
    }

    private record FieldEventFlow(UiEventRuntimeService runtime, UiEventExecuteRequest request,
                                  UiDataSourceProvider provider, EntityActionCapabilityService permissions,
                                  SimpleAsyncTaskExecutor executor) implements AutoCloseable {
        @Override
        public void close() {
            executor.close();
        }
    }

    private void assertSpoofedFieldEvent(UiExtensionExecuteRequest request) {
        BusinessForbiddenException error = assertThrows(BusinessForbiddenException.class,
                () -> context.service().authorizePublished(
                        definition("REGISTERED_PROVIDER", "GLOBAL", null), request));
        assertEquals("UI_DATA_SOURCE_EXECUTION_CONTEXT_SPOOFED", error.getErrorCode());
    }

    private Map<String, Object> resolvedButtonSnapshot() {
        return Map.of(
                "configType", "FORM",
                "form", Map.of(
                        "id", "form-1",
                        "entityId", "entity-1"),
                "eventBindings", List.of(Map.of(
                        "ownerType", "ENTITY",
                        "ownerId", "entity-1",
                        "targetType", "OWNER",
                        "targetKey", "",
                        "eventCode", "FORM_BUTTON_CLICK",
                        "inheritanceMode", "INHERIT",
                        "steps", List.of(Map.of(
                                "serviceId", "source-1",
                                "operationCode", "query")))));
    }

    private UiExtensionExecuteRequest resolvedButtonRequest() {
        UiExtensionExecuteRequest request = request(
                "FORM_BUTTON_CLICK", "form-1", "base-release");
        request.setReleaseVersion(3);
        request.setTargetType("BUTTON");
        request.setTargetKey("generate");
        request.setServerPinnedRelease(true);
        request.setServerIdempotencyKey("server-seed");
        request.setServerBindingOwnerType("ENTITY");
        request.setServerBindingOwnerId("entity-1");
        request.setServerBindingTargetType("OWNER");
        request.setServerBindingTargetKey("");
        return request;
    }

    /** 预置已发布表单授权：装配表单目标、激活发布与权限计划 */
    private void allowPublishedForm(
            String releaseId,
            String bindingsDocument) {
        allowFormTarget(releaseId);
        UiConfigRelease release = release(
                releaseId,
                3,
                bindingsDocument);
        when(context.releaseMapper().findActive("FORM", "form-1"))
                .thenReturn(release);
        allowPermissionPlan();
    }

    /** 构造含数据源绑定节点的已发布表单快照对象 */
    private UiConfigRelease release(
            String releaseId,
            int version,
            String bindingsDocument) {
        UiConfigRelease release = new UiConfigRelease();
        release.setId(releaseId);
        release.setConfigType("FORM");
        release.setConfigId("form-1");
        release.setVersion(version);
        release.setSnapshotDocument(context.codec().write(
                Map.of(
                        "configType", "FORM",
                        "form", Map.of(
                                "id", "form-1",
                                "entityId", "entity-1"),
                        "nodes", List.of(Map.of(
                                "id", "node-1",
                                "nodeKey", "field-1",
                                "dataSourceBindingsDocument",
                                bindingsDocument)),
                        "legacyFields", List.of()),
                "测试表单发布快照"));
        return release;
    }

    /** 预置表单目标授权（默认 release-1） */
    private void allowFormTarget() {
        allowFormTarget("release-1");
    }

    /** 预置表单目标授权，指定激活 releaseId */
    private void allowFormTarget(String releaseId) {
        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setActiveReleaseId(releaseId);
        when(context.formMapper().selectById("form-1"))
                .thenReturn(form);
        when(context.definitionMapper().selectById("entity-1"))
                .thenReturn(entity());
        when(context.userService().getById("user-1"))
                .thenReturn(user());
    }

    /** 预置权限计划 Mock，返回 owner_id 条件与发布版本 */
    private void allowPermissionPlan() {
        when(context.userService().getById("user-1"))
                .thenReturn(user());
        DataPermissionResult permission =
                DataPermissionResult.withCondition(
                        "owner_id = #{permissionParameters.ownerId}", Map.of("ownerId", "user-1"));
        permission.setMatchedRuleNames(List.of("owner-rule"));
        permission.setReleaseVersion(8);
        when(context.dataPermissionEngine().calculatePermission(
                "expense",
                null,
                user()))
                .thenReturn(permission);
    }

    /** 构造表单类型的数据源执行请求 */
    private UiExtensionExecuteRequest request(
            String usage,
            String formId,
            String releaseId) {
        UiExtensionExecuteRequest request =
                new UiExtensionExecuteRequest();
        request.setUsage(usage);
        request.setOperationCode("query");
        request.setConfigType("FORM");
        request.setConfigId(formId);
        request.setTargetType("NODE");
        request.setTargetKey("field-1");
        request.setReleaseId(releaseId);
        request.setContext(Map.of());
        request.setInput(Map.of());
        return request;
    }

    /** 构造列表类型的数据源执行请求 */
    private UiExtensionExecuteRequest listRequest() {
        UiExtensionExecuteRequest request =
                new UiExtensionExecuteRequest();
        request.setUsage("LIST_QUERY");
        request.setOperationCode("query");
        request.setConfigType("LIST");
        request.setConfigId("list-1");
        request.setTargetType("OWNER");
        request.setEntityCode("expense");
        request.setListKey("default");
        request.setContext(Map.of());
        request.setInput(Map.of());
        return request;
    }

    /** 构造带类型与作用域的数据源定义 */
    private UiExtensionDefinition definition(
            String sourceType,
            String scopeType,
            String scopeId) {
        UiExtensionDefinition definition =
                new UiExtensionDefinition();
        definition.setId("source-1");
        definition.setSourceType(sourceType);
        definition.setScopeType(scopeType);
        definition.setScopeId(scopeId);
        definition.setOperationCode("query");
        definition.setEnabled(true);
        definition.setRevision(2);
        return definition;
    }

    /** 构造测试实体定义（编码 expense） */
    private EntityDefinition entity() {
        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-1");
        entity.setEntityCode("expense");
        return entity;
    }

    /** 构造测试列表配置 */
    private EntityListConfig list() {
        EntityListConfig list = new EntityListConfig();
        list.setId("list-1");
        list.setEntityId("entity-1");
        list.setEntityCode("expense");
        list.setListKey("default");
        list.setActiveReleaseId("list-release-1");
        return list;
    }

    /** 构造带组织/部门的测试用户 */
    private SysUser user() {
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setUsername("tester");
        user.setStatus(SysUser.Status.ENABLED.getValue());
        user.setOrgId("org-1");
        user.setDeptId("dept-1");
        return user;
    }

    /** 装配被测访问控制服务及其 Mock 依赖，返回测试上下文 */
    private TestContext context() {
        UiConfigReleaseMapper releaseMapper =
                mock(UiConfigReleaseMapper.class);
        EntityFormMapper formMapper =
                mock(EntityFormMapper.class);
        EntityFormNodeMapper formNodeMapper =
                mock(EntityFormNodeMapper.class);
        EntityListConfigMapper listMapper =
                mock(EntityListConfigMapper.class);
        EntityListFieldMapper listFieldMapper =
                mock(EntityListFieldMapper.class);
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        SysMenuMapper menuMapper =
                mock(SysMenuMapper.class);
        SysUserService userService =
                mock(SysUserService.class);
        DataPermissionEngine dataPermissionEngine =
                mock(DataPermissionEngine.class);
        UiConfigurationAccessService configurationAccessService =
                mock(UiConfigurationAccessService.class);
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        ObjectMapper objectMapper =
                new ObjectMapper().findAndRegisterModules();
        JsonDocumentCodec codec =
                new JsonDocumentCodec(objectMapper);
        when(releaseService.verifiedReleaseSnapshot(any()))
                .thenAnswer(invocation -> {
                    UiConfigRelease release =
                            invocation.getArgument(0);
                    return codec.readObject(
                            release.getSnapshotDocument(),
                            "测试发布快照");
                });
        UiDataSourceExecutionAccessService service =
                new UiDataSourceExecutionAccessService(
                        releaseMapper,
                        new UiDataSourceBindingMatcher(
                                mock(UiEventBindingMapper.class),
                                codec,
                                objectMapper),
                        formMapper,
                        formNodeMapper,
                                listMapper,
                        listFieldMapper,
                        definitionMapper,
                        menuMapper,
                        userService,
                        dataPermissionEngine,
                        configurationAccessService,
                        releaseService,
                        objectMapper);
        return new TestContext(
                service,
                releaseMapper,
                formMapper,
                formNodeMapper,
                listMapper,
                definitionMapper,
                menuMapper,
                userService,
                dataPermissionEngine,
                configurationAccessService,
                releaseService,
                codec);
    }

    /** 测试上下文记录，聚合被测服务与各 Mock 依赖 */
    private record TestContext(
            UiDataSourceExecutionAccessService service,
            UiConfigReleaseMapper releaseMapper,
            EntityFormMapper formMapper,
            EntityFormNodeMapper formNodeMapper,
            EntityListConfigMapper listMapper,
            EntityDefinitionMapper definitionMapper,
            SysMenuMapper menuMapper,
            SysUserService userService,
            DataPermissionEngine dataPermissionEngine,
            UiConfigurationAccessService configurationAccessService,
            UiConfigReleaseService releaseService,
            JsonDocumentCodec codec) {
    }
}
