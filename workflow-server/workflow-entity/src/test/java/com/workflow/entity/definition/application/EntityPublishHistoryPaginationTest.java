package com.workflow.entity.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.result.PageResult;
import com.workflow.entity.definition.api.response.EntityPublishHistoryDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityPublishHistoryMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityPublishHistory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityPublishHistoryPaginationTest {

    @Test
    void returnsRequestedPageWithoutLoadingTheFullHistory() {
        EntityPublishHistoryMapper mapper =
                mock(EntityPublishHistoryMapper.class);
        when(mapper.countByEntityId("entity-1")).thenReturn(12L);
        when(mapper.findPageByEntityId("entity-1", 5L, 5L))
                .thenReturn(List.of(history("history-7", 7)));
        EntityPublishHistoryService service = service(mapper);

        PageResult<EntityPublishHistoryDTO> result =
                service.getVersionHistoryPage("entity-1", 2, 5);

        assertEquals(12L, result.getTotal());
        assertEquals(2L, result.getPageNum());
        assertEquals(5L, result.getPageSize());
        assertEquals(List.of(7), result.getRecords().stream()
                .map(EntityPublishHistoryDTO::getVersion)
                .toList());
        verify(mapper, never()).findByEntityId("entity-1");
    }

    @Test
    void normalizesInvalidPageValuesAndCapsThePageSize() {
        EntityPublishHistoryMapper mapper =
                mock(EntityPublishHistoryMapper.class);
        when(mapper.findPageByEntityId("entity-1", 0L, 50L))
                .thenReturn(List.of());
        EntityPublishHistoryService service = service(mapper);

        PageResult<EntityPublishHistoryDTO> result =
                service.getVersionHistoryPage("entity-1", 0, 1_000);

        assertEquals(1L, result.getPageNum());
        assertEquals(50L, result.getPageSize());
        verify(mapper).findPageByEntityId("entity-1", 0L, 50L);
    }

    @Test
    void readsOneExactVersionForDiffInsteadOfParsingAllSnapshots() {
        EntityPublishHistoryMapper mapper =
                mock(EntityPublishHistoryMapper.class);
        when(mapper.findByEntityIdAndVersion("entity-1", 6))
                .thenReturn(history("history-6", 6));
        EntityPublishHistoryService service = service(mapper);

        EntityPublishHistoryDTO result = service.getVersion("entity-1", 6);

        assertNotNull(result);
        assertEquals(6, result.getVersion());
        verify(mapper, never()).findByEntityId("entity-1");
    }

    private EntityPublishHistoryService service(
            EntityPublishHistoryMapper mapper) {
        return new EntityPublishHistoryService(mapper, new ObjectMapper());
    }

    private EntityPublishHistory history(String id, int version) {
        EntityPublishHistory history = new EntityPublishHistory();
        history.setId(id);
        history.setEntityId("entity-1");
        history.setEntityCode("DEMO");
        history.setEntityName("演示实体");
        history.setVersion(version);
        history.setFieldsSnapshot("[]");
        return history;
    }
}
