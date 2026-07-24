package com.workflow.service;

import com.workflow.dto.NodeConfigDTO;
import com.workflow.entity.*;
import com.workflow.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 节点配置服务单元测试。
 *
 * <p>被测对象：{@link NodeConfigService}，覆盖节点配置的查询、新增（含处理人）、删除，
 * 以及按流程查询时处理人、表单的装配等场景。
 */
@ExtendWith(MockitoExtension.class)
public class NodeConfigServiceTest {

    @Mock
    private NodeConfigMapper nodeMapper;

    @Mock
    private ProcessDefinitionConfigMapper processMapper;

    @Mock
    private AssigneeConfigMapper assigneeMapper;

    @Mock
    private FormConfigMapper formMapper;

    @Mock
    private FormFieldConfigMapper fieldMapper;

    @InjectMocks
    private NodeConfigService nodeService;

    /** 测试节点配置 */
    private NodeConfig testNode;
    /** 测试流程定义 */
    private ProcessDefinitionConfig testProcess;

    /** 初始化测试流程与测试节点 */
    @BeforeEach
    void setUp() {
        testProcess = new ProcessDefinitionConfig();
        testProcess.setId("proc-1");
        testProcess.setProcessKey("test_process");
        testProcess.setProcessName("测试流程");

        testNode = new NodeConfig();
        testNode.setId("node-1");
        testNode.setNodeId("Task_1");
        testNode.setNodeName("审批节点");
        testNode.setNodeType(NodeConfig.NodeType.USER_TASK);
        testNode.setProcessConfigId("proc-1");
    }

    /** 测试按流程 ID 查询节点：验证返回数量与节点关键字段正确 */
    @Test
    void testFindByProcessId() {
        when(nodeMapper.findByProcessConfigId("proc-1")).thenReturn(Arrays.asList(testNode));
        when(assigneeMapper.findByNodeConfigId("node-1")).thenReturn(Collections.emptyList());
        when(formMapper.findByNodeConfigId("node-1")).thenReturn(Collections.emptyList());

        List<NodeConfigDTO> result = nodeService.findByProcessId("proc-1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Task_1", result.get(0).getNodeId());
        assertEquals("审批节点", result.get(0).getNodeName());
    }

    /** 测试按 ID 查询节点：验证返回的 id 与 nodeId 正确 */
    @Test
    void testFindById() {
        when(nodeMapper.selectById("node-1")).thenReturn(testNode);
        when(assigneeMapper.findByNodeConfigId("node-1")).thenReturn(Collections.emptyList());
        when(formMapper.findByNodeConfigId("node-1")).thenReturn(Collections.emptyList());

        NodeConfigDTO result = nodeService.findById("node-1");

        assertNotNull(result);
        assertEquals("node-1", result.getId());
        assertEquals("Task_1", result.getNodeId());
    }

    /** 测试按 ID 查询不存在节点：验证抛出 RuntimeException 且消息包含对应 ID */
    @Test
    void testFindByIdNotFound() {
        when(nodeMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            nodeService.findById("999");
        });

