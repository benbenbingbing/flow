package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDefinitionDTO;
import com.workflow.dto.EntityFieldDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityPublishHistory;
import com.workflow.entity.EntityRelation;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityPublishHistoryMapper;
import com.workflow.mapper.EntityRelationMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.service.permission.EntityPermissionCatalogService;
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
 * 实体定义服务单元测试
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
    private ProcessDefinitionConfigMapper processMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EntityDataDynamicMapper entityDataDynamicMapper;

    @Mock
    private DynamicTableService dynamicTableService;

    @Mock
    private EntityPublishHistoryService publishHistoryService;

    @Mock
    private EntityFieldFileItemService fileItemService;

    @Mock
    private EntityPermissionCatalogService entityPermissionCatalogService;

    @InjectMocks
    private EntityDefinitionService entityService;

    private EntityDefinition testEntity;
    private EntityField testField;
    private ProcessDefinitionConfig testProcess;

    @BeforeEach
    void setUp() {
        testEntity = new EntityDefinition();
        testEntity.setId("1");
        testEntity.setEntityCode("test_entity");
        testEntity.setEntityName("测试实体");
        testEntity.setDescription("测试描述");
        testEntity.setProcessDefinitionId("proc-1");
        testEntity.setStatus(EntityDefinition.Status.DRAFT);

        testField = new EntityField();
        testField.setId("f1");
        testField.setEntityId("1");
        testField.setFieldCode("name");
        testField.setFieldName("名称");
        testField.setFieldType(EntityField.FieldType.STRING);

        testProcess = new ProcessDefinitionConfig();
        testProcess.setId("proc-1");
        testProcess.setProcessKey("test_process");
        testProcess.setProcessName("测试流程");
    }

    @Test
    void testFindAll() {
        when(entityMapper.selectList(null)).thenReturn(Arrays.asList(testEntity));
        when(processMapper.selectById("proc-1")).thenReturn(testProcess);

        List<EntityDefinitionDTO> result = entityService.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test_entity", result.get(0).getEntityCode());
        assertEquals("测试流程", result.get(0).getProcessName());
        verify(entityMapper, times(1)).selectList(null);
    }

    @Test
    void testFindAllWithoutProcess() {
        testEntity.setProcessDefinitionId(null);
        when(entityMapper.selectList(null)).thenReturn(Arrays.asList(testEntity));

        List<EntityDefinitionDTO> result = entityService.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertNull(result.get(0).getProcessName());
    }

    @Test
    void testFindById() {
        when(entityMapper.selectById("1")).thenReturn(testEntity);
        when(fieldMapper.findByEntityId("1")).thenReturn(Arrays.asList(testField));
        when(relationMapper.selectByParentEntityId("1")).thenReturn(Collections.emptyList());
        when(processMapper.selectById("proc-1")).thenReturn(testProcess);

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
        when(processMapper.selectById("proc-1")).thenReturn(testProcess);

        EntityDefinitionDTO result = entityService.findById("1");
        EntityFieldDTO field = result.getFields().get(0);

        assertEquals("child-1", field.getChildEntityId());
        assertEquals("child", field.getChildEntityCode());
        assertEquals("parentId", field.getChildRefFieldCode());
        assertEquals("ONE_TO_MANY", field.getRelationType());
        assertEquals(true, field.getCascadeDelete());
    }

    @Test
    void testFindByIdNotFound() {
        when(entityMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            entityService.findById("999");
        });

        assertEquals("实体不存在: 999", exception.getMessage());
    }

    @Test
    void testFindByCode() {
        when(entityMapper.findByEntityCode("test_entity")).thenReturn(Optional.of(testEntity));
        when(fieldMapper.findByEntityId("1")).thenReturn(Arrays.asList(testField));
        when(processMapper.selectById("proc-1")).thenReturn(testProcess);

        EntityDefinitionDTO result = entityService.findByCode("test_entity");

        assertNotNull(result);
        assertEquals("test_entity", result.getEntityCode());
        assertNotNull(result.getFields());
        verify(entityMapper, times(1)).findByEntityCode("test_entity");
    }

    @Test
    void testFindByCodeNotFound() {
        when(entityMapper.findByEntityCode("not_exist")).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> {
            entityService.findByCode("not_exist");
        });

        assertEquals("实体不存在: not_exist", exception.getMessage());
    }

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

    @Test
    void testDelete() {
        when(entityMapper.deleteById("1")).thenReturn(1);

        entityService.delete("1");

        verify(entityMapper, times(1)).deleteById("1");
    }

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

    @Test
    void testPublishNotFound() {
        when(entityMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            entityService.publish("999", "user1", "测试用户");
        });

        assertEquals("实体不存在: 999", exception.getMessage());
    }

    @Test
    void testBindProcess() {
        when(entityMapper.selectById("1")).thenReturn(testEntity);
        when(dynamicTableService.getTableName("test_entity")).thenReturn("wf_test_entity");
        when(dynamicTableService.tableExists("test_entity")).thenReturn(false);
        when(entityMapper.updateById(any(EntityDefinition.class))).thenReturn(1);

        EntityDefinitionDTO result = entityService.bindProcess("1", "proc-2");

        assertNotNull(result);
        assertTrue(result.getEnableProcess());
        assertEquals("proc-2", result.getProcessDefinitionId());
        verify(entityMapper, times(1)).updateById(any(EntityDefinition.class));
    }

    @Test
    void testBindProcessEntityNotFound() {
        when(entityMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            entityService.bindProcess("999", "proc-1");
        });

        assertEquals("实体不存在: 999", exception.getMessage());
    }

    @Test
    void testBindProcessSetsProcessId() {
        when(entityMapper.selectById("1")).thenReturn(testEntity);
        when(entityMapper.updateById(any(EntityDefinition.class))).thenReturn(1);

        EntityDefinitionDTO result = entityService.bindProcess("1", "proc-2");

        assertNotNull(result);
        assertEquals("proc-2", result.getProcessDefinitionId());
        assertTrue(result.getEnableProcess());
    }

    @Test
    void testBindProcessUpdatesLatestPublishedSnapshot() {
        EntityPublishHistory history = new EntityPublishHistory();
        history.setId("history-1");
        history.setEntityId("1");
        history.setProcessDefinitionId("proc-1");

        when(entityMapper.selectById("1")).thenReturn(testEntity);
        when(publishHistoryMapper.findLatestByEntityId("1")).thenReturn(history);

        entityService.bindProcess("1", "proc-2");

        ArgumentCaptor<EntityPublishHistory> captor = ArgumentCaptor.forClass(EntityPublishHistory.class);
        verify(publishHistoryMapper).updateById(captor.capture());
        assertEquals("proc-2", captor.getValue().getProcessDefinitionId());
    }
}
