package com.workflow.migration.application;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** UI 宿主预建服务于跨实体引用，与接口扩展的实体作用域独立。 */
@ExtendWith(MockitoExtension.class)
class ConfigMigrationUiOwnerPreparationTest {
    @Mock private EntityFormMapper formMapper;
    @Mock private EntityListConfigMapper listConfigMapper;
    @Mock private EntityFormService entityFormService;
    @InjectMocks private ConfigMigrationImportApplyService service;

    @Test
    void createsMissingUiOwnersWithoutAnyInterfaceExtensions() {
        ReflectionTestUtils.invokeMethod(service, "prepareUiOwners", entity(), snapshot());
        var form = ArgumentCaptor.forClass(EntityForm.class);
        verify(entityFormService).saveForm(form.capture());
        assertEquals("entity-id", form.getValue().getEntityId());
        assertEquals("edit", form.getValue().getFormKey());
        assertEquals("编辑", form.getValue().getFormName());
        assertEquals("vertical", form.getValue().getLayoutType());
        assertFalse(form.getValue().getIsDefault());
        var list = ArgumentCaptor.forClass(EntityListConfig.class);
        verify(listConfigMapper).insert(list.capture());
        assertNotNull(list.getValue().getId());
        assertEquals("entity-id", list.getValue().getEntityId());
        assertEquals("order", list.getValue().getEntityCode());
        assertEquals("browse", list.getValue().getListKey());
        assertFalse(list.getValue().getIsDefault());
        assertEquals(0, list.getValue().getPublishedVersion());
    }

    @Test
    void reusesExistingBusinessKeysWithoutOverwritingOrRepublishing() {
        when(formMapper.selectByEntityIdAndFormKey("entity-id", "edit")).thenReturn(new EntityForm());
        when(listConfigMapper.findByEntityIdAndListKey("entity-id", "browse")).thenReturn(new EntityListConfig());
        ReflectionTestUtils.invokeMethod(service, "prepareUiOwners", entity(), snapshot());
        verifyNoInteractions(entityFormService);
        verify(listConfigMapper, never()).insert(any(EntityListConfig.class));
    }

    private EntityDefinition entity() {
        var entity = new EntityDefinition();
        entity.setId("entity-id"); entity.setEntityCode("order");
        return entity;
    }

    private Map<String, Object> snapshot() {
        return Map.of("forms", List.of(Map.of("formKey", "edit", "formName", "编辑")),
                "lists", List.of(Map.of("listKey", "browse", "listName", "浏览")));
    }
}
