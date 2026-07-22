package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.PageResult;
import com.workflow.dto.ProcessDefinitionDTO;
import com.workflow.dto.ProcessDefinitionQueryDTO;
import com.workflow.dto.ProcessVersionHistoryDTO;
import com.workflow.dto.migration.ConfigMigrationPublishRequest;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.service.ProcessDefinitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 流程定义控制器单元测试
 */
@WebMvcTest(ProcessDefinitionController.class)
public class ProcessDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProcessDefinitionService processService;

    @Autowired
    private ObjectMapper objectMapper;

    private ProcessDefinitionDTO testProcess;

    @BeforeEach
    void setUp() {
        testProcess = new ProcessDefinitionDTO();
        testProcess.setId("1");
        testProcess.setProcessKey("leave_process");
        testProcess.setProcessName("请假流程");
        testProcess.setDescription("员工请假审批流程");
        testProcess.setVersion(1);
        testProcess.setStatus(ProcessDefinitionConfig.ProcessStatus.DRAFT);
    }

    @Test
    void testList() throws Exception {
        PageResult<ProcessDefinitionDTO> pageResult = new PageResult<>(
                Arrays.asList(testProcess), 1, 1, 10);
        when(processService.findPage(any(ProcessDefinitionQueryDTO.class))).thenReturn(pageResult);

        mockMvc.perform(get("/api/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].processKey").value("leave_process"))
                .andExpect(jsonPath("$.data.records[0].processName").value("请假流程"));

        verify(processService, times(1)).findPage(any(ProcessDefinitionQueryDTO.class));
    }

    @Test
    void testListPublished() throws Exception {
        testProcess.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
        List<ProcessDefinitionDTO> processList = Arrays.asList(testProcess);
        when(processService.findByStatus(any(ProcessDefinitionConfig.ProcessStatus.class))).thenReturn(processList);

        mockMvc.perform(get("/api/process/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].status").value("PUBLISHED"));

        verify(processService, times(1)).findByStatus(any(ProcessDefinitionConfig.ProcessStatus.class));
    }

    @Test
    void testGetById() throws Exception {
        when(processService.findById("1")).thenReturn(testProcess);

        mockMvc.perform(get("/api/process/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.processKey").value("leave_process"));

        verify(processService, times(1)).findById("1");
    }

    @Test
    void testGetByKey() throws Exception {
        when(processService.findByProcessKey("leave_process")).thenReturn(testProcess);

        mockMvc.perform(get("/api/process/key/leave_process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.processKey").value("leave_process"));

        verify(processService, times(1)).findByProcessKey("leave_process");
    }

    @Test
    void testCreate() throws Exception {
        when(processService.save(any(ProcessDefinitionDTO.class))).thenReturn(testProcess);

        mockMvc.perform(post("/api/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testProcess)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.processKey").value("leave_process"));

        verify(processService, times(1)).save(any(ProcessDefinitionDTO.class));
    }

    @Test
    void testUpdate() throws Exception {
        when(processService.update(eq("1"), any(ProcessDefinitionDTO.class))).thenReturn(testProcess);

        mockMvc.perform(post("/api/process/1/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testProcess)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"));

        verify(processService, times(1)).update(eq("1"), any(ProcessDefinitionDTO.class));
    }

    @Test
    void testDelete() throws Exception {
        doNothing().when(processService).delete("1");

        mockMvc.perform(post("/api/process/1/delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(processService, times(1)).delete("1");
    }

    @Test
    void testPublish() throws Exception {
        testProcess.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
        when(processService.publish(eq("1"), any(ConfigMigrationPublishRequest.class))).thenReturn(testProcess);

        Map<String, String> request = new HashMap<>();
        request.put("versionDescription", "初始版本");

        mockMvc.perform(post("/api/process/1/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        verify(processService, times(1)).publish(eq("1"), any(ConfigMigrationPublishRequest.class));
    }

    @Test
    void testDisable() throws Exception {
        testProcess.setStatus(ProcessDefinitionConfig.ProcessStatus.DISABLED);
        when(processService.disable("1")).thenReturn(testProcess);

        mockMvc.perform(post("/api/process/1/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        verify(processService, times(1)).disable("1");
    }

    @Test
    void testGetVersions() throws Exception {
        ProcessVersionHistoryDTO version = new ProcessVersionHistoryDTO();
        version.setId("v1");
        version.setProcessKey("leave_process");
        version.setVersion(1);
        
        List<ProcessVersionHistoryDTO> versions = Arrays.asList(version);
        when(processService.findVersionsByProcessId("1")).thenReturn(versions);

        mockMvc.perform(get("/api/process/1/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].version").value(1));

        verify(processService, times(1)).findVersionsByProcessId("1");
    }

    @Test
    void testGetVersionById() throws Exception {
        ProcessVersionHistoryDTO version = new ProcessVersionHistoryDTO();
        version.setId("v1");
        version.setProcessKey("leave_process");
        
        when(processService.findVersionById("v1")).thenReturn(version);

        mockMvc.perform(get("/api/process/versions/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("v1"));

        verify(processService, times(1)).findVersionById("v1");
    }

    @Test
    void testRollbackToVersion() throws Exception {
        when(processService.rollbackToVersion(eq("1"), eq("v1"), any())).thenReturn(testProcess);

        Map<String, String> request = new HashMap<>();
        request.put("reason", "回滚测试");

        mockMvc.perform(post("/api/process/1/rollback/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(processService, times(1)).rollbackToVersion(eq("1"), eq("v1"), any());
    }
}
