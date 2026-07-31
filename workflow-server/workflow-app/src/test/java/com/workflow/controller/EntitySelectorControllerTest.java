package com.workflow.controller;

import com.workflow.entity.data.api.web.EntitySelectorController;

import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.api.response.EntityDefinitionDTO;
import com.workflow.entity.definition.application.EntityDefinitionService;
import com.workflow.entity.definition.application.EntityFieldService;
import com.workflow.entity.definition.application.SystemEntityService;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实体选择器控制器单元测试。
 *
 * <p>被测对象为 {@link EntitySelectorController}，重点验证自定义选择器(CUSTOM)场景下
 * 单条/批量查询是否走权限感知的数据查询接口，以及越权数据是否被正确过滤。</p>
 */
class EntitySelectorControllerTest {

    /** 模拟的实体动态数据服务，用于校验权限感知查询调用 */
    private final EntityDataDynamicService dynamicService =
            mock(EntityDataDynamicService.class);
    /** 模拟的动态表服务，用于判断实体表是否存在 */
    private final DynamicTableService tableService = mock(DynamicTableService.class);
    /** 模拟的系统实体服务 */
    private final SystemEntityService systemEntityService =
            mock(SystemEntityService.class);
    /** 模拟的实体字段服务 */
    private final EntityFieldService fieldService = mock(EntityFieldService.class);
    /** 模拟的实体定义服务 */
    private final EntityDefinitionService definitionService =
            mock(EntityDefinitionService.class);
    /** 模拟的平台系统实体读取服务 */
    private final SystemEntityReadService systemEntityReadService =
            mock(SystemEntityReadService.class);
    /** 被测控制器实例，注入上述 mock 依赖 */
    private final EntitySelectorController controller = new EntitySelectorController(
            dynamicService,
            tableService,
            systemEntityService,
            fieldService,
            definitionService,
            systemEntityReadService);

    /**
     * 自定义选择器单条详情查询应使用权限感知接口。
     *
     * <p>场景：expense 表存在，通过 findAccessibleById 查询，断言返回 200 且数据正确，
     * 并验证未调用无权限校验的 findById。</p>
     */
    @Test
    void customSelectorDetailUsesPermissionAwareLookup() {
        when(tableService.tableExists("expense")).thenReturn(true);
        EntityDataDTO dto = new EntityDataDTO();
        dto.setData(Map.of("id", "row-1", "name", "报销单"));
        when(dynamicService.findAccessibleById("expense", "row-1", null))
                .thenReturn(dto);

        Result<Map<String, Object>> result = controller.getById(
                "CUSTOM",
                "row-1",
                "expense",
                null);

        assertEquals(200, result.getCode());
        assertEquals("row-1", result.getData().get("id"));
        verify(dynamicService).findAccessibleById("expense", "row-1", null);
        verify(dynamicService, never()).findById("expense", "row-1");
    }

    /**
     * 自定义选择器批量查询应过滤掉权限范围外的数据行。
     *
     * <p>场景：row-1 可访问、row-2 抛出 ForbiddenException，断言最终仅返回 row-1 一条数据。</p>
     */
    @Test
    void customSelectorBatchOmitsRowsOutsidePermissionScope() {
        when(tableService.tableExists("expense")).thenReturn(true);
        EntityDataDTO allowed = new EntityDataDTO();
        allowed.setId("row-1");
        allowed.setName("可见数据");
        allowed.setCode("EXP-001");
        allowed.setData(Map.of("amount", 100));
        when(dynamicService.findAccessibleById("expense", "row-1", null))
                .thenReturn(allowed);
        when(dynamicService.findAccessibleById("expense", "row-2", null))
                .thenThrow(new ForbiddenException("无权访问"));

        Result<List<Map<String, Object>>> result = controller.getBatch(
                "CUSTOM",
                "row-1,row-2",
                "expense",
                null,
                "id");

        assertEquals(1, result.getData().size());
        assertEquals("row-1", result.getData().get(0).get("id"));
        assertEquals("可见数据", result.getData().get(0).get("name"));
        assertEquals("EXP-001", result.getData().get(0).get("code"));
    }

