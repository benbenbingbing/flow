package com.workflow.entity.excel.config;

import com.workflow.entity.definition.application.*;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.*;
import com.workflow.entity.definition.infrastructure.persistence.record.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityExcelReferenceMetadataTest {
    private final EntityDefinitionMapper entities = mock(EntityDefinitionMapper.class);
    private final EntityFieldMapper fields = mock(EntityFieldMapper.class);
    private final EntityPublishedSnapshotService snapshots = mock(EntityPublishedSnapshotService.class);
    private final EntityExcelReferenceMetadata metadata = new EntityExcelReferenceMetadata(entities, fields, snapshots, new SystemEntityFieldPolicy());

    @Test void optionsUsePublishedTargetAndExcludeSensitiveUnreadableAndComplexFields() {
        var target = new EntityDefinition(); target.setId("target"); target.setEntityCode("customers"); target.setEntityName("客户");
        when(entities.selectById("target")).thenReturn(target);
        var hidden = field("hidden", EntityField.FieldType.STRING); hidden.setRuntimeReadable(false);
        var snapshot = new EntityPublishedSnapshot(); snapshot.setFields(List.of(field("id",EntityField.FieldType.STRING),
                field("code",EntityField.FieldType.STRING),field("name",EntityField.FieldType.STRING),field("email",EntityField.FieldType.STRING),
                hidden,field("password",EntityField.FieldType.STRING),field("items",EntityField.FieldType.MULTI_REFERENCE)));
        when(snapshots.getLatestByEntityId("target")).thenReturn(snapshot);
        var source = field("customer", EntityField.FieldType.REFERENCE); source.setRefEntityId("target");
        var resolved = metadata.resolve(source);
        assertEquals(Set.of("id","code","name","email"),resolved.properties().keySet());
        var defaults = EntityExcelReferenceMetadata.settings(resolved,Map.of(),true,true);
        assertEquals(List.of("code","name"),defaults.matchFields());assertEquals("name",defaults.exportField());
        assertThrows(IllegalArgumentException.class, () -> EntityExcelReferenceMetadata.settings(resolved,Map.of("matchFields",List.of()),true,false));
        assertThrows(IllegalArgumentException.class, () -> EntityExcelReferenceMetadata.settings(resolved,Map.of("matchFields",List.of("hidden")),true,false));
        assertThrows(IllegalArgumentException.class, () -> EntityExcelReferenceMetadata.settings(resolved,Map.of("exportField","password"),false,true));
        assertThrows(IllegalArgumentException.class, () -> EntityExcelReferenceMetadata.settings(resolved,Map.of("exportField","name) FROM sys_user"),false,true));
        assertThrows(IllegalArgumentException.class, () -> metadata.resolve(field("text",EntityField.FieldType.STRING)));
        assertThrows(IllegalArgumentException.class, () -> metadata.resolve(field("missing",EntityField.FieldType.REFERENCE)));
    }

    @Test void systemDirectoryDefaultsQueryActualCodeAndNameProperties() {
        var target = new EntityDefinition(); target.setId("user"); target.setEntityCode("sys_user");target.setStorageMode(EntityDefinition.StorageMode.SYSTEM);
        when(entities.findByEntityCode("sys_user")).thenReturn(Optional.of(target));
        when(fields.findByEntityId("user")).thenReturn(List.of(field("id",EntityField.FieldType.STRING),field("username",EntityField.FieldType.STRING),
                field("nickname",EntityField.FieldType.STRING),field("email",EntityField.FieldType.STRING),field("password",EntityField.FieldType.STRING)));
        var source = field("user",EntityField.FieldType.REFERENCE); source.setRefEntityType(EntityField.RefEntityType.USER);
        var resolved = metadata.resolve(source);
        assertEquals("username",resolved.properties().get("code").queryField());
        assertEquals("nickname",resolved.properties().get("name").queryField());
        assertFalse(resolved.properties().containsKey("password"));
        verifyNoInteractions(snapshots);
    }

    private static EntityField field(String code,EntityField.FieldType type) { var field=new EntityField();field.setFieldCode(code);field.setFieldName(code);field.setFieldType(type);return field; }
}
