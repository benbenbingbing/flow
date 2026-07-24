package com.workflow.service;

import com.workflow.entity.EntityFlowStatusMapping;
import com.workflow.mapper.EntityFlowStatusMappingMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 实体流程状态映射服务测试。
 *
 * <p>被测对象：{@link EntityFlowStatusService}，覆盖流程状态映射保存时对必填遗留状态字段的自动填充逻辑。
 */
class EntityFlowStatusServiceTest {

    /**
     * 测试保存状态映射时自动回填必填的 legacy status 值：
     * 验证仅设置 statusCode 时，status 字段会被同步填充为相同的值。
     */
    @Test
    void saveStatusMappingsPopulatesRequiredLegacyStatusValue() {
        EntityFlowStatusMappingMapper mapper = mock(EntityFlowStatusMappingMapper.class);
        EntityFlowStatusService service = new EntityFlowStatusService(mapper);
        EntityFlowStatusMapping mapping = new EntityFlowStatusMapping();
        mapping.setEntityStatusCode("FINANCE_REVIEW");

        service.saveStatusMappings("process-1", "flow-1", "expense", List.of(mapping));

        ArgumentCaptor<EntityFlowStatusMapping> captor = ArgumentCaptor.forClass(EntityFlowStatusMapping.class);
        verify(mapper).insert(captor.capture());
        assertEquals("FINANCE_REVIEW", captor.getValue().getEntityStatus());
        assertEquals("FINANCE_REVIEW", captor.getValue().getEntityStatusCode());
    }
}
