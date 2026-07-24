package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.BusinessConflictException;
import com.workflow.common.BusinessForbiddenException;
import com.workflow.common.UserContext;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.UiDataSourceExecuteRequest;
import com.workflow.dto.permission.DataPermissionResult;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormNode;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.SysUser;
import com.workflow.entity.UiConfigRelease;
import com.workflow.entity.UiDataSourceDefinition;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFormFieldMapper;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityFormNodeMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListFieldMapper;
import com.workflow.mapper.SysMenuMapper;
import com.workflow.mapper.UiConfigReleaseMapper;
import com.workflow.service.permission.DataPermissionEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UI 数据源执行访问控制服务测试。
 *
 * <p>被测对象：{@link UiDataSourceExecutionAccessService}，覆盖发布执行需可验证来源、绑定校验、
 * 缺失激活发布拒绝、客户端过期发布声明拒绝、连接器上下文防伪造、服务端幂等种子校验、
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
                {"FIELD_OPTIONS":{"sourceId":"different-source"}}
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
                {"FIELD_OPTIONS":{"sourceId":"source-1"}}
                """);
        UiDataSourceExecuteRequest request =
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

    /** 测试拒绝连接器客户端伪造租户元数据：验证 input 含 orgId 时抛出上下文伪造异常 */
    @Test
    void rejectsConnectorClientSpoofingTenantMetadata() {
        UiDataSourceExecuteRequest request =
                request("BEFORE_SUBMIT", "form-1", null);
        request.setInput(Map.of(
                "orgId",
                "forged-tenant"));

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().authorizePublished(
                        definition(
                                "INTEGRATION_CONNECTOR",
                                "GLOBAL",
                                null),
                        request));

        assertEquals(
                "UI_DATA_SOURCE_EXECUTION_CONTEXT_SPOOFED",
                exception.getErrorCode());
        verifyNoInteractions(context.releaseMapper());
    }

    /** 测试拒绝连接器客户端伪造幂等键：验证 input 含 idempotencyKey 时抛出上下文伪造异常 */
    @Test
    void rejectsConnectorClientSpoofingIdempotencyKey() {
        UiDataSourceExecuteRequest request =
                request("BEFORE_SUBMIT", "form-1", null);
        request.setInput(Map.of(
                "idempotencyKey",
                "client-forged"));

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> context.service().authorizePublished(
                        definition(
                                "INTEGRATION_CONNECTOR",
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
                {"BEFORE_SUBMIT":{"sourceId":"source-1"}}
                """);
        UiDataSourceExecuteRequest request =
                request("BEFORE_SUBMIT", "form-1", "release-1");
        request.setServerIdempotencyKey("server-seed");
        request.setInput(Map.of(
                "idempotencyKey",
                "server-seed"));

        UiDataSourceExecutionAuthorization authorization =
                context.service().authorizePublished(
                        definition(
                                "INTEGRATION_CONNECTOR",
                                "GLOBAL",
                                null),
                        request);

        assertEquals(
                "server-seed",
                authorization.idempotencySeed());
    }

    /** 测试忽略客户端写入服务端幂等种子的尝试：验证反序列化后 serverIdempotencyKey 为 null */
    @Test
    void ignoresClientAttemptToWriteServerIdempotencySeed() throws Exception {
        UiDataSourceExecuteRequest request =
                new ObjectMapper().readValue(
                        """
                        {
                          "usage":"BEFORE_SUBMIT",
                          "serverIdempotencyKey":"client-forged"
                        }
                        """,
                        UiDataSourceExecuteRequest.class);

        assertNull(request.getServerIdempotencyKey());
    }

    /** 测试忽略客户端启用钉版发布的尝试：验证反序列化后 serverPinnedRelease 为 false */
    @Test
    void ignoresClientAttemptToEnablePinnedReleaseExecution()
            throws Exception {
        UiDataSourceExecuteRequest request =
                new ObjectMapper().readValue(
                        """
                        {
                          "usage":"BEFORE_SUBMIT",
                          "serverPinnedRelease":true
                        }
                        """,
                        UiDataSourceExecuteRequest.class);

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
                {"BEFORE_SUBMIT":{"sourceId":"source-1"}}
                """);
        when(context.releaseMapper().selectById("release-3"))
                .thenReturn(historical);
        UiDataSourceExecuteRequest request =
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
                                "INTEGRATION_CONNECTOR",
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
                {"BEFORE_SUBMIT":{"sourceId":"source-1"}}
                """);
        when(context.releaseMapper().selectById("release-3"))
                .thenReturn(historical);
        UiDataSourceExecuteRequest request =
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
                                "INTEGRATION_CONNECTOR",
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
        node.setDataSourceBindingsDocument(
                """
                {"FIELD_OPTIONS":{"sourceId":"source-1"}}
                """);
        when(context.formNodeMapper().findByFormId("form-1"))
                .thenReturn(List.of(node));
        when(context.formFieldMapper().selectByFormId("form-1"))
                .thenReturn(List.of());
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

    /** 测试构建权限计划并净化不可信运行时上下文：验证用户与计划来自服务端，客户端伪造的 userId 被剔除 */
    @Test
    void buildsPermissionPlanAndSanitizesUntrustedRuntimeContext() {
        allowPublishedForm(
                "release-1",
                """
                {"FIELD_OPTIONS":{"sourceId":"source-1"}}
                """);
        allowPermissionPlan();
        UiDataSourceExecuteRequest request =
                request("FIELD_OPTIONS", "form-1", "release-1");
        request.setEntityCode("expense");
        request.setContext(Map.of(
                "formId", "form-1",
                "mode", "edit",
                "userId", "forged-user"));

        UiDataSourceExecutionAuthorization authorization =
                context.service().authorizePublished(
                        definition("STATIC_OPTIONS", "GLOBAL", null),
                        request);

        assertFalse(authorization.preview());
        assertEquals("release-1", authorization.releaseId());
        assertEquals("user-1", authorization.user().getId());
        assertEquals("org-1", authorization.user().getOrgId());
        assertEquals(
                "owner_id = 'user-1'",
                authorization.dataScopePlan().sqlFragment());
        assertEquals("edit", authorization.requestContext().get("mode"));
        assertFalse(authorization.requestContext().containsKey("userId"));
    }

    /** 测试即使绑定存在也拒绝数据源作用域不匹配：验证抛出 UI_DATA_SOURCE_SCOPE_MISMATCH */
    @Test
    void rejectsDataSourceScopeMismatchEvenWhenBindingExists() {
        allowPublishedForm(
                "release-1",
                """
                {"FIELD_OPTIONS":{"sourceId":"source-1"}}
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
                        "owner_id = 'user-1'");
        permission.setMatchedRuleNames(List.of("owner-rule"));
        permission.setReleaseVersion(8);
        when(context.dataPermissionEngine().calculatePermission(
                "expense",
                null,
                user()))
                .thenReturn(permission);
    }

    /** 构造表单类型的数据源执行请求 */
    private UiDataSourceExecuteRequest request(
            String usage,
            String formId,
            String releaseId) {
        UiDataSourceExecuteRequest request =
                new UiDataSourceExecuteRequest();
        request.setUsage(usage);
        request.setConfigType("FORM");
        request.setConfigId(formId);
        request.setReleaseId(releaseId);
        request.setContext(formId == null
                ? Map.of()
                : Map.of("formId", formId));
        request.setInput(Map.of());
        return request;
    }

    /** 构造列表类型的数据源执行请求 */
    private UiDataSourceExecuteRequest listRequest() {
        UiDataSourceExecuteRequest request =
                new UiDataSourceExecuteRequest();
        request.setUsage("LIST_QUERY");
        request.setConfigType("LIST");
        request.setConfigId("list-1");
        request.setEntityCode("expense");
        request.setListKey("default");
        request.setContext(Map.of("listConfigId", "list-1"));
        request.setInput(Map.of());
        return request;
    }

    /** 构造带类型与作用域的数据源定义 */
    private UiDataSourceDefinition definition(
            String sourceType,
            String scopeType,
            String scopeId) {
        UiDataSourceDefinition definition =
                new UiDataSourceDefinition();
        definition.setId("source-1");
        definition.setSourceType(sourceType);
        definition.setScopeType(scopeType);
        definition.setScopeId(scopeId);
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
        EntityFormFieldMapper formFieldMapper =
                mock(EntityFormFieldMapper.class);
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
                        formMapper,
                        formNodeMapper,
                        formFieldMapper,
                        listMapper,
                        listFieldMapper,
                        definitionMapper,
                        menuMapper,
                        userService,
                        dataPermissionEngine,
                        configurationAccessService,
                        releaseService,
                        codec,
                        objectMapper);
        return new TestContext(
                service,
                releaseMapper,
                formMapper,
                formNodeMapper,
                formFieldMapper,
                listMapper,
                definitionMapper,
                menuMapper,
                userService,
                dataPermissionEngine,
                configurationAccessService,
                codec);
    }

    /** 测试上下文记录，聚合被测服务与各 Mock 依赖 */
    private record TestContext(
            UiDataSourceExecutionAccessService service,
            UiConfigReleaseMapper releaseMapper,
            EntityFormMapper formMapper,
            EntityFormNodeMapper formNodeMapper,
            EntityFormFieldMapper formFieldMapper,
            EntityListConfigMapper listMapper,
            EntityDefinitionMapper definitionMapper,
            SysMenuMapper menuMapper,
            SysUserService userService,
            DataPermissionEngine dataPermissionEngine,
            UiConfigurationAccessService configurationAccessService,
            JsonDocumentCodec codec) {
    }
}
