package com.workflow.process.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.assignment.entity.EntityUserReferenceFieldConfig;
import com.workflow.process.assignment.relative.RelativeOrgPositionConfig;
import com.workflow.process.configuration.infrastructure.persistence.mapper.AssigneeConfigMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigMapper;
import com.workflow.process.configuration.infrastructure.persistence.record.AssigneeConfig;
import com.workflow.process.configuration.infrastructure.persistence.record.NodeConfig;
import com.workflow.process.definition.api.response.ProcessPublishPreviewDTO;
import com.workflow.process.definition.application.port.FlowActionDesignPort;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.common.engine.impl.util.io.StringStreamSource;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 覆盖节点面板所有指定方式及历史多人配置，确保预检只识别声明、不执行人员接口。 */
@ExtendWith(MockitoExtension.class)
class ProcessDefinitionAssignmentPreflightTest {

    @Mock private ProcessDefinitionConfigMapper processMapper;
    @Mock private ProcessVersionHistoryMapper versionMapper;
    @Mock private NodeConfigMapper nodeConfigMapper;
    @Mock private AssigneeConfigMapper assigneeConfigMapper;
    @Mock private FlowActionDesignPort actionDesignPort;
    @Mock private ProcessBpmnPublishSanitizer sanitizer;
    @Mock private ProcessPublishHistoryService publishHistoryService;
    @Mock private RuntimeService runtimeService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private ProcessDefinitionPreflightService service;
    private ProcessDefinitionConfig process;

    @BeforeEach
    void setUp() {
        process = new ProcessDefinitionConfig();
        process.setId("process-1");
        process.setProcessKey("expense_flow");
        process.setProcessName("费用审批");
    }

    /** 普通任务覆盖面板全部八种指定方式，JSON 与原生属性采用前端实际保存形式。 */
    @ParameterizedTest
    @ValueSource(strings = {"user", "group", "role", "expression", "interface",
            "entity_user_reference", "relative_position", "node_reference"})
    void acceptsEveryDesignerAssignmentMode(String mode) throws Exception {
        Map<String, Object> config = designerConfig(mode);
        process.setBpmnXml(xml(config, Map.of(), false, nativeAttributes(mode)));

        assertPublishable(service.preview(process));
    }

