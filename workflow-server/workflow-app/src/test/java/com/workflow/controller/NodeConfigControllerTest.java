package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.NodeConfigDTO;
import com.workflow.entity.NodeConfig;
import com.workflow.service.NodeConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 节点配置控制器单元测试
 */
@WebMvcTest(NodeConfigController.class)
public class NodeConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NodeConfigService nodeService;

    @Autowired
    private ObjectMapper objectMapper;

    /** 每个测试前初始化的节点配置测试数据 */
    private NodeConfigDTO testNode;

    /** 初始化测试用节点配置 DTO */
    @BeforeEach
    void setUp() {
        testNode = new NodeConfigDTO();
        testNode.setId("1");
        testNode.setNodeId("Task_1");
        testNode.setNodeName("审批节点");
        testNode.setNodeType(NodeConfig.NodeType.USER_TASK);
    }

    /** 测试按流程 ID 查询节点列表接口，断言返回 200 且包含预期节点数据 */
    @Test
    void testList() throws Exception {
        List<NodeConfigDTO> nodeList = Arrays.asList(testNode);
        when(nodeService.findByProcessId("proc-1")).thenReturn(nodeList);

        mockMvc.perform(get("/api/process/proc-1/nodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].nodeId").value("Task_1"))
                .andExpect(jsonPath("$.data[0].nodeName").value("审批节点"));

        verify(nodeService, times(1)).findByProcessId("proc-1");
    }

    /** 测试按 ID 查询单个节点接口，断言返回 200 且节点字段正确 */
    @Test
    void testGetById() throws Exception {
        when(nodeService.findById("1")).thenReturn(testNode);

        mockMvc.perform(get("/api/process/proc-1/nodes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.nodeName").value("审批节点"));

        verify(nodeService, times(1)).findById("1");
    }

    /** 测试新增节点接口，断言返回 200 且保存的节点 ID 正确 */
    @Test
    void testCreate() throws Exception {
        when(nodeService.save(eq("proc-1"), any(NodeConfigDTO.class))).thenReturn(testNode);

        mockMvc.perform(post("/api/process/proc-1/nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testNode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nodeId").value("Task_1"));

        verify(nodeService, times(1)).save(eq("proc-1"), any(NodeConfigDTO.class));
    }

    /** 测试删除节点接口，断言返回 200 且 delete 方法被调用 */
    @Test
    void testDelete() throws Exception {
        doNothing().when(nodeService).delete("1");

        mockMvc.perform(post("/api/process/proc-1/nodes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(nodeService, times(1)).delete("1");
    }
}
