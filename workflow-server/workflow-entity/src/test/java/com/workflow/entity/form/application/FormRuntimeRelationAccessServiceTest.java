package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormRuntimeRelationAccessServiceTest {
    private final UiConfigReleaseService releases = mock(UiConfigReleaseService.class);
    private final EntityDataDynamicService data = mock(EntityDataDynamicService.class);
    private final EntityDefinitionMapper definitions = mock(EntityDefinitionMapper.class);
    private final FormRuntimeRelationAccessService service = new FormRuntimeRelationAccessService(
            provider(releases), provider(data), definitions, new JsonDocumentCodec(new ObjectMapper()));

    @Test void taskAuthorizedRootCannotBeReusedForAnUnrelatedChildRecord() {
        fixture("another-parent");
        assertThrows(BusinessForbiddenException.class,
                () -> service.requireRecords("root", "parent", List.of("items"), "child-row", true));
    }

    @Test void taskParticipantCanReadRelatedChildWithoutIndependentListPermission() {
        fixture("parent");
        assertDoesNotThrow(() -> service.requireRecords("root", "parent", List.of(Map.of("nodeKey", "items", "recordId", "child-row")), "child-row", true));
        verify(data, never()).findAccessibleById(anyString(), anyString(), any());
    }

    @Test void rootRecordIsRequiredWhenRequestContainsExistingChild() {
        assertThrows(BusinessForbiddenException.class,
                () -> service.requireRecords("root", null, List.of("items"), "child-row", true));
        verifyNoInteractions(data);
    }

    private void fixture(String parent) {
        EntityForm root = new EntityForm(); root.setId("root"); root.setEntityId("parent-entity");
        EntityFormNode node = new EntityFormNode(); node.setNodeKey("items");
        node.setPropsDocument("{\"componentProps\":{\"subFormConfig\":{\"childFormId\":\"child\",\"childRefFieldCode\":\"parent_id\"}}}");
        root.setNodes(List.of(node));
        EntityForm child = new EntityForm(); child.setId("child"); child.setEntityId("child-entity");
        when(releases.resolveRuntimeFormRelease("root")).thenReturn(new ResolvedEntityFormRelease(root, "rh", 1));
        when(releases.resolveRuntimeFormRelease("child")).thenReturn(new ResolvedEntityFormRelease(child, "ch", 1));
        EntityDefinition parentEntity = new EntityDefinition(); parentEntity.setEntityCode("parents");
        EntityDefinition childEntity = new EntityDefinition(); childEntity.setEntityCode("children");
        when(definitions.selectById("parent-entity")).thenReturn(parentEntity);
        when(definitions.selectById("child-entity")).thenReturn(childEntity);
        EntityDataDTO row = new EntityDataDTO(); row.setId("child-row"); row.setData(Map.of("parent_id", parent));
        when(data.findById("children", "child-row")).thenReturn(row);
    }
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> result = mock(ObjectProvider.class);
        when(result.getObject()).thenReturn(value);
        return result;
    }
}