    /** 会签/或签复用七种可枚举来源，表达式在设计器中禁止用于多人办理。 */
    @ParameterizedTest
    @ValueSource(strings = {"user", "group", "role", "interface",
            "entity_user_reference", "relative_position", "node_reference"})
    void acceptsEveryMultiInstanceAssignmentMode(String mode) throws Exception {
        process.setBpmnXml(xml(designerConfig(mode), Map.of(), true, ""));

        assertPublishable(service.preview(process));
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "group", "role", "interface", "node_reference"})
    void generatedMultiInstanceVariablesDoNotHideMissingSources(String mode) throws Exception {
        process.setBpmnXml(xml(Map.of("assignmentConfigVersion", 2, "assigneeType", mode),
                Map.of(), true, ""));

        assertMissing(service.preview(process));
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "group", "role", "expression", "interface", "node_reference"})
    void staleResolverAndDisplayNamesCannotSatisfyAnotherAssignmentType(String mode) throws Exception {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("assignmentConfigVersion", 2);
        config.put("assigneeType", mode);
        config.put("resolverDisplayName", "历史接口名称");
        config.put("referencedNodeName", "历史审批节点名称");
        if (!"interface".equals(mode)) {
            config.put("resolverCode", "staleResolver");
        }
        process.setBpmnXml(xml(config, Map.of(), false, ""));

        assertMissing(service.preview(process));
    }

    @ParameterizedTest
    @CsvSource({"interface,interfaceName", "resolver,resolverCode", "nodeReference,sourceNodeId"})
    void supportsRuntimeCompatibleLegacyAliases(String type, String key) throws Exception {
        process.setBpmnXml(xml(Map.of("assigneeType", type, key, "SourceTask"), Map.of(), false, ""));

        assertPublishable(service.preview(process));
    }

    @ParameterizedTest
    @ValueSource(strings = {"multiInstanceUsers", "multiInstanceUserIds", "multiInstanceUsernames",
            "multiInstanceGroupIds", "multiInstanceGroupCodes", "multiInstanceRoleIds", "multiInstanceRoleCodes"})
    void recognizesLegacyMultiInstancePeopleFromSeparateProperty(String key) throws Exception {
        process.setBpmnXml(xml(Map.of("assigneeType", "interface"),
                Map.of(key, List.of("alice")), true, ""));

        assertPublishable(service.preview(process));
    }

    @ParameterizedTest
    @ValueSource(strings = {"collectionResolverCode", "collectionInterface"})
    void recognizesLegacyMultiInstanceResolvers(String key) throws Exception {
        process.setBpmnXml(xml(Map.of("assigneeType", "user"),
                Map.of("collectionSource", "interface", key, "legacyResolver"), true, ""));

        assertPublishable(service.preview(process));
    }

    @Test
    void incompleteLegacyResolverCannotBorrowValidBaseResolver() throws Exception {
        process.setBpmnXml(xml(Map.of("assigneeType", "interface", "resolverCode", "baseResolver"),
                Map.of("collectionSource", "interface"), true, ""));

        assertMissing(service.preview(process));
    }

    @Test
    void versionTwoIgnoresLegacyMultiInstanceParticipants() throws Exception {
        process.setBpmnXml(xml(Map.of("assignmentConfigVersion", 2, "assigneeType", "user"),
                Map.of("multiInstanceUsernames", List.of("alice")), true, ""));

        assertMissing(service.preview(process));
    }

    @Test
    void ordinaryTaskDoesNotUseResidualMultiInstanceSource() throws Exception {
        process.setBpmnXml(xml(Map.of("assigneeType", "interface"),
                Map.of("multiInstanceUsernames", List.of("alice")), false, ""));

        assertMissing(service.preview(process));
    }

    @Test
    void legacyMultiInstanceStillAcceptsLiteralBpmnUser() throws Exception {
        process.setBpmnXml(xml(Map.of("assigneeType", "user"), Map.of(), true, "")
                .replace("flowable:assignee=\"${assignee}\"", "flowable:assignee=\"alice\""));

        assertPublishable(service.preview(process));
    }

    @ParameterizedTest
    @CsvSource({"true,SCOPE,true", "true,RESOLVER,true", "false,SCOPE,false", "true,NODE_ASSIGNMENT,false"})
    void onlyEditableIndependentSourceMayOmitDefaultMultiInstancePeople(
            boolean editable, String type, boolean accepted) throws Exception {
        process.setBpmnXml(xml(Map.of("assignmentConfigVersion", 2, "assigneeType", "user",
                "nextApproverSelection", Map.of("visible", true, "editable", editable,
                        "source", Map.of("type", type))), Map.of(), true, ""));

        assertEquals(accepted, service.preview(process).publishable());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{broken", "[]", "null"})
    void invalidJsonIsReportedAtTheNodeEvenWithNativeAssignee(String document) throws Exception {
        process.setBpmnXml(xml(Map.of(), Map.of(), false, "flowable:assignee=\"alice\"")
                .replace("name=\"assigneeConfig\" value=\"{}\"",
                        "name=\"assigneeConfig\" value=\"" + escape(document) + "\""));

        var preview = service.preview(process);

        assertFalse(preview.publishable());
        assertTrue(preview.issues().stream().anyMatch(issue ->
                "USER_TASK_ASSIGNMENT_CONFIG_INVALID".equals(issue.code())
                        && "ApproveTask".equals(issue.elementId())));
        assertTrue(preview.issues().stream().noneMatch(issue -> "BPMN_XML_INVALID".equals(issue.code())));
    }

    @Test
    void clearedDraftDoesNotReuseStoredAssignees() throws Exception {
        process.setBpmnXml(xml(Map.of("assigneeType", "interface"), Map.of(), false, ""));
        NodeConfig node = storedNode();
        when(nodeConfigMapper.findByProcessConfigId(process.getId())).thenReturn(List.of(node));

        assertMissing(service.preview(process));
        verify(assigneeConfigMapper, never()).findByNodeConfigId(anyString());
    }

    @Test
    void legacyNodeWithoutJsonStillUsesStoredAssignment() throws Exception {
        process.setBpmnXml(xml(Map.of(), Map.of(), false, "")
                .replace(property("assigneeConfig", Map.of()), ""));
        NodeConfig node = storedNode();
        when(nodeConfigMapper.findByProcessConfigId(process.getId())).thenReturn(List.of(node));
        AssigneeConfig assignee = new AssigneeConfig();
        assignee.setAssigneeType(AssigneeConfig.AssigneeType.USER);
        assignee.setAssigneeValue("alice");
        when(assigneeConfigMapper.findByNodeConfigId(node.getId())).thenReturn(List.of(assignee));

        assertPublishable(service.preview(process));
    }

    /** 接上真实发布净化器验证接口目录/用途仍会校验，而人员解析方法始终不被调用。 */
    @ParameterizedTest
    @CsvSource({"false,true", "true,true", "false,false", "true,false"})
    void realSanitizerValidatesResolverAvailabilityWithoutResolvingPeople(
            boolean multiInstance, boolean available) throws Exception {
        PersonResolverRuntimeService resolverRuntime = mock(PersonResolverRuntimeService.class);
        ProcessBpmnPublishSanitizer realSanitizer = new ProcessBpmnPublishSanitizer(objectMapper);
        ReflectionTestUtils.setField(realSanitizer, "personResolverRuntimeService", resolverRuntime);
        service = new ProcessDefinitionPreflightService(processMapper, versionMapper, nodeConfigMapper,
                assigneeConfigMapper, actionDesignPort, realSanitizer, publishHistoryService,
                runtimeService, objectMapper);
        process.setBpmnXml(xml(designerConfig("interface"), Map.of(), multiInstance, ""));
        PersonResolveUsage usage = multiInstance ? PersonResolveUsage.MULTI_INSTANCE : PersonResolveUsage.ASSIGNEE;
        if (!available) {
            doThrow(new IllegalArgumentException("人员接口未启用"))
                    .when(resolverRuntime).requireConfigured("testResolver", usage);
        }

        var preview = service.preview(process);

        assertEquals(available, preview.publishable(), () -> preview.issues().toString());
        assertTrue(preview.issues().stream().noneMatch(issue -> "USER_TASK_ASSIGNEE_MISSING".equals(issue.code())));
        verify(resolverRuntime).requireConfigured("testResolver", usage);
        verify(resolverRuntime, never()).resolveUsernames(anyString(), any());
    }

    /** 自闭合普通节点与会签节点相邻时，各节点必须保留自己的人员与循环配置。 */
    @Test
    void sanitizingAdjacentTasksKeepsAssignmentsOnTheirOwnNodes() throws Exception {
        PersonResolverRuntimeService resolverRuntime = mock(PersonResolverRuntimeService.class);
        ProcessBpmnPublishSanitizer realSanitizer = new ProcessBpmnPublishSanitizer(objectMapper);
        ReflectionTestUtils.setField(realSanitizer, "personResolverRuntimeService", resolverRuntime);

        String published = realSanitizer.sanitize(
                xml(designerConfig("interface"), Map.of(), true, ""),
                process.getProcessKey(), process.getId());

        var model = new BpmnXMLConverter().convertToBpmnModel(new StringStreamSource(published), false, false);
        UserTask source = (UserTask) model.getMainProcess().getFlowElement("SourceTask");
        UserTask multi = (UserTask) model.getMainProcess().getFlowElement("ApproveTask");
        assertEquals("alice", source.getAssignee());
        assertFalse(source.hasMultiInstanceLoopCharacteristics());
        assertTrue(multi.hasMultiInstanceLoopCharacteristics());
        assertEquals("${assignee}", multi.getAssignee());
        verify(resolverRuntime).requireConfigured("testResolver", PersonResolveUsage.MULTI_INSTANCE);
        verify(resolverRuntime, never()).requireConfigured("testResolver", PersonResolveUsage.ASSIGNEE);
    }

    private NodeConfig storedNode() {
        NodeConfig node = new NodeConfig();
        node.setId("node-1");
        node.setNodeId("ApproveTask");
        return node;
    }

    private void assertPublishable(ProcessPublishPreviewDTO preview) {
        assertTrue(preview.publishable(), () -> preview.issues().toString());
    }

    private void assertMissing(ProcessPublishPreviewDTO preview) {
        assertFalse(preview.publishable());
        assertTrue(preview.issues().stream().anyMatch(issue ->
                "USER_TASK_ASSIGNEE_MISSING".equals(issue.code()) && "ApproveTask".equals(issue.elementId())));
    }

    private Map<String, Object> designerConfig(String mode) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("assignmentConfigVersion", 2);
        config.put("assigneeType", mode);
        switch (mode) {
            case "user" -> config.put("candidateUsers", "alice,bob");
            case "group", "role" -> config.put("assigneeValue", "finance");
            case "expression" -> config.put("assigneeValue", "${approvers}");
            case "node_reference" -> config.put("referencedNodeId", "SourceTask");
            default -> {
                // 两种内置业务来源由前端投影成统一 interface 契约。
                config.put("assigneeType", "interface");
                config.put("resolverCode", switch (mode) {
                    case "entity_user_reference" -> EntityUserReferenceFieldConfig.RESOLVER_CODE;
                    case "relative_position" -> RelativeOrgPositionConfig.RESOLVER_CODE;
                    default -> "testResolver";
                });
                config.put("extraParams", Map.of());
            }
        }
        return config;
    }

    private String nativeAttributes(String mode) {
        return switch (mode) {
            case "user" -> "flowable:candidateUsers=\"alice,bob\"";
            case "group" -> "flowable:candidateGroups=\"finance\"";
            case "role" -> "flowable:candidateGroups=\"ROLE_finance\"";
            case "expression" -> "flowable:assignee=\"${approver}\"";
            default -> "";
        };
    }

    private String xml(Map<String, Object> config, Map<String, Object> legacy,
            boolean multiInstance, String attributes) throws Exception {
        String loop = multiInstance ? """
                <bpmn:multiInstanceLoopCharacteristics isSequential="false"
                    flowable:collection="_wfMultiInstanceUsers_ApproveTask" flowable:elementVariable="assignee"/>
                """ : "";
        String props = property("assigneeConfig", config)
                + (legacy.isEmpty() ? "" : property("multiInstanceConfig", legacy));
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://workflow.test">
                  <bpmn:process id="expense_flow" isExecutable="true">
                    <bpmn:startEvent id="Start"/>
                    <bpmn:userTask id="SourceTask" flowable:assignee="alice"/>
                    <bpmn:userTask id="ApproveTask" %s>
                      <bpmn:extensionElements><flowable:properties>%s</flowable:properties></bpmn:extensionElements>
                      %s
                    </bpmn:userTask>
                    <bpmn:endEvent id="End"/>
                    <bpmn:sequenceFlow id="Flow1" sourceRef="Start" targetRef="SourceTask"/>
                    <bpmn:sequenceFlow id="Flow2" sourceRef="SourceTask" targetRef="ApproveTask"/>
                    <bpmn:sequenceFlow id="Flow3" sourceRef="ApproveTask" targetRef="End"/>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(multiInstance ? "flowable:assignee=\"${assignee}\"" : attributes, props, loop);
    }

    private String property(String name, Map<String, Object> value) throws Exception {
        return "<flowable:property name=\"" + name + "\" value=\""
                + escape(objectMapper.writeValueAsString(value)) + "\"/>";
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
