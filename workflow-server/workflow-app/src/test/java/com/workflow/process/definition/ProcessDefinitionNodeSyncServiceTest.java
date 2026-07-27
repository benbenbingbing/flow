package com.workflow.process.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.NodeConfigDTO;
import com.workflow.entity.AssigneeConfig;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityFlowStatusMapping;
import com.workflow.entity.FormConfig;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程定义节点同步服务单元测试。
 *
 * <p>被测对象为 {@link ProcessDefinitionNodeSyncService}，验证草稿节点同步(先删后插)、
 * BPMN 节点表单解析写入、实体状态映射同步，以及全节点类型解析持久化。</p>
 */
class ProcessDefinitionNodeSyncServiceTest {

    /**
     * 同步草稿节点时应先删除旧节点再插入新节点。
     *
     * <p>场景：传入一个 USER_TASK 节点 DTO，断言 deleteByProcessConfigId 被调用，
     * 插入的 NodeConfig 字段与 DTO 一致且 skipNode 为 true。</p>
     */
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

    /**
     * 从 BPMN 同步节点表单时应按顺序持久化多个实体表单 ID。
     *
     * <p>场景：BPMN 中 task-1 绑定 form-a 与 form-b，
     * 断言先删除旧记录再按 sortOrder 依次插入两条只读表单记录。</p>
     */
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

    /**
     * 从 BPMN 同步状态映射时应读取实体绑定并写入状态映射列表。
     *
     * <p>场景：流程绑定 expense 实体，BPMN 中连线 flow-1 携带 entityStatusCode，
     * 断言 entityFlowStatusService.saveStatusMappings 收到的映射字段正确。</p>
     */
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
        verify(entityFlowStatusService).replaceStatusMappings(
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

    /**
     * 解析并保存节点配置时应持久化所有受支持的节点类型。
     *
     * <p>场景：BPMN 包含全部 15 种节点类型，断言 insert 被调用 15 次，
     * 且节点类型列表与预期顺序一致。</p>
     */
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

    @Test
    void parseAndSaveNodeConfigsPropagatesInsertFailure() {
        NodeConfigMapper nodeMapper = mock(NodeConfigMapper.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(nodeMapper).insert(any(NodeConfig.class));
        ProcessDefinitionNodeSyncService service = service(nodeMapper, null, null, null, null, null, null, null);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.parseAndSaveNodeConfigs("process-1", bpmnWithEverySupportedNodeType()));

        assertEquals("database unavailable", error.getMessage());
    }

    @Test
    void parseAndSaveNodeConfigsPropagatesAssigneePersistenceFailure() {
        NodeConfigMapper nodeMapper = nodeMapperWithGeneratedIds();
        AssigneeConfigMapper assigneeMapper =
                mock(AssigneeConfigMapper.class);
        doThrow(new IllegalStateException("assignee insert failed"))
                .when(assigneeMapper).insert(
                        any(AssigneeConfig.class));
        ProcessDefinitionNodeSyncService service = service(
                nodeMapper,
                assigneeMapper,
                null,
                null,
                null,
                null,
                null,
                null);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.parseAndSaveNodeConfigs(
                        "process-1",
                        bpmnUserTask(
                                " flowable:assignee=\"admin\"",
                                "")));

        assertEquals(
                "解析执行人配置失败: nodeConfigId=node-0",
                error.getMessage());
    }

    @Test
    void parseAndSaveNodeConfigsPropagatesFormPersistenceFailure() {
        NodeConfigMapper nodeMapper = nodeMapperWithGeneratedIds();
        FormConfigMapper formMapper = mock(FormConfigMapper.class);
        doThrow(new IllegalStateException("form insert failed"))
                .when(formMapper).insert(
                        any(FormConfig.class));
        ProcessDefinitionNodeSyncService service = service(
                nodeMapper,
                null,
                formMapper,
                null,
                null,
                null,
                null,
                null);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.parseAndSaveNodeConfigs(
                        "process-1",
                        bpmnUserTask(
                                "",
                                "<flowable:entityFormId>"
                                        + "form-1"
                                        + "</flowable:entityFormId>")));

