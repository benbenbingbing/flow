package com.workflow.service;

import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.EntityDataMutationService;
import com.workflow.entity.data.application.EntityDataMutationPayloadMapper;
import com.workflow.entity.data.application.EntityDataMutationValidator;
import com.workflow.entity.data.application.EntityRecordTeamService;
import com.workflow.entity.definition.application.EntityCodeGeneratorService;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.contracts.process.ProcessRuntimePort;
import com.workflow.contracts.process.ProcessStartResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.data.application.EntityRelationRuntimeService;
import com.workflow.entity.data.application.EntityMultiValueRuntimeService;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFieldFileItem;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
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
 * 实体数据聚合读写服务-子表单测试。
 *
 * <p>覆盖查询服务与管道内部写入服务的子表单数据写入引用实体表、
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
        EntityDataMutationService service = fixture.mutationService();

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
     * 测试子表单空控件值按主表相同规则归一化：
     * 未填写的日期时间等字段以空字符串提交时，写库前应转换为 null。
     */
    @Test
    void saveNormalizesEmptySubFormValuesToNull() {
        Fixture fixture = new Fixture();
        EntityDataMutationService service = fixture.mutationService();

        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode("parent");
        dto.setSubmitterId("admin");
        dto.setSubmitterName("管理员");
        Map<String, Object> detail = new HashMap<>();
        detail.put("itemName", "明细一");
        detail.put("invalidAt", "");
        dto.setData(new HashMap<>(Map.of(
                "name", "主数据",
                "detailList", new ArrayList<>(List.of(detail))
        )));

        service.save(dto);

        ArgumentCaptor<Map<String, Object>> childCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).insert(eq("wf_child"), childCaptor.capture());
        assertTrue(childCaptor.getValue().containsKey("invalidAt"));
        org.junit.jupiter.api.Assertions.assertNull(childCaptor.getValue().get("invalidAt"));
    }

    /**
     * 测试保存时嵌套关系行被递归写入：验证孙表（tax）行写入并自动回填 childId 与 code。
     */
    @Test
    void saveWritesNestedRelationRowsRecursively() {
        Fixture fixture = new Fixture();
        EntityDataMutationService service = fixture.mutationService();

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
        EntityDataMutationService service = fixture.mutationService();
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
     * 测试保存时执行发布快照中的实体字段验证规则。
     */
    @Test
    void saveValidatesFieldRulesFromPublishedSnapshot() {
        Fixture fixture = new Fixture();
        EntityDataMutationService service = fixture.mutationService();
        EntityField percentage = new EntityField();
        percentage.setFieldCode("percentage");
        percentage.setFieldName("完成比例");
        percentage.setFieldType(EntityField.FieldType.DECIMAL);
        percentage.setValidateRules(
                """
                {"min":0.01,"max":100}
                """);
        EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
        snapshot.setEntityCode("parent");
        snapshot.setFields(List.of(percentage));
        when(fixture.snapshotService.getLatestByEntityCode("parent"))
                .thenReturn(snapshot);

        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode("parent");
        dto.setData(new HashMap<>(Map.of(
                "name", "主数据",
                "percentage", 0)));

        IllegalArgumentException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.save(dto));

        assertEquals("完成比例不能小于 0.01", exception.getMessage());
        verify(fixture.dynamicMapper, never()).insert(
                eq("wf_parent"),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void saveValidatesRequiredAttachmentItemsFromPublishedSnapshot() {
        Fixture fixture = new Fixture();
        EntityDataMutationService service = fixture.mutationService();
        EntityField attachment = new EntityField();
        attachment.setFieldCode("documents");
        attachment.setFieldName("项目附件");
        attachment.setFieldType(EntityField.FieldType.FILE);
        EntityFieldFileItem charter = new EntityFieldFileItem();
        charter.setItemName("项目章程");
        charter.setRequired(true);
        EntityFieldFileItem specification = new EntityFieldFileItem();
        specification.setItemName("需求文档");
        specification.setRequired(false);
        attachment.setFileItems(List.of(charter, specification));

        EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
        snapshot.setEntityCode("parent");
        snapshot.setFields(List.of(attachment));
        when(fixture.snapshotService.getLatestByEntityCode("parent"))
                .thenReturn(snapshot);

        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode("parent");
        dto.setData(new HashMap<>(Map.of(
                "name", "主数据",
                "documents", Map.of("需求文档", List.of(
                        Map.of("url", "/uploads/spec.pdf"))))));

        RuntimeException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        RuntimeException.class,
                        () -> service.save(dto));

        assertEquals(
                "项目附件缺少必填附件项: 项目章程",
                exception.getMessage());
        verify(fixture.dynamicMapper, never()).insert(
                eq("wf_parent"),
                org.mockito.ArgumentMatchers.anyMap());
    }

    /**
     * 测试独立实体拒绝伪造的流程启动请求：验证抛出业务冲突异常且不写入数据表。
     */
    @Test
    void standaloneEntityRejectsForgedProcessStart() {
        Fixture fixture = new Fixture();
        EntityDataMutationService service = fixture.mutationService();
        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode("parent");
        dto.setStartProcess(true);
        dto.setData(new HashMap<>(Map.of("name", "基础资料")));

        com.workflow.core.error.BusinessConflictException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.workflow.core.error.BusinessConflictException.class,
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
        EntityDataDynamicService service = fixture.queryService();

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

    /**
     * 测试更新时只把实体实际列和已发布字段交给动态 SQL：
     * 验证 listKey 与未知运行时字段不会进入更新 Map。
     */
    @Test
    void updateIgnoresRuntimeContextAndUnknownFields() {
        Fixture fixture = new Fixture();
        EntityDataMutationService service = fixture.mutationService();
        EntityField amount = new EntityField();
        amount.setFieldCode("amountTotal");
        amount.setFieldName("金额");
        EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
        snapshot.setEntityCode("parent");
        snapshot.setFields(List.of(amount));
        when(fixture.snapshotService.getLatestByEntityCode("parent")).thenReturn(snapshot);
        when(fixture.dynamicMapper.selectById("wf_parent", "parent-1")).thenReturn(new HashMap<>(Map.of(
                "id", "parent-1",
                "name", "旧名称",
                "deleted", 0
        )));

        service.update("parent", "parent-1", Map.of(
                "data", Map.of(
                        "name", "新名称",
                        "amountTotal", 12,
                        "listKey", "default",
                        "unknownRuntimeValue", "ignored"
                )
        ));

        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).update(eq("wf_parent"), updateCaptor.capture());
        Map<String, Object> updateData = updateCaptor.getValue();
        assertEquals("新名称", updateData.get("name"));
        assertEquals(12, updateData.get("amount_total"));
        assertFalse(updateData.containsKey("list_key"));
        assertFalse(updateData.containsKey("unknown_runtime_value"));
    }

    /**
     * 测试流程发起时同步业务提交时间：发布了 submitted_at 的流程实体应使用与
     * process_start_time 相同的时间值，业务流程无需额外配置专用动作。
     */
    @Test
    void workflowStartSynchronizesSubmittedAtWhenFieldIsPublished() {
        Fixture fixture = new Fixture();
        EntityDataMutationService service = fixture.mutationService();
        EntityDefinition workflowEntity = Fixture.entity("parent-id", "parent");
        workflowEntity.setLifecycleMode(EntityDefinition.LifecycleMode.WORKFLOW);
        workflowEntity.setProcessDefinitionId("process-config-1");
        when(fixture.definitionMapper.findByEntityCode("parent"))
                .thenReturn(Optional.of(workflowEntity));

        EntityField submittedAt = new EntityField();
        submittedAt.setFieldCode("submitted_at");
        submittedAt.setDbColumnName("submitted_at");
        EntityPublishedSnapshot snapshot = Fixture.snapshot("parent");
        snapshot.setProcessDefinitionId("process-config-1");
        snapshot.setFields(List.of(submittedAt));
        when(fixture.snapshotService.getLatestByEntityCode("parent"))
                .thenReturn(snapshot);
        when(fixture.processRuntimePort.start(any()))
                .thenReturn(new ProcessStartResult(
                        "process-1",
                        "SUBMITTED",
                        "task-1",
                        "业务审核",
                        "admin"));

        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode("parent");
        dto.setStartProcess(true);
        dto.setSubmitterId("admin");
        dto.setSubmitterName("管理员");
        dto.setData(new HashMap<>(Map.of("name", "流程数据")));

        service.save(dto);

        ArgumentCaptor<Map<String, Object>> updateCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).update(
                eq("wf_parent"),
                updateCaptor.capture());
        Map<String, Object> updateData = updateCaptor.getValue();
        assertNotNull(updateData.get("process_start_time"));
        assertEquals(
                updateData.get("process_start_time"),
                updateData.get("submitted_at"));
    }

    /**
     * 测试流程正常完成时同步批准时间：发布了 approved_at 的流程实体应使用与
     * process_end_time 相同的时间值，驳回、终止等结果不写入批准时间。
     */
    @Test
    void processCompletionSynchronizesApprovedAtWhenFieldIsPublished() {
        Fixture fixture = new Fixture();
        EntityDataMutationService service = fixture.mutationService();
        EntityField approvedAt = new EntityField();
        approvedAt.setFieldCode("approved_at");
        approvedAt.setDbColumnName("approved_at");
        EntityPublishedSnapshot snapshot = Fixture.snapshot("parent");
        snapshot.setFields(List.of(approvedAt));
        when(fixture.snapshotService.getLatestByEntityCode("parent"))
                .thenReturn(snapshot);
        when(fixture.dynamicMapper.selectById("wf_parent", "parent-1"))
                .thenReturn(new HashMap<>(Map.of(
                        "id", "parent-1",
                        "status", "BACKLOG")));
        EntityStatus completedStatus = new EntityStatus();
        completedStatus.setStatusCode("BACKLOG");
        completedStatus.setStatusCategory("COMPLETED");
        when(fixture.entityStatusMapper.findByEntityAndCode(
                "parent",
                "BACKLOG")).thenReturn(completedStatus);

        service.markProcessEnded(
                "parent",
                "parent-1",
                "COMPLETED",
                "APPROVED");

        ArgumentCaptor<Map<String, Object>> updateCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).update(
                eq("wf_parent"),
                updateCaptor.capture());
        Map<String, Object> updateData = updateCaptor.getValue();
        assertEquals("BACKLOG", updateData.get("status"));
        assertNotNull(updateData.get("process_end_time"));
        assertEquals(
                updateData.get("process_end_time"),
                updateData.get("approved_at"));
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
        final ProcessRuntimePort processRuntimePort = mock(ProcessRuntimePort.class);
        final EntityRecordTeamService entityRecordTeamService = mock(EntityRecordTeamService.class);

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

        private EntityRelationRuntimeService relationRuntimeService(
                EntityRuntimeRecordMapper recordMapper,
                ObjectMapper objectMapper) {
            return new EntityRelationRuntimeService(
                    dynamicMapper, definitionMapper, fieldMapper, relationMapper,
                    dynamicTableService, objectMapper, recordMapper, codeGeneratorService);
        }

        /** 构造管道内部的动态实体聚合写入服务。 */
        EntityDataMutationService mutationService() {
            ObjectMapper objectMapper = new ObjectMapper();
            EntityRuntimeRecordMapper recordMapper = new EntityRuntimeRecordMapper(objectMapper);
            EntityFieldValidationRuleService fieldValidationRuleService =
                    new EntityFieldValidationRuleService(objectMapper);
            EntityDataMutationValidator validator =
                    new EntityDataMutationValidator(
                            dynamicMapper,
                            dynamicTableService,
                            snapshotService,
                            recordMapper,
                            fieldValidationRuleService,
                            objectMapper);
            EntityDataMutationPayloadMapper payloadMapper =
                    new EntityDataMutationPayloadMapper(
                            recordMapper,
                            snapshotService,
                            validator);
            return new EntityDataMutationService(
                    dynamicMapper, definitionMapper, entityStatusMapper,
                    dynamicTableService, codeGeneratorService, recordMapper,
                    relationRuntimeService(recordMapper, objectMapper),
                    multiValueRuntimeService, processRuntimePort, null, snapshotService,
                    entityRecordTeamService, validator, payloadMapper);
        }

        /** 构造只读动态实体聚合查询服务。 */
        EntityDataDynamicService queryService() {
            ObjectMapper objectMapper = new ObjectMapper();
            EntityRuntimeRecordMapper recordMapper =
                    new EntityRuntimeRecordMapper(objectMapper);
            return new EntityDataDynamicService(
                    dynamicMapper,
                    definitionMapper,
                    dynamicTableService,
                    recordMapper,
                    relationRuntimeService(recordMapper, objectMapper),
                    multiValueRuntimeService,
                    null,
                    null,
                    snapshotService);
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
