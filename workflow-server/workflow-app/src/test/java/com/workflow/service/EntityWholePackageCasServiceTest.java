package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.RevisionConflictException;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.EntityListConfigDTO;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListField;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityFormFieldMapper;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityFormNodeMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListFieldMapper;
import com.workflow.mapper.EntityRelationMapper;
import com.workflow.service.config.EntityFormConfigurationValidator;
import com.workflow.service.config.EntityListConfigurationValidator;
import com.workflow.service.permission.EntityActionCapabilityService;
import com.workflow.service.permission.EntityListActionConfigService;
import com.workflow.service.permission.EntityPermissionCatalogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityWholePackageCasServiceTest {

    @Test
    void existingFormWholeSaveRequiresExpectedRevision() {
        FormContext context = formContext();
        EntityForm current = form("form-1", 4);
        when(context.formMapper().selectByIdForUpdate("form-1"))
                .thenReturn(current);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().saveForm(form("form-1", null), null));

        assertEquals("expectedRevision 不能为空", exception.getMessage());
        verify(context.formMapper(), never()).update(any(), any());
        verify(context.formFieldMapper(), never()).selectByFormId(any());
    }

    @Test
    void staleFormWholeSaveReturnsServerCurrentConfiguration() {
        FormContext context = formContext();
        EntityForm current = form("form-1", 7);
        stubCurrentForm(context, current, List.of());
        when(context.formMapper().selectByIdForUpdate("form-1"))
                .thenReturn(current);

        RevisionConflictException exception = assertThrows(
                RevisionConflictException.class,
                () -> context.service().saveForm(form("form-1", null), 6));

        EntityForm server = (EntityForm) exception.getCurrentData();
        assertEquals(7, server.getRevision());
        verify(context.formMapper(), never()).update(any(), any());
        verify(context.formFieldMapper(), never())
                .insert(any(EntityFormField.class));
    }

    @Test
    void formWholeSaveUsesRevisionConditionAndMutableColumnWhitelist() {
        FormContext context = formContext();
        EntityForm current = form("form-1", 3);
        current.setEntityId("entity-server");
        current.setFormKey("server_key");
        current.setActiveReleaseId("release-1");
        EntityForm incoming = form("form-1", null);
        incoming.setEntityId("entity-client");
        incoming.setFormKey("client_key");
        incoming.setFormName("更新后的表单");
        incoming.setDataSourceBindingsDocument(
                "{\"FORM_INIT\":[{\"sourceId\":\"source-1\"}]}");
        EntityForm refreshed = form("form-1", 4);
        refreshed.setEntityId("entity-server");
        refreshed.setFormKey("server_key");
        stubCurrentForm(context, refreshed, List.of());
        when(context.formMapper().selectByIdForUpdate("form-1"))
                .thenReturn(current);
        when(context.formMapper().update(isNull(), any()))
                .thenReturn(1);

        EntityForm saved = context.service().saveForm(incoming, 3);

        assertEquals(4, saved.getRevision());
        ArgumentCaptor<UpdateWrapper<EntityForm>> captor =
                updateWrapperCaptor();
        verify(context.formMapper()).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        String where = captor.getValue().getSqlSegment();
        assertTrue(sqlSet.contains("form_name"));
        assertTrue(sqlSet.contains("data_source_bindings_document"));
        assertTrue(sqlSet.contains("revision"));
        assertFalse(sqlSet.contains("entity_id"));
        assertFalse(sqlSet.contains("form_key"));
        assertFalse(sqlSet.contains("active_release_id"));
        assertTrue(where.contains("revision"));
    }

    @Test
    void legacyFormWholeSavePreservesOmittedFormDataSourceBindings() {
        FormContext context = formContext();
        EntityForm current = form("form-1", 3);
        current.setDataSourceBindingsDocument(
                "{\"FORM_INIT\":[{\"sourceId\":\"source-1\"}]}");
        EntityForm incoming = form("form-1", null);
        EntityForm refreshed = form("form-1", 4);
        refreshed.setDataSourceBindingsDocument(
                current.getDataSourceBindingsDocument());
        stubCurrentForm(context, refreshed, List.of());
        when(context.formMapper().selectByIdForUpdate("form-1"))
                .thenReturn(current);
        when(context.formMapper().update(isNull(), any()))
                .thenReturn(1);

        context.service().saveForm(incoming, 3);

        ArgumentCaptor<UpdateWrapper<EntityForm>> captor =
                updateWrapperCaptor();
        verify(context.formMapper()).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getSqlSet().contains(
                "data_source_bindings_document"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(
                current.getDataSourceBindingsDocument()));
    }

    @Test
    void formFieldSnapshotConflictDoesNotSilentlyOverwrite() {
        FormContext context = formContext();
        EntityForm current = form("form-1", 2);
        EntityForm refreshed = form("form-1", 3);
        EntityFormField existing = formField("field-1", "amount", "旧标签");
        EntityFormField incoming = formField("field-1", "amount", "新标签");
        incoming.setUpdateTime(existing.getUpdateTime());
        EntityForm request = form("form-1", null);
        request.setFields(List.of(incoming));
        when(context.formMapper().selectByIdForUpdate("form-1"))
                .thenReturn(current);
        when(context.formMapper().update(isNull(), any()))
                .thenReturn(1);
        when(context.formFieldMapper().selectByFormId("form-1"))
                .thenReturn(List.of(existing));
        when(context.formFieldMapper().update(isNull(), any()))
                .thenReturn(0);
        stubCurrentForm(context, refreshed, List.of(existing));

        RevisionConflictException exception = assertThrows(
                RevisionConflictException.class,
                () -> context.service().saveForm(request, 2));

        assertEquals(3, ((EntityForm) exception.getCurrentData()).getRevision());
        ArgumentCaptor<UpdateWrapper<EntityFormField>> captor =
                updateWrapperCaptor();
        verify(context.formFieldMapper()).update(isNull(), captor.capture());
        String where = captor.getValue().getSqlSegment();
        assertTrue(where.contains("form_id"));
        assertTrue(where.contains("update_time"));
    }

    @Test
    void formSystemImportHasExplicitNonApiSavePath() {
        FormContext context = formContext();
        EntityForm current = form("form-1", 4);
        EntityForm refreshed = form("form-1", 5);
        stubCurrentForm(context, refreshed, List.of());
        when(context.formMapper().selectByIdForUpdate("form-1"))
                .thenReturn(current);
        when(context.formMapper().update(isNull(), any()))
                .thenReturn(1);

        EntityForm saved =
                context.service().saveFormForImport(form("form-1", null));

        assertEquals(5, saved.getRevision());
    }

    @Test
    void existingListWholeSaveRequiresExpectedRevision() {
        ListContext context = listContext();
        EntityListConfig current = listConfig("list-1", 5);
        when(context.configMapper().selectByIdForUpdate("list-1"))
                .thenReturn(current);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.service().saveConfig(
                        listRequest("list-1", null),
                        null));

        assertEquals("expectedRevision 不能为空", exception.getMessage());
        verify(context.configMapper(), never()).update(any(), any());
        verify(context.fieldMapper(), never()).findByListConfigId(any());
    }

    @Test
    void staleListWholeSaveReturnsServerCurrentConfiguration() {
        ListContext context = listContext();
        EntityListConfig current = listConfig("list-1", 8);
        stubCurrentList(context, current, List.of());
        when(context.configMapper().selectByIdForUpdate("list-1"))
                .thenReturn(current);

        RevisionConflictException exception = assertThrows(
                RevisionConflictException.class,
                () -> context.service().saveConfig(
                        listRequest("list-1", null),
                        7));

        EntityListConfigDTO server =
                (EntityListConfigDTO) exception.getCurrentData();
        assertEquals(8, server.getRevision());
        verify(context.configMapper(), never()).update(any(), any());
        verify(context.fieldMapper(), never())
                .insert(any(EntityListField.class));
    }

    @Test
    void listWholeSaveUsesRevisionConditionAndMutableColumnWhitelist() {
        ListContext context = listContext();
        EntityListConfig current = listConfig("list-1", 2);
        current.setEntityId("entity-server");
        current.setEntityCode("order");
        current.setListKey("server_key");
        current.setActiveReleaseId("release-1");
        current.setPublishedVersion(9);
        EntityListConfigDTO incoming = listRequest("list-1", null);
        incoming.setEntityId("entity-client");
        incoming.setEntityCode("client");
        incoming.setListKey("client_key");
        incoming.setListName("更新后的列表");
        EntityListConfig refreshed = listConfig("list-1", 3);
        refreshed.setEntityId("entity-server");
        refreshed.setEntityCode("order");
        refreshed.setListKey("server_key");
        stubCurrentList(context, refreshed, List.of());
        when(context.configMapper().selectByIdForUpdate("list-1"))
                .thenReturn(current);
        when(context.configMapper().update(isNull(), any()))
                .thenReturn(1);

        EntityListConfigDTO saved =
                context.service().saveConfig(incoming, 2);

        assertEquals(3, saved.getRevision());
        ArgumentCaptor<UpdateWrapper<EntityListConfig>> captor =
                updateWrapperCaptor();
        verify(context.configMapper()).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        String where = captor.getValue().getSqlSegment();
        assertTrue(sqlSet.contains("list_name"));
        assertTrue(sqlSet.contains("revision"));
        assertFalse(sqlSet.contains("entity_id"));
        assertFalse(sqlSet.contains("entity_code"));
        assertFalse(sqlSet.contains("list_key"));
        assertFalse(sqlSet.contains("active_release_id"));
        assertFalse(sqlSet.contains("published_version"));
        assertTrue(where.contains("revision"));
    }

    @Test
    void listFieldRevisionMismatchStopsBeforeFieldUpdate() {
        ListContext context = listContext();
        EntityListConfig current = listConfig("list-1", 3);
        EntityListConfig refreshed = listConfig("list-1", 4);
        EntityListField existing = listField("field-1", "amount", 4, 100);
        EntityListField incoming = listField("field-1", "amount", 3, 120);
        EntityListConfigDTO request = listRequest("list-1", List.of(incoming));
        when(context.configMapper().selectByIdForUpdate("list-1"))
                .thenReturn(current);
        when(context.configMapper().update(isNull(), any()))
                .thenReturn(1);
        when(context.fieldMapper().findByListConfigId("list-1"))
                .thenReturn(List.of(existing));
        stubCurrentList(context, refreshed, List.of(existing));

        RevisionConflictException exception = assertThrows(
                RevisionConflictException.class,
                () -> context.service().saveConfig(request, 3));

        assertEquals(
                4,
                ((EntityListConfigDTO) exception.getCurrentData()).getRevision());
        verify(context.fieldMapper(), never()).update(any(), any());
    }

    @Test
    void listFieldConditionalUpdateFailureReturnsWholeServerConfiguration() {
        ListContext context = listContext();
        EntityListConfig current = listConfig("list-1", 6);
        EntityListConfig refreshed = listConfig("list-1", 7);
        EntityListField existing = listField("field-1", "amount", 2, 100);
        EntityListField incoming = listField("field-1", "amount", 2, 160);
        EntityListConfigDTO request = listRequest("list-1", List.of(incoming));
        when(context.configMapper().selectByIdForUpdate("list-1"))
                .thenReturn(current);
        when(context.configMapper().update(isNull(), any()))
                .thenReturn(1);
        when(context.fieldMapper().findByListConfigId("list-1"))
                .thenReturn(List.of(existing));
        when(context.fieldMapper().update(isNull(), any()))
                .thenReturn(0);
        stubCurrentList(context, refreshed, List.of(existing));

        RevisionConflictException exception = assertThrows(
                RevisionConflictException.class,
                () -> context.service().saveConfig(request, 6));

        assertEquals(
                7,
                ((EntityListConfigDTO) exception.getCurrentData()).getRevision());
        ArgumentCaptor<UpdateWrapper<EntityListField>> captor =
                updateWrapperCaptor();
        verify(context.fieldMapper()).update(isNull(), captor.capture());
        String where = captor.getValue().getSqlSegment();
        assertTrue(where.contains("list_config_id"));
        assertTrue(where.contains("revision"));
    }

    @Test
    void listSystemImportHasExplicitNonApiSavePath() {
        ListContext context = listContext();
        EntityListConfig current = listConfig("list-1", 9);
        EntityListConfig refreshed = listConfig("list-1", 10);
        stubCurrentList(context, refreshed, List.of());
        when(context.configMapper().selectByIdForUpdate("list-1"))
                .thenReturn(current);
        when(context.configMapper().update(isNull(), any()))
                .thenReturn(1);

        EntityListConfigDTO saved =
                context.service().saveConfigForImport(
                        listRequest("list-1", null));

        assertEquals(10, saved.getRevision());
    }

    private FormContext formContext() {
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityFormFieldMapper formFieldMapper =
                mock(EntityFormFieldMapper.class);
        EntityFormNodeMapper formNodeMapper =
                mock(EntityFormNodeMapper.class);
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityFieldMapper entityFieldMapper = mock(EntityFieldMapper.class);
        EntityRelationMapper relationMapper = mock(EntityRelationMapper.class);
        EntityFormService service = new EntityFormService(
                formMapper,
                formFieldMapper,
                formNodeMapper,
                definitionMapper,
                entityFieldMapper,
                relationMapper,
                mock(EntityFormConfigurationValidator.class),
                mock(EntityDefinitionAccessPolicy.class),
                new JsonDocumentCodec(new ObjectMapper()));
        return new FormContext(
                service,
                formMapper,
                formFieldMapper,
                formNodeMapper);
    }

    private ListContext listContext() {
        EntityListConfigMapper configMapper =
                mock(EntityListConfigMapper.class);
        EntityListFieldMapper fieldMapper = mock(EntityListFieldMapper.class);
        EntityListActionConfigService actionConfigService =
                mock(EntityListActionConfigService.class);
        EntityListRelationalConfigService relationalConfigService =
                mock(EntityListRelationalConfigService.class);
        EntityListConfigService service = new EntityListConfigService(
                configMapper,
                fieldMapper,
                actionConfigService,
                mock(EntityPermissionCatalogService.class),
                mock(EntityActionCapabilityService.class),
                mock(EntityListConfigurationValidator.class),
                mock(CurrentUserRoleService.class),
                mock(EntityDefinitionAccessPolicy.class),
                new JsonDocumentCodec(new ObjectMapper()),
                relationalConfigService);
        when(actionConfigService.resolveToolbarButtons(any(), any()))
                .thenReturn(List.of());
        when(actionConfigService.resolveRowButtons(any(), any()))
                .thenReturn(List.of());
        when(relationalConfigService.findScenes(any())).thenReturn(List.of());
        return new ListContext(
                service,
                configMapper,
                fieldMapper);
    }

    private void stubCurrentForm(
            FormContext context,
            EntityForm form,
            List<EntityFormField> fields) {
        when(context.formMapper().selectById(form.getId())).thenReturn(form);
        when(context.formFieldMapper().selectByFormId(form.getId()))
                .thenReturn(fields);
        when(context.formNodeMapper().findByFormId(form.getId()))
                .thenReturn(List.of());
    }

    private void stubCurrentList(
            ListContext context,
            EntityListConfig config,
            List<EntityListField> fields) {
        when(context.configMapper().selectById(config.getId()))
                .thenReturn(config);
        when(context.fieldMapper().findByListConfigId(config.getId()))
                .thenReturn(fields);
    }

    private EntityForm form(String id, Integer revision) {
        EntityForm form = new EntityForm();
        form.setId(id);
        form.setEntityId("entity-1");
        form.setFormKey("default_form");
        form.setFormName("默认表单");
        form.setLayoutType("vertical");
        form.setIsDefault(false);
        form.setStatus(1);
        form.setRevision(revision);
        return form;
    }

    private EntityFormField formField(
            String id,
            String fieldCode,
            String label) {
        EntityFormField field = new EntityFormField();
        field.setId(id);
        field.setFormId("form-1");
        field.setFieldId("entity-field-1");
        field.setFieldCode(fieldCode);
        field.setFieldLabel(label);
        field.setSortOrder(0);
        field.setUpdateTime(LocalDateTime.of(2026, 7, 20, 20, 0));
        return field;
    }

    private EntityListConfig listConfig(String id, Integer revision) {
        EntityListConfig config = new EntityListConfig();
        config.setId(id);
        config.setEntityId("entity-1");
        config.setEntityCode("order");
        config.setListKey("default");
        config.setListName("默认列表");
        config.setDataScopeMode("INHERIT");
        config.setRevision(revision);
        config.setDeleted(0);
        return config;
    }

    private EntityListConfigDTO listRequest(
            String id,
            List<EntityListField> fields) {
        EntityListConfigDTO dto = new EntityListConfigDTO();
        dto.setId(id);
        dto.setEntityId("entity-1");
        dto.setEntityCode("order");
        dto.setListKey("default");
        dto.setListName("默认列表");
        dto.setDataScopeMode("INHERIT");
        dto.setFields(fields);
        return dto;
    }

    private EntityListField listField(
            String id,
            String fieldCode,
            Integer revision,
            Integer width) {
        EntityListField field = new EntityListField();
        field.setId(id);
        field.setListConfigId("list-1");
        field.setFieldId("entity-field-1");
        field.setFieldCode(fieldCode);
        field.setFieldName("金额");
        field.setRevision(revision);
        field.setWidth(width);
        field.setSortOrder(0);
        field.setOrderKey(1_000_000L);
        field.setDeleted(0);
        return field;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> ArgumentCaptor<UpdateWrapper<T>> updateWrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(UpdateWrapper.class);
    }

    private record FormContext(
            EntityFormService service,
            EntityFormMapper formMapper,
            EntityFormFieldMapper formFieldMapper,
            EntityFormNodeMapper formNodeMapper) {
    }

    private record ListContext(
            EntityListConfigService service,
            EntityListConfigMapper configMapper,
            EntityListFieldMapper fieldMapper) {
    }
}