    @Test
    void systemUserBatchCanResolveStoredUsernames() {
        List<Map<String, Object>> users = List.of(
                Map.of(
                        "id", "user-1",
                        "code", "admin",
                        "name", "管理员"));
        when(systemEntityService.selectBatch(
                "USER",
                List.of("admin"),
                "code"))
                .thenReturn(users);

        Result<List<Map<String, Object>>> result = controller.getBatch(
                "USER",
                "admin",
                null,
                null,
                "code");

        assertEquals(users, result.getData());
        verify(systemEntityService).selectBatch(
                "USER",
                List.of("admin"),
                "code");
    }

    @Test
    void customSelectorDetailUsesDtoStandardFieldsForDisplay() {
        when(tableService.tableExists("project")).thenReturn(true);
        EntityDataDTO project = new EntityDataDTO();
        project.setId("project-1");
        project.setName("统一客户运营平台建设");
        project.setCode("PRJ-001");
        project.setStatus("APPROVED");
        project.setData(Map.of("project_type", "NEW_SYSTEM"));
        when(dynamicService.findAccessibleById(
                "project",
                "project-1",
                null))
                .thenReturn(project);

        Result<Map<String, Object>> result = controller.getById(
                "CUSTOM",
                "project-1",
                "project",
                null);

        assertEquals("统一客户运营平台建设",
                result.getData().get("name"));
        assertEquals("PRJ-001", result.getData().get("code"));
        assertEquals("APPROVED", result.getData().get("status"));
    }

    @Test
    void publishedSystemDefinitionUsesReadOnlySelectorPage() {
        when(definitionService.findById("system-user-definition"))
                .thenReturn(systemDefinition());
        EntityDataDTO user = new EntityDataDTO();
        user.setId("user-1");
        user.setName("超级管理员");
        user.setCode("admin");
        when(systemEntityReadService.findSelectorPage(
                "sys_user",
                "管理",
                1,
                10))
                .thenReturn(new PageResult<>(
                        List.of(user), 1, 1, 10));

        Result<Map<String, Object>> result =
                controller.selectList(
                        "CUSTOM",
                        null,
                        "system-user-definition",
                        "管理",
                        1,
                        10);

        assertEquals(200, result.getCode());
        assertEquals(1L, result.getData().get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records =
                (List<Map<String, Object>>) result.getData()
                        .get("records");
        assertEquals("超级管理员", records.get(0).get("name"));
        verify(systemEntityReadService).findSelectorPage(
                "sys_user",
                "管理",
                1,
                10);
        verify(tableService, never()).tableExists("sys_user");
    }

    @Test
    void publishedSystemDefinitionDetailUsesReadOnlyLookup() {
        when(definitionService.findById("system-user-definition"))
                .thenReturn(systemDefinition());
        EntityDataDTO user = new EntityDataDTO();
        user.setId("user-1");
        user.setName("超级管理员");
        user.setCode("admin");
        when(systemEntityReadService.findById(
                "sys_user", "user-1"))
                .thenReturn(user);

        Result<Map<String, Object>> result = controller.getById(
                "CUSTOM",
                "user-1",
                null,
                "system-user-definition");

        assertEquals(200, result.getCode());
        assertEquals("超级管理员", result.getData().get("name"));
        assertEquals("admin", result.getData().get("code"));
        verify(tableService, never()).tableExists("sys_user");
        verify(dynamicService, never()).findAccessibleById(
                "sys_user", "user-1", null);
    }

    private EntityDefinitionDTO systemDefinition() {
        EntityDefinitionDTO definition = new EntityDefinitionDTO();
        definition.setId("system-user-definition");
        definition.setEntityCode("sys_user");
        definition.setEntityName("系统用户");
        definition.setStatus(EntityDefinition.Status.PUBLISHED);
        definition.setStorageMode(
                EntityDefinition.StorageMode.SYSTEM);
        return definition;
    }
}
