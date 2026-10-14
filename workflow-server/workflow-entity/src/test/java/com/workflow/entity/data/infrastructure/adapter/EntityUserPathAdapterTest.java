package com.workflow.entity.data.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.port.EntityUserPathPort.*;
import com.workflow.contracts.entity.port.EntityUserReferencePort.EntityUserReferenceException;
import com.workflow.core.database.mybatis.ParameterizedSql;
import com.workflow.entity.data.application.*;
import com.workflow.entity.data.infrastructure.persistence.mapper.*;
import com.workflow.entity.definition.application.*;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.*;
import com.workflow.entity.definition.infrastructure.persistence.record.*;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 真实元数据编译器与有限步执行器一起验证业务语义；持久层以可检查的最小投影替身隔离。 */
class EntityUserPathAdapterTest {
    EntityDefinitionMapper definitions = mock(EntityDefinitionMapper.class);
    EntityFieldMapper fields = mock(EntityFieldMapper.class);
    EntityPublishedSnapshotService snapshots = mock(EntityPublishedSnapshotService.class);
    EntityRelationMapper relations = mock(EntityRelationMapper.class);
    EntityUserPathMapper mapper = mock(EntityUserPathMapper.class);
    EntityPhysicalTableResolver tables = mock(EntityPhysicalTableResolver.class);
    DynamicTableService dynamic = mock(DynamicTableService.class);
    EntityUserPathMetadata metadata = new EntityUserPathMetadata(definitions, fields, snapshots, new SystemEntityFieldPolicy(), relations);
    EntityUserPathAdapter adapter = new EntityUserPathAdapter(metadata, mapper, tables, dynamic,
            DatabaseQueryDialects.forDatabaseId("MYSQL"), new ObjectMapper());
    EntityField applicant;
    EntityPublishedSnapshot root;
    List<Step> path = List.of(new Step("REFERENCE_FIELD", "applicant", "sys_user"),
            new Step("SYSTEM_REFERENCE", "dept_id", "sys_organization"),
            new Step("SYSTEM_REFERENCE", "create_by", "sys_user"));

    @BeforeEach void setup() {
        definition("expense", false);
        definition("sys_user", true);
        definition("sys_organization", true);
        applicant = field("applicant", EntityField.FieldType.REFERENCE, "sys_user");
        root = snapshot("expense", List.of(applicant));
        when(fields.findByEntityId("sys_user")).thenReturn(List.of(field("dept_id", EntityField.FieldType.STRING, null)));
        when(fields.findByEntityId("sys_organization")).thenReturn(List.of(field("create_by", EntityField.FieldType.STRING, null)));
        when(tables.resolve(any(EntityDefinition.class))).thenAnswer(inv -> "biz_" + ((EntityDefinition) inv.getArgument(0)).getEntityCode());
        when(dynamic.getMultiValueTableName("expense")).thenReturn("biz_expense_multi");
    }

    @Test void userCanBeEndpointAndStillExpandIntoDepartment() {
        var options = adapter.options("expense", List.of());
        assertTrue(options.get(0).terminal());
        assertEquals("sys_user", options.get(0).step().targetEntityCode());
        var next = adapter.options("expense", List.of(path.get(0)));
        assertEquals("所属部门", next.get(0).label());
        assertFalse(next.get(0).terminal());
        var description = adapter.describe("expense", path, List.of());
        assertFalse(description.multiple());
        assertEquals(3, description.versions().size());
    }

    @Test void userDepartmentCreatorMayReturnToTheSameUser() {
        when(mapper.select(any())).thenReturn(
                List.of(Map.of("id", "r1", "link_value", "u1")),
                List.of(Map.of("id", "u1", "deleted", 0)),
                List.of(Map.of("id", "u1", "link_value", "d1")),
                List.of(Map.of("id", "d1")),
                List.of(Map.of("id", "d1", "link_value", "u1")),
                List.of(Map.of("id", "u1", "deleted", 0)),
                List.of(Map.of("id", "u1", "username", "alice")));
        assertEquals(List.of("alice"), adapter.readUserKeys("expense", "r1", path, List.of()));
        verify(tables, never()).resolve(argThat((EntityDefinition def) -> def.getStorageMode() == EntityDefinition.StorageMode.SYSTEM));
        verify(mapper).select(argThat(sql -> sql.sql().contains("`sys_organization`") && sql.sql().contains("`create_by` AS link_value")));
    }

