package com.workflow.process.assignment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.assignment.domain.AssigneeResolutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** BPMN 发布边界空办理人策略测试。 */
@ExtendWith(MockitoExtension.class)
class EmptyAssigneePolicyBpmnValidatorTest {

    @Mock private AssigneeResolutionService resolutionService;
    private EmptyAssigneePolicyBpmnValidator validator;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        validator = new EmptyAssigneePolicyBpmnValidator(
                objectMapper,
                new EmptyAssigneePolicyResolver(objectMapper),
                resolutionService);
    }

    @Test
    void acceptsValidFallbackIdentity() {
        when(resolutionService.resolvePrincipals(anyList(), anyString()))
                .thenReturn(AssigneeResolutionResult.resolved(
                        List.of("backup-user"), "test"));

        assertDoesNotThrow(() -> validator.validate(bpmn(
                jsonAttribute("""
                        {"policy":"FALLBACK_USER",
                         "fallbackUser":"backup-user",
                         "responsibilityOwner":"ops"}
                        """), "")));
    }

    @Test
    void rejectsBlockPolicyWhenStaticAssignmentIsEmpty() {
        when(resolutionService.resolvePrincipals(anyList(), anyString()))
                .thenReturn(AssigneeResolutionResult.empty(
                        "EMPTY", "无有效人员", "test"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(bpmn(
                        jsonAttribute("""
                                {"policy":"BLOCK_PUBLISH",
                                 "responsibilityOwner":"ops"}
                                """), "")));

        assertTrue(error.getMessage().contains(
                "EMPTY_ASSIGNEE_POLICY_INVALID [ApproveTask]"));
    }

    @Test
    void nodeIncidentOverrideAllowsDynamicEmptyResult() {
        String nodeConfig = jsonAttribute("""
                {"assigneeType":"resolver",
                 "emptyAssigneeStrategy":{
                   "policy":"CREATE_INCIDENT",
                   "responsibilityOwner":"ops"}}
                """);

        assertDoesNotThrow(() -> validator.validate(bpmn(
                jsonAttribute("""
                        {"policy":"BLOCK_PUBLISH",
                         "responsibilityOwner":"central-ops"}
                """), nodeConfig)));
    }

    @Test
    void blockPublishUsesLegacyStaticAssignmentFromMultiInstanceDocument() {
        when(resolutionService.resolvePrincipals(anyList(), anyString()))
                .thenReturn(AssigneeResolutionResult.resolved(
                        List.of("alice"), "test"));

        assertDoesNotThrow(() -> validator.validate(bpmn(
                jsonAttribute("""
                        {"policy":"BLOCK_PUBLISH",
                         "responsibilityOwner":"ops"}
                        """),
                "",
                jsonAttribute("""
                        {"multiInstanceUsernames":["alice"],
                         "multiInstanceGroupCodes":["finance"]}
                        """),
                true)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.workflow.contracts.identity.resolver.PersonPrincipal>>
                principals = ArgumentCaptor.forClass(List.class);
        verify(resolutionService).resolvePrincipals(
                principals.capture(), anyString());
        assertEquals(2, principals.getValue().size());
    }

    @Test
    void blockPublishDefersLegacyDynamicResolverToPublishSanitizer() {
        assertDoesNotThrow(() -> validator.validate(bpmn(
                jsonAttribute("""
                        {"policy":"BLOCK_PUBLISH",
                         "responsibilityOwner":"ops"}
                        """),
                "",
                jsonAttribute("""
                        {"collectionSource":"resolver",
                         "collectionResolverCode":"entityUserReferenceField",
                         "collectionExtraParams":{"fieldCode":"approver"}}
                        """),
                true)));

        verify(resolutionService, never()).resolvePrincipals(
                anyList(), anyString());
    }

    @Test
    void rejectsDoctypeToPreventExternalEntityExpansion() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "<!DOCTYPE foo [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>"
                        + bpmn("", "")));
    }

    private String bpmn(String processPolicy, String nodeConfig) {
        return bpmn(processPolicy, nodeConfig, "", false);
    }

    private String bpmn(
            String processPolicy,
            String nodeConfig,
            String multiInstanceConfig,
            boolean multiInstance) {
        String processExtension = processPolicy.isBlank() ? "" : """
                <bpmn:extensionElements><flowable:properties>
                  <flowable:property name="emptyAssigneeDefault" value="%s"/>
                </flowable:properties></bpmn:extensionElements>
                """.formatted(processPolicy);
        String assigneeProperty = nodeConfig.isBlank() ? "" : """
                  <flowable:property name="assigneeConfig" value="%s"/>
                """.formatted(nodeConfig);
        String multiInstanceProperty = multiInstanceConfig.isBlank()
                ? "" : """
                  <flowable:property name="multiInstanceConfig" value="%s"/>
                """.formatted(multiInstanceConfig);
        String nodeExtension = assigneeProperty.isBlank()
                && multiInstanceProperty.isBlank() ? "" : """
                <bpmn:extensionElements><flowable:properties>
                  %s
                  %s
                </flowable:properties></bpmn:extensionElements>
                """.formatted(assigneeProperty, multiInstanceProperty);
        String loop = multiInstance ? """
                <bpmn:multiInstanceLoopCharacteristics
                  flowable:collection="${reviewers}"
                  flowable:elementVariable="reviewer"/>
                """ : "";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn">
                  <bpmn:process id="expense" isExecutable="true">
                    %s
                    <bpmn:userTask id="ApproveTask">%s%s</bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(processExtension, nodeExtension, loop);
    }

    private String jsonAttribute(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
