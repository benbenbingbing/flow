package com.workflow.entity.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityPublishHistoryMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityPublishHistory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityPublishedSnapshotRelationsTest {

    @Test
    void parsesFrozenRelationMetadata() {
        EntityPublishHistoryMapper mapper =
                mock(EntityPublishHistoryMapper.class);
        EntityPublishHistory history = history();
        history.setRelationsSnapshot("""
                [{
                  "relationCode":"detail_relation",
                  "relationName":"订单明细",
                  "dataKey":"details",
                  "childEntityCode":"order_detail",
                  "childRefFieldCode":"parentId",
                  "relationType":"ONE_TO_MANY",
                  "ownershipType":"COMPOSITION"
                }]
                """);
        when(mapper.findLatestByEntityCode("order"))
                .thenReturn(history);
        EntityPublishedSnapshotService service =
                new EntityPublishedSnapshotService(
                        mapper, new ObjectMapper());

        EntityPublishedSnapshot snapshot =
                service.getLatestByEntityCode("order");

        assertTrue(snapshot.isRelationsSnapshotAvailable());
        assertEquals(1, snapshot.getRelations().size());
        EntityRelation relation = snapshot.getRelations().get(0);
        assertEquals("订单明细", relation.getRelationName());
        assertEquals("details", relation.getDataKey());
    }

    @Test
    void distinguishesLegacyNullFromExplicitEmptySnapshot() {
        EntityPublishHistoryMapper mapper =
                mock(EntityPublishHistoryMapper.class);
        EntityPublishHistory legacy = history();
        when(mapper.findLatestByEntityCode("legacy"))
                .thenReturn(legacy);
        EntityPublishHistory empty = history();
        empty.setRelationsSnapshot("[]");
        when(mapper.findLatestByEntityCode("empty"))
                .thenReturn(empty);
        EntityPublishedSnapshotService service =
                new EntityPublishedSnapshotService(
                        mapper, new ObjectMapper());

        assertFalse(service.getLatestByEntityCode("legacy")
                .isRelationsSnapshotAvailable());
        assertTrue(service.getLatestByEntityCode("empty")
                .isRelationsSnapshotAvailable());
    }

    private EntityPublishHistory history() {
        EntityPublishHistory history = new EntityPublishHistory();
        history.setId("history-1");
        history.setEntityId("parent-1");
        history.setEntityCode("order");
        history.setEntityName("订单");
        history.setVersion(1);
        history.setFieldsSnapshot("[]");
        return history;
    }
}
