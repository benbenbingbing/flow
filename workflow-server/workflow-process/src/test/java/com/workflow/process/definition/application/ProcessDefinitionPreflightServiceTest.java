package com.workflow.process.definition.application;

import com.workflow.contracts.action.FlowActionDesignPort;
import com.workflow.process.assignment.application.EmptyAssigneePolicyBpmnValidator;
import com.workflow.process.configuration.infrastructure.persistence.mapper.AssigneeConfigMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigMapper;
import com.workflow.process.definition.api.response.ProcessPublishPreviewDTO;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 流程发布预检服务测试。 */
@ExtendWith(MockitoExtension.class)
class ProcessDefinitionPreflightServiceTest {

    @Mock private ProcessDefinitionConfigMapper processMapper;
    @Mock private ProcessVersionHistoryMapper versionMapper;
    @Mock private NodeConfigMapper nodeConfigMapper;
    @Mock private AssigneeConfigMapper assigneeConfigMapper;
    @Mock private FlowActionDesignPort actionDesignPort;
    @Mock private ProcessBpmnPublishSanitizer sanitizer;
    @Mock private ProcessPublishHistoryService publishHistoryService;
    @Mock private RuntimeService runtimeService;
    @Mock private ProcessInstanceQuery processInstanceQuery;
    @Mock private EmptyAssigneePolicyBpmnValidator emptyAssigneePolicyValidator;

    private ProcessDefinitionPreflightService service;
    private ProcessDefinitionConfig process;