    @Test void intermediateMultiValueControlsCardinalityAndDeduplicatesFinalUsers() {
        applicant.setFieldType(EntityField.FieldType.MULTI_REFERENCE);
        assertTrue(adapter.describe("expense", path, List.of()).multiple());
        when(mapper.select(any())).thenReturn(
                List.of(Map.of("id", "r1")),
                List.of(Map.of("link_value", "u1"), Map.of("link_value", "u2")),
                List.of(Map.of("id", "u1", "deleted", 0), Map.of("id", "u2", "deleted", 0)),
                List.of(Map.of("id", "u1", "link_value", "d1"), Map.of("id", "u2", "link_value", "d1")),
                List.of(Map.of("id", "d1")),
                List.of(Map.of("id", "d1", "link_value", "u1")),
                List.of(Map.of("id", "u1", "deleted", 0)),
                List.of(Map.of("id", "u1", "username", "alice")));
        assertEquals(List.of("alice"), adapter.readUserKeys("expense", "r1", path, List.of()));
        verify(mapper).select(argThat(sql -> sql.sql().contains("biz_expense_multi")
                && sql.parameters().stream().map(ParameterizedSql.Parameter::value).toList().equals(List.of("r1", "applicant", "sys_user"))));
    }

    @Test void emptyIntermediateRelationReturnsEmptyWithoutQueryingRemainingHops() {
        when(mapper.select(any())).thenReturn(List.of(Map.of("id", "r1")));
        assertTrue(adapter.readUserKeys("expense", "r1", path, List.of()).isEmpty());
        verify(mapper, times(1)).select(any());
    }

    @Test void missingDepartmentFailsInsteadOfReturningPartialPeople() {
        when(mapper.select(any())).thenReturn(List.of(Map.of("id", "r1", "link_value", "u1")),
                List.of(Map.of("id", "u1", "deleted", 0)), List.of(Map.of("id", "u1", "link_value", "missing")), List.of());
        var error = assertThrows(EntityUserReferenceException.class, () -> adapter.readUserKeys("expense", "r1", path, List.of()));
        assertEquals("ENTITY_USER_PATH_RECORD_MISSING", error.reasonCode());
    }

    @Test void oldUserKeyCannotBeReassignedByUsernameAfterSoftDeletion() {
        applicant.setRefEntityId(null);
        applicant.setFieldType(EntityField.FieldType.USER);
        when(mapper.select(any())).thenReturn(List.of(Map.of("id", "r1", "link_value", "u1")), List.of(Map.of("id", "u1", "deleted", 1)));
        assertTrue(adapter.readUserKeys("expense", "r1", path, List.of()).isEmpty());
        verify(mapper, never()).select(argThat(sql -> sql.sql().contains("username IN")));
    }

    @Test void rowBudgetRejectsResultsWithoutTruncating() {
        when(mapper.select(any())).thenReturn(IntStream.range(0, 201).mapToObj(i -> Map.<String, Object>of("id", "r" + i)).toList());
        assertEquals("ENTITY_USER_PATH_LIMIT", assertThrows(EntityUserReferenceException.class,
                () -> adapter.readUserKeys("expense", "r1", path, List.of())).reasonCode());
    }

    @Test void frozenPathUsesHistoryAndRejectsTamperedFingerprints() {
        var description = adapter.describe("expense", path, List.of());
        clearInvocations(snapshots);
        adapter.describe("expense", path, description.versions());
        verify(snapshots, never()).getLatestPinnedByEntityId(any());
        verify(snapshots).getPinnedByHistoryId("expense-v1");
        assertThrows(EntityUserReferenceException.class, () -> adapter.describe("expense", path,
                List.of(new Version("expense", "expense-v1", "tampered", null))));
    }

