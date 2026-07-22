package com.workflow.process.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.NodeConfigDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityFlowStatusMapping;
import com.workflow.entity.NodeConfig;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.mapper.AssigneeConfigMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.FormConfigMapper;
import com.workflow.mapper.NodeConfigMapper;
import com.workflow.mapper.ProcessNodeApprovalMapper;
import com.workflow.mapper.ProcessNodeFormMapper;
import com.workflow.service.EntityFlowStatusService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessDefinitionNodeSyncServiceTest {

    @Test
    void syncDraftNodesReplacesExistingNodes() {
        NodeConfigMapper nodeMapper = mock(NodeConfigMapper.class);
        ProcessDefinitionNodeSyncService service = service(nodeMapper, null, null, null, null, null, null, null);

        NodeConfigDTO nodeDTO = new NodeConfigDTO();
        nodeDTO.setId("node-config-1");
        nodeDTO.setNodeId("task-1");
        nodeDTO.setNodeName("审批");
        nodeDTO.setNodeType(NodeConfig.NodeType.USER_TASK);
        nodeDTO.setConfigJson("{\"x\":1}");
        nodeDTO.setSkipNode(true);

        service.syncDraftNodes("process-1", List.of(nodeDTO));

        verify(nodeMapper).deleteByProcessConfigId("process-1");
        ArgumentCaptor<NodeConfig> captor = ArgumentCaptor.forClass(NodeConfig.class);
        verify(nodeMapper).insert(captor.capture());
        NodeConfig node = captor.getValue();
        assertEquals("process-1", node.getProcessConfigId());
        assertEquals("node-config-1", node.getId());
        assertEquals("task-1", node.getNodeId());
        assertEquals("审批", node.getNodeName());
        assertEquals(NodeConfig.NodeType.USER_TASK, node.getNodeType());
        assertEquals("{\"x\":1}", node.getConfigJson());
        assertEquals(true, node.getSkipNode());
    }

    @Test
    void syncNodeFormsFromBpmnPersistsMultipleEntityFormIdsInOrder() {
        ProcessNodeFormMapper nodeFormMapper = mock(ProcessNodeFormMapper.class);
        ProcessDefinitionNodeSyncService service = service(null, null, null, null, null, null, nodeFormMapper, null);

        service.syncNodeFormsFromBpmn("process-1", bpmnWithEntityFormIds());

        verify(nodeFormMapper).deleteByProcessConfigIdAndNodeId("process-1", "task-1");
        ArgumentCaptor<ProcessNodeForm> captor = ArgumentCaptor.forClass(ProcessNodeForm.class);
        verify(nodeFormMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<ProcessNodeForm> inserted = captor.getAllValues();
        assertEquals("form-a", inserted.get(0).getFormId());
        assertEquals(0, inserted.get(0).getSortOrder());
        assertEquals(1, inserted.get(0).getIsReadonly());
        assertEquals("form-b", inserted.get(1).getFormId());
        assertEquals(1, inserted.get(1).getSortOrder());
        assertEquals(1, inserted.get(1).getIsReadonly());
    }

    @Test
    void syncStatusMappingsFromBpmnReadsEntityBinding() {
        EntityDefinitionMapper entityDefinitionMapper = mock(EntityDefinitionMapper.class);
        EntityFlowStatusService entityFlowStatusService = mock(EntityFlowStatusService.class);
        EntityDefinition entity = new EntityDefinition();
        entity.setEntityCode("expense");
        when(entityDefinitionMapper.findByProcessDefinitionId("process-1")).thenReturn(Optional.of(entity));
        ProcessDefinitionNodeSyncService service = service(null, null, null, null, entityFlowStatusService,
                entityDefinitionMapper, null, null);

        service.syncStatusMappingsFromBpmn("process-1", "expense_flow", bpmnWithStatusMapping());

        ArgumentCaptor<List<EntityFlowStatusMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(entityFlowStatusService).saveStatusMappings(
                org.mockito.Mockito.eq("process-1"),
                org.mockito.Mockito.eq("expense_flow"),
                org.mockito.Mockito.eq("expense"),
                captor.capture());
        EntityFlowStatusMapping mapping = captor.getValue().get(0);
        assertEquals("flow-1", mapping.getSequenceFlowId());
        assertEquals("start-1", mapping.getSourceNodeId());
        assertEquals("开始", mapping.getSourceNodeName());
        assertEquals("task-1", mapping.getTargetNodeId());
        assertEquals("审批", mapping.getTargetNodeName());
        assertEquals("approving", mapping.getEntityStatusCode());
    }

    @Test
    void parseAndSaveNodeConfigsPersistsEverySupportedNodeType() {
        NodeConfigMapper nodeMapper = mock(NodeConfigMapper.class);
        List<NodeConfig> insertedNodes = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            NodeConfig node = invocation.getArgument(0);
            node.setId("node-" + insertedNodes.size());
            insertedNodes.add(node);
            return 1;
        }).when(nodeMapper).insert(org.mockito.ArgumentMatchers.any(NodeConfig.class));
        when(nodeMapper.findByProcessConfigId("process-1")).thenAnswer(invocation -> insertedNodes);
        ProcessDefinitionNodeSyncService service = service(nodeMapper, null, null, null, null, null, null, null);

        service.parseAndSaveNodeConfigs("process-1", bpmnWithEverySupportedNodeType());

        ArgumentCaptor<NodeConfig> captor = ArgumentCaptor.forClass(NodeConfig.class);
        verify(nodeMapper, times(15)).insert(captor.capture());
        List<NodeConfig.NodeType> nodeTypes = captor.getAllValues().stream()
                .map(NodeConfig::getNodeType)
                .toList();
        assertEquals(List.of(
                NodeConfig.NodeType.START,
                NodeConfig.NodeType.END,
                NodeConfig.NodeType.USER_TASK,
                NodeConfig.NodeType.SERVICE_TASK,
                NodeConfig.NodeType.SCRIPT_TASK,
                NodeConfig.NodeType.SEND_TASK,
                NodeConfig.NodeType.RECEIVE_TASK,
                NodeConfig.NodeType.MANUAL_TASK,
                NodeConfig.NodeType.BUSINESS_RULE_TASK,
                NodeConfig.NodeType.EXCLUSIVE_GATEWAY,
                NodeConfig.NodeType.PARALLEL_GATEWAY,
                NodeConfig.NodeType.INCLUSIVE_GATEWAY,
                NodeConfig.NodeType.EVENT_BASED_GATEWAY,
                NodeConfig.NodeType.CALL_ACTIVITY,
                NodeConfig.NodeType.SUB_PROCESS
        ), nodeTypes);
    }

    private static ProcessDefinitionNodeSyncService service(NodeConfigMapper nodeMapper,
                                                           AssigneeConfigMapper assigneeMapper,
                                                           FormConfigMapper formMapper,
                                                           ObjectMapper objectMapper,
                                                           EntityFlowStatusService entityFlowStatusService,
                                                           EntityDefinitionMapper entityDefinitionMapper,
                                                           ProcessNodeFormMapper nodeFormMapper,
                                                           ProcessNodeApprovalMapper nodeApprovalMapper) {
        return new ProcessDefinitionNodeSyncService(nodeMapper, assigneeMapper, formMapper,
                objectMapper == null ? new ObjectMapper() : objectMapper,
                entityFlowStatusService, entityDefinitionMapper, nodeFormMapper, nodeApprovalMapper,
                null, null);
    }

    private static String bpmnWithEntityFormIds() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:flowable=\"http://flowable.org/bpmn\">"
                + "<bpmn:process id=\"process_1\">"
                + "<bpmn:userTask id=\"task-1\" name=\"审批\">"
                + "<bpmn:extensionElements>"
                + "<flowable:properties>"
                + "<flowable:property name=\"entityFormIds\" value=\"[&quot;form-a&quot;,&quot;form-b&quot;]\" />"
                + "<flowable:property name=\"entityFormReadonly\" value=\"true\" />"
                + "</flowable:properties>"
                + "</bpmn:extensionElements>"
                + "</bpmn:userTask>"
                + "</bpmn:process>"
                + "</bpmn:definitions>";
    }

    private static String bpmnWithStatusMapping() {
        return "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                + "<bpmn:process id=\"expense_flow\">"
                + "<bpmn:startEvent id=\"start-1\" name=\"开始\" />"
                + "<bpmn:userTask id=\"task-1\" name=\"审批\" />"
                + "<bpmn:sequenceFlow id=\"flow-1\" sourceRef=\"start-1\" targetRef=\"task-1\">"
                + "<bpmn:extensionElements>"
                + "<flowable:property name=\"entityStatusCode\" value=\"approving\" />"
                + "</bpmn:extensionElements>"
                + "</bpmn:sequenceFlow>"
                + "</bpmn:process>"
                + "</bpmn:definitions>";
    }

    private static String bpmnWithEverySupportedNodeType() {
        return "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                + "<bpmn:process id=\"all_nodes\">"
                + "<bpmn:startEvent id=\"start\" />"
                + "<bpmn:endEvent id=\"end\" />"
                + "<bpmn:userTask id=\"user\" />"
                + "<bpmn:serviceTask id=\"service\" />"
                + "<bpmn:scriptTask id=\"script\" />"
                + "<bpmn:sendTask id=\"send\" />"
                + "<bpmn:receiveTask id=\"receive\" />"
                + "<bpmn:manualTask id=\"manual\" />"
                + "<bpmn:businessRuleTask id=\"rule\" />"
                + "<bpmn:exclusiveGateway id=\"exclusive\" />"
                + "<bpmn:parallelGateway id=\"parallel\" />"
                + "<bpmn:inclusiveGateway id=\"inclusive\" />"
                + "<bpmn:eventBasedGateway id=\"event\" />"
                + "<bpmn:callActivity id=\"call\" />"
                + "<bpmn:subProcess id=\"sub\" />"
                + "</bpmn:process>"
                + "</bpmn:definitions>";
    }
}