    @BeforeEach
    void setUp() {
        service = new ProcessDefinitionPreflightService(
                processMapper, versionMapper, nodeConfigMapper, assigneeConfigMapper,
                actionDesignPort, sanitizer, publishHistoryService, runtimeService);
        service.setEmptyAssigneePolicyBpmnValidator(
                emptyAssigneePolicyValidator);
        process = new ProcessDefinitionConfig();
        process.setId("process-1");
        process.setProcessKey("expense_flow");
        process.setProcessName("费用审批");
        process.setDraftRevision(4L);
        process.setPublishedRevision(3L);
        process.setBasePublishedVersion(2);

        when(versionMapper.findByProcessConfigId("process-1")).thenReturn(List.of());
        when(nodeConfigMapper.findByProcessConfigId("process-1")).thenReturn(List.of());
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processDefinitionKey("expense_flow")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.active()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.count()).thenReturn(3L);
        lenient().when(sanitizer.sanitize(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** 验证合法流程可发布，并返回稳定 revision、影响统计和令牌。 */
    @Test
    void validProcessProducesPublishablePreview() {
        process.setBpmnXml(validXml());

        ProcessPublishPreviewDTO first = service.preview(process);
        ProcessPublishPreviewDTO second = service.preview(process);

        assertTrue(first.publishable());
        assertEquals(0, first.blockerCount());
        assertEquals(4L, first.revision());
        assertEquals(3L, first.activeInstanceCount());
        assertEquals(64, first.previewToken().length());
        assertEquals(first.previewToken(), second.previewToken());
        verify(emptyAssigneePolicyValidator, times(2))
                .validate(process.getBpmnXml());
    }

    @Test
    void emptyAssigneePolicyFailureBlocksPreflight() {
        process.setBpmnXml(validXml());
        doThrow(new IllegalArgumentException(
                "EMPTY_ASSIGNEE_POLICY_INVALID [ApproveTask]"))
                .when(emptyAssigneePolicyValidator)
                .validate(process.getBpmnXml());

        ProcessPublishPreviewDTO preview = service.preview(process);

        assertFalse(preview.publishable());
        assertTrue(preview.issues().stream().anyMatch(issue ->
                issue.message().contains("EMPTY_ASSIGNEE_POLICY_INVALID")));
    }

    /** 多流程协作图的主流程歧义应转换为稳定、可定位的发布阻断码。 */
    @Test
    void ambiguousExecutableProcessesProduceStableBlocker() {
        process.setBpmnXml(validXml());
        doThrow(new IllegalArgumentException(
                "BPMN_EXECUTABLE_PROCESS_AMBIGUOUS: 多流程协作图必须且只能包含一个主流程"))
                .when(sanitizer)
                .sanitize(process.getBpmnXml(), process.getProcessKey());

        ProcessPublishPreviewDTO preview = service.preview(process);

        assertFalse(preview.publishable());
        assertTrue(preview.issues().stream().anyMatch(issue ->
                issue.code().equals("BPMN_EXECUTABLE_PROCESS_AMBIGUOUS")
                        && issue.blocking()));
    }

    /** 未命名数据对象应由共享发布清洗器转换为稳定、可定位的预检阻断。 */
    @Test
    void unnamedDataObjectProducesStableBlocker() {
        process.setBpmnXml(validXml());
        doThrow(new IllegalArgumentException(
                "BPMN_DATA_OBJECT_NAME_MISSING: 数据对象必须配置名称, "
                        + "element=DataObjectReference_1"))
                .when(sanitizer)
                .sanitize(process.getBpmnXml(), process.getProcessKey());

        ProcessPublishPreviewDTO preview = service.preview(process);

        assertFalse(preview.publishable());
        assertTrue(preview.issues().stream().anyMatch(issue ->
                issue.code().equals("BPMN_DATA_OBJECT_NAME_MISSING")
                        && "DataObjectReference_1".equals(issue.elementId())
                        && issue.blocking()));
    }

    /** 数据存储和普通任务数据关联应提示能力边界，但不能阻断合法流程发布。 */
    @Test
    void modelingOnlyDataComponentsProduceNonBlockingWarnings() {
        process.setBpmnXml(modelingOnlyDataComponentsXml());

        ProcessPublishPreviewDTO preview = service.preview(process);

        assertTrue(preview.publishable());
        assertEquals(0, preview.blockerCount());
        assertEquals(3, preview.warningCount());
        assertTrue(preview.issues().stream().anyMatch(issue ->
                issue.code().equals("BPMN_DATA_STORE_MODEL_ONLY")
                        && "DataStoreReference_1".equals(issue.elementId())
                        && issue.message().contains("不会自动持久化")
                        && !issue.blocking()));
        assertTrue(preview.issues().stream().anyMatch(issue ->
                issue.code().equals("BPMN_DATA_ASSOCIATION_MODEL_ONLY")
                        && "DataInputAssociation_1".equals(issue.elementId())
                        && issue.message().contains("不会自动映射流程变量")
                        && !issue.blocking()));
        assertTrue(preview.issues().stream().anyMatch(issue ->
                issue.code().equals("BPMN_DATA_ASSOCIATION_MODEL_ONLY")
                        && "DataOutputAssociation_1".equals(issue.elementId())
                        && !issue.blocking()));
    }

    /** 验证无办理人和无默认分支都定位为阻断问题。 */
    @Test
    void invalidGatewayAndAssignmentAreBlocking() {
        process.setBpmnXml(invalidXml());

        ProcessPublishPreviewDTO preview = service.preview(process);

        assertFalse(preview.publishable());
        assertTrue(preview.issues().stream().anyMatch(issue ->
                issue.code().equals("USER_TASK_ASSIGNEE_MISSING")
                        && "ApproveTask".equals(issue.elementId())));
        assertTrue(preview.issues().stream().anyMatch(issue ->
                issue.code().equals("GATEWAY_DEFAULT_FLOW_MISSING")
                        && "Decision".equals(issue.elementId())));
        assertTrue(preview.issues().stream().allMatch(issue -> issue.fixRoute() != null));
    }

    private String validXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn">
                  <bpmn:process id="expense_flow" isExecutable="true">
                    <bpmn:startEvent id="Start"/>
                    <bpmn:userTask id="ApproveTask" flowable:assignee="admin"/>
                    <bpmn:endEvent id="End"/>
                    <bpmn:sequenceFlow id="Flow1" sourceRef="Start" targetRef="ApproveTask"/>
                    <bpmn:sequenceFlow id="Flow2" sourceRef="ApproveTask" targetRef="End"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
    }

    private String invalidXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="expense_flow" isExecutable="true">
                    <bpmn:startEvent id="Start"/>
                    <bpmn:userTask id="ApproveTask"/>
                    <bpmn:exclusiveGateway id="Decision"/>
                    <bpmn:endEvent id="Approved"/>
                    <bpmn:endEvent id="Rejected"/>
                    <bpmn:sequenceFlow id="Flow1" sourceRef="Start" targetRef="ApproveTask"/>
                    <bpmn:sequenceFlow id="Flow2" sourceRef="ApproveTask" targetRef="Decision"/>
                    <bpmn:sequenceFlow id="Flow3" sourceRef="Decision" targetRef="Approved">
                      <bpmn:conditionExpression>${approved == true}</bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                    <bpmn:sequenceFlow id="Flow4" sourceRef="Decision" targetRef="Rejected">
                      <bpmn:conditionExpression>${approved == false}</bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                  </bpmn:process>
                </bpmn:definitions>
                """;
    }

    private String modelingOnlyDataComponentsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn">
                  <bpmn:dataStore id="DataStore_1" name="共享业务数据"/>
                  <bpmn:process id="expense_flow" isExecutable="true">
                    <bpmn:dataStoreReference id="DataStoreReference_1"
                                             name="共享业务数据存储"
                                             dataStoreRef="DataStore_1"/>
                    <bpmn:dataObject id="DataObject_Input" name="输入数据"/>
                    <bpmn:dataObjectReference id="DataObjectReference_Input"
                                              name="输入数据"
                                              dataObjectRef="DataObject_Input"/>
                    <bpmn:dataObject id="DataObject_Output" name="输出数据"/>
                    <bpmn:dataObjectReference id="DataObjectReference_Output"
                                              name="输出数据"
                                              dataObjectRef="DataObject_Output"/>
                    <bpmn:startEvent id="Start"/>
                    <bpmn:serviceTask id="MappedTask">
                      <bpmn:ioSpecification>
                        <bpmn:dataInput id="DataInput_1" name="input"/>
                        <bpmn:dataOutput id="DataOutput_1" name="output"/>
                        <bpmn:inputSet id="InputSet_1">
                          <bpmn:dataInputRefs>DataInput_1</bpmn:dataInputRefs>
                        </bpmn:inputSet>
                        <bpmn:outputSet id="OutputSet_1">
                          <bpmn:dataOutputRefs>DataOutput_1</bpmn:dataOutputRefs>
                        </bpmn:outputSet>
                      </bpmn:ioSpecification>
                      <bpmn:dataInputAssociation id="DataInputAssociation_1">
                        <bpmn:sourceRef>DataObjectReference_Input</bpmn:sourceRef>
                        <bpmn:targetRef>DataInput_1</bpmn:targetRef>
                      </bpmn:dataInputAssociation>
                      <bpmn:dataOutputAssociation id="DataOutputAssociation_1">
                        <bpmn:sourceRef>DataOutput_1</bpmn:sourceRef>
                        <bpmn:targetRef>DataObjectReference_Output</bpmn:targetRef>
                      </bpmn:dataOutputAssociation>
                    </bpmn:serviceTask>
                    <bpmn:endEvent id="End"/>
                    <bpmn:sequenceFlow id="Flow1" sourceRef="Start" targetRef="MappedTask"/>
                    <bpmn:sequenceFlow id="Flow2" sourceRef="MappedTask" targetRef="End"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
    }
}
