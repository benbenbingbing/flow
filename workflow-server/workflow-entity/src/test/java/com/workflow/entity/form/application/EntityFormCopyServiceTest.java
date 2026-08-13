package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.form.application.validation.EntityFormConfigurationValidator;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormFieldMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListActionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityFormCopyServiceTest {

    @Test
    void copyUsesRequestedNameAndKey() {
        Fixture fixture = fixture();

        EntityForm copied = fixture.service().copyForm(
                "form-1", "归档申请", "archive_request");

        assertEquals("归档申请", copied.getFormName());
        assertEquals("archive_request", copied.getFormKey());
        verify(fixture.validator()).validateFormIdentity(copied);
        verify(fixture.formMapper()).existsFormKey(
                "entity-1", "archive_request", "");
    }

    @Test
    void copyWithoutRequestUsesReadableIncrementingKey() {
        Fixture fixture = fixture();
        when(fixture.formMapper().existsFormKey(
                "entity-1", "request_form_copy", ""))
                .thenReturn(true);

        EntityForm copied = fixture.service().copyForm("form-1");

        assertEquals("request_form copy", copied.getFormName());
        assertEquals("request_form_copy_2", copied.getFormKey());
    }

    @Test
    void copyRejectsRequestedDuplicateKeyBeforeInsert() {
        Fixture fixture = fixture();
        when(fixture.formMapper().existsFormKey(
                "entity-1", "existing_form", ""))
                .thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().copyForm(
                        "form-1", "重复副本", "existing_form"));

        assertEquals("表单标识已存在：existing_form", exception.getMessage());
        verify(fixture.formMapper(), never()).insert(any(EntityForm.class));
    }

    private Fixture fixture() {
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityFormFieldMapper fieldMapper =
                mock(EntityFormFieldMapper.class);
        EntityFormNodeMapper nodeMapper = mock(EntityFormNodeMapper.class);
        EntityFormConfigurationValidator validator =
                mock(EntityFormConfigurationValidator.class);
        EntityForm source = new EntityForm();
        source.setId("form-1");
        source.setEntityId("entity-1");
        source.setFormName("request_form");
        source.setFormKey("request_form");
        source.setLayoutType("vertical");
        source.setStatus(1);

        when(formMapper.selectById("form-1")).thenReturn(source);
        when(fieldMapper.selectByFormId(any())).thenReturn(List.of());
        when(nodeMapper.findByFormId(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            EntityForm inserted = invocation.getArgument(0);
            inserted.setId("form-copy");
            return 1;
        }).when(formMapper).insert(any(EntityForm.class));

        EntityFormService service = new EntityFormService(
                formMapper,
                fieldMapper,
                nodeMapper,
                mock(EntityDefinitionMapper.class),
                mock(EntityFieldMapper.class),
                mock(EntityRelationMapper.class),
                validator,
                mock(EntityUiConfigurationPolicy.class),
                mock(SystemEntityFieldPolicy.class),
                mock(EntityListActionMapper.class),
                mock(UiConfigReleaseMapper.class),
                new JsonDocumentCodec(new ObjectMapper()));
        return new Fixture(service, formMapper, validator);
    }

    private record Fixture(
            EntityFormService service,
            EntityFormMapper formMapper,
            EntityFormConfigurationValidator validator) {
    }
}
