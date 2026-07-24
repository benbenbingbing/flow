package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.migration.MigrationAssetRecorder;
import com.workflow.contracts.process.ProcessCatalogItem;
import com.workflow.contracts.process.ProcessCatalogPort;
import com.workflow.dto.EntityDefinitionDTO;
import com.workflow.dto.EntityFieldDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityPublishHistory;
import com.workflow.entity.EntityRelation;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityPublishHistoryMapper;
import com.workflow.mapper.EntityRelationMapper;
import com.workflow.service.permission.EntityPermissionCatalogService;
import com.workflow.service.permission.EntityListScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 实体定义服务单元测试。
 *
 * <p>被测对象：{@link EntityDefinitionService}，覆盖实体定义的增删改查、子表单关系同步、发布、
 * 流程绑定、生命周期模式校验等核心场景。
 */
@ExtendWith(MockitoExtension.class)
public class EntityDefinitionServiceTest {

    @Mock
    private EntityDefinitionMapper entityMapper;

    @Mock
    private EntityFieldMapper fieldMapper;

    @Mock
    private EntityRelationMapper relationMapper;

    @Mock
    private EntityPublishHistoryMapper publishHistoryMapper;

    @Mock
    private ProcessCatalogPort processCatalogPort;

    @Mock
    private MigrationAssetRecorder migrationAssetRecorder;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EntityDataDynamicMapper entityDataDynamicMapper;

    @Mock
    private DynamicTableService dynamicTableService;

    @Mock
    private EntityPhysicalTableNaming physicalTableNaming;

    @Mock
    private EntityPublishHistoryService publishHistoryService;

    @Mock
    private EntityFieldFileItemService fileItemService;

    @Mock
    private EntityPermissionCatalogService entityPermissionCatalogService;

    @Mock
    private EntityListScopeService entityListScopeService;

    @Mock
    private EntityRecordTeamService entityRecordTeamService;

    @Mock
    private EntityFieldOptionService fieldOptionService;

    @InjectMocks
    private EntityDefinitionService entityService;

    /** 测试实体定义（含已绑定流程） */
    private EntityDefinition testEntity;
    /** 测试字段 */
    private EntityField testField;

    /** 初始化测试实体与字段，并预置流程目录、表名生成、字段选项等 Mock 返回值 */
    @BeforeEach
    void setUp() {
        testEntity = new EntityDefinition();
        testEntity.setId("1");
        testEntity.setEntityCode("test_entity");
        testEntity.setEntityName("测试实体");
        testEntity.setDescription("测试描述");
        testEntity.setProcessDefinitionId("proc-1");
        testEntity.setLifecycleMode(EntityDefinition.LifecycleMode.WORKFLOW);
        testEntity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        testEntity.setStatus(EntityDefinition.Status.DRAFT);

        testField = new EntityField();
        testField.setId("f1");
        testField.setEntityId("1");
        testField.setFieldCode("name");
        testField.setFieldName("名称");
        testField.setFieldType(EntityField.FieldType.STRING);

        lenient().when(processCatalogPort.findNamesByIds(anyCollection()))
                .thenAnswer(invocation -> {
                    java.util.Collection<String> ids = invocation.getArgument(0);
                    return ids.contains("proc-1")
                            ? java.util.Map.of("proc-1", "测试流程")
                            : java.util.Map.of();
                });
        lenient().when(processCatalogPort.findItemsByIds(anyCollection()))
                .thenAnswer(invocation -> {
                    java.util.Collection<String> ids = invocation.getArgument(0);
                    java.util.Map<String, ProcessCatalogItem> result = new java.util.LinkedHashMap<>();
                    if (ids.contains("proc-1")) {
                        result.put("proc-1", new ProcessCatalogItem(
                                "proc-1", "test_process", "测试流程", "PUBLISHED"));
                    }
                    if (ids.contains("proc-2")) {
                        result.put("proc-2", new ProcessCatalogItem(
                                "proc-2", "next_process", "新流程", "DRAFT"));
                    }
                    return result;
                });
        lenient().when(physicalTableNaming.generate(anyString()))
                .thenAnswer(invocation -> "biz_" + invocation.getArgument(0));
        lenient().when(fieldOptionService.findOptions(anyString()))
                .thenReturn(List.of());

        EntityPublishHistory history = new EntityPublishHistory();
        history.setId("history-1");
        lenient().when(publishHistoryService.createVersion(
                        any(), anyList(), any(), any(), any(), any(), any(), any()))
                .thenReturn(history);
    }

    /** 测试查询全部实体：验证返回数量与流程名映射正确 */
    @Test
    void testFindAll() {
        when(entityMapper.selectList(null)).thenReturn(Arrays.asList(testEntity));
        List<EntityDefinitionDTO> result = entityService.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test_entity", result.get(0).getEntityCode());
        assertEquals("测试流程", result.get(0).getProcessName());
        verify(entityMapper, times(1)).selectList(null);
    }

