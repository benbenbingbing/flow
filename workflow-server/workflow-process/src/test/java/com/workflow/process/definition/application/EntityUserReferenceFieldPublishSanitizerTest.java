package com.workflow.process.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolverConfigurationValidationRequest;
import com.workflow.contracts.identity.resolver.PersonResolverConfigurationValidator;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实体用户关系字段办理人发布校验测试。
 */
class EntityUserReferenceFieldPublishSanitizerTest {

    @Test
    void publishPassesEntityCoordinatesToTheRegisteredValidator() {
        ProcessBpmnPublishSanitizer sanitizer = configuredSanitizer();
        PersonResolverConfigurationValidator validator =
                mock(PersonResolverConfigurationValidator.class);
        when(validator.resolverCode())
                .thenReturn("entityUserReferenceField");
        ReflectionTestUtils.setField(
                sanitizer,
                "personResolverConfigurationValidators",
                List.of(validator));

        sanitizer.sanitize(
                wrap(userTask("approve", entityFieldConfig())),
                "entity_reference_process",
                "process-config-1");

        ArgumentCaptor<PersonResolverConfigurationValidationRequest> request =
                ArgumentCaptor.forClass(
                        PersonResolverConfigurationValidationRequest.class);
        verify(validator).validate(request.capture());
        assertEquals(PersonResolveUsage.ASSIGNEE, request.getValue().usage());
        assertEquals("DIRECT", request.getValue().assignmentMode());
        assertFalse(request.getValue().multiInstance());
        assertEquals(
                "process-config-1",
                request.getValue().processConfigId());
        assertEquals(
                "purchase_order",
                request.getValue().extraParams().get("entityCode"));
        assertEquals(
                "approver",
                request.getValue().extraParams().get("fieldCode"));
    }

    @Test
    void multiInstancePublishesNodeEntryCollectionHandler() {
        ProcessBpmnPublishSanitizer sanitizer = configuredSanitizer();
        PersonResolverConfigurationValidator validator =
                mock(PersonResolverConfigurationValidator.class);
        when(validator.resolverCode())
                .thenReturn("entityUserReferenceField");
        ReflectionTestUtils.setField(
                sanitizer,
                "personResolverConfigurationValidators",
                List.of(validator));

        String output = sanitizer.sanitize(
                wrap(userTask(
                        "approve",
                        entityFieldConfig("CANDIDATE"),
                        """
                        <bpmn:multiInstanceLoopCharacteristics
                          isSequential="false"
                          flowable:collection="${reviewers}"
                          flowable:elementVariable="reviewer" />
                        """)),
                "entity_reference_process");
        String secondPass = sanitizer.sanitize(
                output, "entity_reference_process");

        assertTrue(output.contains(
                "${relativeOrgPositionCollectionHandler}"));
        assertTrue(output.contains(
                "flowable:collection=\"__wfEntryDynamicCollectionSeed\""));
        assertTrue(output.contains(
                "name=\"entryDynamicCollectionVariable\" value=\"reviewers\""));
        assertTrue(secondPass.contains(
                "name=\"entryDynamicCollectionVariable\" value=\"reviewers\""),
                "二次净化不得把原业务 collection 覆盖成内部 seed");
        ArgumentCaptor<PersonResolverConfigurationValidationRequest> request =
                ArgumentCaptor.forClass(
                        PersonResolverConfigurationValidationRequest.class);
        verify(validator, times(2)).validate(request.capture());
        PersonResolverConfigurationValidationRequest lastRequest =
                request.getAllValues().get(1);
        assertTrue(lastRequest.multiInstance());
        assertEquals(
                PersonResolveUsage.MULTI_INSTANCE,
                lastRequest.usage());
    }