        assertEquals(
                "解析表单配置失败: nodeConfigId=node-0",
                error.getMessage());
    }

    @Test
    void parseAndSaveNodeConfigsPropagatesMultiInstanceMergeFailure() {
        NodeConfigMapper nodeMapper = nodeMapperWithGeneratedIds();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                "SELECT config_json FROM process_node_config WHERE id = ?",
                String.class,
                "node-0"))
                .thenThrow(new IllegalStateException("jdbc failed"));
        ProcessDefinitionNodeSyncService service = service(
                nodeMapper,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                jdbcTemplate);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.parseAndSaveNodeConfigs(
                        "process-1",
                        bpmnUserTask(
                                "",
                                "<bpmn:multiInstanceLoopCharacteristics "
                                        + "isSequential=\"false\" "
                                        + "flowable:collection=\"users\" "
                                        + "flowable:elementVariable=\"user\" />")));

        assertEquals(
                "解析多实例配置失败: nodeConfigId=node-0",
                error.getMessage());
    }

    @Test
    void parseAndSaveNodeConfigsRejectsMalformedApprovalConfig() {
        NodeConfigMapper nodeMapper = nodeMapperWithGeneratedIds();
        ProcessDefinitionNodeSyncService service = service(
                nodeMapper,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.parseAndSaveNodeConfigs(
                        "process-1",
                        bpmnUserTask(
                                "",
                                "<bpmn:extensionElements>"
                                        + "<flowable:properties>"
                                        + "<flowable:property "
                                        + "name=\"approvalConfig\" "
                                        + "value=\"{bad}\" />"
                                        + "</flowable:properties>"
                                        + "</bpmn:extensionElements>")));

        assertEquals(
                "解析审批配置失败: nodeConfigId=node-0",
                error.getMessage());
    }

    @Test
    void syncNodeFormsFromBpmnPropagatesPersistenceFailure() {
        ProcessNodeFormMapper nodeFormMapper = mock(ProcessNodeFormMapper.class);
        doThrow(new IllegalStateException("delete failed"))
                .when(nodeFormMapper).deleteByProcessConfigIdAndNodeId("process-1", "task-1");
        ProcessDefinitionNodeSyncService service = service(null, null, null, null, null, null, nodeFormMapper, null);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.syncNodeFormsFromBpmn("process-1", bpmnWithEntityFormIds()));

        assertEquals("同步节点表单配置失败: processConfigId=process-1", error.getMessage());
    }

    @Test
    void syncStatusMappingsFromBpmnClearsRemovedMappings() {
        EntityDefinitionMapper entityDefinitionMapper = mock(EntityDefinitionMapper.class);
        EntityFlowStatusService entityFlowStatusService = mock(EntityFlowStatusService.class);
        EntityDefinition entity = new EntityDefinition();
        entity.setEntityCode("expense");
        when(entityDefinitionMapper.findByProcessDefinitionId("process-1")).thenReturn(Optional.of(entity));
        ProcessDefinitionNodeSyncService service = service(null, null, null, null, entityFlowStatusService,
                entityDefinitionMapper, null, null);

        service.syncStatusMappingsFromBpmn("process-1", "expense_flow", bpmnWithoutStatusMapping());

        verify(entityFlowStatusService).replaceStatusMappings(
                "process-1", "expense_flow", "expense", List.of());
    }

    /**
     * 构造被测服务实例，按参数注入对应 mock 依赖。
     *
     * @param nodeMapper 节点配置 Mapper
     * @param assigneeMapper 审批人配置 Mapper
     * @param formMapper 表单配置 Mapper
     * @param objectMapper JSON 序列化器
     * @param entityFlowStatusService 实体流程状态服务
     * @param entityDefinitionMapper 实体定义 Mapper
     * @param nodeFormMapper 节点表单 Mapper
     * @param nodeApprovalMapper 节点审批 Mapper
     * @return 已组装的节点同步服务实例
     */
    private static ProcessDefinitionNodeSyncService service(NodeConfigMapper nodeMapper,
                                                           AssigneeConfigMapper assigneeMapper,
                                                           FormConfigMapper formMapper,
                                                           ObjectMapper objectMapper,
                                                           EntityFlowStatusService entityFlowStatusService,
                                                           EntityDefinitionMapper entityDefinitionMapper,
                                                           ProcessNodeFormMapper nodeFormMapper,
                                                           ProcessNodeApprovalMapper nodeApprovalMapper) {
        return service(
                nodeMapper,
                assigneeMapper,
                formMapper,
                objectMapper,
                entityFlowStatusService,
                entityDefinitionMapper,
                nodeFormMapper,
                nodeApprovalMapper,
                null);
    }

    private static ProcessDefinitionNodeSyncService service(
            NodeConfigMapper nodeMapper,
            AssigneeConfigMapper assigneeMapper,
            FormConfigMapper formMapper,
            ObjectMapper objectMapper,
            EntityFlowStatusService entityFlowStatusService,
            EntityDefinitionMapper entityDefinitionMapper,
            ProcessNodeFormMapper nodeFormMapper,
            ProcessNodeApprovalMapper nodeApprovalMapper,
            JdbcTemplate jdbcTemplate) {
        return new ProcessDefinitionNodeSyncService(nodeMapper, assigneeMapper, formMapper,
                objectMapper == null ? new ObjectMapper() : objectMapper,
                entityFlowStatusService, entityDefinitionMapper, nodeFormMapper, nodeApprovalMapper,
                null, jdbcTemplate);
    }

    private static NodeConfigMapper nodeMapperWithGeneratedIds() {
        NodeConfigMapper nodeMapper = mock(NodeConfigMapper.class);
        List<NodeConfig> insertedNodes = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            NodeConfig node = invocation.getArgument(0);
            node.setId("node-" + insertedNodes.size());
            insertedNodes.add(node);
            return 1;
        }).when(nodeMapper).insert(any(NodeConfig.class));
        when(nodeMapper.findByProcessConfigId("process-1"))
                .thenAnswer(invocation -> insertedNodes);
        return nodeMapper;
    }

    /** 构造包含多表单绑定扩展属性的 BPMN XML */
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

    /** 构造包含实体状态映射扩展属性的 BPMN XML */
    private static String bpmnWithStatusMapping() {
        return "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:flowable=\"http://flowable.org/bpmn\">"
                + "<bpmn:process id=\"expense_flow\">"
                + "<bpmn:startEvent id=\"start-1\" name=\"开始\" />"
                + "<bpmn:userTask id=\"task-1\" name=\"审批\" />"
                + "<bpmn:sequenceFlow targetRef=\"task-1\" id=\"flow-1\" sourceRef=\"start-1\">"
                + "<bpmn:extensionElements>"
                + "<flowable:properties>"
                + "<flowable:property value=\"approving\" name=\"entityStatusCode\" />"
                + "</flowable:properties>"
                + "</bpmn:extensionElements>"
                + "</bpmn:sequenceFlow>"
                + "</bpmn:process>"
                + "</bpmn:definitions>";
    }

    private static String bpmnWithoutStatusMapping() {
        return "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                + "<process id=\"expense_flow\">"
                + "<startEvent id=\"start-1\" name=\"开始\" />"
                + "<userTask id=\"task-1\" name=\"审批\" />"
                + "<sequenceFlow id=\"flow-1\" sourceRef=\"start-1\" targetRef=\"task-1\" />"
                + "</process>"
                + "</definitions>";
    }

    private static String bpmnUserTask(
            String attributes,
            String content) {
        return "<bpmn:definitions "
                + "xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" "
                + "xmlns:flowable=\"http://flowable.org/bpmn\">"
                + "<bpmn:process id=\"user_task_test\">"
                + "<bpmn:userTask id=\"user\""
                + attributes
                + ">"
                + content
                + "</bpmn:userTask>"
                + "</bpmn:process>"
                + "</bpmn:definitions>";
    }

    /** 构造包含全部受支持节点类型的 BPMN XML */
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