    /** 测试查询无流程绑定的实体：验证 processName 为 null */
    @Test
    void testFindAllWithoutProcess() {
        testEntity.setProcessDefinitionId(null);
        when(entityMapper.selectList(null)).thenReturn(Arrays.asList(testEntity));

        List<EntityDefinitionDTO> result = entityService.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertNull(result.get(0).getProcessName());
    }

    /** 测试按 ID 查询实体：验证返回的实体编码与字段列表符合预期 */
    @Test
    void testFindById() {
        when(entityMapper.selectById("1")).thenReturn(testEntity);
        when(fieldMapper.findByEntityId("1")).thenReturn(Arrays.asList(testField));
        when(relationMapper.selectByParentEntityId("1")).thenReturn(Collections.emptyList());
        EntityDefinitionDTO result = entityService.findById("1");

        assertNotNull(result);
        assertEquals("1", result.getId());
        assertEquals("test_entity", result.getEntityCode());
        assertNotNull(result.getFields());
        assertEquals(1, result.getFields().size());
        assertEquals("name", result.getFields().get(0).getFieldCode());
        verify(entityMapper, times(1)).selectById("1");
        verify(fieldMapper, times(1)).findByEntityId("1");
    }

    /** 测试按 ID 查询返回子表单关系元数据：验证字段上的子实体、引用字段、关系类型等映射正确 */
    @Test
    void testFindByIdReturnsRelationMetadata() {
        EntityField subField = new EntityField();
        subField.setId("field-detail");
        subField.setEntityId("1");
        subField.setFieldCode("detailList");
        subField.setFieldName("明细");
        subField.setFieldType(EntityField.FieldType.SUB_FORM_LIST);

        EntityRelation relation = new EntityRelation();
        relation.setParentEntityId("1");
        relation.setParentEntityCode("test_entity");
        relation.setParentFieldId("field-detail");
        relation.setParentFieldCode("detailList");
        relation.setRelationCode("test_entity_detailList");
        relation.setRelationName("明细");
        relation.setChildEntityId("child-1");
        relation.setChildEntityCode("child");
        relation.setChildRefFieldCode("parentId");
        relation.setRelationType(EntityRelation.RelationType.ONE_TO_MANY);
        relation.setCascadeDelete(true);
        relation.setRequired(false);

        when(entityMapper.selectById("1")).thenReturn(testEntity);
        when(fieldMapper.findByEntityId("1")).thenReturn(List.of(subField));
        when(relationMapper.selectByParentEntityId("1")).thenReturn(List.of(relation));
        EntityDefinitionDTO result = entityService.findById("1");
        EntityFieldDTO field = result.getFields().get(0);

        assertEquals("child-1", field.getChildEntityId());
        assertEquals("child", field.getChildEntityCode());
        assertEquals("parentId", field.getChildRefFieldCode());
        assertEquals("ONE_TO_MANY", field.getRelationType());
        assertEquals(true, field.getCascadeDelete());
    }

    /** 测试按 ID 查询不存在实体：验证抛出 RuntimeException 且消息包含对应 ID */
    @Test
    void testFindByIdNotFound() {
        when(entityMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            entityService.findById("999");
        });