    @Test
    void publishRejectsTheBuiltInResolverWhenItsValidatorIsMissing() {
        ProcessBpmnPublishSanitizer sanitizer = configuredSanitizer();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap(userTask("approve", entityFieldConfig())),
                        "entity_reference_process"));

        assertTrue(failure.getMessage().contains(
                "entityUserReferenceField 配置校验器未注册"));
    }

    @Test
    void legacyStaticMultiInstanceIgnoresStaleBaseResolver() {
        ProcessBpmnPublishSanitizer sanitizer = configuredSanitizer();
        String legacyStaticConfig = """
                {
                  "multiInstanceUsernames": ["alice"],
                  "assigneeType": "interface",
                  "resolverCode": "entityUserReferenceField"
                }
                """;

        String output = sanitizer.sanitize(
                wrap(userTask(
                        "approve",
                        legacyStaticConfig,
                        """
                        <bpmn:multiInstanceLoopCharacteristics
                          isSequential="false"
                          flowable:collection="${reviewers}"
                          flowable:elementVariable="reviewer" />
                        """)),
                "legacy_static_process");

        assertFalse(output.contains(
                "${relativeOrgPositionCollectionHandler}"));
        assertTrue(output.contains(
                "flowable:collection=\"${reviewers}\""));
    }

    @Test
    void secondPassKeepsDistinctEditableDynamicCollections() {
        ProcessBpmnPublishSanitizer sanitizer = configuredSanitizer();
        PersonResolverConfigurationValidator validator =
                mock(PersonResolverConfigurationValidator.class);
        when(validator.resolverCode())
                .thenReturn("entityUserReferenceField");
        ReflectionTestUtils.setField(
                sanitizer,
                "personResolverConfigurationValidators",
                List.of(validator));
        String config = """
                {
                  "assignmentConfigVersion": 2,
                  "assigneeType": "interface",
                  "resolverCode": "entityUserReferenceField",
                  "assignmentMode": "CANDIDATE",
                  "extraParams": {
                    "schemaVersion": 1,
                    "entityCode": "purchase_order",
                    "fieldCode": "approver"
                  },
                  "nextApproverSelection": {
                    "version": 1,
                    "visible": true,
                    "editable": true,
                    "source": {"type": "NODE_ASSIGNMENT"}
                  }
                }
                """;
        String first = sanitizer.sanitize(
                wrap(userTask(
                        "review-a",
                        config,
                        multiInstanceLoop("reviewersA"))
                        + userTask(
                        "review-b",
                        config,
                        multiInstanceLoop("reviewersB"))),
                "dynamic_collection_process");

        String second = sanitizer.sanitize(
                first, "dynamic_collection_process");

        assertTrue(second.contains(
                "name=\"entryDynamicCollectionVariable\" "
                        + "value=\"reviewersA\""));
        assertTrue(second.contains(
                "name=\"entryDynamicCollectionVariable\" "
                        + "value=\"reviewersB\""));
    }

    @Test
    void dynamicCollectionCannotOverwriteTrustedOrInternalContext() {
        ProcessBpmnPublishSanitizer sanitizer = configuredSanitizer();
        PersonResolverConfigurationValidator validator =
                mock(PersonResolverConfigurationValidator.class);
        when(validator.resolverCode())
                .thenReturn("entityUserReferenceField");
        ReflectionTestUtils.setField(
                sanitizer,
                "personResolverConfigurationValidators",
                List.of(validator));

        for (String collectionVariable
                : List.of("entityDataId", "_wfAttackerCollection")) {
            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> sanitizer.sanitize(
                            wrap(userTask(
                                    "approve",
                                    entityFieldConfig("CANDIDATE"),
                                    multiInstanceLoop(collectionVariable))),
                            "reserved_collection_process"));

            assertTrue(failure.getMessage().contains(
                    "多实例 collection 不能覆盖平台保留流程变量"));
        }
    }

    private ProcessBpmnPublishSanitizer configuredSanitizer() {
        ProcessBpmnPublishSanitizer sanitizer =
                new ProcessBpmnPublishSanitizer(new ObjectMapper());
        ReflectionTestUtils.setField(
                sanitizer,
                "personResolverRuntimeService",
                mock(PersonResolverRuntimeService.class));
        return sanitizer;
    }

    private String entityFieldConfig() {
        return entityFieldConfig("DIRECT");
    }

    private String entityFieldConfig(String assignmentMode) {
        return """
                {
                  "assignmentConfigVersion": 2,
                  "assigneeType": "interface",
                  "resolverCode": "entityUserReferenceField",
                  "assignmentMode": "%s",
                  "extraParams": {
                    "schemaVersion": 1,
                    "entityCode": "purchase_order",
                    "fieldCode": "approver"
                  }
                }
                """.formatted(assignmentMode);
    }

    private String multiInstanceLoop(String collectionVariable) {
        return """
                <bpmn:multiInstanceLoopCharacteristics
                  isSequential="false"
                  flowable:collection="${%s}"
                  flowable:elementVariable="reviewer" />
                """.formatted(collectionVariable);
    }

    private String userTask(String id, String assigneeConfig) {
        return userTask(id, assigneeConfig, "");
    }

    private String userTask(
            String id,
            String assigneeConfig,
            String body) {
        return "<bpmn:userTask id=\"" + id + "\">"
                + "<bpmn:extensionElements><flowable:properties>"
                + "<flowable:property name=\"assigneeConfig\" value=\""
                + escape(assigneeConfig.trim())
                + "\" /></flowable:properties></bpmn:extensionElements>"
                + body
                + "</bpmn:userTask>";
    }

    private String wrap(String element) {
        return """
                <bpmn:definitions
                  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn"
                  targetNamespace="http://workflow.test/process">
                  <bpmn:process id="draft_process" isExecutable="true">
                    %s
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(element);
    }

    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
