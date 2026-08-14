package com.workflow.entity.definition.application;

import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EntityPublishedRelationServiceTest {

    private final EntityPublishedSnapshotService snapshotService =
            mock(EntityPublishedSnapshotService.class);
    private final EntityDefinitionMapper definitionMapper =
            mock(EntityDefinitionMapper.class);
    private final EntityRelationMapper relationMapper =
            mock(EntityRelationMapper.class);
    private final EntityPublishedRelationService service =
            new EntityPublishedRelationService(
                    snapshotService,
                    definitionMapper,
                    relationMapper);

    @Test
    void newPublishStrictlyUsesFrozenRelations() {
        EntityDefinition parent = definition();
        when(definitionMapper.findByEntityCode("order"))
                .thenReturn(Optional.of(parent));
        EntityRelation frozen = relation("published_relation");
        EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
        snapshot.setRelationsSnapshotAvailable(true);
        snapshot.setRelations(List.of(frozen));
        when(snapshotService.findLatestByEntityCode("order"))
                .thenReturn(snapshot);

        List<EntityRelation> result =
                service.listByParentEntityCode("order");

        assertEquals(List.of(frozen), result);
        verifyNoInteractions(relationMapper);
    }

    @Test
    void legacyPublishFallsBackToCurrentRelationTable() {
        EntityDefinition parent = definition();
        when(definitionMapper.selectById("parent-1"))
                .thenReturn(parent);
        EntityPublishedSnapshot legacy = new EntityPublishedSnapshot();
        legacy.setRelationsSnapshotAvailable(false);
        when(snapshotService.findLatestByEntityCode("order"))
                .thenReturn(legacy);
        EntityRelation current = relation("legacy_relation");
        when(relationMapper.selectByParentEntityId("parent-1"))
                .thenReturn(List.of(current));

        List<EntityRelation> result =
                service.listByParentEntityId("parent-1");

        assertEquals(List.of(current), result);
        verify(relationMapper)
                .selectByParentEntityId("parent-1");
    }

    @Test
    void explicitEmptyPublishedSnapshotDoesNotFallback() {
        EntityDefinition parent = definition();
        when(definitionMapper.selectById("parent-1"))
                .thenReturn(parent);
        EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
        snapshot.setRelationsSnapshotAvailable(true);
        snapshot.setRelations(List.of());
        when(snapshotService.findLatestByEntityCode("order"))
                .thenReturn(snapshot);

        assertEquals(
                List.of(),
                service.listByParentEntityId("parent-1"));
        verifyNoInteractions(relationMapper);
    }

    private EntityDefinition definition() {
        EntityDefinition definition = new EntityDefinition();
        definition.setId("parent-1");
        definition.setEntityCode("order");
        return definition;
    }

    private EntityRelation relation(String relationCode) {
        EntityRelation relation = new EntityRelation();
        relation.setRelationCode(relationCode);
        relation.setDataKey("details");
        return relation;
    }
}
