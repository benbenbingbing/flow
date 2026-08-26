package com.workflow.entity.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.definition.api.response.EntityPublishHistoryDTO;
import com.workflow.entity.definition.api.response.EntityVersionDiffDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityVersionDiffHistoryLookupTest {

    @Test
    void comparesOnlyTheTwoRequestedHistoryVersions() {
        EntityPublishHistoryService historyService =
                mock(EntityPublishHistoryService.class);
        when(historyService.getVersion("entity-1", 4))
                .thenReturn(history(4));
        when(historyService.getVersion("entity-1", 5))
                .thenReturn(history(5));
        EntityVersionDiffService service = new EntityVersionDiffService(
                mock(EntityDefinitionMapper.class),
                mock(EntityFieldMapper.class),
                historyService,
                mock(DynamicTableService.class),
                new ObjectMapper());

        EntityVersionDiffDTO result =
                service.compareVersions("entity-1", 4, 5);

        assertEquals(4, result.getCurrentVersion());
        assertEquals(5, result.getNextVersion());
        verify(historyService).getVersion("entity-1", 4);
        verify(historyService).getVersion("entity-1", 5);
        verify(historyService, never()).getVersionHistory("entity-1");
    }

    private EntityPublishHistoryDTO history(int version) {
        EntityPublishHistoryDTO history = new EntityPublishHistoryDTO();
        history.setEntityCode("DEMO");
        history.setEntityName("演示实体");
        history.setVersion(version);
        history.setFields(List.of());
        return history;
    }
}
