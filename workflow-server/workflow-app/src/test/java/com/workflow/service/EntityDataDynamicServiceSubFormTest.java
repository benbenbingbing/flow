package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityRelation;
import com.workflow.entity.publish.EntityPublishedSnapshot;
import com.workflow.entity.publish.EntityPublishedSnapshotService;
import com.workflow.entity.runtime.EntityRelationRuntimeService;
import com.workflow.entity.runtime.EntityMultiValueRuntimeService;
import com.workflow.entity.runtime.EntityRuntimeRecordMapper;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityRelationMapper;
import com.workflow.mapper.EntityStatusMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实体数据动态服务-子表单测试。
 *
 * <p>被测对象：{@link EntityDataDynamicService}，覆盖子表单数据写入引用实体表、
 * 嵌套关系递归写入、必填字段校验、独立实体拒绝伪造流程启动、按引用字段加载子表单行等场景。
 */
class EntityDataDynamicServiceSubFormTest {

    /**
     * 测试保存时子表单行写入被引用实体表而非父表：
     * 验证父表数据不含子表单字段，子表行写入引用表并自动回填 parentId、deleted、code。
     */
    @Test
    void saveWritesSubFormRowsToReferencedEntityTableInsteadOfParentTable() {
        Fixture fixture = new Fixture();
        EntityDataDynamicService service = fixture.service();

        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode("parent");
        dto.setSubmitterId("admin");
        dto.setSubmitterName("管理员");
        dto.setData(new HashMap<>(Map.of(
                "name", "主数据",
                "detailList", new ArrayList<>(List.of(Map.of("itemName", "明细一")))
        )));

        service.save(dto);

        ArgumentCaptor<Map<String, Object>> parentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).insert(eq("wf_parent"), parentCaptor.capture());
        assertFalse(parentCaptor.getValue().containsKey("detail_list"));
        assertFalse(parentCaptor.getValue().containsKey("detailList"));

