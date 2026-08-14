package com.workflow.entity.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityPublishHistoryMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityPublishHistory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityPublishHistoryRelationsTest {

    @Test
    void freezesIndependentRelationsDuringEntityPublish() throws Exception {
        EntityPublishHistoryMapper mapper =
                mock(EntityPublishHistoryMapper.class);
        when(mapper.getLatestVersion("parent-1")).thenReturn(2);
        when(mapper.insert(any(EntityPublishHistory.class)))
                .thenReturn(1);
        EntityPublishHistoryService service =
                new EntityPublishHistoryService(
                        mapper, new ObjectMapper());
        EntityDefinition entity = new EntityDefinition();
        entity.setId("parent-1");
        entity.setEntityCode("order");
        entity.setEntityName("订单");
        EntityRelation relation = new EntityRelation();
        relation.setRelationCode("detail_relation");
        relation.setRelationName("订单明细");
        relation.setDataKey("details");
        relation.setChildEntityCode("order_detail");
        relation.setChildRefFieldCode("parentId");
        relation.setRelationType(
                EntityRelation.RelationType.ONE_TO_MANY);

        service.createVersion(
                entity,
                List.of(),
                null,
                EntityPublishHistory.PublishType.ALTER,
                "关系变更",
                "user-1",
                "测试用户",
                "发布关系",
                List.of(relation));

        ArgumentCaptor<EntityPublishHistory> captor =
                ArgumentCaptor.forClass(EntityPublishHistory.class);
        verify(mapper).insert(captor.capture());
        EntityPublishHistory saved = captor.getValue();
        assertEquals(3, saved.getVersion());
        assertTrue(saved.getRelationsSnapshot()
                .contains("detail_relation"));
        List<EntityRelation> frozen = new ObjectMapper().readValue(
                saved.getRelationsSnapshot(),
                new ObjectMapper().getTypeFactory()
                        .constructCollectionType(
                                List.class, EntityRelation.class));
        assertEquals("details", frozen.get(0).getDataKey());
    }
}
