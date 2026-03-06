package com.workflow.controller;

import com.workflow.dto.ProcessProgressDTO;
import com.workflow.service.ProcessInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 流程实例控制器单元测试
 */
@WebMvcTest(ProcessInstanceController.class)
public class ProcessInstanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProcessInstanceService processInstanceService;

    private ProcessProgressDTO testProgress;

    @BeforeEach
    void setUp() {
        testProgress = new ProcessProgressDTO();
        testProgress.setProcessInstanceId("proc-inst-1");
        testProgress.setProcessDefinitionId("proc-def-1");
        testProgress.setProcessKey("leave_process");
        testProgress.setProcessName("请假流程");
        testProgress.setStatus("RUNNING");
        testProgress.setCompletedNodes(Arrays.asList("StartEvent_1", "Flow_1"));
        testProgress.setActiveNodes(Arrays.asList("Task_1"));
        testProgress.setExecutedSequenceFlows(Arrays.asList("Flow_1"));
        
        // 设置节点历史
        ProcessProgressDTO.NodeHistoryDTO history = new ProcessProgressDTO.NodeHistoryDTO();
        history.setNodeId("StartEvent_1");
        history.setNodeName("开始");
        history.setNodeType("startEvent");
        history.setStatus("COMPLETED");
        testProgress.setNodeHistory(Arrays.asList(history));
        
        // 设置处理人映射
        Map<String, ProcessProgressDTO.AssigneeInfoDTO> assigneeMap = new HashMap<>();
        ProcessProgressDTO.AssigneeInfoDTO assigneeInfo = new ProcessProgressDTO.AssigneeInfoDTO();
        assigneeInfo.setAssigneeId("user1");
        assigneeInfo.setAssigneeName("张三");
        assigneeInfo.setAction("APPROVED");
        assigneeMap.put("Task_1", assigneeInfo);
        testProgress.setNodeAssigneeMap(assigneeMap);
    }

    @Test
    void testGetProcessProgress() throws Exception {
        when(processInstanceService.getProcessProgress("proc-inst-1")).thenReturn(testProgress);

        mockMvc.perform(get("/api/process-instance/proc-inst-1/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.processInstanceId").value("proc-inst-1"))
                .andExpect(jsonPath("$.data.processKey").value("leave_process"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.completedNodes[0]").value("StartEvent_1"))
                .andExpect(jsonPath("$.data.activeNodes[0]").value("Task_1"))
                .andExpect(jsonPath("$.data.nodeHistory[0].nodeName").value("开始"))
                .andExpect(jsonPath("$.data.nodeAssigneeMap.Task_1.assigneeName").value("张三"));

        verify(processInstanceService, times(1)).getProcessProgress("proc-inst-1");
    }

    @Test
    void testGetProcessProgressCompleted() throws Exception {
        testProgress.setStatus("COMPLETED");
        testProgress.setActiveNodes(Arrays.asList());
        
        when(processInstanceService.getProcessProgress("proc-inst-2")).thenReturn(testProgress);

        mockMvc.perform(get("/api/process-instance/proc-inst-2/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        verify(processInstanceService, times(1)).getProcessProgress("proc-inst-2");
    }
}
