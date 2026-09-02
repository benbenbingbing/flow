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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelativePositionPublishSanitizerTest {

    @Test
    void multiInstancePublishesDynamicCollectionHandlerAndValidatesUsage() {
        ProcessBpmnPublishSanitizer sanitizer = configuredSanitizer();
        PersonResolverConfigurationValidator validator =
                mock(PersonResolverConfigurationValidator.class);
        when(validator.resolverCode())
                .thenReturn("relativeOrgPosition");
        ReflectionTestUtils.setField(
                sanitizer,
                "personResolverConfigurationValidators",
                List.of(validator));

        String output = sanitizer.sanitize(
                wrap(userTask("approve", relativeConfig(), """
                        <bpmn:multiInstanceLoopCharacteristics
                          isSequential="false"
                          flowable:collection="${reviewers}"
                          flowable:elementVariable="reviewer" />
                        """)),
                "relative_process");

        assertTrue(output.contains(
                "${relativeOrgPositionCollectionHandler}"));
        assertTrue(output.contains(
                "flowable:collection=\"__wfEntryDynamicCollectionSeed\""));
        assertTrue(output.contains(
                "name=\"entryDynamicCollectionVariable\" value=\"reviewers\""));
        ArgumentCaptor<PersonResolverConfigurationValidationRequest> request =
                ArgumentCaptor.forClass(
                        PersonResolverConfigurationValidationRequest.class);
        verify(validator).validate(request.capture());
        assertTrue(request.getValue().multiInstance());
        org.junit.jupiter.api.Assertions.assertEquals(
                PersonResolveUsage.MULTI_INSTANCE,
                request.getValue().usage());
    }

    @Test
    void relativeResolverCannotPublishWithoutItsConfigurationValidator() {
        ProcessBpmnPublishSanitizer sanitizer = configuredSanitizer();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitize(
                        wrap(userTask(
                                "approve", relativeConfig(), "")),
                        "relative_process"));

        assertTrue(failure.getMessage().contains(
                "relativeOrgPosition 配置校验器未注册"));
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

    private String relativeConfig() {
        return """
                {
                  "assignmentConfigVersion": 2,
                  "assigneeType": "interface",
                  "resolverCode": "relativeOrgPosition",
                  "assignmentMode": "CANDIDATE",
                  "extraParams": {
                    "schemaVersion": 1,
                    "subject": "PROCESS_INITIATOR",
                    "anchor": "DEPARTMENT",
                    "positionCode": "UNIT_LEADER",
                    "hierarchy": {"mode": "SELF"},
                    "multipleMatchPolicy": "ALL"
                  }
                }
                """;
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
