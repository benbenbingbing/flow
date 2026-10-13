package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityUserReferenceMapper;
import com.workflow.core.database.mybatis.ParameterizedSql;
import static org.mockito.ArgumentMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EntityUserReferenceAdapterTest {

    private static final String ENTITY_CODE = "leave_request";
    private static final String ENTITY_ID = "entity-leave-request";
    private static final String RECORD_ID = "record-1";

    private EntityDefinitionMapper definitionMapper;
    private EntityFieldMapper fieldMapper;
    private EntityDataDynamicMapper dataMapper;
    private EntityPhysicalTableResolver tableResolver;
    private DynamicTableService dynamicTableService;
    private EntityUserReferenceMapper businessMapper;
    private EntityUserReferenceAdapter adapter;

    @BeforeEach
    void setUp() {
        definitionMapper = mock(EntityDefinitionMapper.class);
        fieldMapper = mock(EntityFieldMapper.class);
        dataMapper = mock(EntityDataDynamicMapper.class);
        tableResolver = mock(EntityPhysicalTableResolver.class);
        dynamicTableService = mock(DynamicTableService.class);
        businessMapper = mock(EntityUserReferenceMapper.class);
        adapter = new EntityUserReferenceAdapter(
                definitionMapper,
                fieldMapper,
                dataMapper,
                tableResolver,
                dynamicTableService,
                businessMapper, com.workflow.integration.database.api.query.DatabaseQueryDialects.forDatabaseId("MYSQL"));

        when(definitionMapper.findByEntityCode(ENTITY_CODE))
                .thenReturn(Optional.of(definition(
                        ENTITY_ID, ENTITY_CODE)));
    }

    @Test
    void userFieldPrefersExactIdMappingOverConflictingUsername() {
        EntityField field = userField(
                "approverId", EntityField.FieldType.USER);
        field.setDbColumnName("approver_id");
        when(fieldMapper.findByEntityIdAndFieldCode(
                ENTITY_ID, "approverId"))
                .thenReturn(field);
        when(tableResolver.resolve(any(EntityDefinition.class)))
                .thenReturn("biz_leave_request");
        when(dataMapper.selectById(
                "biz_leave_request", RECORD_ID))
                .thenReturn(Map.of("approver_id", " id-alice "));
        when(businessMapper.selectUserIdentities(argThat(statement -> java.util.Objects.equals("SELECT id, username, deleted FROM sys_user"
                        + " WHERE id IN (?)", statement.sql()))))
                .thenReturn(List.of(Map.of(
                        "id", "id-alice",
                        "username", "alice",
                        "deleted", 0)));

        List<String> result = adapter.readUserKeys(
                ENTITY_CODE, RECORD_ID, "approverId");

        assertEquals(List.of("alice"), result);
        verify(dataMapper).selectById(
                "biz_leave_request", RECORD_ID);
        verifyNoInteractions(dynamicTableService);
    }

    @Test
    void userFieldAcceptsValidatedLegacyUsernameWhenNoIdExists() {
        EntityField field = userField(
                "approverId", EntityField.FieldType.USER);
        field.setDbColumnName("approver_id");
        when(fieldMapper.findByEntityIdAndFieldCode(
                ENTITY_ID, "approverId"))
                .thenReturn(field);
        when(tableResolver.resolve(any(EntityDefinition.class)))
                .thenReturn("biz_leave_request");
        when(dataMapper.selectById(
                "biz_leave_request", RECORD_ID))
                .thenReturn(Map.of("approver_id", "legacy-approver"));
        when(businessMapper.selectUserIdentities(argThat(statement -> java.util.Objects.equals("SELECT id, username, deleted FROM sys_user"
                        + " WHERE id IN (?)", statement.sql()))))
                .thenReturn(List.of());
        when(businessMapper.selectLegacyUsernames(argThat(statement -> java.util.Objects.equals("SELECT username FROM sys_user"
                        + " WHERE username IN (?) AND deleted = 0", statement.sql()))))
                .thenReturn(List.of("legacy-approver"));

        assertEquals(
                List.of("legacy-approver"),
                adapter.readUserKeys(
                        ENTITY_CODE, RECORD_ID, "approverId"));
    }

    @Test
    void userFieldDoesNotReinterpretDeletedIdAsLegacyUsername() {
        EntityField field = userField(
                "approverId", EntityField.FieldType.USER);
        field.setDbColumnName("approver_id");
        when(fieldMapper.findByEntityIdAndFieldCode(
                ENTITY_ID, "approverId"))
                .thenReturn(field);
        when(tableResolver.resolve(any(EntityDefinition.class)))
                .thenReturn("biz_leave_request");
        when(dataMapper.selectById(
                "biz_leave_request", RECORD_ID))
                .thenReturn(Map.of(
                        "approver_id", "deleted-user-id"));
        when(businessMapper.selectUserIdentities(argThat(statement -> java.util.Objects.equals("SELECT id, username, deleted FROM sys_user"
                        + " WHERE id IN (?)", statement.sql()))))
                .thenReturn(List.of(Map.of(
                        "id", "deleted-user-id",
                        "username", "former-user",
                        "deleted", 1)));
        // 若 precedence blocker 失效，大小写不敏感的数据库会把该值
        // 解析为另一名启用用户的 username，导致审批误派。
        lenient().when(businessMapper.selectLegacyUsernames(argThat(statement -> java.util.Objects.equals("SELECT username FROM sys_user"
                        + " WHERE username IN (?) AND deleted = 0", statement.sql()))))
                .thenReturn(List.of("deleted-user-id"));

        assertEquals(
                List.of(),
                adapter.readUserKeys(
                        ENTITY_CODE, RECORD_ID, "approverId"));
        verify(businessMapper, never()).selectLegacyUsernames(argThat(statement -> java.util.Objects.equals("SELECT username FROM sys_user"
                        + " WHERE username IN (?) AND deleted = 0", statement.sql())));
    }

    @Test
    void userFieldReturnsCanonicalLegacyUsernameIgnoringCase() {
        EntityField field = userField(
                "approverId", EntityField.FieldType.USER);
        field.setDbColumnName("approver_id");
        when(fieldMapper.findByEntityIdAndFieldCode(
                ENTITY_ID, "approverId"))
                .thenReturn(field);
        when(tableResolver.resolve(any(EntityDefinition.class)))
                .thenReturn("biz_leave_request");
        when(dataMapper.selectById(
                "biz_leave_request", RECORD_ID))
                .thenReturn(Map.of(
                        "approver_id", "Legacy-Approver"));
        when(businessMapper.selectUserIdentities(argThat(statement -> java.util.Objects.equals("SELECT id, username, deleted FROM sys_user"
                        + " WHERE id IN (?)", statement.sql()))))
                .thenReturn(List.of());
        when(businessMapper.selectLegacyUsernames(argThat(statement -> java.util.Objects.equals("SELECT username FROM sys_user"
                        + " WHERE username IN (?) AND deleted = 0", statement.sql()))))
                .thenReturn(List.of("legacy-approver"));

        assertEquals(
                List.of("legacy-approver"),
                adapter.readUserKeys(
                        ENTITY_CODE, RECORD_ID, "approverId"));
    }

    @Test
    void modernReferenceAlsoPrefersExactIdOverConflictingUsername() {
        EntityField field = userField(
                "approverId", EntityField.FieldType.REFERENCE);
        field.setDbColumnName("approver_id");
        field.setRefEntityId("entity-sys-user");
        when(fieldMapper.findByEntityIdAndFieldCode(
                ENTITY_ID, "approverId"))
                .thenReturn(field);
        when(definitionMapper.selectById("entity-sys-user"))
                .thenReturn(definition("entity-sys-user", "sys_user"));
        when(tableResolver.resolve(any(EntityDefinition.class)))
                .thenReturn("biz_leave_request");
        when(dataMapper.selectById(
                "biz_leave_request", RECORD_ID))
                .thenReturn(Map.of("approver_id", "id-bob"));
        when(businessMapper.selectUserIdentities(argThat(statement -> java.util.Objects.equals("SELECT id, username, deleted FROM sys_user"
                        + " WHERE id IN (?)", statement.sql()))))
                .thenReturn(List.of(Map.of(
                        "id", "id-bob",
                        "username", "bob",
                        "deleted", 0)));

        assertEquals(
                List.of("bob"),
                adapter.readUserKeys(
                        ENTITY_CODE, RECORD_ID, "approverId"));
    }

    @Test
    void readsMultiUserReferenceFromSideTableInDistinctOrder() {
        EntityField field = userField(
                "approverIds", EntityField.FieldType.MULTI_REFERENCE);
        field.setRefEntityId("entity-sys-user");
        when(fieldMapper.findByEntityIdAndFieldCode(
                ENTITY_ID, "approverIds"))
                .thenReturn(field);
        when(definitionMapper.selectById("entity-sys-user"))
                .thenReturn(definition("entity-sys-user", "sys_user"));
        when(dynamicTableService.getMultiValueTableName(ENTITY_CODE))
                .thenReturn("biz_leave_request_multi");
        when(tableResolver.resolve(any(EntityDefinition.class)))
                .thenReturn("biz_leave_request");
        when(dataMapper.selectById("biz_leave_request", RECORD_ID))
                .thenReturn(Map.of("id", RECORD_ID));
        when(businessMapper.selectRelatedUserIds(argThat(statement -> java.util.Objects.equals(RECORD_ID, statement.parameters().get(0).value()) && java.util.Objects.equals("approverIds", statement.parameters().get(1).value()) && java.util.Objects.equals("entity-sys-user", statement.parameters().get(2).value()))))
                .thenReturn(List.of(
                        " user-2 ", "user-1", "user-2", " "));
        when(businessMapper.selectUserIdentities(argThat(statement -> java.util.Objects.equals("SELECT id, username, deleted FROM sys_user"
                        + " WHERE id IN (?,?)", statement.sql()))))
                .thenReturn(List.of(
                        Map.of(
                                "id", "user-1",
                                "username", "alice",
                                "deleted", 0),
                        Map.of(
                                "id", "user-2",
                                "username", "bob",
                                "deleted", 0)));

        List<String> result = adapter.readUserKeys(
                ENTITY_CODE, RECORD_ID, "approverIds");

        assertEquals(List.of("bob", "alice"), result);
        verify(businessMapper).selectRelatedUserIds(argThat(statement -> java.util.Objects.equals("SELECT target_record_id FROM `biz_leave_request_multi`"
                        + " WHERE record_id = ? AND field_code = ?"
                        + " AND target_entity_id = ? AND deleted = 0"
                        + " ORDER BY sort_order, id LIMIT 0, 201", statement.sql()) && java.util.Objects.equals(RECORD_ID, statement.parameters().get(0).value()) && java.util.Objects.equals("approverIds", statement.parameters().get(1).value()) && java.util.Objects.equals("entity-sys-user", statement.parameters().get(2).value())));
        verify(dataMapper).selectById("biz_leave_request", RECORD_ID);
    }

    @Test
    void rejectsMissingParentRecordBeforeReadingMultiValueRows() {
        EntityField field = userField(
                "approverIds", EntityField.FieldType.MULTI_REFERENCE);
        field.setRefEntityId("entity-sys-user");
        when(fieldMapper.findByEntityIdAndFieldCode(
                ENTITY_ID, "approverIds"))
                .thenReturn(field);
        when(definitionMapper.selectById("entity-sys-user"))
                .thenReturn(definition("entity-sys-user", "sys_user"));
        when(tableResolver.resolve(any(EntityDefinition.class)))
                .thenReturn("biz_leave_request");
        when(dataMapper.selectById("biz_leave_request", RECORD_ID))
                .thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.readUserKeys(
                        ENTITY_CODE, RECORD_ID, "approverIds"));

        assertEquals(
                "实体记录不存在: leave_request/record-1",
                exception.getMessage());
        verifyNoInteractions(dynamicTableService, businessMapper);
    }

    @Test
    void rejectsMissingFieldBeforeReadingRecord() {
        when(fieldMapper.findByEntityIdAndFieldCode(
                ENTITY_ID, "missingApprover"))
                .thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.readUserKeys(
                        ENTITY_CODE, RECORD_ID, "missingApprover"));

        assertEquals(
                "实体字段不存在: leave_request.missingApprover",
                exception.getMessage());
        verifyNoInteractions(
                dataMapper,
                tableResolver,
                dynamicTableService,
                businessMapper);
    }

    @Test
    void rejectsReferenceThatDoesNotTargetSystemUser() {
        EntityField field = userField(
                "projectId", EntityField.FieldType.REFERENCE);
        field.setRefEntityId("entity-project");
        when(fieldMapper.findByEntityIdAndFieldCode(
                ENTITY_ID, "projectId"))
                .thenReturn(field);
        when(definitionMapper.selectById("entity-project"))
                .thenReturn(definition("entity-project", "project"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.readUserKeys(
                        ENTITY_CODE, RECORD_ID, "projectId"));

        assertEquals(
                "字段不是用户单选或多选关系: leave_request.projectId",
                exception.getMessage());
        verifyNoInteractions(
                dataMapper,
                tableResolver,
                dynamicTableService,
                businessMapper);
    }

    private EntityField userField(
            String fieldCode,
            EntityField.FieldType fieldType) {
        EntityField field = new EntityField();
        field.setEntityId(ENTITY_ID);
        field.setFieldCode(fieldCode);
        field.setFieldType(fieldType);
        field.setIsPublished(true);
        return field;
    }

    private EntityDefinition definition(String id, String code) {
        EntityDefinition definition = new EntityDefinition();
        definition.setId(id);
        definition.setEntityCode(code);
        return definition;
    }
}
