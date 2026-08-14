package com.workflow.process.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class NextApproverPublishValidationTest {

    private final ProcessBpmnPublishSanitizer sanitizer =
            new ProcessBpmnPublishSanitizer(new ObjectMapper());

    @Test
    void rejectsInvalidEnabledSelectionBeforeDeployment() {
        IllegalArgumentException visibility = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(wrap(userTask(
                        "review",
                        """
                        {"nextApproverSelection":{"version":1,
                        "visible":false,"editable":true,
                        "source":{"type":"SCOPE","rules":[]}}}
                        """,
                        "")), "runtime_process"));
        assertTrue(visibility.getMessage().contains(
                "editable=true 时 visible 必须为 true"));

        IllegalArgumentException scope = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(wrap(userTask(
                        "review",
                        """
                        {"nextApproverSelection":{"version":1,
                        "visible":true,"editable":true,
                        "source":{"type":"SCOPE","rules":[]}}}
                        """,
                        "")), "runtime_process"));
        assertTrue(scope.getMessage().contains(
                "SCOPE source.rules 不能为空"));
    }

    @Test
    void allowsDisabledEmptyScopeButRejectsUnknownVersion() {
        assertDoesNotThrow(() -> sanitizer.sanitize(wrap(userTask(
                "hidden-review",
                """
                {"nextApproverSelection":{"version":1,
                "visible":false,"editable":false,
                "source":{"type":"SCOPE","rules":[]}}}
                """,
                "")), "runtime_process"));

        IllegalArgumentException version = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(wrap(userTask(
                        "future-review",
                        """
                        {"nextApproverSelection":{"version":2,
                        "visible":false,"editable":false}}
                        """,
                        "")), "runtime_process"));
        assertTrue(version.getMessage().contains("不支持的配置版本: 2"));
    }

    @Test
    void rejectsEditableMultiInstanceNodesSharingCollection() {
        String config = """
                {"nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "source":{"type":"SCOPE","rules":[
                {"type":"ALL_USERS","values":[]}]}}}
                """;
        String loop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${sharedApprovers}\" "
                + "flowable:elementVariable=\"approver\" />";

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(wrap(
                        userTask("review-a", config, loop)
                                + userTask("review-b", config, loop)),
                        "runtime_process"));

        assertTrue(error.getMessage().contains(
                "不能共用 collection"));
    }

    @Test
    void rejectsResolverThatIsNotConfiguredForCandidateUsage() {
        ProcessBpmnPublishSanitizer guardedSanitizer =
                new ProcessBpmnPublishSanitizer(new ObjectMapper());
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        doThrow(new IllegalArgumentException(
                "人员接口未配置、未启用或不支持用途: financeResolver"))
                .when(resolverRuntimeService)
                .requireConfigured(
                        "financeResolver",
                        PersonResolveUsage.CANDIDATE);
        ReflectionTestUtils.setField(
                guardedSanitizer,
                "personResolverRuntimeService",
                resolverRuntimeService);
        String config = """
                {"nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "source":{"type":"RESOLVER",
                "resolverCode":"financeResolver","extraParams":{}}}}
                """;

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> guardedSanitizer.sanitize(
                        wrap(userTask("review", config, "")),
                        "runtime_process"));

        assertTrue(error.getMessage().contains("RESOLVER 不可用"));
        assertTrue(error.getMessage().contains("financeResolver"));
    }

    @Test
    void resolverSelectionFailsClosedWhenCatalogValidationIsUnavailable() {
        String config = """
                {"nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "source":{"type":"RESOLVER",
                "resolverCode":"financeResolver","extraParams":{}}}}
                """;

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap(userTask("review", config, "")),
                        "runtime_process"));

        assertTrue(error.getMessage().contains(
                "RESOLVER 校验服务不可用"));
    }

    @Test
    void hiddenSelectionStillRejectsUnknownSourceAndRuleTypes() {
        IllegalArgumentException source = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(wrap(userTask(
                        "hidden-source",
                        """
                        {"nextApproverSelection":{"version":1,
                        "visible":false,"editable":false,
                        "source":{"type":"UNTRUSTED_SOURCE"}}}
                        """,
                        "")), "runtime_process"));
        assertTrue(source.getMessage().contains(
                "source.type 仅支持 SCOPE 或 RESOLVER"));

        IllegalArgumentException rule = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(wrap(userTask(
                        "hidden-rule",
                        """
                        {"nextApproverSelection":{"version":1,
                        "visible":false,"editable":false,
                        "source":{"type":"SCOPE","rules":[
                        {"type":"UNTRUSTED_RULE","values":[]}]}}}
                        """,
                        "")), "runtime_process"));
        assertTrue(rule.getMessage().contains(
                "不支持的 SCOPE rule.type: UNTRUSTED_RULE"));
    }

    @Test
    void rejectsEditableMultiInstanceWithNonVariableCollection() {
        String config = """
                {"nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "source":{"type":"SCOPE","rules":[
                {"type":"ALL_USERS","values":[]}]}}}
                """;
        String loop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${approval.people}\" "
                + "flowable:elementVariable=\"approver\" />";

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(wrap(userTask(
                        "review", config, loop)), "runtime_process"));

        assertTrue(error.getMessage().contains(
                "collection 必须是简单流程变量"));
    }

    @Test
    void rejectsEditableMultiInstanceSharingCollectionWithHiddenNode() {
        String editable = """
                {"nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "source":{"type":"SCOPE","rules":[
                {"type":"ALL_USERS","values":[]}]}}}
                """;
        String hidden = """
                {"nextApproverSelection":{"version":1,
                "visible":false,"editable":false,
                "source":{"type":"SCOPE","rules":[]}}}
                """;
        String loop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${sharedApprovers}\" "
                + "flowable:elementVariable=\"approver\" />";

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(wrap(
                        userTask("editable-review", editable, loop)
                                + userTask(
                                "hidden-review", hidden, loop)),
                        "runtime_process"));

        assertTrue(error.getMessage().contains("不能共用 collection"));
    }

    private String userTask(
            String id,
            String assigneeConfig,
            String body) {
        return "<bpmn:userTask id=\""
                + id
                + "\">"
                + "<bpmn:extensionElements><flowable:properties>"
                + "<flowable:property name=\"assigneeConfig\" value=\""
                + escape(assigneeConfig.trim())
                + "\" />"
                + "</flowable:properties></bpmn:extensionElements>"
                + body
                + "</bpmn:userTask>";
    }

    private String wrap(String elements) {
        return """
                <bpmn:definitions
                  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn"
                  targetNamespace="http://workflow.test/process">
                  <bpmn:process id="draft_process" isExecutable="true">
                    %s
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(elements);
    }

    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
