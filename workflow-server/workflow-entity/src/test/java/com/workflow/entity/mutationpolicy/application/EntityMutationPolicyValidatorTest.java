package com.workflow.entity.mutationpolicy.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationPhase;
import com.workflow.contracts.entity.mutation.EntityMutationStepProvider;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.mutationpolicy.application.model.EntityMutationPolicyDocument;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityMutationPolicyValidatorTest {

    @Mock
    private EntityDefinitionMapper definitionMapper;
    @Mock
    private EntityMutationStepProvider provider;

    private EntityMutationPolicyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EntityMutationPolicyValidator(
                definitionMapper, List.of(provider));
    }

    @Test
    void fieldMappingCannotRunAfterWrite() {
        EntityMutationPolicyDocument document = documentWithStep(
                "FIELD_MAPPING", "AFTER_WRITE");

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(document));
    }

    @Test
    void builtInRuleMustSelectImplementation() {
        EntityMutationPolicyDocument document = documentWithStep(
                "BUILT_IN_RULE", "BEFORE_WRITE");

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(document));
    }

    @Test
    void javaProviderMustSupportConfiguredPhase() {
        EntityMutationPolicyDocument document = documentWithStep(
                "JAVA_PROVIDER", "AFTER_WRITE");
        document.getSteps().get(0).setProviderCode("test-provider");
        when(provider.getCode()).thenReturn("test-provider");
        when(provider.supportedPhases()).thenReturn(
                Set.of(EntityMutationPhase.BEFORE_WRITE));

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(document));
    }

    @Test
    void mutationJsonDoesNotContainVersionCaptureConfiguration()
            throws Exception {
        EntityMutationPolicyDocument document =
                new EntityMutationPolicyDocument();

        String json = new ObjectMapper().writeValueAsString(document);

        assertTrue(json.contains("\"schemaVersion\":1"));
        assertFalse(json.contains("triggers"));
        assertFalse(json.contains("snapshotScope"));
        assertFalse(json.contains("diffPolicy"));
    }

    private EntityMutationPolicyDocument documentWithStep(
            String type,
            String phase) {
        EntityMutationPolicyDocument document =
                new EntityMutationPolicyDocument();
        EntityVersionConfiguration.Step step =
                new EntityVersionConfiguration.Step();
        step.setStepName("测试步骤");
        step.setStepType(type);
        step.setPhase(phase);
        document.setSteps(List.of(step));
        return document;
    }
}