        assertEquals("实体不存在: 999", exception.getMessage());
    }

    /** 测试按实体编码查询实体：验证返回结果与字段装配正确 */
    @Test
    void testFindByCode() {
        when(entityMapper.findByEntityCode("test_entity")).thenReturn(Optional.of(testEntity));
        when(fieldMapper.findByEntityId("1")).thenReturn(Arrays.asList(testField));
        EntityDefinitionDTO result = entityService.findByCode("test_entity");

        assertNotNull(result);
        assertEquals("test_entity", result.getEntityCode());
        assertNotNull(result.getFields());
        verify(entityMapper, times(1)).findByEntityCode("test_entity");
    }

    /** 测试按不存在的编码查询实体：验证抛出 RuntimeException 且消息包含对应编码 */
    @Test
    void testFindByCodeNotFound() {
        when(entityMapper.findByEntityCode("not_exist")).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> {
            entityService.findByCode("not_exist");
        });

        assertEquals("实体不存在: not_exist", exception.getMessage());
    }

    /** 测试新增实体：验证返回的实体编码正确并触发 insert */
    @Test
    void testSave() {
        EntityDefinitionDTO dto = new EntityDefinitionDTO();
        dto.setEntityCode("new_entity");
        dto.setEntityName("新实体");
        dto.setDescription("新描述");

        when(entityMapper.insert(any(EntityDefinition.class))).thenReturn(1);

        EntityDefinitionDTO result = entityService.save(dto);

        assertNotNull(result);
        assertEquals("new_entity", result.getEntityCode());
        // Status is set inside the service method, not returned from DTO
        verify(entityMapper, times(1)).insert(any(EntityDefinition.class));
    }

    /** 测试更新实体：验证更新成功并触发 selectById 与 updateById */
    @Test
    void testUpdate() {
        EntityDefinitionDTO dto = new EntityDefinitionDTO();
        dto.setEntityName("更新后的名称");
        dto.setDescription("更新后的描述");

        when(entityMapper.selectById("1")).thenReturn(testEntity);
        when(entityMapper.updateById(any(EntityDefinition.class))).thenReturn(1);

        EntityDefinitionDTO result = entityService.update("1", dto);

        assertNotNull(result);
        verify(entityMapper, times(1)).selectById("1");
        verify(entityMapper, times(1)).updateById(any(EntityDefinition.class));
    }

    /** 测试更新时同步子表单关系：验证旧关系被删除并按 DTO 重新插入正确的关系记录 */
    @Test
    void testUpdateSyncsSubFormRelation() {
        EntityDefinition child = new EntityDefinition();
        child.setId("child-1");
        child.setEntityCode("child");

        EntityField detailField = new EntityField();
        detailField.setId("field-detail");
        detailField.setEntityId("1");
        detailField.setFieldCode("detailList");
        detailField.setFieldName("明细");
        detailField.setFieldType(EntityField.FieldType.SUB_FORM_LIST);

        EntityFieldDTO detailDTO = new EntityFieldDTO();
        detailDTO.setFieldCode("detailList");
        detailDTO.setFieldName("明细");
        detailDTO.setFieldType(EntityField.FieldType.SUB_FORM_LIST);
        detailDTO.setChildEntityId("child-1");
        detailDTO.setChildRefFieldCode("parentId");
        detailDTO.setRelationType("ONE_TO_MANY");
        detailDTO.setCascadeDelete(true);
        detailDTO.setSortOrder(20);

        EntityDefinitionDTO dto = new EntityDefinitionDTO();
        dto.setEntityName("测试实体");
        dto.setDescription("测试描述");
        dto.setFields(List.of(detailDTO));

        when(entityMapper.selectById("1")).thenReturn(testEntity);
        when(entityMapper.selectById("child-1")).thenReturn(child);
        when(fieldMapper.findByEntityId("1")).thenReturn(List.of(detailField));
        when(entityMapper.updateById(any(EntityDefinition.class))).thenReturn(1);

        entityService.update("1", dto);

        ArgumentCaptor<EntityRelation> relationCaptor = ArgumentCaptor.forClass(EntityRelation.class);
        verify(relationMapper).deleteByParentEntityId("1");
        verify(relationMapper).insert(relationCaptor.capture());
        EntityRelation relation = relationCaptor.getValue();
        assertEquals("1", relation.getParentEntityId());
        assertEquals("test_entity", relation.getParentEntityCode());
        assertEquals("field-detail", relation.getParentFieldId());
        assertEquals("detailList", relation.getParentFieldCode());
        assertEquals("child-1", relation.getChildEntityId());
        assertEquals("child", relation.getChildEntityCode());
        assertEquals("parentId", relation.getChildRefFieldCode());
        assertEquals(EntityRelation.RelationType.ONE_TO_MANY, relation.getRelationType());
        assertEquals(true, relation.getCascadeDelete());
    }

    /** 测试更新不存在的实体：验证抛出 RuntimeException 且消息包含对应 ID */
    @Test
    void testUpdateNotFound() {
        when(entityMapper.selectById("999")).thenReturn(null);

        EntityDefinitionDTO dto = new EntityDefinitionDTO();
        dto.setEntityName("更新");

        Exception exception = assertThrows(RuntimeException.class, () -> {
            entityService.update("999", dto);
        });

        assertEquals("实体不存在: 999", exception.getMessage());
    }

    /** 测试删除实体：验证触发 deleteById */
    @Test
    void testDelete() {
        when(entityMapper.deleteById("1")).thenReturn(1);

        entityService.delete("1");

        verify(entityMapper, times(1)).deleteById("1");
    }

    /** 测试发布实体：验证发布后状态变为 PUBLISHED 并触发表结构同步与更新 */
    @Test
    void testPublish() {
        when(entityMapper.selectById("1")).thenReturn(testEntity);
        when(fieldMapper.findByEntityId("1")).thenReturn(List.of(testField));
        when(dynamicTableService.syncEntityTableStructure(any(EntityDefinition.class))).thenReturn(Collections.emptyList());
        when(entityMapper.updateById(any(EntityDefinition.class))).thenReturn(1);

        EntityDefinitionDTO result = entityService.publish("1", "user1", "测试用户");

        assertNotNull(result);
        assertEquals(EntityDefinition.Status.PUBLISHED, result.getStatus());
        verify(entityMapper, times(1)).updateById(any(EntityDefinition.class));
    }

    /** 测试发布不存在的实体：验证抛出 RuntimeException 且消息包含对应 ID */
    @Test
    void testPublishNotFound() {
        when(entityMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            entityService.publish("999", "user1", "测试用户");
        });

        assertEquals("实体不存在: 999", exception.getMessage());
    }

    /** 测试绑定流程：验证生命周期切换为 WORKFLOW，绑定状态为草稿且记录新流程 ID */
    @Test
    void testBindWorkflow() {
        when(entityMapper.selectById("1")).thenReturn(testEntity);
        when(dynamicTableService.tableExists("test_entity")).thenReturn(false);
        when(entityMapper.updateById(any(EntityDefinition.class))).thenReturn(1);

        EntityDefinitionDTO result = entityService.bindWorkflow("1", "proc-2");

        assertNotNull(result);
        assertEquals(EntityDefinition.LifecycleMode.WORKFLOW, result.getLifecycleMode());
        assertEquals(EntityDefinitionDTO.WorkflowBindingStatus.DRAFT, result.getWorkflowBindingStatus());
        assertEquals("proc-2", result.getProcessDefinitionId());
        verify(entityMapper, times(1)).updateById(any(EntityDefinition.class));
    }

    /** 测试绑定流程时实体不存在：验证抛出 RuntimeException 且消息包含对应 ID */
    @Test
    void testBindWorkflowEntityNotFound() {
        when(entityMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            entityService.bindWorkflow("999", "proc-1");
        });

        assertEquals("实体不存在: 999", exception.getMessage());
    }

    /** 测试绑定流程设置流程 ID：验证返回的 processDefinitionId 与生命周期模式符合预期 */
    @Test
    void testBindWorkflowSetsProcessId() {
        when(entityMapper.selectById("1")).thenReturn(testEntity);
        when(entityMapper.updateById(any(EntityDefinition.class))).thenReturn(1);

        EntityDefinitionDTO result = entityService.bindWorkflow("1", "proc-2");

        assertNotNull(result);
        assertEquals("proc-2", result.getProcessDefinitionId());
        assertEquals(EntityDefinition.LifecycleMode.WORKFLOW, result.getLifecycleMode());
    }

    /** 测试绑定流程时同步更新最新发布快照的流程 ID：验证发布历史的 processDefinitionId 被改写为新流程 */
    @Test
    void testBindWorkflowUpdatesLatestPublishedSnapshot() {
        EntityPublishHistory history = new EntityPublishHistory();
        history.setId("history-1");
        history.setEntityId("1");
        history.setProcessDefinitionId("proc-1");

        when(entityMapper.selectById("1")).thenReturn(testEntity);
        when(publishHistoryMapper.findLatestByEntityId("1")).thenReturn(history);

        entityService.bindWorkflow("1", "proc-2");

        ArgumentCaptor<EntityPublishHistory> captor = ArgumentCaptor.forClass(EntityPublishHistory.class);
        verify(publishHistoryMapper).updateById(captor.capture());
        assertEquals("proc-2", captor.getValue().getProcessDefinitionId());
    }

    /** 测试新建实体默认为独立生命周期与动态存储：验证生命周期、存储模式与流程绑定状态默认值 */
    @Test
    void standaloneIsDefaultForNewEntity() {
        EntityDefinitionDTO dto = new EntityDefinitionDTO();
        dto.setEntityCode("reference_data");
        dto.setEntityName("基础资料");
        when(entityMapper.insert(any(EntityDefinition.class))).thenReturn(1);

        EntityDefinitionDTO result = entityService.save(dto);

        assertEquals(EntityDefinition.LifecycleMode.STANDALONE, result.getLifecycleMode());
        assertEquals(EntityDefinition.StorageMode.DYNAMIC, result.getStorageMode());
        assertEquals(EntityDefinitionDTO.WorkflowBindingStatus.NOT_APPLICABLE, result.getWorkflowBindingStatus());
    }

    /** 测试工作流实体不能降级为独立模式：验证抛出业务冲突异常且错误码为 ENTITY_LIFECYCLE_DOWNGRADE_FORBIDDEN */
    @Test
    void workflowEntityCannotDowngrade() {
        when(entityMapper.selectById("1")).thenReturn(testEntity);

        var exception = assertThrows(
                com.workflow.common.BusinessConflictException.class,
                () -> entityService.updateLifecycleMode(
                        "1",
                        EntityDefinition.LifecycleMode.STANDALONE));

        assertEquals("ENTITY_LIFECYCLE_DOWNGRADE_FORBIDDEN", exception.getErrorCode());
    }
}
