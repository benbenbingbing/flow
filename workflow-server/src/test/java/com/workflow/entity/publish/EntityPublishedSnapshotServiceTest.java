package com.workflow.entity.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityPublishHistory;
import com.workflow.mapper.EntityPublishHistoryMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityPublishedSnapshotServiceTest {

    @Test
    void getLatestByEntityIdParsesFieldsSnapshot() throws Exception {
        EntityPublishHistoryMapper historyMapper = mock(EntityPublishHistoryMapper.class);
        EntityField amount = new EntityField();
        amount.setFieldCode("amount");
        amount.setFieldName("金额");
        amount.setIsRequired(true);

        EntityPublishHistory history = history("history-1", "entity-1", "expense", 3,
                new ObjectMapper().writeValueAsString(List.of(amount)));
        when(historyMapper.findLatestByEntityId("entity-1")).thenReturn(history);

        EntityPublishedSnapshotService service = new EntityPublishedSnapshotService(
                historyMapper, new ObjectMapper());

        EntityPublishedSnapshot snapshot = service.getLatestByEntityId("entity-1");

        assertEquals("history-1", snapshot.getHistoryId());
        assertEquals("entity-1", snapshot.getEntityId());
        assertEquals("expense", snapshot.getEntityCode());
        assertEquals("process-config-1", snapshot.getProcessDefinitionId());
        assertEquals(3, snapshot.getVersion());
        assertEquals(1, snapshot.getFields().size());
        assertEquals("amount", snapshot.getFields().get(0).getFieldCode());
        assertEquals(true, snapshot.getFields().get(0).getIsRequired());
    }

    @Test
    void getLatestByEntityCodeReadsPublishedSnapshotDirectly() {
        EntityPublishHistoryMapper historyMapper = mock(EntityPublishHistoryMapper.class);
        when(historyMapper.findLatestByEntityCode("expense")).thenReturn(
                history("history-1", "entity-1", "expense", 1, "[]"));

        EntityPublishedSnapshotService service = new EntityPublishedSnapshotService(
                historyMapper, new ObjectMapper());

        EntityPublishedSnapshot snapshot = service.getLatestByEntityCode("expense");

        assertEquals("entity-1", snapshot.getEntityId());
        assertEquals("expense", snapshot.getEntityCode());
        assertEquals("process-config-1", snapshot.getProcessDefinitionId());
    }

    @Test
    void getLatestByEntityIdFailsWhenNoSnapshotExists() {
        EntityPublishHistoryMapper historyMapper = mock(EntityPublishHistoryMapper.class);
        EntityPublishedSnapshotService service = new EntityPublishedSnapshotService(
                historyMapper, new ObjectMapper());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.getLatestByEntityId("entity-1"));

        assertEquals("实体未发布: entity-1", exception.getMessage());
    }

    private static EntityPublishHistory history(String id,
                                                String entityId,
                                                String entityCode,
                                                Integer version,
                                                String fieldsSnapshot) {
        EntityPublishHistory history = new EntityPublishHistory();
        history.setId(id);
        history.setEntityId(entityId);
        history.setEntityCode(entityCode);
        history.setEntityName("费用");
        history.setProcessDefinitionId("process-config-1");
        history.setVersion(version);
        history.setFieldsSnapshot(fieldsSnapshot);
        return history;
    }
}