    @Test void invalidTargetsDepthAndNonUserEndpointsAreRejected() {
        assertThrows(EntityUserReferenceException.class, () -> adapter.describe("expense", path.subList(0, 2), List.of()));
        assertThrows(EntityUserReferenceException.class, () -> adapter.describe("expense", Collections.nCopies(9, path.get(0)), List.of()));
        assertThrows(EntityUserReferenceException.class, () -> adapter.describe("expense", List.of(new Step("REFERENCE_FIELD", "applicant", "sys_organization")), List.of()));
        applicant.setRefEntityId("missing");
        assertThrows(EntityUserReferenceException.class, () -> adapter.describe("expense", path, List.of()));
        assertNotNull(adapter.options("expense", List.of()).get(0).disabledReason());
    }

    @Test void explicitRelationCanBeTraversedInBothDirections() {
        definition("line", false);
        EntityField parent = field("expense_id", EntityField.FieldType.STRING, null);
        var line = snapshot("line", List.of(parent, field("owner", EntityField.FieldType.REFERENCE, "sys_user")));
        var relation = new com.workflow.entity.data.infrastructure.persistence.record.EntityRelation();
        relation.setRelationCode("lines"); relation.setRelationName("明细"); relation.setParentEntityId("expense");
        relation.setParentEntityCode("expense"); relation.setChildEntityId("line"); relation.setChildEntityCode("line");
        relation.setChildRefFieldCode("expense_id"); relation.setEnabled(true);
        relation.setRelationType(com.workflow.entity.data.infrastructure.persistence.record.EntityRelation.RelationType.ONE_TO_MANY);
        root.setRelations(List.of(relation));
        when(relations.selectAllByChildEntityId("line")).thenReturn(List.of(relation));
        var forward = List.of(new Step("RELATION", "lines", "line"), new Step("REFERENCE_FIELD", "owner", "sys_user"));
        assertTrue(adapter.describe("expense", forward, List.of()).multiple());
        var back = List.of(new Step("PARENT_RELATION", "lines", "expense"), path.get(0));
        assertFalse(adapter.describe("line", back, List.of()).multiple());
        assertTrue(adapter.options("line", List.of()).stream().anyMatch(option -> option.step().type().equals("PARENT_RELATION")));
        when(mapper.select(any())).thenReturn(List.of(Map.of("id", "r1")),
                List.of(Map.of("id", "line1", "link_value", "r1"), Map.of("id", "line2", "link_value", "r1")),
                List.of(Map.of("id", "line1"), Map.of("id", "line2")),
                List.of(Map.of("id", "line1", "link_value", "u1"), Map.of("id", "line2", "link_value", "u1")),
                List.of(Map.of("id", "u1", "deleted", 0)), List.of(Map.of("id", "u1", "username", "alice")));
        assertEquals(List.of("alice"), adapter.readUserKeys("expense", "r1", forward, List.of()));
    }

    private EntityDefinition definition(String code, boolean system) {
        EntityDefinition value = new EntityDefinition(); value.setId(code); value.setEntityCode(code);
        value.setStorageMode(system ? EntityDefinition.StorageMode.SYSTEM : EntityDefinition.StorageMode.DYNAMIC);
        when(definitions.findByEntityCode(code)).thenReturn(Optional.of(value));
        when(definitions.selectById(code)).thenReturn(value);
        return value;
    }
    private EntityPublishedSnapshot snapshot(String code, List<EntityField> fields) {
        var value = new EntityPublishedSnapshot(); value.setEntityId(code); value.setEntityCode(code);
        value.setHistoryId(code + "-v1"); value.setFields(fields); value.setRelations(List.of()); value.setRelationsSnapshotAvailable(true);
        var pinned = new EntityPublishedSnapshotService.PinnedEntitySnapshot(value, "hash-" + code);
        when(snapshots.getLatestPinnedByEntityId(code)).thenReturn(pinned);
        when(snapshots.getPinnedByHistoryId(code + "-v1")).thenReturn(pinned);
        return value;
    }
    private EntityField field(String code, EntityField.FieldType type, String target) {
        var value = new EntityField(); value.setFieldCode(code); value.setFieldName(code); value.setFieldType(type);
        value.setRefEntityId(target); value.setIsPublished(true); return value;
    }
}
