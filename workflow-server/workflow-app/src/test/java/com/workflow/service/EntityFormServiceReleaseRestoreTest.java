package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.form.application.validation.EntityFormConfigurationValidator;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormFieldMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListActionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 发布快照恢复表单草稿时的稳定身份回归测试。 */
class EntityFormServiceReleaseRestoreTest {

    /**
     * 同编码草稿字段使用了新 ID 时，撤销必须物理删除该行并以发布 ID 重建，
     * 不能沿用 SYSTEM_IMPORT 的 fieldCode 兼容匹配结果。
     */
    @Test
    void restoreFormForReleaseRebuildsLegacyFieldWithPublishedId() {
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityFormFieldMapper formFieldMapper = mock(EntityFormFieldMapper.class);
        EntityFormNodeMapper formNodeMapper = mock(EntityFormNodeMapper.class);
        EntityDefinitionMapper entityMapper = mock(EntityDefinitionMapper.class);
        EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
        EntityFormConfigurationValidator validator =
                mock(EntityFormConfigurationValidator.class);
        EntityFormService service = new EntityFormService(
                formMapper,
                formFieldMapper,
                formNodeMapper,
                entityMapper,
                fieldMapper,
                mock(EntityRelationMapper.class),
                validator,
                mock(EntityUiConfigurationPolicy.class),
                mock(SystemEntityFieldPolicy.class),
                mock(EntityListActionMapper.class),
                mock(UiConfigReleaseMapper.class),
                new JsonDocumentCodec(new ObjectMapper()));

        EntityForm current = form(7);
        EntityForm published = form(3);
        EntityFormField draftField = field("draft-field-id", "customerName");
        EntityFormField publishedField = field(
                "published-node-id",
                "customerName");
        published.setFields(List.of(publishedField));

        EntityForm restored = form(8);
        AtomicReference<EntityFormField> persistedField =
                new AtomicReference<>(draftField);
        when(formMapper.selectByIdForUpdate("form-1"))
                .thenReturn(current, current);
        when(formFieldMapper.selectByFormIdForUpdate("form-1"))
                .thenReturn(List.of(draftField));
        when(formFieldMapper.selectByFormId("form-1"))
                .thenAnswer(ignored -> persistedField.get() == null
                        ? List.of()
                        : List.of(persistedField.get()));
        when(formFieldMapper.selectById(any()))
                .thenAnswer(invocation -> {
                    EntityFormField persisted = persistedField.get();
                    return persisted != null
                            && invocation.getArgument(0).equals(
                                    persisted.getId())
                            ? persisted : null;
                });
        doAnswer(ignored -> {
            persistedField.set(null);
            return null;
        }).when(formFieldMapper).deleteByFormId("form-1");
        doAnswer(invocation -> {
            persistedField.set(invocation.getArgument(0));
            return 1;
        }).when(formFieldMapper).insert(any(EntityFormField.class));
        when(formMapper.update(any(), any())).thenReturn(1);
        when(formMapper.selectById("form-1")).thenReturn(restored);
        when(formNodeMapper.findByFormId("form-1")).thenReturn(List.of());

        EntityForm result = service.restoreFormForRelease(published, 7);

        assertEquals(8, result.getRevision());
        assertEquals("published-node-id", result.getFields().get(0).getId());
        InOrder fieldWriteOrder = inOrder(formFieldMapper);
        fieldWriteOrder.verify(formFieldMapper).deleteByFormId("form-1");
        ArgumentCaptor<EntityFormField> inserted =
                ArgumentCaptor.forClass(EntityFormField.class);
        fieldWriteOrder.verify(formFieldMapper).insert(inserted.capture());
        assertEquals("published-node-id", inserted.getValue().getId());
    }

    private static EntityForm form(int revision) {
        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setFormName("客户表单");
        form.setLayoutType("vertical");
        form.setStatus(1);
        form.setIsDefault(false);
        form.setRevision(revision);
        return form;
    }

    private static EntityFormField field(String id, String fieldCode) {
        EntityFormField field = new EntityFormField();
        field.setId(id);
        field.setFormId("form-1");
        field.setFieldId("entity-field-1");
        field.setFieldCode(fieldCode);
        field.setFieldName("客户名称");
        field.setFieldLabel("客户名称");
        field.setFieldType("STRING");
        field.setComponentType("input");
        field.setSortOrder(0);
        return field;
    }
}
