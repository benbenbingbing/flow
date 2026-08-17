package com.workflow.process.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @ParameterizedTest
    @CsvSource({
            "show,allowModify",
            "display,allowEdit"
    })
    void legacyBooleanAliasesActivateTheSamePublishValidation(
            String visibleAlias,
            String editableAlias) {
        String config = """
                {"nextApproverSelection":{"version":1,
                "%s":true,"%s":true,
                "source":{"type":"SCOPE","rules":[]}}}
                """.formatted(visibleAlias, editableAlias);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap(userTask("legacy-alias", config, "")),
                        "runtime_process"));

        assertTrue(error.getMessage().contains(
                "SCOPE source.rules 不能为空"));
    }

    @Test
    void canonicalBooleanKeysTakePrecedenceOverLegacyAliases() {
        String config = """
                {"nextApproverSelection":{"version":1,
                "visible":false,"show":true,
                "editable":true,"allowModify":false,
                "source":{"type":"SCOPE","rules":[
                {"type":"ALL_USERS","values":[]}]}}}
                """;

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap(userTask("alias-priority", config, "")),
                        "runtime_process"));

        assertTrue(error.getMessage().contains(
                "editable=true 时 visible 必须为 true"));
    }

    @Test
    void acceptsLegacyFlatAndNestedScopeAliases() {
        String flat = """
                {"nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "sourceType":"SCOPE","scopeRules":[
                {"type":"USER","values":["alice"]}]}}
                """;
        String nested = """
                {"nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "source":{"type":"SCOPE","scopes":[
                {"type":"ROLE","values":["manager"]}]}}}
                """;
        String flatScope = """
                {"nextApproverSelection":{"version":"1",
                "visible":true,"editable":true,
                "source":"SCOPE","scopeType":"ORGANIZATION",
                "scopeValues":"finance","includeChildren":"true"}}
                """;

        assertDoesNotThrow(() -> sanitizer.sanitize(
                wrap(userTask("flat-scope", flat, "")
                        + userTask("nested-scope", nested, "")
                        + userTask("single-scope", flatScope, "")),
                "runtime_process"));
    }

    @Test
    void acceptsLegacyFlatResolverAliasesWithCandidateUsage() {
        ProcessBpmnPublishSanitizer guardedSanitizer =
                new ProcessBpmnPublishSanitizer(new ObjectMapper());
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        ReflectionTestUtils.setField(
                guardedSanitizer,
                "personResolverRuntimeService",
                resolverRuntimeService);
        String config = """
                {"nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "sourceType":"RESOLVER",
                "interfaceName":"financeResolver",
                "extraParams":{"region":"east"}}}
                """;

        assertDoesNotThrow(() -> guardedSanitizer.sanitize(
                wrap(userTask("flat-resolver", config, "")),
                "runtime_process"));
        verify(resolverRuntimeService).requireConfigured(
                "financeResolver",
                PersonResolveUsage.CANDIDATE);
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

    @Test
    void rejectsVersionTwoMultiInstanceExpressionAssignment() {
        String config = """
                {"assignmentConfigVersion":2,
                "assigneeType":"expression",
                "assigneeValue":"${owner}",
                "candidateUsers":"${reviewers}",
                "nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "source":{"type":"NODE_ASSIGNMENT"}}}
                """;
        String loop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${_wfMultiInstanceUsers_review}\" "
                + "flowable:elementVariable=\"approver\" />";

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap(userTask("review", config, loop)),
                        "runtime_process"));

        assertTrue(error.getMessage().contains("多实例"));
        assertTrue(error.getMessage().contains("表达式"));
    }

    @Test
    void rejectsEmptyVersionTwoMultiInstanceUsersDespiteTechnicalAssignee() {
        String config = """
                {"assignmentConfigVersion":2,
                "assigneeType":"user",
                "assigneeValue":"",
                "candidateUsers":"",
                "nextApproverSelection":{"version":1,
                "visible":false,"editable":false,
                "source":{"type":"NODE_ASSIGNMENT"}}}
                """;
        String loop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${_wfMultiInstanceUsers_review}\" "
                + "flowable:elementVariable=\"reviewer\" />";
        String task = userTask("review", config, loop).replace(
                "<bpmn:userTask id=\"review\">",
                "<bpmn:userTask id=\"review\" "
                        + "flowable:assignee=\"${reviewer}\">");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap(task),
                        "runtime_process"));

        assertTrue(error.getMessage().contains("基础固定人员配置不能为空"));
    }

    @Test
    void allowsEditableVersionTwoMultiInstanceWithoutDefaultForIndependentScope() {
        String config = """
                {"assignmentConfigVersion":2,
                "assigneeType":"user",
                "assigneeValue":"",
                "candidateUsers":"",
                "nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "source":{"type":"SCOPE","rules":[
                {"type":"USER","values":["alice"]}]}}}
                """;
        String loop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${_wfMultiInstanceUsers_review}\" "
                + "flowable:elementVariable=\"reviewer\" />";
        String task = userTask("review", config, loop).replace(
                "<bpmn:userTask id=\"review\">",
                "<bpmn:userTask id=\"review\" "
                        + "flowable:assignee=\"${reviewer}\">");

        assertDoesNotThrow(() -> sanitizer.sanitize(
                wrap(task), "runtime_process"));
    }

    @Test
    void rejectsWhitespaceOnlyVersionTwoMultiInstanceUserArray() {
        String config = """
                {"assignmentConfigVersion":2,
                "assigneeType":"user",
                "assigneeValue":["", "   "],
                "candidateUsers":[],
                "nextApproverSelection":{"version":1,
                "visible":false,"editable":false,
                "source":{"type":"NODE_ASSIGNMENT"}}}
                """;
        String loop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${_wfMultiInstanceUsers_review}\" "
                + "flowable:elementVariable=\"reviewer\" />";

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap(userTask("review", config, loop)),
                        "runtime_process"));

        assertTrue(error.getMessage().contains("基础固定人员配置不能为空"));
    }

    @Test
    void legacyNodeAssignmentValidatesCollectionResolverWithMultiInstanceUsage() {
        ProcessBpmnPublishSanitizer guardedSanitizer =
                new ProcessBpmnPublishSanitizer(new ObjectMapper());
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        ReflectionTestUtils.setField(
                guardedSanitizer,
                "personResolverRuntimeService",
                resolverRuntimeService);
        String config = """
                {"assigneeType":"resolver",
                "resolverCode":"staleBaseResolver",
                "collectionSource":"resolver",
                "collectionResolverCode":"legacyJointResolver",
                "collectionExtraParams":{},
                "nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "source":{"type":"NODE_ASSIGNMENT"}}}
                """;
        String loop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${legacyReviewers}\" "
                + "flowable:elementVariable=\"reviewer\" />";

        assertDoesNotThrow(() -> guardedSanitizer.sanitize(
                wrap(userTask("review", config, loop)),
                "runtime_process"));

        verify(resolverRuntimeService).requireConfigured(
                "legacyJointResolver",
                PersonResolveUsage.MULTI_INSTANCE);
        verify(resolverRuntimeService, never()).requireConfigured(
                "staleBaseResolver",
                PersonResolveUsage.ASSIGNEE);
        verify(resolverRuntimeService, never()).requireConfigured(
                "legacyJointResolver",
                PersonResolveUsage.CANDIDATE);
    }

    @Test
    void legacyNodeAssignmentAcceptsMixedStaticMultiInstanceUsers() {
        String config = """
                {"multiInstanceUsernames":"alice",
                "multiInstanceGroupCodes":"finance",
                "multiInstanceRoleCodes":"manager",
                "nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "source":{"type":"NODE_ASSIGNMENT"}}}
                """;
        String loop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${legacyReviewers}\" "
                + "flowable:elementVariable=\"reviewer\" />";

        assertDoesNotThrow(() -> sanitizer.sanitize(
                wrap(userTask("review", config, loop)),
                "runtime_process"));
    }

    @Test
    void legacyNodeAssignmentAcceptsIdsAndOldMixedPeopleField() {
        String config = """
                {"collectionSource":"variable",
                "collectionResolverCode":"staleResolver",
                "multiInstanceUserIds":"user-1",
                "multiInstanceGroupIds":"group-1",
                "multiInstanceRoleIds":"role-1",
                "multiInstanceUsers":"alice,ROLE_AUDITOR",
                "nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "source":{"type":"NODE_ASSIGNMENT"}}}
                """;
        String loop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${legacyReviewers}\" "
                + "flowable:elementVariable=\"reviewer\" />";

        assertDoesNotThrow(() -> sanitizer.sanitize(
                wrap(userTask("review", config, loop)),
                "runtime_process"));
    }

    @Test
    void blankLegacyPassthroughFallsBackToBaseAssignmentValidation() {
        String config = """
                {"assigneeType":"user",
                "assigneeValue":"alice",
                "multiInstanceUserIds":["", "   "],
                "multiInstanceGroupIds":"",
                "multiInstanceRoleIds":"",
                "collectionSource":"variable",
                "collectionResolverCode":"staleResolver",
                "nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "source":{"type":"NODE_ASSIGNMENT"}}}
                """;
        String loop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${legacyReviewers}\" "
                + "flowable:elementVariable=\"reviewer\" />";

        assertDoesNotThrow(() -> sanitizer.sanitize(
                wrap(userTask("review", config, loop)),
                "runtime_process"));
    }

    @Test
    void legacyMultiInstanceDocumentParticipatesInNodeAssignmentValidation() {
        String assigneeConfig = """
                {"nextApproverSelection":{"version":1,
                "visible":true,"editable":true,
                "source":{"type":"NODE_ASSIGNMENT"}}}
                """;
        String multiInstanceConfig = """
                {"multiInstanceUserIds":"user-1",
                "multiInstanceUsers":"alice,ROLE_AUDITOR"}
                """;
        String loop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${legacyReviewers}\" "
                + "flowable:elementVariable=\"reviewer\" />";

        assertDoesNotThrow(() -> sanitizer.sanitize(
                wrap(userTaskWithMultiConfig(
                        "review",
                        assigneeConfig,
                        multiInstanceConfig,
                        loop)),
                "runtime_process"));
    }

    @Test
    void acceptsDirectAndChainedNodeReferencesWithinTheSameProcess() {
        String current = referenceConfig("middle");
        String middle = """
                {"assignmentConfigVersion":2,
                 "assigneeType":"nodeReference",
                 "sourceNodeId":"source"}
                """;
        String source = """
                {"assignmentConfigVersion":2,
                 "assigneeType":"user",
                 "assigneeValue":"alice"}
                """;

        assertDoesNotThrow(() -> sanitizer.sanitize(
                wrap(userTask("current", current, "")
                        + userTask("middle", middle, "")
                        + userTask("source", source, "")),
                "runtime_process"));
    }

    @Test
    void acceptsReferenceToLegacyLiteralBpmnAssignment() {
        String legacySource = userTask("legacy-source", "{}", "")
                .replace(
                        "<bpmn:userTask id=\"legacy-source\">",
                        "<bpmn:userTask id=\"legacy-source\" "
                                + "flowable:assignee=\"alice\">");

        assertDoesNotThrow(() -> sanitizer.sanitize(
                wrap(userTask(
                        "current", referenceConfig("legacy-source"), "")
                        + legacySource),
                "runtime_process"));
    }

    @Test
    void rejectsSelfCycleMissingAndNonUserNodeReferences() {
        IllegalArgumentException self = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap(userTask(
                                "self", referenceConfig("self"), "")),
                        "runtime_process"));
        assertTrue(self.getMessage().contains("形成环"));

        IllegalArgumentException cycle = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap(userTask(
                                "first", referenceConfig("second"), "")
                                + userTask(
                                "second", referenceConfig("first"), "")),
                        "runtime_process"));
        assertTrue(cycle.getMessage().contains("形成环"));

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap(userTask(
                                "missing", referenceConfig("unknown"), "")),
                        "runtime_process"));
        assertTrue(missing.getMessage().contains("引用目标不存在"));

        String service = "<bpmn:serviceTask id=\"service\" "
                + "flowable:class=\"java.lang.String\" />";
        IllegalArgumentException nonUser = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap(userTask(
                                "wrong-type",
                                referenceConfig("service"), "")
                                + service),
                        "runtime_process"));
        assertTrue(nonUser.getMessage().contains("不是 UserTask"));
    }

    @Test
    void validatesReferencedResolverUsingTheReferencingNodeMode() {
        ProcessBpmnPublishSanitizer guardedSanitizer =
                new ProcessBpmnPublishSanitizer(new ObjectMapper());
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        ReflectionTestUtils.setField(
                guardedSanitizer,
                "personResolverRuntimeService",
                resolverRuntimeService);
        String source = """
                {"assignmentConfigVersion":2,
                 "assigneeType":"resolver",
                 "resolverCode":"sharedResolver"}
                """;
        String loop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${jointUsers}\" "
                + "flowable:elementVariable=\"reviewer\" />";

        assertDoesNotThrow(() -> guardedSanitizer.sanitize(
                wrap(userTask(
                        "direct-current", referenceConfig("source"), "")
                        + userTask(
                        "joint-current", referenceConfig("source"), loop)
                        + userTask("source", source, "")),
                "runtime_process"));

        verify(resolverRuntimeService).requireConfigured(
                "sharedResolver", PersonResolveUsage.ASSIGNEE);
        verify(resolverRuntimeService).requireConfigured(
                "sharedResolver", PersonResolveUsage.MULTI_INSTANCE);
        verify(resolverRuntimeService, never()).requireConfigured(
                "sharedResolver", PersonResolveUsage.CANDIDATE);
    }

    @Test
    void ordinaryReferenceIgnoresOnlySourceMultiInstanceTechnicalAssignee() {
        String sourceConfig = """
                {"assignmentConfigVersion":2,
                 "assigneeType":"user",
                 "assigneeValue":"alice"}
                """;
        String sourceLoop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${sourceUsers}\" "
                + "flowable:elementVariable=\"sourceReviewer\" />";
        String source = userTask(
                "source", sourceConfig, sourceLoop).replace(
                "<bpmn:userTask id=\"source\">",
                "<bpmn:userTask id=\"source\" "
                        + "flowable:assignee=\"${sourceReviewer}\">");

        assertDoesNotThrow(() -> sanitizer.sanitize(
                wrap(userTask(
                        "ordinary-current", referenceConfig("source"), "")
                        + source),
                "runtime_process"));
    }

    @Test
    void multiInstanceReferenceRejectsDynamicCandidateFromOrdinarySource() {
        String loop = "<bpmn:multiInstanceLoopCharacteristics "
                + "flowable:collection=\"${jointUsers}\" "
                + "flowable:elementVariable=\"reviewer\" />";
        String source = userTask(
                "source",
                "{\"assignmentConfigVersion\":2,"
                        + "\"assigneeType\":\"candidate\","
                        + "\"candidateUsers\":\"alice\"}",
                "").replace(
                "<bpmn:userTask id=\"source\">",
                "<bpmn:userTask id=\"source\" "
                        + "flowable:candidateUsers=\"${dynamicUsers}\">");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap(userTask(
                                "joint-current",
                                referenceConfig("source"),
                                loop) + source),
                        "runtime_process"));

        assertTrue(error.getMessage().contains("BPMN 办理人属性"));
        assertTrue(error.getMessage().contains("表达式"));
    }

    private String referenceConfig(String referencedNodeId) {
        return "{\"assignmentConfigVersion\":2,"
                + "\"assigneeType\":\"node_reference\","
                + "\"referencedNodeId\":\"" + referencedNodeId + "\","
                + "\"referencedNodeName\":\"回显名称\"}";
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

    private String userTaskWithMultiConfig(
            String id,
            String assigneeConfig,
            String multiInstanceConfig,
            String body) {
        String task = userTask(id, assigneeConfig, body);
        String property = "<flowable:property "
                + "name=\"multiInstanceConfig\" value=\""
                + escape(multiInstanceConfig.trim())
                + "\" />";
        return task.replace(
                "</flowable:properties>",
                property + "</flowable:properties>");
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
