package com.workflow.entity.permission.application;

import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityFileUploadAuthorizationServiceTest {

    @Mock
    private EntityActionCapabilityService capabilityService;
    @Mock
    private EntityDefinitionMapper entityDefinitionMapper;
    @Mock
    private EntityFieldMapper entityFieldMapper;

    private EntityFileUploadAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new EntityFileUploadAuthorizationService(
                capabilityService,
                entityDefinitionMapper,
                entityFieldMapper);
    }

    @ParameterizedTest
    @EnumSource(value = EntityField.FieldType.class, names = {"FILE", "IMAGE"})
    void publishedFileFieldAllowsUpload(EntityField.FieldType fieldType) {
        EntityDefinition entity = entity(EntityDefinition.Status.PUBLISHED);
        EntityField field = field(fieldType, true);
        when(entityDefinitionMapper.findByEntityCode("ZDWREQ"))
                .thenReturn(Optional.of(entity));
        when(entityFieldMapper.findByEntityIdAndFieldCode(
                "entity-1",
                "attachment"))
                .thenReturn(field);

        service.requireUpload(" ZDWREQ ", "create", " attachment ");

        verify(capabilityService).requireStandardPermission(
                "zdwreq",
                EntityPermissionAction.CREATE);
        verify(entityDefinitionMapper).findByEntityCode("ZDWREQ");
        verify(entityFieldMapper).findByEntityIdAndFieldCode(
                "entity-1",
                "attachment");
    }

    @Test
    void permissionDenialStopsBeforeMetadataLookup() {
        doThrow(new ForbiddenException("缺少权限"))
                .when(capabilityService)
                .requireStandardPermission(
                        "zdwreq",
                        EntityPermissionAction.CREATE);

        assertThrows(ForbiddenException.class,
                () -> service.requireUpload(
                        "ZDWREQ",
                        "create",
                        "attachment"));

        verify(capabilityService).requireStandardPermission(
                "zdwreq",
                EntityPermissionAction.CREATE);
        verifyNoInteractions(entityDefinitionMapper, entityFieldMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"view", "delete", "unsupported"})
    void nonUploadActionIsRejectedBeforeAuthorization(String action) {
        assertThrows(ForbiddenException.class,
                () -> service.requireUpload(
                        "zdwreq",
                        action,
                        "attachment"));

        verifyNoInteractions(
                capabilityService,
                entityDefinitionMapper,
                entityFieldMapper);
    }

    @ParameterizedTest
    @EnumSource(
            value = EntityDefinition.Status.class,
            names = {"DRAFT", "DISABLED"})
    void unpublishedEntityIsRejected(EntityDefinition.Status status) {
        when(entityDefinitionMapper.findByEntityCode("zdwreq"))
                .thenReturn(Optional.of(entity(status)));

        assertThrows(ForbiddenException.class,
                () -> service.requireUpload(
                        "zdwreq",
                        "update",
                        "attachment"));

        verify(capabilityService).requireStandardPermission(
                "zdwreq",
                EntityPermissionAction.UPDATE);
        verifyNoInteractions(entityFieldMapper);
    }

    @Test
    void unpublishedFileFieldIsRejected() {
        EntityDefinition entity = entity(EntityDefinition.Status.PUBLISHED);
        when(entityDefinitionMapper.findByEntityCode("zdwreq"))
                .thenReturn(Optional.of(entity));
        when(entityFieldMapper.findByEntityIdAndFieldCode(
                "entity-1",
                "attachment"))
                .thenReturn(field(EntityField.FieldType.FILE, false));

        assertThrows(ForbiddenException.class,
                () -> service.requireUpload(
                        "zdwreq",
                        "approve",
                        "attachment"));

        verify(capabilityService).requireStandardPermission(
                "zdwreq",
                EntityPermissionAction.APPROVE);
    }

    @Test
    void nonFileFieldIsRejected() {
        EntityDefinition entity = entity(EntityDefinition.Status.PUBLISHED);
        when(entityDefinitionMapper.findByEntityCode("zdwreq"))
                .thenReturn(Optional.of(entity));
        when(entityFieldMapper.findByEntityIdAndFieldCode(
                "entity-1",
                "attachment"))
                .thenReturn(field(EntityField.FieldType.STRING, true));

        assertThrows(ForbiddenException.class,
                () -> service.requireUpload(
                        "zdwreq",
                        "create",
                        "attachment"));

        verify(capabilityService).requireStandardPermission(
                "zdwreq",
                EntityPermissionAction.CREATE);
    }

    private EntityDefinition entity(EntityDefinition.Status status) {
        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-1");
        entity.setEntityCode("zdwreq");
        entity.setStatus(status);
        return entity;
    }

    private EntityField field(
            EntityField.FieldType fieldType,
            boolean published) {
        EntityField field = new EntityField();
        field.setEntityId("entity-1");
        field.setFieldCode("attachment");
        field.setFieldType(fieldType);
        field.setIsPublished(published);
        return field;
    }
}
