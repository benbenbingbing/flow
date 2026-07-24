package com.workflow.controller;

import com.workflow.common.ForbiddenException;
import com.workflow.common.Result;
import com.workflow.dto.EntityDataDTO;
import com.workflow.service.DynamicTableService;
import com.workflow.service.EntityDataDynamicService;
import com.workflow.service.EntityDefinitionService;
import com.workflow.service.EntityFieldService;
import com.workflow.service.SystemEntityService;
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
    /** 被测控制器实例，注入上述 mock 依赖 */
    private final EntitySelectorController controller = new EntitySelectorController(
            dynamicService,
            tableService,
            systemEntityService,
            fieldService,
            definitionService);

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
        allowed.setData(Map.of("id", "row-1", "name", "可见数据"));
        when(dynamicService.findAccessibleById("expense", "row-1", null))
                .thenReturn(allowed);
        when(dynamicService.findAccessibleById("expense", "row-2", null))
                .thenThrow(new ForbiddenException("无权访问"));

        Result<List<Map<String, Object>>> result = controller.getBatch(
                "CUSTOM",
                "row-1,row-2",
                "expense",
                null);

        assertEquals(1, result.getData().size());
        assertEquals("row-1", result.getData().get(0).get("id"));
    }
}
