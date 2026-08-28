package com.workflow.entity.form.infrastructure.adapter;

import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicFormUniqueConflictQueryTest {

    @Test
    void temporalFieldsUseFullScanToAvoidIsoAndMysqlTextMismatch() {
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityFieldMapper fieldMapper =
                mock(EntityFieldMapper.class);
        EntityDataDynamicMapper dynamicMapper =
                mock(EntityDataDynamicMapper.class);
        DynamicTableService tableService =
                mock(DynamicTableService.class);
        EntityRuntimeRecordMapper recordMapper =
                mock(EntityRuntimeRecordMapper.class);
        DynamicFormUniqueConflictQuery query =
                new DynamicFormUniqueConflictQuery(
                        definitionMapper,
                        fieldMapper,
                        dynamicMapper,
                        tableService,
                        recordMapper);
        EntityDefinition definition = new EntityDefinition();
        definition.setId("event-definition");
        definition.setEntityCode("event");
        EntityField field = new EntityField();
        field.setFieldCode("startsAt");
        field.setDbColumnName("starts_at");
        when(definitionMapper.findByEntityCode("event"))
                .thenReturn(Optional.of(definition));
        when(fieldMapper.findByEntityId("event-definition"))
                .thenReturn(List.of(field));
        when(tableService.getTableName("event"))
                .thenReturn("wf_event");
        when(dynamicMapper.selectList("wf_event"))
                .thenReturn(List.of());

        field.setFieldType(EntityField.FieldType.DATETIME);
        query.findCandidates(
                "event",
                "startsAt",
                "2026-08-27t10:30:00",
                "record-1");
        field.setFieldType(EntityField.FieldType.DATE);
        query.findCandidates(
                "event",
                "startsAt",
                "2026-08-27",
                "record-1");

        verify(dynamicMapper, times(2))
                .selectList("wf_event");
        verify(dynamicMapper, never())
                .selectFormUniqueCandidates(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingPersistentTargetFieldFailsClosedBeforeSql() {
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityFieldMapper fieldMapper =
                mock(EntityFieldMapper.class);
        EntityDataDynamicMapper dynamicMapper =
                mock(EntityDataDynamicMapper.class);
        DynamicTableService tableService =
                mock(DynamicTableService.class);
        EntityDefinition definition = new EntityDefinition();
        definition.setId("event-definition");
        when(definitionMapper.findByEntityCode("event"))
                .thenReturn(Optional.of(definition));
        when(fieldMapper.findByEntityId("event-definition"))
                .thenReturn(List.of());
        DynamicFormUniqueConflictQuery query =
                new DynamicFormUniqueConflictQuery(
                        definitionMapper,
                        fieldMapper,
                        dynamicMapper,
                        tableService,
                        mock(EntityRuntimeRecordMapper.class));

        assertThrows(
                IllegalArgumentException.class,
                () -> query.findCandidates(
                        "event",
                        "forgedField",
                        "value",
                        null));

        verify(dynamicMapper, never()).selectFormUniqueCandidates(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        verify(dynamicMapper, never()).selectList(
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void authoritativeTemporalCandidateUsesFullScanForUpdate() {
        QueryFixture fixture = fixture(
                EntityField.FieldType.DATETIME);
        when(fixture.dynamicMapper()
                .selectListForUpdate("wf_event"))
                .thenReturn(List.of());

        fixture.query().findCandidatesForAuthoritativeCheck(
                "event",
                "startsAt",
                "2026-08-27t10:30:00",
                "record-1");

        verify(fixture.dynamicMapper())
                .selectListForUpdate("wf_event");
        verify(fixture.dynamicMapper(), never())
                .selectList("wf_event");
        verify(fixture.dynamicMapper(), never())
                .selectFormUniqueCandidatesForUpdate(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authoritativeTextCandidateUsesFilteredScanForUpdate() {
        QueryFixture fixture = fixture(
                EntityField.FieldType.STRING);
        when(fixture.dynamicMapper()
                .selectFormUniqueCandidatesForUpdate(
                        "wf_event",
                        "starts_at",
                        "event a",
                        "record-1"))
                .thenReturn(List.of());

        fixture.query().findCandidatesForAuthoritativeCheck(
                "event",
                "startsAt",
                "event a",
                "record-1");

        verify(fixture.dynamicMapper())
                .selectFormUniqueCandidatesForUpdate(
                        "wf_event",
                        "starts_at",
                        "event a",
                        "record-1");
        verify(fixture.dynamicMapper(), never())
                .selectFormUniqueCandidates(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
        verify(fixture.dynamicMapper(), never())
                .selectListForUpdate("wf_event");
    }

    @Test
    void precheckTextCandidateKeepsOrdinaryRead() {
        QueryFixture fixture = fixture(
                EntityField.FieldType.STRING);
        when(fixture.dynamicMapper()
                .selectFormUniqueCandidates(
                        "wf_event",
                        "starts_at",
                        "event a",
                        null))
                .thenReturn(List.of());

        fixture.query().findCandidates(
                "event",
                "startsAt",
                "event a",
                null);

        verify(fixture.dynamicMapper())
                .selectFormUniqueCandidates(
                        "wf_event",
                        "starts_at",
                        "event a",
                        null);
        verify(fixture.dynamicMapper(), never())
                .selectFormUniqueCandidatesForUpdate(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
    }

    private QueryFixture fixture(
            EntityField.FieldType fieldType) {
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityFieldMapper fieldMapper =
                mock(EntityFieldMapper.class);
        EntityDataDynamicMapper dynamicMapper =
                mock(EntityDataDynamicMapper.class);
        DynamicTableService tableService =
                mock(DynamicTableService.class);
        EntityRuntimeRecordMapper recordMapper =
                mock(EntityRuntimeRecordMapper.class);
        EntityDefinition definition = new EntityDefinition();
        definition.setId("event-definition");
        definition.setEntityCode("event");
        EntityField field = new EntityField();
        field.setFieldCode("startsAt");
        field.setDbColumnName("starts_at");
        field.setFieldType(fieldType);
        when(definitionMapper.findByEntityCode("event"))
                .thenReturn(Optional.of(definition));
        when(fieldMapper.findByEntityId("event-definition"))
                .thenReturn(List.of(field));
        when(tableService.getTableName("event"))
                .thenReturn("wf_event");
        return new QueryFixture(
                new DynamicFormUniqueConflictQuery(
                        definitionMapper,
                        fieldMapper,
                        dynamicMapper,
                        tableService,
                        recordMapper),
                dynamicMapper);
    }

    private record QueryFixture(
            DynamicFormUniqueConflictQuery query,
            EntityDataDynamicMapper dynamicMapper) {
    }
}