        ArgumentCaptor<Map<String, Object>> childCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).insert(eq("wf_child"), childCaptor.capture());
        Map<String, Object> childData = childCaptor.getValue();
        assertEquals("明细一", childData.get("itemName"));
        assertEquals(dto.getId(), childData.get("parentId"));
        assertEquals(0, childData.get("deleted"));
        assertEquals("C001", childData.get("code"));
    }

    /**
     * 测试保存时嵌套关系行被递归写入：验证孙表（tax）行写入并自动回填 childId 与 code。
     */
    @Test
    void saveWritesNestedRelationRowsRecursively() {
        Fixture fixture = new Fixture();
        EntityDataDynamicService service = fixture.service();

        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode("parent");
        dto.setSubmitterId("admin");
        dto.setSubmitterName("管理员");
        dto.setData(new HashMap<>(Map.of(
                "name", "主数据",
                "detailList", new ArrayList<>(List.of(new HashMap<>(Map.of(
                        "itemName", "明细一",
                        "taxRows", new ArrayList<>(List.of(Map.of("taxName", "税一")))
                ))))
        )));

        service.save(dto);

        ArgumentCaptor<Map<String, Object>> childCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).insert(eq("wf_child"), childCaptor.capture());
        String childId = String.valueOf(childCaptor.getValue().get("id"));
        assertNotNull(childId);

        ArgumentCaptor<Map<String, Object>> taxCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).insert(eq("wf_tax"), taxCaptor.capture());
        Map<String, Object> taxData = taxCaptor.getValue();
        assertEquals("税一", taxData.get("taxName"));
        assertEquals(childId, taxData.get("childId"));
        assertEquals("T001", taxData.get("code"));
    }

    /**
     * 测试保存时基于发布快照校验必填字段：验证缺少必填字段时抛出异常且不写入数据表。
     */
    @Test
    void saveValidatesRequiredFieldsFromPublishedSnapshot() {
        Fixture fixture = new Fixture();
        EntityDataDynamicService service = fixture.service();
        EntityField requiredAmount = new EntityField();
        requiredAmount.setFieldCode("amount");
        requiredAmount.setFieldName("金额");
        requiredAmount.setIsRequired(true);
        EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
        snapshot.setEntityCode("parent");
        snapshot.setFields(List.of(requiredAmount));
        when(fixture.snapshotService.getLatestByEntityCode("parent")).thenReturn(snapshot);

        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode("parent");
        dto.setSubmitterId("admin");
        dto.setSubmitterName("管理员");
        dto.setData(new HashMap<>(Map.of("name", "主数据")));

        RuntimeException exception = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service.save(dto));

        assertEquals("字段必填: 金额", exception.getMessage());
        verify(fixture.snapshotService).getLatestByEntityCode("parent");
        verify(fixture.dynamicMapper, never()).insert(eq("wf_parent"), org.mockito.ArgumentMatchers.anyMap());
    }

    /**
     * 测试独立实体拒绝伪造的流程启动请求：验证抛出业务冲突异常且不写入数据表。
     */
    @Test
    void standaloneEntityRejectsForgedProcessStart() {
        Fixture fixture = new Fixture();
        EntityDataDynamicService service = fixture.service();
        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode("parent");
        dto.setStartProcess(true);
        dto.setData(new HashMap<>(Map.of("name", "基础资料")));

        com.workflow.common.BusinessConflictException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.workflow.common.BusinessConflictException.class,
                        () -> service.save(dto));

        assertEquals("ENTITY_WORKFLOW_NOT_SUPPORTED", exception.getErrorCode());
        verify(fixture.dynamicMapper, never()).insert(
                eq("wf_parent"),
                org.mockito.ArgumentMatchers.anyMap());
    }

    /**
     * 测试按 ID 查询时通过引用字段递归加载子表单行：
     * 验证子表与孙表行被正确装配，且查询条件使用 parentId/childId 的 EQ 过滤。
     */
    @Test
    void findByIdLoadsSubFormRowsByReferenceField() {
        Fixture fixture = new Fixture();
        EntityDataDynamicService service = fixture.service();

        when(fixture.dynamicMapper.selectById("wf_parent", "parent-1")).thenReturn(new HashMap<>(Map.of(
                "id", "parent-1",
                "name", "主数据",
                "deleted", 0
        )));
        when(fixture.dynamicMapper.selectByCondition(eq("wf_child"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(List.of(new HashMap<>(Map.of(
                        "id", "child-1",
                        "parent_id", "parent-1",
                        "item_name", "明细一",
                        "deleted", 0
                ))));
        when(fixture.dynamicMapper.selectByCondition(eq("wf_tax"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(List.of(new HashMap<>(Map.of(
                        "id", "tax-1",
                        "child_id", "child-1",
                        "tax_name", "税一",
                        "deleted", 0
                ))));

        EntityDataDTO dto = service.findById("parent", "parent-1");

        assertNotNull(dto.getData().get("detailList"));
        List<?> rows = (List<?>) dto.getData().get("detailList");
        assertEquals(1, rows.size());
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals("child-1", row.get("id"));
        assertEquals("明细一", row.get("itemName"));
        assertEquals("parent-1", row.get("parentId"));
        List<?> taxes = (List<?>) row.get("taxRows");
        assertEquals(1, taxes.size());
        assertEquals("税一", ((Map<?, ?>) taxes.get(0)).get("taxName"));
        verify(fixture.dynamicMapper).selectByCondition(eq("wf_child"), org.mockito.ArgumentMatchers.argThat(condition ->
                "parent-1".equals(condition.get("parentId")) && "EQ".equals(condition.get("parentId_op"))));
        verify(fixture.dynamicMapper).selectByCondition(eq("wf_tax"), org.mockito.ArgumentMatchers.argThat(condition ->
                "child-1".equals(condition.get("childId")) && "EQ".equals(condition.get("childId_op"))));
    }

    /** 测试夹具：装配被测服务与各 Mock 依赖，并预置父/子/孙实体的定义与关系数据 */
    private static class Fixture {
        final EntityDataDynamicMapper dynamicMapper = mock(EntityDataDynamicMapper.class);
        final EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        final EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
        final EntityRelationMapper relationMapper = mock(EntityRelationMapper.class);
        final EntityStatusMapper entityStatusMapper = mock(EntityStatusMapper.class);
        final DynamicTableService dynamicTableService = mock(DynamicTableService.class);
        final EntityCodeGeneratorService codeGeneratorService = mock(EntityCodeGeneratorService.class);
        final EntityPublishedSnapshotService snapshotService = mock(EntityPublishedSnapshotService.class);
        final EntityMultiValueRuntimeService multiValueRuntimeService = mock(EntityMultiValueRuntimeService.class);

        Fixture() {
            EntityDefinition parent = entity("parent-id", "parent");
            EntityDefinition child = entity("child-id", "child");
            EntityDefinition tax = entity("tax-id", "tax");
            EntityField subForm = subFormField();

            when(definitionMapper.findByEntityCode("parent")).thenReturn(Optional.of(parent));
            when(definitionMapper.findByEntityCode("child")).thenReturn(Optional.of(child));
            when(definitionMapper.selectById("child-id")).thenReturn(child);
            when(definitionMapper.selectById("tax-id")).thenReturn(tax);
            when(fieldMapper.findByEntityId("parent-id")).thenReturn(List.of(subForm));
            when(fieldMapper.findByEntityId("child-id")).thenReturn(List.of());
            when(fieldMapper.findByEntityId("tax-id")).thenReturn(List.of());
            when(relationMapper.selectByParentEntityId("parent-id")).thenReturn(List.of(relation(
                    "parent-id", "parent", "detailList", "child-id", "child", "parentId", EntityRelation.RelationType.ONE_TO_MANY, 1)));
            when(relationMapper.selectByParentEntityId("child-id")).thenReturn(List.of(relation(
                    "child-id", "child", "taxRows", "tax-id", "tax", "childId", EntityRelation.RelationType.ONE_TO_MANY, 1)));
            when(relationMapper.selectByParentEntityId("tax-id")).thenReturn(List.of());
            when(relationMapper.selectByParentEntityCode("parent")).thenReturn(List.of(relation(
                    "parent-id", "parent", "detailList", "child-id", "child", "parentId", EntityRelation.RelationType.ONE_TO_MANY, 1)));
            when(relationMapper.selectByParentEntityCode("child")).thenReturn(List.of(relation(
                    "child-id", "child", "taxRows", "tax-id", "tax", "childId", EntityRelation.RelationType.ONE_TO_MANY, 1)));
            when(relationMapper.selectByParentEntityCode("tax")).thenReturn(List.of());
            when(dynamicTableService.getTableName("parent")).thenReturn("wf_parent");
            when(dynamicTableService.getTableName("child")).thenReturn("wf_child");
            when(dynamicTableService.getTableName("tax")).thenReturn("wf_tax");
            when(dynamicTableService.tableExists("parent")).thenReturn(true);
            when(dynamicTableService.tableExists("child")).thenReturn(true);
            when(dynamicTableService.tableExists("tax")).thenReturn(true);
            when(codeGeneratorService.generateCode("parent")).thenReturn("P001");
            when(codeGeneratorService.generateCode("child")).thenReturn("C001");
            when(codeGeneratorService.generateCode("tax")).thenReturn("T001");
            when(entityStatusMapper.findByCategory("parent", "NEW")).thenReturn(List.of());
            when(snapshotService.getLatestByEntityCode("parent")).thenReturn(snapshot("parent"));
        }

        /** 构造被测的 EntityDataDynamicService 及其关系运行时依赖 */
        EntityDataDynamicService service() {
            ObjectMapper objectMapper = new ObjectMapper();
            EntityRuntimeRecordMapper recordMapper = new EntityRuntimeRecordMapper(objectMapper);
            EntityRelationRuntimeService relationRuntimeService = new EntityRelationRuntimeService(
                    dynamicMapper, definitionMapper, fieldMapper, relationMapper,
                    dynamicTableService, objectMapper, recordMapper, codeGeneratorService);
            return new EntityDataDynamicService(
                    dynamicMapper, definitionMapper, entityStatusMapper,
                    dynamicTableService, codeGeneratorService, recordMapper, relationRuntimeService,
                    multiValueRuntimeService, null, null, null, snapshotService,
                    mock(EntityRecordTeamService.class));
        }

        /** 构造指定实体编码的空字段发布快照 */
        private static EntityPublishedSnapshot snapshot(String entityCode) {
            EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
            snapshot.setEntityCode(entityCode);
            snapshot.setFields(List.of());
            return snapshot;
        }

        /** 构造独立生命周期+动态存储模式的实体定义 */
        private static EntityDefinition entity(String id, String code) {
            EntityDefinition entity = new EntityDefinition();
            entity.setId(id);
            entity.setEntityCode(code);
            entity.setLifecycleMode(EntityDefinition.LifecycleMode.STANDALONE);
            entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
            return entity;
        }

        /** 构造指向 child 实体、引用字段 parentId 的子表单字段 */
        private static EntityField subFormField() {
            EntityField field = new EntityField();
            field.setFieldCode("detailList");
            field.setFieldName("明细");
            field.setFieldType(EntityField.FieldType.SUB_FORM);
            field.setRefEntityId("child-id");
            field.setRefFieldCode("parentId");
            return field;
        }

        /** 构造一对多、级联删除的实体关系对象 */
        private static EntityRelation relation(String parentId, String parentCode, String parentFieldCode,
                                               String childId, String childCode, String childRefFieldCode,
                                               EntityRelation.RelationType relationType, int sortOrder) {
            EntityRelation relation = new EntityRelation();
            relation.setParentEntityId(parentId);
            relation.setParentEntityCode(parentCode);
            relation.setParentFieldCode(parentFieldCode);
            relation.setRelationCode(parentCode + "_" + parentFieldCode);
            relation.setRelationName(parentFieldCode);
            relation.setChildEntityId(childId);
            relation.setChildEntityCode(childCode);
            relation.setChildRefFieldCode(childRefFieldCode);
            relation.setRelationType(relationType);
            relation.setCascadeDelete(true);
            relation.setRequired(false);
            relation.setEnabled(true);
            relation.setSortOrder(sortOrder);
            return relation;
        }
    }
}
