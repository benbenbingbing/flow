package com.workflow.migration.application;

import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.api.request.EntityRelationSaveRequest;
import com.workflow.entity.definition.application.EntityRelationDefinitionService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证独立组成关系恢复目标身份，以及回滚只停用本次新增关系。 */
class ConfigMigrationRelationImporterTest {
    private final EntityDefinitionMapper entities = mock(EntityDefinitionMapper.class);
    private final EntityFieldMapper fields = mock(EntityFieldMapper.class);
    private final EntityRelationMapper relations = mock(EntityRelationMapper.class);
    private final EntityRelationDefinitionService definitions = mock(EntityRelationDefinitionService.class);
    private final ConfigMigrationRelationImporter importer = new ConfigMigrationRelationImporter(entities, fields, relations, definitions);

    @Test
    void restoresStandaloneRelationWithTargetIdentityAndStableDataKey() {
        EntityDefinition parent = entity("target-parent", "parent");
        when(entities.findByEntityCode("target-child")).thenReturn(Optional.of(entity("target-child-id", "target-child")));
        importer.apply(parent, Map.of("relations", List.of(Map.of(
                "relationCode", "details", "dataKey", "detail_data", "childEntityCode", "source-child",
                "childEntityId", "source-id", "childRefFieldCode", "parent_id", "ownershipType", "COMPOSITION"))),
                (type, key) -> "source-child".equals(key) ? "target-child" : key);
        var request = ArgumentCaptor.forClass(EntityRelationSaveRequest.class);
        verify(definitions).create(eq("target-parent"), request.capture());
        assertEquals("target-child-id", request.getValue().getChildEntityId());
        assertEquals("detail_data", request.getValue().getDataKey());
        assertEquals(EntityRelation.OwnershipType.COMPOSITION, request.getValue().getOwnershipType());
        assertNull(request.getValue().getParentFieldId());
        verifyNoInteractions(fields); // 独立关系没有虚构的 SUB_FORM 物理字段。
    }

    @Test
    void rollbackDisablesOnlyImportedRelationAbsentFromRestoredSnapshot() {
        EntityDefinition parent = entity("target-parent", "parent");
        EntityRelation added = new EntityRelation(); added.setId("added"); added.setEnabled(true);
        when(relations.selectByRelationCode("target-parent", "added")).thenReturn(added);
        importer.disableAdded(parent, Map.of("relations", List.of(Map.of("relationCode", "added"), Map.of("relationCode", "kept"))),
                Map.of("relations", List.of(Map.of("relationCode", "kept"))));
        assertFalse(added.getEnabled());
        verify(relations).updateById(added);
        verify(relations, never()).selectByRelationCode("target-parent", "kept");
        verifyNoInteractions(definitions, fields, entities);
    }

    private EntityDefinition entity(String id, String code) {
        EntityDefinition entity = new EntityDefinition(); entity.setId(id); entity.setEntityCode(code); return entity;
    }
}
