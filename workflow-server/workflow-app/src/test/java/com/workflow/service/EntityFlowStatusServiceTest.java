package com.workflow.service;

import com.workflow.entity.EntityFlowStatusMapping;
import com.workflow.mapper.EntityFlowStatusMappingMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EntityFlowStatusServiceTest {

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
