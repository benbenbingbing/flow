package com.workflow.process.task.application.nextapproval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NextApproverSelectionPolicyReaderTest {

    private final NextApproverSelectionPolicyReader reader =
            new NextApproverSelectionPolicyReader(new ObjectMapper());

    @Test
    void readsNestedSourceRulesAndBuildsDefinitionBoundScopeKey() {
        UserTask task = userTask("finance-review", """
                {
                  "assigneeType": "user",
                  "assigneeValue": "alice",
                  "nextApproverSelection": {
                    "visible": true,
                    "editable": true,
                    "source": {
                      "type": "SCOPE",
                      "rules": [
                        {
                          "type": "ROLE",
                          "values": ["finance-manager", "finance-manager"]
                        },
                        {
                          "type": "ORGANIZATION",
                          "values": ["finance-dept"],
                          "includeChildren": true
                        }
                      ]
                    }
                  }
                }
                """);
        task.setAssignee("alice");

        NextApproverSelectionPolicy policy = reader.read(
                "definition-7", task).selectionPolicy();

        assertTrue(policy.configured());
        assertEquals(1, policy.version(),
                "未带版本的历史配置必须按 version 1 兼容读取");
        assertTrue(policy.visible());
        assertTrue(policy.editable());
        assertEquals("DIRECT", policy.assignmentMode());
        assertFalse(policy.multiple());
        assertEquals(
                NextApproverSelectionPolicy.SourceType.SCOPE,
                policy.sourceType());
        assertEquals(2, policy.scopes().size());
        assertEquals(
                List.of("finance-manager"),
                policy.scopes().get(0).values());
        assertTrue(policy.scopes().get(1).includeChildren());
        assertTrue(policy.scopeKey().matches("[0-9a-f]{64}"));

        UserTask equivalent = userTask("finance-review", """
                {
                  "nextApproverSelection": {
                    "source": {
                      "rules": [
                        {"values":["finance-manager"],"type":"ROLE"},
                        {"includeChildren":true,"values":["finance-dept"],"type":"ORGANIZATION"}
                      ],
                      "type": "SCOPE"
                    },
                    "editable": true,
                    "visible": true
                  },
                  "assigneeValue": "alice",
                  "assigneeType": "user"
                }
                """);
        equivalent.setAssignee("alice");
        assertEquals(
                policy.scopeKey(),
                reader.read("definition-7", equivalent)
                        .selectionPolicy().scopeKey(),
                "JSON 对象键顺序和重复范围值不应改变 scopeKey");
        assertNotEquals(
                policy.scopeKey(),
                reader.read("definition-8", equivalent)
                        .selectionPolicy().scopeKey(),
                "scopeKey 必须绑定已部署流程定义版本");
    }

    @Test
    void readsVersionTwoNodeAssignmentSourceAndBindsBaseAssignmentToScopeKey() {
        UserTask task = userTask("manager-review", """
                {
                  "assignmentConfigVersion": 2,
                  "assigneeType": "user",
                  "assigneeValue": "alice",
                  "candidateUsers": "bob",
                  "nextApproverSelection": {
                    "version": 1,
                    "visible": true,
                    "editable": true,
                    "source": {"type": "NODE_ASSIGNMENT"}
                  }
                }
                """);
        task.setAssignee("alice");
        task.setCandidateUsers(List.of("bob"));

        NextApproverSelectionPolicy policy = reader.read(
                "definition-v2", task).selectionPolicy();

        assertEquals(
                NextApproverSelectionPolicy.SourceType.NODE_ASSIGNMENT,
                policy.sourceType());
        assertEquals("DIRECT", policy.assignmentMode());
        assertTrue(policy.scopes().isEmpty());
        assertNull(policy.resolverCode());
        assertTrue(policy.scopeKey().matches("[0-9a-f]{64}"));

        UserTask changedBaseAssignment = userTask("manager-review", """
                {
                  "assignmentConfigVersion": 2,
                  "assigneeType": "user",
                  "assigneeValue": "carol",
                  "candidateUsers": "bob",
                  "nextApproverSelection": {
                    "version": 1,
                    "visible": true,
                    "editable": true,
                    "source": {"type": "NODE_ASSIGNMENT"}
                  }
                }
                """);
        changedBaseAssignment.setAssignee("carol");
        changedBaseAssignment.setCandidateUsers(List.of("bob"));

        assertNotEquals(
                policy.scopeKey(),
                reader.read("definition-v2", changedBaseAssignment)
                        .selectionPolicy().scopeKey(),
                "NODE_ASSIGNMENT 的 scopeKey 必须包含基础审批人配置，不能只签名展示开关");
    }

    @Test
    void resolvesNodeReferenceAndBindsTheReferenceChainToScopeKey() {
        UserTask current = userTask("current-review", """
                {"assignmentConfigVersion":2,
                 "assigneeType":"node_reference",
                 "referencedNodeId":"candidate-source",
                 "referencedNodeName":"候选审批",
                 "nextApproverSelection":{"version":1,
                   "visible":true,"editable":true,
                   "source":{"type":"NODE_ASSIGNMENT"}}}
                """);
        UserTask candidateSource = userTask("candidate-source", """
                {"assignmentConfigVersion":2,
                 "assigneeType":"group",
                 "assigneeValue":"finance"}
                """);
        candidateSource.setCandidateGroups(List.of("finance"));
        BpmnModel model = model(current, candidateSource);

        NextApprovalTarget target = reader.read(
                "definition-ref", current, model);

        assertEquals(candidateSource, target.assignmentSourceTask());
        assertEquals("CANDIDATE",
                target.selectionPolicy().assignmentMode());
        assertEquals("group", target.assigneeConfig().get("assigneeType"));

        UserTask alternate = userTask("alternate-source", """
                {"assignmentConfigVersion":2,
                 "assigneeType":"group",
                 "assigneeValue":"finance"}
                """);
        alternate.setCandidateGroups(List.of("finance"));
        UserTask changedReference = userTask("current-review", """
                {"assignmentConfigVersion":2,
                 "assigneeType":"node_reference",
                 "referencedNodeId":"alternate-source",
                 "nextApproverSelection":{"version":1,
                   "visible":true,"editable":true,
                   "source":{"type":"NODE_ASSIGNMENT"}}}
                """);
        assertNotEquals(
                target.selectionPolicy().scopeKey(),
                reader.read(
                        "definition-ref",
                        changedReference,
                        model(changedReference, alternate))
                        .selectionPolicy().scopeKey(),
                "即使终端规则相同，引用链变化也必须使 scopeKey 失效");
    }

    @Test
    void currentMultiInstanceDeterminesModeInsteadOfReferencedNodeLoop() {
        UserTask current = userTask("joint-review", """
                {"assignmentConfigVersion":2,
                 "assigneeType":"node_reference",
                 "referencedNodeId":"resolver-source",
                 "nextApproverSelection":{"version":1,
                   "visible":true,"editable":true,
                   "source":{"type":"NODE_ASSIGNMENT"}}}
                """);
        current.setLoopCharacteristics(
                new MultiInstanceLoopCharacteristics());
        UserTask source = userTask("resolver-source", """
                {"assignmentConfigVersion":2,
                 "assigneeType":"interface",
                 "resolverCode":"managerResolver"}
                """);

        NextApprovalTarget target = reader.read(
                "definition-ref-mi", current, model(current, source));

        assertEquals("MULTI_INSTANCE",
                target.selectionPolicy().assignmentMode());
        assertEquals(source, target.assignmentSourceTask());
    }

    @Test
    void legacyIdAndMixedConfigsAreUnionedAndBoundToScopeKey() {
        UserTask task = userTask(
                "legacy-joint-review",
                """
                {"multiInstanceUserIds":"alice",
                 "multiInstanceUsers":"carol,ROLE_AUDITOR",
                 "nextApproverSelection":{"version":1,
                 "visible":true,"editable":true,
                 "source":{"type":"NODE_ASSIGNMENT"}}}
                """,
                """
                {"multiInstanceUsernames":"bob",
                 "multiInstanceGroupIds":"finance-id",
                 "multiInstanceRoleIds":"manager-id",
                 "multiInstanceUsers":"dave,ROLE_REVIEWER"}
                """);
        task.setLoopCharacteristics(
                new MultiInstanceLoopCharacteristics());

        NextApprovalTarget target = reader.read(
                "definition-legacy", task);

        assertEquals(
                List.of("alice", "bob", "carol", "dave"),
                target.assigneeConfig().get("multiInstanceUsernames"));
        assertEquals(
                List.of("finance-id"),
                target.assigneeConfig().get("multiInstanceGroupCodes"));
        assertEquals(
                List.of("manager-id", "AUDITOR", "REVIEWER"),
                target.assigneeConfig().get("multiInstanceRoleCodes"));

        UserTask changed = userTask(
                "legacy-joint-review",
                """
                {"multiInstanceUserIds":"alice",
                 "multiInstanceUsers":"carol,ROLE_AUDITOR",
                 "nextApproverSelection":{"version":1,
                 "visible":true,"editable":true,
                 "source":{"type":"NODE_ASSIGNMENT"}}}
                """,
                """
                {"multiInstanceUsernames":"zoe",
                 "multiInstanceGroupIds":"finance-id",
                 "multiInstanceRoleIds":"manager-id",
                 "multiInstanceUsers":"dave,ROLE_REVIEWER"}
                """);
        changed.setLoopCharacteristics(
                new MultiInstanceLoopCharacteristics());
        assertNotEquals(
                target.selectionPolicy().scopeKey(),
                reader.read("definition-legacy", changed)
                        .selectionPolicy().scopeKey(),
                "multiInstanceConfig 中仅 ID/mixed 旧字段变化也必须使 scopeKey 失效");
    }

    @Test
    void derivesCandidateAndMultiInstanceAssignmentModesFromBpmn() {
        UserTask candidateTask = userTask("candidate-review", scopeConfig());
        candidateTask.setCandidateGroups(List.of("ROLE_MANAGER"));

        NextApproverSelectionPolicy candidatePolicy = reader.read(
                "definition-1", candidateTask).selectionPolicy();

        assertEquals("CANDIDATE", candidatePolicy.assignmentMode());
        assertTrue(candidatePolicy.multiple(),
                "候选办理应允许覆盖为多个候选人");

        UserTask multiInstanceTask = userTask(
                "serial-review", resolverConfig());
        multiInstanceTask.setLoopCharacteristics(
                new MultiInstanceLoopCharacteristics());

        NextApproverSelectionPolicy multiInstancePolicy = reader.read(
                "definition-1", multiInstanceTask).selectionPolicy();

        assertEquals(
                "MULTI_INSTANCE",
                multiInstancePolicy.assignmentMode());
        assertTrue(multiInstancePolicy.multiple());
    }

    @Test
    void literalAssigneeTakesPrecedenceOverCandidateUsersAndGroups() {
        UserTask candidateUsersTask = userTask(
                "assignee-with-candidate-users", scopeConfig());
        candidateUsersTask.setAssignee("alice");
        candidateUsersTask.setCandidateUsers(List.of("bob"));

        NextApproverSelectionPolicy candidateUsersPolicy = reader.read(
                "definition-1", candidateUsersTask).selectionPolicy();

        assertEquals("DIRECT", candidateUsersPolicy.assignmentMode());
        assertFalse(candidateUsersPolicy.multiple());

        UserTask candidateGroupsTask = userTask(
                "assignee-with-candidate-groups", """
                {
                  "assignmentMode": "CANDIDATE",
                  "nextApproverSelection": {
                    "version": 1,
                    "visible": true,
                    "editable": true,
                    "source": {
                      "type": "SCOPE",
                      "rules": [{"type":"ALL_USERS","values":[]}]
                    }
                  }
                }
                """);
        candidateGroupsTask.setAssignee("alice");
        candidateGroupsTask.setCandidateGroups(List.of("ROLE_MANAGER"));

        NextApproverSelectionPolicy candidateGroupsPolicy = reader.read(
                "definition-1", candidateGroupsTask).selectionPolicy();

        assertEquals("DIRECT", candidateGroupsPolicy.assignmentMode(),
                "已部署 BPMN 的明确 assignee 必须优先于候选组和陈旧模式配置");
        assertFalse(candidateGroupsPolicy.multiple());
    }

    @Test
    void supportsLegacyFlatScopeConfigurationWithoutBroadeningIt() {
        UserTask task = userTask("legacy-review", """
                {
                  "nextApproverSelection": {
                    "visible": true,
                    "editable": true,
                    "sourceType": "SCOPE",
                    "scopes": [
                      {"type":"USER","values":["alice"]}
                    ]
                  }
                }
                """);

        NextApproverSelectionPolicy policy = reader.read(
                "definition-legacy", task).selectionPolicy();

        assertEquals(
                NextApproverSelectionPolicy.ScopeType.USER,
                policy.scopes().get(0).type());
        assertEquals(List.of("alice"), policy.scopes().get(0).values());
    }

    @Test
    void usesTheSameLegacyAliasesAsPublishValidation() {
        UserTask scopeRulesTask = userTask("scope-rules", """
                {"nextApproverSelection":{
                  "display":true,"allowEdit":true,
                  "sourceType":"SCOPE",
                  "scopeRules":[
                    {"type":"USER","values":["alice"]}]
                }}
                """);
        UserTask sourceScopesTask = userTask("source-scopes", """
                {"nextApproverSelection":{
                  "show":true,"allowModify":true,
                  "source":{"type":"SCOPE","scopes":[
                    {"type":"ROLE","values":["manager"]}]}
                }}
                """);

        NextApproverSelectionPolicy flat = reader.read(
                "definition-legacy", scopeRulesTask).selectionPolicy();
        NextApproverSelectionPolicy nested = reader.read(
                "definition-legacy", sourceScopesTask).selectionPolicy();

        assertTrue(flat.visible());
        assertTrue(flat.editable());
        assertEquals(
                NextApproverSelectionPolicy.ScopeType.USER,
                flat.scopes().get(0).type());
        assertTrue(nested.visible());
        assertTrue(nested.editable());
        assertEquals(
                NextApproverSelectionPolicy.ScopeType.ROLE,
                nested.scopes().get(0).type());
    }

    @Test
    void failsClosedForUnknownOrEmptyEditableScope() {
        UserTask unknown = userTask("unsafe-review", """
                {
                  "nextApproverSelection": {
                    "visible": true,
                    "editable": true,
                    "source": {
                      "type": "SCOPE",
                      "rules": [{"type":"UNTRUSTED_SCOPE","values":[]}]
                    }
                  }
                }
                """);
        IllegalArgumentException unknownError = assertThrows(
                IllegalArgumentException.class,
                () -> reader.read("definition-1", unknown));
        assertTrue(unknownError.getMessage().contains("unsafe-review"));
        assertTrue(unknownError.getMessage().contains("不支持的人员范围类型"));

        UserTask empty = userTask("empty-scope-review", """
                {
                  "nextApproverSelection": {
                    "visible": true,
                    "editable": true,
                    "source": {"type":"SCOPE","rules":[]}
                  }
                }
                """);
        IllegalArgumentException emptyError = assertThrows(
                IllegalArgumentException.class,
                () -> reader.read("definition-1", empty));
        assertTrue(emptyError.getMessage().contains("至少配置一个人员范围"));
    }

    @Test
    void acceptsVersionOneAndRejectsUnknownConfigurationVersion() {
        NextApproverSelectionPolicy versionOne = reader.read(
                "definition-1",
                userTask("version-one-review", scopeConfig()))
                .selectionPolicy();
        assertEquals(1, versionOne.version());

        UserTask unsupported = userTask("future-version-review", """
                {
                  "nextApproverSelection": {
                    "version": 2,
                    "visible": true,
                    "editable": true,
                    "source": {
                      "type": "SCOPE",
                      "rules": [{"type":"ALL_USERS","values":[]}]
                    }
                  }
                }
                """);
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> reader.read("definition-1", unsupported));
        assertTrue(error.getMessage().contains("future-version-review"));
        assertTrue(error.getMessage().contains("不支持的 nextApproverSelection 版本: 2"));
    }

    @Test
    void defaultsMissingVisibilityToHiddenAndPreservesNullableResolverParams() {
        UserTask task = userTask("resolver-review", """
                {
                  "nextApproverSelection": {
                    "source": {
                      "type": "RESOLVER",
                      "resolverCode": "managerResolver",
                      "extraParams": {"region":null}
                    }
                  }
                }
                """);

        NextApproverSelectionPolicy policy = reader.read(
                "definition-1", task).selectionPolicy();

        assertFalse(policy.visible());
        assertFalse(policy.editable());
        assertEquals("managerResolver", policy.resolverCode());
        assertTrue(policy.extraParams().containsKey("region"));
        assertNull(policy.extraParams().get("region"));
    }

    private String scopeConfig() {
        return """
                {
                  "nextApproverSelection": {
                    "version": 1,
                    "visible": true,
                    "editable": true,
                    "source": {
                      "type": "SCOPE",
                      "rules": [{"type":"ALL_USERS","values":[]}]
                    }
                  }
                }
                """;
    }

    private String resolverConfig() {
        return """
                {
                  "nextApproverSelection": {
                    "visible": true,
                    "editable": true,
                    "source": {
                      "type": "RESOLVER",
                      "resolverCode": "managerResolver",
                      "extraParams": {}
                    }
                  }
                }
                """;
    }

    private UserTask userTask(String id, String assigneeConfig) {
        UserTask task = new UserTask();
        task.setId(id);
        task.setName(id);
        ExtensionElement properties = extensionElement("properties");
        properties.addChildElement(property(
                "assigneeConfig", assigneeConfig));
        task.addExtensionElement(properties);
        return task;
    }

    private BpmnModel model(UserTask... tasks) {
        Process process = new Process();
        process.setId("process");
        for (UserTask task : tasks) {
            process.addFlowElement(task);
        }
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        return model;
    }

    private UserTask userTask(
            String id,
            String assigneeConfig,
            String multiInstanceConfig) {
        UserTask task = userTask(id, assigneeConfig);
        task.getExtensionElements().get("properties").get(0)
                .addChildElement(property(
                        "multiInstanceConfig", multiInstanceConfig));
        return task;
    }

    private ExtensionElement property(String name, String value) {
        ExtensionElement property = extensionElement("property");
        property.addAttribute(new ExtensionAttribute("name", name));
        property.addAttribute(new ExtensionAttribute("value", value));
        return property;
    }

    private ExtensionElement extensionElement(String name) {
        ExtensionElement element = new ExtensionElement();
        element.setName(name);
        element.setNamespace("http://flowable.org/bpmn");
        element.setNamespacePrefix("flowable");
        return element;
    }
}
