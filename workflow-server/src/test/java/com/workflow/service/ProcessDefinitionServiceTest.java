package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.ProcessDefinitionDTO;
import com.workflow.dto.ProcessVersionHistoryDTO;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessVersionHistory;
import com.workflow.mapper.*;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
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
 * 流程定义服务单元测试
 */
@ExtendWith(MockitoExtension.class)
public class ProcessDefinitionServiceTest {

    @Mock
    private ProcessDefinitionConfigMapper processMapper;

    @Mock
    private ProcessVersionHistoryMapper versionHistoryMapper;

    @Mock
    private NodeConfigMapper nodeMapper;

    @Mock
    private AssigneeConfigMapper assigneeMapper;

    @Mock
    private FormConfigMapper formMapper;

    @Mock
    private FormFieldConfigMapper fieldMapper;

    @Mock
    private RepositoryService activitiRepositoryService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProcessDefinitionService processService;

    private ProcessDefinitionConfig testProcess;

    @BeforeEach
    void setUp() {
        testProcess = new ProcessDefinitionConfig();
        testProcess.setId("1");
        testProcess.setProcessKey("leave_process");
        testProcess.setProcessName("请假流程");
        testProcess.setDescription("员工请假审批流程");
        testProcess.setVersion(1);
        testProcess.setStatus(ProcessDefinitionConfig.ProcessStatus.DRAFT);
        testProcess.setBpmnXml("<bpmn:definitions>...</bpmn:definitions>");
    }

    @Test
    void testFindAll() {
        when(processMapper.selectList(null)).thenReturn(Arrays.asList(testProcess));

        List<ProcessDefinitionDTO> result = processService.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("leave_process", result.get(0).getProcessKey());
        verify(processMapper, times(1)).selectList(null);
    }

    @Test
    void testFindByStatus() {
        testProcess.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
        when(processMapper.findByStatus("PUBLISHED")).thenReturn(Arrays.asList(testProcess));

        List<ProcessDefinitionDTO> result = processService.findByStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(ProcessDefinitionConfig.ProcessStatus.PUBLISHED, result.get(0).getStatus());
    }

    @Test
    void testFindById() {
        when(processMapper.selectById("1")).thenReturn(testProcess);

        ProcessDefinitionDTO result = processService.findById("1");

        assertNotNull(result);
        assertEquals("1", result.getId());
        assertEquals("leave_process", result.getProcessKey());
    }

    @Test
    void testFindByIdNotFound() {
        when(processMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            processService.findById("999");
        });

        assertEquals("Process not found: 999", exception.getMessage());
    }

    @Test
    void testFindByProcessKey() {
        when(processMapper.findByProcessKey("leave_process")).thenReturn(Optional.of(testProcess));

        ProcessDefinitionDTO result = processService.findByProcessKey("leave_process");

        assertNotNull(result);
        assertEquals("leave_process", result.getProcessKey());
    }

    @Test
    void testFindByProcessKeyNotFound() {
        when(processMapper.findByProcessKey("not_exist")).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> {
            processService.findByProcessKey("not_exist");
        });

        assertEquals("Process not found: not_exist", exception.getMessage());
    }

    @Test
    void testSave() {
        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setProcessKey("new_process");
        dto.setProcessName("新流程");

        when(processMapper.insert(any(ProcessDefinitionConfig.class))).thenReturn(1);

        ProcessDefinitionDTO result = processService.save(dto);

        assertNotNull(result);
        assertEquals("new_process", result.getProcessKey());
        assertEquals(0, result.getVersion());
        assertEquals(ProcessDefinitionConfig.ProcessStatus.DRAFT, result.getStatus());
    }

    @Test
    void testUpdate() {
        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setProcessName("更新后的流程名");
        dto.setDescription("更新后的描述");
        dto.setBpmnXml("<bpmn:definitions>updated</bpmn:definitions>");

        when(processMapper.selectById("1")).thenReturn(testProcess);
        when(processMapper.updateById(any(ProcessDefinitionConfig.class))).thenReturn(1);

        ProcessDefinitionDTO result = processService.update("1", dto);

        assertNotNull(result);
        verify(processMapper, times(1)).selectById("1");
        verify(processMapper, times(1)).updateById(any(ProcessDefinitionConfig.class));
    }

    @Test
    void testUpdateNotFound() {
        when(processMapper.selectById("999")).thenReturn(null);

        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setProcessName("更新");

        Exception exception = assertThrows(RuntimeException.class, () -> {
            processService.update("999", dto);
        });

        assertEquals("Process not found: 999", exception.getMessage());
    }

    @Test
    void testDelete() {
        when(versionHistoryMapper.delete(any())).thenReturn(1);
        when(processMapper.deleteById("1")).thenReturn(1);

        processService.delete("1");

        verify(versionHistoryMapper, times(1)).delete(any());
        verify(processMapper, times(1)).deleteById("1");
    }

    @Test
    void testFindVersionsByProcessId() {
        ProcessVersionHistory version = new ProcessVersionHistory();
        version.setId("v1");
        version.setProcessConfigId("1");
        version.setProcessKey("leave_process");
        version.setVersion(1);

        when(versionHistoryMapper.findByProcessConfigId("1")).thenReturn(Arrays.asList(version));

        List<ProcessVersionHistoryDTO> result = processService.findVersionsByProcessId("1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getVersion());
    }

    @Test
    void testFindVersionById() {
        ProcessVersionHistory version = new ProcessVersionHistory();
        version.setId("v1");
        version.setProcessKey("leave_process");
        version.setVersion(1);

        when(versionHistoryMapper.selectById("v1")).thenReturn(version);

        ProcessVersionHistoryDTO result = processService.findVersionById("v1");

        assertNotNull(result);
        assertEquals("v1", result.getId());
    }

    @Test
    void testFindVersionByIdNotFound() {
        when(versionHistoryMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            processService.findVersionById("999");
        });

        assertEquals("Version not found: 999", exception.getMessage());
    }

    @Test
    void testDisable() {
        testProcess.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
        when(processMapper.selectById("1")).thenReturn(testProcess);
        when(processMapper.updateById(any(ProcessDefinitionConfig.class))).thenReturn(1);

        ProcessDefinitionDTO result = processService.disable("1");

        assertNotNull(result);
        assertEquals(ProcessDefinitionConfig.ProcessStatus.DISABLED, result.getStatus());
    }

    @Test
    void testDisableNotFound() {
        when(processMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            processService.disable("999");
        });

        assertEquals("Process not found: 999", exception.getMessage());
    }
}