        assertEquals("Node not found: 999", exception.getMessage());
    }

    /** 测试新增节点：验证返回 nodeId 正确并触发 insert */
    @Test
    void testSave() {
        NodeConfigDTO dto = new NodeConfigDTO();
        dto.setNodeId("Task_2");
        dto.setNodeName("新节点");
        dto.setNodeType(NodeConfig.NodeType.USER_TASK);

        when(processMapper.selectById("proc-1")).thenReturn(testProcess);
        when(nodeMapper.insert(any(NodeConfig.class))).thenAnswer(invocation -> {
            NodeConfig node = invocation.getArgument(0);
            node.setId("new-node-id");
            return 1;
        });

        NodeConfigDTO result = nodeService.save("proc-1", dto);

        assertNotNull(result);
        assertEquals("Task_2", result.getNodeId());
        verify(nodeMapper, times(1)).insert(any(NodeConfig.class));
    }

    /** 测试新增节点时流程不存在：验证抛出 RuntimeException 且消息包含对应 ID */
    @Test
    void testSaveProcessNotFound() {
        NodeConfigDTO dto = new NodeConfigDTO();
        dto.setNodeId("Task_2");
        dto.setNodeName("新节点");

        when(processMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            nodeService.save("999", dto);
        });

        assertEquals("Process not found: 999", exception.getMessage());
    }

    /** 测试新增带处理人的节点：验证节点与处理人各触发一次 insert */
    @Test
    void testSaveWithAssignees() {
        NodeConfigDTO dto = new NodeConfigDTO();
        dto.setNodeId("Task_2");
        dto.setNodeName("审批节点");
        dto.setNodeType(NodeConfig.NodeType.USER_TASK);
        
        com.workflow.dto.AssigneeConfigDTO assigneeDTO = new com.workflow.dto.AssigneeConfigDTO();
        assigneeDTO.setAssigneeType(AssigneeConfig.AssigneeType.USER);
        assigneeDTO.setAssigneeValue("user1");
        assigneeDTO.setAssigneeName("张三");
        dto.setAssignees(Arrays.asList(assigneeDTO));

        when(processMapper.selectById("proc-1")).thenReturn(testProcess);
        when(nodeMapper.insert(any(NodeConfig.class))).thenAnswer(invocation -> {
            NodeConfig node = invocation.getArgument(0);
            node.setId("new-node-id");
            return 1;
        });
        when(assigneeMapper.insert(any(AssigneeConfig.class))).thenReturn(1);

        NodeConfigDTO result = nodeService.save("proc-1", dto);

        assertNotNull(result);
        verify(nodeMapper, times(1)).insert(any(NodeConfig.class));
        verify(assigneeMapper, times(1)).insert(any(AssigneeConfig.class));
    }

    /** 测试删除节点：验证触发 deleteById */
    @Test
    void testDelete() {
        when(nodeMapper.deleteById("node-1")).thenReturn(1);

        nodeService.delete("node-1");

        verify(nodeMapper, times(1)).deleteById("node-1");
    }

    /** 测试按流程查询节点时装配处理人：验证处理人列表数量与名称正确 */
    @Test
    void testFindByProcessIdWithAssignees() {
        AssigneeConfig assignee = new AssigneeConfig();
        assignee.setId("assignee-1");
        assignee.setNodeConfigId("node-1");
        assignee.setAssigneeType(AssigneeConfig.AssigneeType.USER);
        assignee.setAssigneeValue("user1");
        assignee.setAssigneeName("张三");

        when(nodeMapper.findByProcessConfigId("proc-1")).thenReturn(Arrays.asList(testNode));
        when(assigneeMapper.findByNodeConfigId("node-1")).thenReturn(Arrays.asList(assignee));
        when(formMapper.findByNodeConfigId("node-1")).thenReturn(Collections.emptyList());

        List<NodeConfigDTO> result = nodeService.findByProcessId("proc-1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertNotNull(result.get(0).getAssignees());
        assertEquals(1, result.get(0).getAssignees().size());
        assertEquals("张三", result.get(0).getAssignees().get(0).getAssigneeName());
    }

    /** 测试按流程查询节点时装配表单：验证表单列表数量与名称正确 */
    @Test
    void testFindByProcessIdWithForms() {
        FormConfig form = new FormConfig();
        form.setId("form-1");
        form.setNodeConfigId("node-1");
        form.setFormName("审批表单");
        form.setFormKey("approval_form");

        when(nodeMapper.findByProcessConfigId("proc-1")).thenReturn(Arrays.asList(testNode));
        when(assigneeMapper.findByNodeConfigId("node-1")).thenReturn(Collections.emptyList());
        when(formMapper.findByNodeConfigId("node-1")).thenReturn(Arrays.asList(form));
        when(fieldMapper.findByFormConfigId("form-1")).thenReturn(Collections.emptyList());

        List<NodeConfigDTO> result = nodeService.findByProcessId("proc-1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertNotNull(result.get(0).getForms());
        assertEquals(1, result.get(0).getForms().size());
        assertEquals("审批表单", result.get(0).getForms().get(0).getFormName());
    }
}
