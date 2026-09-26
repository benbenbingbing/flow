package com.workflow.entity.list.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.entity.list.spi.EntityListDataProvider;
import com.workflow.contracts.entity.list.spi.EntityListSchemaProvider;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.list.api.request.EntityListQueryRequest;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.permission.application.model.DataPermissionResult;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import com.workflow.entity.permission.application.DataPermissionEngine;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityListActionConfigService;
import com.workflow.entity.permission.application.EntityListScopeAuditService;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import com.workflow.entity.ui.application.UiEventRuntimeService;
import com.workflow.entity.ui.application.UiViewCompositionTokenService;
import com.workflow.entity.ui.api.response.UiEventExecutionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class EntityListRuntimeViewCompositionTest {

    private EntityDataListConfigService dataListService;
    private EntityDataDynamicService dynamicService;
    private SystemEntityReadService systemEntityReadService;
    private EntityDefinitionMapper definitionMapper;
    private EntityListFieldMapper fieldMapper;
    private EntityListConfigMapper listConfigMapper;
    private SysUserService sysUserService;
    private DataPermissionEngine dataPermissionEngine;
    private EntityActionCapabilityService actionCapabilityService;
    private EntityListPublishedRuntimeService publishedRuntimeService;
    private EntityListPageResultNormalizer pageResultNormalizer;
    private UiEventRuntimeService uiEventRuntimeService;
    private UiInterfaceExtensionService uiDataSourceService;
    private UiViewCompositionTokenService tokenService;
    private List<EntityListDataProvider> dataProviders;
    private List<EntityListSchemaProvider> schemaProviders;
    private EntityListRuntimeService service;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("runtime-user", "reader");
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        when(menuMapper.selectPermsByUserId("runtime-user"))
                .thenReturn(Set.of("*"));
        ReflectionTestUtils.setField(
                PermissionUtil.class,
                "staticMenuMapper",
                menuMapper);

        dataListService = mock(EntityDataListConfigService.class);
        dynamicService = mock(EntityDataDynamicService.class);
        systemEntityReadService = mock(SystemEntityReadService.class);
        definitionMapper = mock(EntityDefinitionMapper.class);
        fieldMapper = mock(EntityListFieldMapper.class);
        listConfigMapper = mock(EntityListConfigMapper.class);
        sysUserService = mock(SysUserService.class);
        dataPermissionEngine = mock(DataPermissionEngine.class);
        actionCapabilityService = mock(
                EntityActionCapabilityService.class);
        publishedRuntimeService =
                mock(EntityListPublishedRuntimeService.class);
        pageResultNormalizer = new EntityListPageResultNormalizer();
        uiEventRuntimeService = mock(UiEventRuntimeService.class);
        when(uiEventRuntimeService.execute(any(), any())).thenAnswer(invocation -> {
            com.workflow.entity.ui.api.request.UiEventExecuteRequest event = invocation.getArgument(0);
            Function<Map<String, Object>, Object> handler = invocation.getArgument(1);
            if (event == null || handler == null) return new UiEventExecutionResult();
            UiEventExecutionResult result = new UiEventExecutionResult();
            result.setData(handler.apply(event.getInput()));
            return result;
        });
        uiDataSourceService = mock(UiInterfaceExtensionService.class);
        tokenService = mock(UiViewCompositionTokenService.class);
        dataProviders = new ArrayList<>();
        schemaProviders = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        service = new EntityListRuntimeService(
                dataListService,
                dynamicService,
                systemEntityReadService,
                mock(EntityListConfigService.class),
                definitionMapper,
                mock(EntityFieldMapper.class),
                mock(SystemEntityFieldPolicy.class),
                fieldMapper,
                listConfigMapper,
                sysUserService,
                dataPermissionEngine,
                mock(EntityListScopeAuditService.class),
                actionCapabilityService,
                objectMapper,
                new JsonDocumentCodec(objectMapper),
                mock(EntityListActionConfigService.class),
                publishedRuntimeService,
                pageResultNormalizer,
                uiEventRuntimeService,
                mock(CurrentUserRoleService.class),
                tokenService,
                List.of(),
                dataProviders,
                schemaProviders);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 入口标识只作为查询上下文，不限制列表加载或数据查询。 */
    @ParameterizedTest
    @ValueSource(strings = {"MENU", "PAGE", "DIALOG", "DRAWER", "EMBEDDED",
            "FORM_PICKER", "SUB_TABLE", "CUSTOM_ENTRY"})
    void schemaAndQueryAcceptAnyEntry(String scene) {
        EntityListConfig published = publishedList();
        configureActiveList(published);
        when(publishedRuntimeService.resolveFields(eq(published), any()))
                .thenReturn(List.of());
        PageResult<EntityDataDTO> expected = new PageResult<>(
                List.of(record("record-1", "可见记录")), 1, 1, 10);
        when(dataListService.findPageWithResolvedConfig(
                "target_entity", "default", published, Map.of(), 1, 10))
                .thenReturn(expected);
        when(uiEventRuntimeService.execute(any(), any()))
                .thenAnswer(invocation -> {
                    com.workflow.entity.ui.api.request.UiEventExecuteRequest event =
                            invocation.getArgument(0);
                    assertEquals(scene, event.getInput().get("scene"));
                    Function<Map<String, Object>, Object> handler = invocation.getArgument(1);
                    UiEventExecutionResult result = new UiEventExecutionResult();
                    result.setData(handler.apply(event.getInput()));
                    return result;
                });

        assertEquals(scene, service.schema("target_entity", "default", scene).getScene());
        EntityListQueryRequest request = new EntityListQueryRequest();
        request.setScene(scene);
        PageResult<?> result = (PageResult<?>) service.query("target_entity", "default", request);
        assertEquals(1, result.getTotal());
        assertEquals(expected.getRecords(), result.getRecords());
        verify(dataListService).findPageWithResolvedConfig(
                "target_entity", "default", published, Map.of(), 1, 10);
    }

    /** 去掉入口限制不影响列表访问权限，未授权请求仍在查询前被拒绝。 */
    @Test
    void listAccessPermissionStillAppliesToEveryEntry() {
        configureActiveList(publishedList());
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        when(menuMapper.selectPermsByUserId("runtime-user")).thenReturn(Set.of());
        ReflectionTestUtils.setField(PermissionUtil.class, "staticMenuMapper", menuMapper);
        EntityListQueryRequest request = new EntityListQueryRequest();
        request.setScene("CUSTOM_ENTRY");

        assertThrows(ForbiddenException.class,
                () -> service.schema("target_entity", "default", "CUSTOM_ENTRY"));
        assertThrows(ForbiddenException.class,
                () -> service.query("target_entity", "default", request));
        verify(dataListService, never()).findPageWithResolvedConfig(
                any(), any(), any(), anyMap(), anyLong(), anyLong());
    }

    @Test
    void matchNoneTokenReturnsEmptyWithoutExecutingTargetQuery() {
        UiViewCompositionTokenService.Claims claims = claims(
                Map.of(), true);
        when(tokenService.verifyListContext("signed-list-context"))
                .thenReturn(claims);
        EntityListConfig published = publishedList();
        configurePinnedList(published);
        EntityListQueryRequest request = new EntityListQueryRequest();
        request.setViewCompositionContextToken("signed-list-context");

        Object result = service.query(
                "target_entity", "default", request);

        PageResult<?> page = (PageResult<?>) result;
        assertEquals(0, page.getTotal());
        assertEquals(List.of(), page.getRecords());
        verify(publishedRuntimeService).resolveViewCompositionConfig(
                org.mockito.ArgumentMatchers.any(EntityListConfig.class),
                org.mockito.ArgumentMatchers.eq("target-release"),
                org.mockito.ArgumentMatchers.eq(3));
        verify(dataListService, never()).findPageWithResolvedConfig(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void requestCannotReplaceTokenPinnedTargetRelease() {
        when(tokenService.verifyListContext("signed-list-context"))
                .thenReturn(claims(Map.of("projectId", "p-1"), false));
        EntityListQueryRequest request = new EntityListQueryRequest();
        request.setViewCompositionContextToken("signed-list-context");
        request.setReleaseId("attacker-selected-release");

        assertThrows(
                ForbiddenException.class,
                () -> service.query(
                        "target_entity", "default", request));

        verify(publishedRuntimeService, never())
                .resolveViewCompositionConfig(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void signedFiltersAreCombinedWithPublishedFixedFilters() {
        when(tokenService.verifyListContext("signed-list-context"))
                .thenReturn(claims(
                        Map.of(
                                "projectId", "project-1",
                                "projectId_op", "EQ"),
                        false));
        EntityListConfig published = publishedList();
        published.setFixedFilterConfig("{\"status\":\"ACTIVE\"}");
        configurePinnedList(published);
        PageResult<EntityDataDTO> page =
                new PageResult<>(List.of(), 0, 1, 10);
        Map<String, Object> expectedFilters = Map.of(
                "status", "ACTIVE",
                "status_op", "EQ",
                "projectId", "project-1",
                "projectId_op", "EQ");
        when(dataListService.findPageWithResolvedConfig(
                "target_entity",
                "default",
                published,
                expectedFilters,
                1,
                10)).thenReturn(page);
        EntityListQueryRequest request = new EntityListQueryRequest();
        request.setViewCompositionContextToken("signed-list-context");

        assertEquals(
                page,
                service.query("target_entity", "default", request));
        verify(dataListService).findPageWithResolvedConfig(
                "target_entity",
                "default",
                published,
                expectedFilters,
                1,
                10);
    }

    @Test
    void compositionProviderFailsClosedWhenAnyCandidateIsOutsidePlatformScope() {
        when(tokenService.verifyListContext("signed-list-context"))
                .thenReturn(claims(Map.of(), false));
        EntityListConfig published = publishedList();
        published.setQueryProviderCode("malicious-provider");
        configurePinnedList(published);
        EntityListDataProvider provider = provider(
                "malicious-provider",
                Map.of(
                        "records", List.of(
                                Map.of("id", "allowed-1"),
                                Map.of("id", "foreign-1")),
                        "total", 999));
        dataProviders.add(provider);
        stubProviderPermission();
        when(dataListService.findPageWithResolvedConfig(
                eq("target_entity"),
                eq("default"),
                eq(published),
                anyMap(),
                eq(1L),
                eq(2L)))
                .thenReturn(new PageResult<>(
                        List.of(record("allowed-1", "可见记录")),
                        1,
                        1,
                        2));

        EntityListQueryRequest request = compositionRequest(1, 10);

        assertThrows(
                ForbiddenException.class,
                () -> service.query(
                        "target_entity", "default", request));
        verify(dataListService).findPageWithResolvedConfig(
                eq("target_entity"),
                eq("default"),
                eq(published),
                org.mockito.ArgumentMatchers.argThat(filters ->
                        "IN".equals(filters.get("id_op"))
                                && Set.copyOf((List<?>) filters.get("id"))
                                .equals(Set.of(
                                        "allowed-1", "foreign-1"))),
                eq(1L),
                eq(2L));
    }

    @Test
    void compositionConnectorFailsClosedWhenCandidateIsOutsidePlatformScope() {
        when(tokenService.verifyListContext("signed-list-context"))
                .thenReturn(claims(Map.of(), false));
        EntityListConfig published = publishedList();
        published.setQueryInterfaceExtensionId("connector-query");
        configurePinnedList(published);
        stubReplacement(Map.of("records", List.of(Map.of("recordId", "foreign-1")), "total", 80));
        when(dataListService.findPageWithResolvedConfig(
                eq("target_entity"),
                eq("default"),
                eq(published),
                anyMap(),
                eq(1L),
                eq(1L)))
                .thenReturn(new PageResult<>(
                        List.of(), 0, 1, 1));

        assertThrows(
                ForbiddenException.class,
                () -> service.query(
                        "target_entity",
                        "default",
                        compositionRequest(1, 10)));
    }

    @Test
    void compositionProviderReturnsOnlyPlatformVerifiedPublishedFieldsAndSafeTotal() {
        when(tokenService.verifyListContext("signed-list-context"))
                .thenReturn(claims(
                        Map.of(
                                "projectId", "project-1",
                                "projectId_op", "EQ"),
                        false));
        EntityListConfig published = publishedList();
        published.setQueryProviderCode("ordered-provider");
        configurePinnedList(published);
        configureVisibleFields(
                published,
                "name",
                "publicValue");
        dataProviders.add(provider(
                "ordered-provider",
                Map.of(
                        "records", List.of(
                                Map.of(
                                        "id", "record-2",
                                        "tenantSecret", "connector-secret"),
                                Map.of(
                                        "id", "record-1",
                                        "data", Map.of(
                                                "publicValue", "伪造值",
                                                "hiddenValue", "connector-secret"))),
                        "total", 123456)));
        stubProviderPermission();
        EntityDataDTO first = record(
                "record-1", "平台记录一");
        first.setData(Map.of(
                "projectId", "project-1",
                "publicValue", "平台公开值一",
                "hiddenValue", "平台敏感值一"));
        EntityDataDTO second = record(
                "record-2", "平台记录二");
        second.setData(Map.of(
                "projectId", "project-1",
                "publicValue", "平台公开值二",
                "hiddenValue", "平台敏感值二"));
        when(dataListService.findPageWithResolvedConfig(
                eq("target_entity"),
                eq("default"),
                eq(published),
                anyMap(),
                eq(1L),
                eq(2L)))
                .thenReturn(new PageResult<>(
                        List.of(first, second), 2, 1, 2));

        PageResult<?> page = (PageResult<?>) service.query(
                "target_entity",
                "default",
                compositionRequest(1, 10));

        assertEquals(2, page.getTotal());
        assertEquals(1, page.getPageNum());
        assertEquals(10, page.getPageSize());
        assertEquals(2, page.getRecords().size());
        Map<?, ?> firstOutput =
                (Map<?, ?>) page.getRecords().get(0);
        Map<?, ?> secondOutput =
                (Map<?, ?>) page.getRecords().get(1);
        assertEquals("record-2", firstOutput.get("id"));
        assertEquals("平台记录二", firstOutput.get("name"));
        assertEquals(
                Map.of("publicValue", "平台公开值二"),
                firstOutput.get("data"));
        assertFalse(firstOutput.containsKey("tenantSecret"));
        assertEquals("record-1", secondOutput.get("id"));
        assertEquals(
                Map.of("publicValue", "平台公开值一"),
                secondOutput.get("data"));
    }

    @Test
    void compositionProviderUsesVerifiedWindowInsteadOfUntrustedTotal() {
        when(tokenService.verifyListContext("signed-list-context"))
                .thenReturn(claims(Map.of(), false));
        EntityListConfig published = publishedList();
        published.setQueryProviderCode("paged-provider");
        configurePinnedList(published);
        configureVisibleFields(published, "name");
        dataProviders.add(provider(
                "paged-provider",
                Map.of(
                        "records", List.of(
                                Map.of("id", "record-1"),
                                Map.of("id", "record-2")),
                        "total", 9000)));
        stubProviderPermission();
        when(dataListService.findPageWithResolvedConfig(
                eq("target_entity"),
                eq("default"),
                eq(published),
                anyMap(),
                eq(1L),
                eq(2L)))
                .thenReturn(new PageResult<>(
                        List.of(
                                record("record-1", "一"),
                                record("record-2", "二")),
                        2,
                        1,
                        2));

        PageResult<?> page = (PageResult<?>) service.query(
                "target_entity",
                "default",
                compositionRequest(3, 2));

        // 第 3 页已验证两条记录，只暴露下一页哨兵，不泄露外部 total=9000。
        assertEquals(7, page.getTotal());
        assertEquals(3, page.getPageNum());
        assertEquals(2, page.getPageSize());
    }

    @Test
    void ordinaryCustomProviderRecomputesCapabilitiesFromAuthoritativeRows() {
        EntityListConfig published = publishedList();
        published.setQueryProviderCode("legacy-provider");
        configureActiveList(published);
        Map<String, Object> providerResult = Map.of(
                "records", List.of(Map.of(
                        "id", "record-1",
                        "actionCapabilities", Map.of(
                                "delete", Map.of(
                                        "visible", true,
                                        "enabled", true)),
                        "legacyField", "legacy-value")),
                "total", 88);
        dataProviders.add(provider(
                "legacy-provider", providerResult));
        stubProviderPermission();
        when(uiEventRuntimeService.execute(any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Function<Map<String, Object>, Object> handler =
                            invocation.getArgument(1);
                    UiEventExecutionResult result =
                            new UiEventExecutionResult();
                    result.setData(handler.apply(
                            invocation.<com.workflow.entity.ui.api.request.UiEventExecuteRequest>
                                            getArgument(0)
                                    .getInput()));
                    return result;
                });
        EntityDataDTO authoritative = record(
                "record-1", "平台记录");
        when(dynamicService.findAccessibleById(
                "target_entity", "record-1", "default"))
                .thenReturn(authoritative);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<EntityDataDTO> rows = invocation.getArgument(2);
            rows.get(0).setActionCapabilities(Map.of(
                    "view", EntityActionCapabilityDTO.allowed()));
            return null;
        }).when(actionCapabilityService).enrichRows(
                eq("target_entity"), eq(published), anyList());

        PageResult<?> page = (PageResult<?>) service.query(
                "target_entity",
                "default",
                new EntityListQueryRequest());

        assertEquals(88, page.getTotal());
        assertEquals(
                "legacy-value",
                ((Map<?, ?>) page.getRecords().get(0))
                        .get("legacyField"));
        assertEquals(Set.of("view"),
                ((Map<?, ?>) ((Map<?, ?>) page.getRecords().get(0))
                        .get("actionCapabilities")).keySet());
        verify(dynamicService).findAccessibleById(
                "target_entity", "record-1", "default");
        verify(actionCapabilityService).enrichRows(
                eq("target_entity"), eq(published), anyList());
        verify(dataListService, never())
                .findPageWithResolvedConfig(
                        any(), any(), any(), anyMap(),
                        anyLong(), anyLong());
    }

    @Test
    void listLoadReplacementCannotSupplyActionCapabilities() {
        EntityListConfig published = publishedList();
        configureActiveList(published);
        UiEventExecutionResult replacement =
                new UiEventExecutionResult();
        replacement.setReplaced(true);
        replacement.setData(Map.of(
                "records", List.of(Map.of(
                        "id", "record-1",
                        "actionCapabilities", Map.of(
                                "delete", Map.of(
                                        "visible", true,
                                        "enabled", true)))),
                "total", 1));
        when(uiEventRuntimeService.execute(any(), any()))
                .thenReturn(replacement);
        EntityDataDTO authoritative = record(
                "record-1", "平台记录");
        when(dynamicService.findAccessibleById(
                "target_entity", "record-1", "default"))
                .thenReturn(authoritative);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<EntityDataDTO> rows = invocation.getArgument(2);
            rows.get(0).setActionCapabilities(Map.of(
                    "view", EntityActionCapabilityDTO.allowed()));
            return null;
        }).when(actionCapabilityService).enrichRows(
                eq("target_entity"), eq(published), anyList());

        PageResult<?> page = (PageResult<?>) service.query(
                "target_entity", "default",
                new EntityListQueryRequest());

        Map<?, ?> capabilities = (Map<?, ?>) ((Map<?, ?>)
                page.getRecords().get(0)).get("actionCapabilities");
        assertEquals(Set.of("view"), capabilities.keySet());
        verify(dynamicService).findAccessibleById(
                "target_entity", "record-1", "default");
    }

    @Test
    void systemEntityCompositionRejectsCustomQueryOverride() {
        when(tokenService.verifyListContext("signed-list-context"))
                .thenReturn(claims(Map.of(), false));
        EntityListConfig published = publishedList();
        published.setQueryInterfaceExtensionId("connector-query");
        configurePinnedList(published);
        EntityDefinition system = definition(
                EntityDefinition.StorageMode.SYSTEM);
        when(definitionMapper.findByEntityCode("target_entity"))
                .thenReturn(Optional.of(system));

        assertThrows(
                IllegalStateException.class,
                () -> service.query(
                        "target_entity",
                        "default",
                        compositionRequest(1, 10)));
        verify(uiDataSourceService, never())
                .execute(any(), any());
        verify(dataListService, never())
                .findPageWithResolvedConfig(
                        any(), any(), any(), anyMap(),
                        anyLong(), anyLong());
    }

    @Test
    void systemEntityCompositionUsesPlatformReadServiceWithoutExtraRecheck() {
        when(tokenService.verifyListContext("signed-list-context"))
                .thenReturn(claims(Map.of(), false));
        EntityListConfig published = publishedList();
        configurePinnedList(published);
        EntityDefinition system = definition(
                EntityDefinition.StorageMode.SYSTEM);
        when(definitionMapper.findByEntityCode("target_entity"))
                .thenReturn(Optional.of(system));
        when(systemEntityReadService.isSystemEntity("target_entity"))
                .thenReturn(true);
        PageResult<EntityDataDTO> systemPage =
                new PageResult<>(
                        List.of(record(
                                "system-1", "系统记录")),
                        1,
                        1,
                        10);
        when(systemEntityReadService.findPage(
                eq("target_entity"),
                eq(Map.of()),
                eq(1L),
                eq(10L),
                isNull(),
                isNull()))
                .thenReturn(systemPage);

        PageResult<?> result = (PageResult<?>) service.query(
                "target_entity",
                "default",
                compositionRequest(1, 10));

        assertEquals(1, result.getTotal());
        Map<?, ?> row = (Map<?, ?>) result.getRecords().get(0);
        Map<?, ?> capabilities =
                (Map<?, ?>) row.get("actionCapabilities");
        EntityActionCapabilityDTO view =
                (EntityActionCapabilityDTO) capabilities.get("view");
        assertTrue(view.isVisible());
        assertTrue(view.isEnabled());
        assertEquals(Set.of("view"), capabilities.keySet());
        verify(dataListService, never())
                .findPageWithResolvedConfig(
                        any(), any(), any(), anyMap(),
                        anyLong(), anyLong());
    }

    @Test
    void embedPinnedQueryCombinesPublishedClientAndTrustedContextFilters() {
        EntityListConfig published = publishedList();
        published.setFixedFilterConfig("{\"status\":\"ACTIVE\"}");
        configurePinnedList(published);
        EntityListField queryField = new EntityListField();
        queryField.setFieldCode("title");
        queryField.setIsQuery(true);
        when(fieldMapper.findByListConfigId("target-list")).thenReturn(List.of());
        when(publishedRuntimeService.resolveFields(eq(published), anyList()))
                .thenReturn(List.of(queryField));
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("title", "pump");
        expected.put("status", "ACTIVE");
        expected.put("status_op", "EQ");
        expected.put("supplier_id", "S-10086");
        expected.put("supplier_id_op", "EQ");
        PageResult<EntityDataDTO> page = new PageResult<>(List.of(), 0, 1, 20);
        when(dataListService.findPageWithResolvedConfig(
                "target_entity", "default", published, expected, 1, 20))
                .thenReturn(page);

        Object result = service.queryPinned(
                "target_entity", "default", "target-release", 3,
                1, 20,
                Map.of("title", "pump"),
                Map.of("supplier_id", "S-10086", "supplier_id_op", "EQ"));

        assertEquals(page, result);
        verify(dataListService).findPageWithResolvedConfig(
                "target_entity", "default", published, expected, 1, 20);
        verify(uiEventRuntimeService).execute(org.mockito.ArgumentMatchers.argThat(event ->
                event.isServerPinnedRelease() && "target-release".equals(event.getReleaseId())
                        && event.getReleaseVersion() == 3 && "LIST_LOAD".equals(event.getEventCode())), any());
    }

    @Test
    void embedPinnedQueryUsesAndSemanticsForConflictingFixedFilters() {
        EntityListConfig published = publishedList();
        published.setFixedFilterConfig("{\"supplier_id\":\"S-1\"}");
        configurePinnedList(published);

        PageResult<?> result = (PageResult<?>) service.queryPinned(
                "target_entity", "default", "target-release", 3,
                1, 20, Map.of(), Map.of("supplier_id", "S-2"));

        assertEquals(List.of(), result.getRecords());
        assertEquals(0, result.getTotal());
        verify(dataListService, never()).findPageWithResolvedConfig(
                any(), any(), any(), anyMap(), anyLong(), anyLong());
    }

    @Test
    void embedPinnedSchemaUsesPublishedCustomComponentWithoutEmbedWhitelist() {
        EntityListConfig published = publishedList();
        published.setCustomComponent("partner-widget");
        configurePinnedList(published);
        EntityListSchemaProvider provider = mock(EntityListSchemaProvider.class);
        when(provider.getCode()).thenReturn("partner-widget");
        when(provider.enhance(any(), anyMap()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        schemaProviders.add(provider);

        var schema = service.schemaPinned(
                "target_entity", "default", "target-release", 3);

        assertEquals("partner-widget", schema.getCustomComponent());
        verify(provider).enhance(any(), anyMap());
        verify(publishedRuntimeService).resolveFields(any(), anyList());
    }

    @Test
    void embedPinnedQueryUsesPublishedProviderWithMappedUserDataScope() {
        EntityListConfig published = publishedList();
        published.setQueryProviderCode("partner-query");
        configurePinnedList(published);
        EntityListDataProvider provider = provider(
                "partner-query",
                Map.of(
                        "records", List.of(Map.of("id", "record-1")),
                        "total", 1));
        dataProviders.add(provider);
        stubProviderPermission();
        when(dynamicService.findAccessibleById("target_entity", "record-1", "default"))
                .thenReturn(record("record-1", "平台记录"));

        PageResult<?> result = (PageResult<?>) service.queryPinned(
                "target_entity", "default", "target-release", 3,
                1, 20, Map.of(), Map.of());

        assertEquals(1, result.getTotal());
        verify(provider).query(any(), any(), anyMap());
        verify(dataListService, never()).findPageWithResolvedConfig(
                any(), any(), any(), anyMap(), anyLong(), anyLong());
    }

    @Test
    void embedPinnedQueryUsesPublishedDataSourceAndFieldExtension() {
        EntityListConfig published = publishedList();
        published.setQueryInterfaceExtensionId("external-source");
        EntityListField field = new EntityListField();
        field.setFieldCode("amount");
        field.setDataSourceType("CUSTOM_PROVIDER");
        field.setRenderComponent("native-rich-column");
        published.setRuntimeFields(List.of(field));
        configurePinnedList(published);
        stubReplacement(Map.of("records", List.of(Map.of("id", "record-1")), "total", 1));
        when(dynamicService.findAccessibleById("target_entity", "record-1", "default"))
                .thenReturn(record("record-1", "平台记录"));

        PageResult<?> result = (PageResult<?>) service.queryPinned(
                "target_entity", "default", "target-release", 3,
                1, 20, Map.of(), Map.of());

        assertEquals(1, result.getTotal());
        verify(uiEventRuntimeService).execute(org.mockito.ArgumentMatchers.argThat(event ->
                event.isServerPinnedRelease() && "LIST_LOAD".equals(event.getEventCode())), any());
        verify(uiDataSourceService, never()).execute(any(), any());
        verify(dataListService, never()).findPageWithResolvedConfig(
                any(), any(), any(), anyMap(), anyLong(), anyLong());
    }

    /** 前置步骤故意清空筛选时，默认查询仍应应用固定值及其运算符。 */
    @ParameterizedTest
    @ValueSource(strings = {"INHERIT", "NARROW", "OVERRIDE"})
    void fixedFiltersSurviveBeforeStepForEveryScopeMode(String mode) {
        EntityListConfig published = publishedList();
        published.setDataScopeMode(mode);
        published.setFixedFilterConfig("{\"status\":\"ACTIVE\"}");
        configureActiveList(published);
        org.mockito.Mockito.doAnswer(invocation -> {
            Function<Map<String, Object>, Object> handler = invocation.getArgument(1);
            UiEventExecutionResult result = new UiEventExecutionResult();
            result.setData(handler.apply(Map.of("filters", Map.of("status", "DELETED", "status_op", "NE"))));
            return result;
        }).when(uiEventRuntimeService).execute(any(), any());
        when(dataListService.findPageWithResolvedConfig(any(), any(), any(), anyMap(), anyLong(), anyLong()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

        service.query("target_entity", "default", new EntityListQueryRequest());

        verify(dataListService).findPageWithResolvedConfig(eq("target_entity"), eq("default"), eq(published),
                eq(Map.of("status", "ACTIVE", "status_op", "EQ")), anyLong(), anyLong());
    }

    /** 页面金额等值查询与发布的金额下界必须同时进入最终数据库查询。 */
    @Test
    void pageQueryIntersectsPublishedFixedRangeOnTheSameField() {
        EntityListConfig published = publishedList();
        published.setFixedFilterConfig("{\"amount_start\":\"2\"}");
        configureActiveList(published);
        EntityListField amount = new EntityListField();
        amount.setFieldCode("amount");
        amount.setIsQuery(true);
        when(publishedRuntimeService.resolveFields(eq(published), any())).thenReturn(List.of(amount));
        when(dataListService.findPageWithResolvedConfig(any(), any(), any(), anyMap(), anyLong(), anyLong()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 10));
        EntityListQueryRequest request = new EntityListQueryRequest();
        request.setFilters(Map.of("amount", "1", "amount_op", "EQ"));

        service.query("target_entity", "default", request);

        verify(dataListService).findPageWithResolvedConfig(eq("target_entity"), eq("default"), eq(published),
                eq(Map.of("amount", "1", "amount_op", "EQ", "amount_start", "2")), anyLong(), anyLong());
    }

    @Test
    void incompatiblePageEqualityCannotBeSilentlyReplacedByPublishedEquality() {
        EntityListConfig published = publishedList();
        published.setFixedFilterConfig("{\"amount\":2}");
        configureActiveList(published);
        EntityListField amount = new EntityListField();
        amount.setFieldCode("amount");
        amount.setIsQuery(true);
        when(publishedRuntimeService.resolveFields(eq(published), any())).thenReturn(List.of(amount));
        EntityListQueryRequest request = new EntityListQueryRequest();
        request.setFilters(Map.of("amount", 1, "amount_op", "EQ"));

        PageResult<?> page = (PageResult<?>) service.query("target_entity", "default", request);

        assertEquals(0, page.getTotal());
        verify(dataListService, never()).findPageWithResolvedConfig(any(), any(), any(), anyMap(), anyLong(), anyLong());
    }

    /** REPLACE 和 AFTER 的结果都由 replaced 标记触发同一服务端复核，不能返回范围外的候选 ID。 */
    @Test
    void replacementCannotBypassPublishedFixedFilters() {
        EntityListConfig published = publishedList();
        published.setFixedFilterConfig("{\"status\":\"ACTIVE\"}");
        configureActiveList(published);
        stubReplacement(Map.of("records", List.of(Map.of("id", "deleted-record")), "total", 100));
        when(dataListService.findPageWithResolvedConfig(any(), any(), any(), anyMap(), anyLong(), anyLong()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 1));

        assertThrows(ForbiddenException.class, () -> service.query(
                "target_entity", "default", new EntityListQueryRequest()));
        verify(dataListService).findPageWithResolvedConfig(eq("target_entity"), eq("default"), eq(published),
                eq(Map.of("status", "ACTIVE", "status_op", "EQ", "id", List.of("deleted-record"), "id_op", "IN")),
                eq(1L), eq(1L));
    }

    @Test
    void conflictingCompositionContextCannotOverrideFixedFilters() {
        EntityListConfig published = publishedList();
        published.setFixedFilterConfig("{\"status\":\"ACTIVE\"}");
        configurePinnedList(published);
        when(tokenService.verifyListContext("signed-list-context"))
                .thenReturn(claims(Map.of("status", "DELETED"), false));

        PageResult<?> result = (PageResult<?>) service.query("target_entity", "default", compositionRequest(1, 10));

        assertEquals(0, result.getTotal());
        verify(uiEventRuntimeService, never()).execute(any(), any());
    }

    private void stubReplacement(Object data) {
        UiEventExecutionResult replacement = new UiEventExecutionResult();
        replacement.setReplaced(true);
        replacement.setData(data);
        org.mockito.Mockito.doReturn(replacement).when(uiEventRuntimeService).execute(any(), any());
    }

    private void configurePinnedList(EntityListConfig published) {
        published.setPinnedRelease(true);
        EntityDefinition definition = definition(
                EntityDefinition.StorageMode.DYNAMIC);
        when(definitionMapper.findByEntityCode("target_entity"))
                .thenReturn(Optional.of(definition));
        EntityListConfig draft = new EntityListConfig();
        draft.setId("target-list");
        when(listConfigMapper.findByEntityIdAndListKey(
                "target-entity-id", "default"))
                .thenReturn(draft);
        when(publishedRuntimeService.resolveViewCompositionConfig(
                draft, "target-release", 3))
                .thenReturn(published);
    }

    private void configureActiveList(EntityListConfig published) {
        EntityDefinition definition = definition(
                EntityDefinition.StorageMode.DYNAMIC);
        when(definitionMapper.findByEntityCode("target_entity"))
                .thenReturn(Optional.of(definition));
        when(dataListService.findListConfig(
                "target_entity",
                "default",
                null,
                null,
                null))
                .thenReturn(published);
    }

    private EntityDefinition definition(
            EntityDefinition.StorageMode storageMode) {
        EntityDefinition definition = new EntityDefinition();
        definition.setId("target-entity-id");
        definition.setEntityCode("target_entity");
        definition.setStorageMode(storageMode);
        return definition;
    }

    private void configureVisibleFields(
            EntityListConfig published,
            String... fieldCodes) {
        List<EntityListField> fields =
                java.util.Arrays.stream(fieldCodes)
                        .map(code -> {
                            EntityListField field =
                                    new EntityListField();
                            field.setFieldCode(code);
                            field.setShowInList(true);
                            return field;
                        })
                        .toList();
        when(fieldMapper.findByListConfigId(
                published.getId()))
                .thenReturn(List.of());
        when(publishedRuntimeService.resolveFields(
                eq(published), anyList()))
                .thenReturn(fields);
    }

    private EntityListDataProvider provider(
            String code,
            Object result) {
        EntityListDataProvider provider =
                mock(EntityListDataProvider.class);
        when(provider.getCode()).thenReturn(code);
        when(provider.query(any(), any(), anyMap()))
                .thenReturn(result);
        return provider;
    }

    private void stubProviderPermission() {
        SysUser user = new SysUser();
        user.setId("runtime-user");
        when(sysUserService.getById("runtime-user"))
                .thenReturn(user);
        when(dataPermissionEngine.calculatePermission(
                eq("target_entity"),
                eq("default"),
                eq(user)))
                .thenReturn(DataPermissionResult.allowAll());
    }

    private EntityDataDTO record(
            String id,
            String name) {
        EntityDataDTO record = new EntityDataDTO();
        record.setId(id);
        record.setEntityCode("target_entity");
        record.setName(name);
        record.setData(new LinkedHashMap<>());
        return record;
    }

    private EntityListQueryRequest compositionRequest(
            long pageNum,
            long pageSize) {
        EntityListQueryRequest request =
                new EntityListQueryRequest();
        request.setViewCompositionContextToken(
                "signed-list-context");
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        return request;
    }

    @Test
    void pageParametersCannotReplaceTrustedRelationBounds() {
        when(tokenService.verifyListContext("signed-list-context")).thenReturn(claims(Map.of("projectId", "owned"), false));
        EntityListConfig config = publishedList();
        config.setViewConfig("""
                {"inputParameterSchema":{"type":"object","properties":{"project":{"type":"string"}},"required":["project"]},
                "inputParameterBindings":[{"parameter":"project","usage":"FILTER","targetField":"projectId","operator":"EQ"}]}
                """);
        configurePinnedList(config);
        EntityListField field = new EntityListField();
        field.setFieldCode("projectId");
        field.setIsQuery(true);
        when(publishedRuntimeService.resolveFields(eq(config), any())).thenReturn(List.of(field));
        EntityListQueryRequest request = compositionRequest(1, 10);
        var context = new com.workflow.entity.list.api.response.EntityListRuntimeContextDTO();
        context.setParameters(Map.of("project", "foreign"));
        request.setContext(context);
        PageResult<?> page = (PageResult<?>) service.query("target_entity", "default", request);
        assertEquals(0, page.getTotal());
        verify(dataListService, never()).findPageWithResolvedConfig(any(), any(), any(), anyMap(), anyLong(), anyLong());
        verify(uiEventRuntimeService, never()).execute(any(), any());
    }

    @Test
    void pageParameterFilterAndRelationBothReachPublishedQuery() {
        when(tokenService.verifyListContext("signed-list-context")).thenReturn(claims(Map.of("projectId", "owned"), false));
        EntityListConfig config = publishedList();
        config.setViewConfig("""
                {"inputParameterSchema":{"properties":{"keyword":{"type":"string"}}},
                "inputParameterBindings":[{"parameter":"keyword","usage":"FILTER","targetField":"name","operator":"EQ"}]}
                """);
        configurePinnedList(config);
        EntityListField field = new EntityListField(); field.setFieldCode("name"); field.setIsQuery(true);
        when(publishedRuntimeService.resolveFields(eq(config), any())).thenReturn(List.of(field));
        when(dataListService.findPageWithResolvedConfig(any(), any(), any(), anyMap(), anyLong(), anyLong()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 10));
        EntityListQueryRequest request = compositionRequest(1, 10);
        var context = new com.workflow.entity.list.api.response.EntityListRuntimeContextDTO();
        context.setParameters(Map.of("keyword", "unsaved-name")); request.setContext(context);
        service.query("target_entity", "default", request);
        verify(dataListService).findPageWithResolvedConfig(any(), any(), eq(config),
                org.mockito.ArgumentMatchers.argThat(filters -> "owned".equals(filters.get("projectId"))
                        && "unsaved-name".equals(filters.get("name")) && "EQ".equals(filters.get("name_op"))), anyLong(), anyLong());
    }

    private EntityListConfig publishedList() {
        EntityListConfig config = new EntityListConfig();
        config.setId("target-list");
        config.setEntityCode("target_entity");
        config.setListKey("default");
        config.setListName("目标列表");
        config.setAccessPermissionCode("target:list");
        config.setPublishedSnapshot(true);
        config.setPinnedRelease(true);
        config.setActiveReleaseId("target-release");
        config.setPublishedVersion(3);
        return config;
    }

    private UiViewCompositionTokenService.Claims claims(
            Map<String, Object> filters,
            boolean matchNone) {
        return new UiViewCompositionTokenService.Claims(
                "TARGET_LIST",
                "FORM",
                "host-form",
                "host-release",
                2,
                "project-requirements",
                "project",
                "project-1",
                "target_entity",
                "target-list",
                "target-release",
                3,
                filters,
                matchNone,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "runtime-user",
                1,
                Long.MAX_VALUE);
    }
}
