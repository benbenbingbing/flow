package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDefinitionDTO;
import com.workflow.dto.EntityFieldDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
    private ProcessDefinitionConfigMapper processMapper;

    @Mock
    private ObjectMapper objectMapper;

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
}
