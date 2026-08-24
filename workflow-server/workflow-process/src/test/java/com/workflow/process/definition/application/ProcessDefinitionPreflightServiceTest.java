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
}
