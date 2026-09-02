package com.workflow.process.assignment.entity;

import com.workflow.contracts.entity.EntityUserReferencePort;
import com.workflow.contracts.entity.EntityUserReferencePort.UserReferenceField;
import com.workflow.contracts.entity.EntityUserReferencePort.EntityUserReferenceException;
import com.workflow.contracts.entity.EntityCodeCatalogPort;
import com.workflow.contracts.identity.resolver.PersonPrincipalType;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolutionException;
import com.workflow.contracts.identity.resolver.PersonResolverConfigurationValidationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityUserReferenceFieldPersonResolverTest {

    private EntityUserReferencePort referencePort;
    private EntityCodeCatalogPort entityCodeCatalogPort;
    private EntityUserReferenceFieldPersonResolver resolver;

    @BeforeEach
    void setUp() {
        referencePort = mock(EntityUserReferencePort.class);
        entityCodeCatalogPort = mock(EntityCodeCatalogPort.class);
        resolver = new EntityUserReferenceFieldPersonResolver(
                referencePort, entityCodeCatalogPort);
    }

    @Test
    void descriptorPublishesTheStrictV1ConfigurationContract() {
        var descriptor = resolver.descriptor();

        assertEquals(
                EntityUserReferenceFieldConfig.RESOLVER_CODE,
                descriptor.code());
        assertEquals(
                Set.of(
                        PersonResolveUsage.ASSIGNEE,
                        PersonResolveUsage.CANDIDATE,
                        PersonResolveUsage.MULTI_INSTANCE),
                descriptor.supportedUsages());
        assertFalse(descriptor.dynamicExtraParams());
        assertEquals(
                false,
                descriptor.extraParamSchema().get("additionalProperties"));
        assertEquals(
                List.of("schemaVersion", "entityCode", "fieldCode"),
                descriptor.extraParamSchema().get("required"));
    }

    @Test
    void validationUsesAuthoritativeEntityMetadataForOrdinaryAndMultiInstanceModes() {
        when(referencePort.requireUserReferenceField(
                "purchase_order", "approvers"))
                .thenReturn(new UserReferenceField(
                        "purchase_order", "approvers", true));

        resolver.validate(validationRequest(
                PersonResolveUsage.ASSIGNEE,
                "CANDIDATE",
                false,
                config("purchase_order", "approvers")));
        resolver.validate(validationRequest(
                PersonResolveUsage.MULTI_INSTANCE,
                "MULTI_INSTANCE",
                true,
                config("purchase_order", "approvers")));

        verify(referencePort, org.mockito.Mockito.times(2))
                .requireUserReferenceField("purchase_order", "approvers");
    }

    @Test
    void validationRejectsAssignmentModeThatConflictsWithFieldCardinality() {
        when(referencePort.requireUserReferenceField(
                "purchase_order", "approvers"))
                .thenReturn(new UserReferenceField(
                        "purchase_order", "approvers", true));
        when(referencePort.requireUserReferenceField(
                "purchase_order", "owner"))
                .thenReturn(new UserReferenceField(
                        "purchase_order", "owner", false));

        IllegalArgumentException multipleDirect = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.validate(validationRequest(
                        PersonResolveUsage.ASSIGNEE,
                        "DIRECT",
                        false,
                        config("purchase_order", "approvers"))));
        IllegalArgumentException singleCandidate = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.validate(validationRequest(
                        PersonResolveUsage.ASSIGNEE,
                        "CANDIDATE",
                        false,
                        config("purchase_order", "owner"))));

        assertTrue(multipleDirect.getMessage().contains("多选"));
        assertTrue(singleCandidate.getMessage().contains("单选"));
    }

    @Test
    void validationRejectsFieldCoordinatesFromAnotherBoundEntity() {
        when(entityCodeCatalogPort.findEntityCodeByProcessDefinitionId(
                "process-config-1"))
                .thenReturn("expense_claim");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.validate(
                        new PersonResolverConfigurationValidationRequest(
                                PersonResolveUsage.ASSIGNEE,
                                "DIRECT",
                                false,
                                "process-config-1",
                                config("purchase_order", "owner"))));

        assertTrue(failure.getMessage().contains("流程绑定实体不一致"));
        verify(referencePort, never()).requireUserReferenceField(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validationRejectsInvalidSchemaAndAssignmentModeBeforeReadingMetadata() {
        Map<String, Object> unsupportedVersion = Map.of(
                "schemaVersion", 2,
                "entityCode", "purchase_order",
                "fieldCode", "approver");

        IllegalArgumentException versionFailure = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.validate(validationRequest(
                        PersonResolveUsage.ASSIGNEE,
                        "DIRECT",
                        false,
                        unsupportedVersion)));
        IllegalArgumentException modeFailure = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.validate(validationRequest(
                        PersonResolveUsage.MULTI_INSTANCE,
                        "DIRECT",
                        true,
                        config("purchase_order", "approver"))));

        assertTrue(versionFailure.getMessage().contains("schemaVersion"));
        assertTrue(modeFailure.getMessage().contains("多人办理"));
        verify(referencePort, never()).requireUserReferenceField(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validationRejectsMissingEntityAndFieldCoordinates() {
        IllegalArgumentException entityFailure = assertThrows(
                IllegalArgumentException.class,
                () -> EntityUserReferenceFieldConfig.parse(Map.of(
                        "schemaVersion", 1,
                        "entityCode", " ",
                        "fieldCode", "approver")));
        IllegalArgumentException fieldFailure = assertThrows(
                IllegalArgumentException.class,
                () -> EntityUserReferenceFieldConfig.parse(Map.of(
                        "schemaVersion", 1,
                        "entityCode", "purchase_order")));

        assertTrue(entityFailure.getMessage().contains("entityCode"));
        assertTrue(fieldFailure.getMessage().contains("fieldCode"));
    }

    @Test
    void configurationRejectsMissingVersionAndUnknownParameters() {
        IllegalArgumentException missingVersion = assertThrows(
                IllegalArgumentException.class,
                () -> EntityUserReferenceFieldConfig.parse(Map.of(
                        "entityCode", "purchase_order",
                        "fieldCode", "approver")));
        IllegalArgumentException unknownParameter = assertThrows(
                IllegalArgumentException.class,
                () -> EntityUserReferenceFieldConfig.parse(Map.of(
                        "schemaVersion", 1,
                        "entityCode", "purchase_order",
                        "fieldCode", "approver",
                        "tableName", "sys_user")));

        assertTrue(missingVersion.getMessage().contains("schemaVersion"));
        assertTrue(unknownParameter.getMessage().contains("tableName"));
    }

    @Test
    void resolveRejectsMismatchedEntityContextWithoutReadingTheRecord() {
        PersonResolutionException failure = assertThrows(
                PersonResolutionException.class,
                () -> resolver.resolve(request(
                        "expense_claim",
                        "record-1",
                        config("purchase_order", "approver"))));

        assertEquals("ENTITY_CONTEXT_MISMATCH", failure.reasonCode());
        assertEquals(
                "purchase_order",
                failure.details().get("configuredEntityCode"));
        assertEquals(
                "expense_claim",
                failure.details().get("runtimeEntityCode"));
        verify(referencePort, never()).readUserKeys(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolveRequiresTheTrustedEntityRecordId() {
        PersonResolutionException failure = assertThrows(
                PersonResolutionException.class,
                () -> resolver.resolve(request(
                        "purchase_order",
                        " ",
                        config("purchase_order", "approver"))));

        assertEquals("ENTITY_RECORD_MISSING", failure.reasonCode());
        verify(referencePort, never()).readUserKeys(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolveProjectsASingleEntityUserValueToAUserPrincipal() {
        when(referencePort.readUserKeys(
                "purchase_order", "record-1", "approver"))
                .thenReturn(List.of("user-1"));

        var result = resolver.resolve(request(
                "purchase_order",
                "record-1",
                config("purchase_order", "approver")));

        assertEquals(1, result.principals().size());
        assertEquals(PersonPrincipalType.USER, result.principals().get(0).type());
        assertEquals("user-1", result.principals().get(0).key());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void resolvePreservesMultipleEntityUserValuesInPortOrder() {
        when(referencePort.readUserKeys(
                "purchase_order", "record-1", "approvers"))
                .thenReturn(List.of("alice", "user-2", "bob"));

        var result = resolver.resolve(request(
                "purchase_order",
                "record-1",
                config("purchase_order", "approvers")));

        assertEquals(
                List.of("alice", "user-2", "bob"),
                result.principals().stream().map(item -> item.key()).toList());
        assertTrue(result.principals().stream().allMatch(
                item -> item.type() == PersonPrincipalType.USER));
        verify(referencePort).readUserKeys(
                "purchase_order", "record-1", "approvers");
    }

    @Test
    void resolveMapsExpectedEntityRecordFailureToStableResolutionReason() {
        when(referencePort.readUserKeys(
                "purchase_order", "record-404", "approver"))
                .thenThrow(new EntityUserReferenceException(
                        "ENTITY_USER_REFERENCE_RECORD_MISSING",
                        "实体记录不存在"));

        PersonResolutionException failure = assertThrows(
                PersonResolutionException.class,
                () -> resolver.resolve(request(
                        "purchase_order",
                        "record-404",
                        config("purchase_order", "approver"))));

        assertEquals(
                "ENTITY_USER_REFERENCE_RECORD_MISSING",
                failure.reasonCode());
        assertEquals("approver", failure.details().get("fieldCode"));
    }

    @Test
    void resolveDoesNotDisguiseInfrastructureFailureAsAnEmptyAssignment() {
        IllegalStateException infrastructureFailure =
                new IllegalStateException("database unavailable");
        when(referencePort.readUserKeys(
                "purchase_order", "record-1", "approver"))
                .thenThrow(infrastructureFailure);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> resolver.resolve(request(
                        "purchase_order",
                        "record-1",
                        config("purchase_order", "approver"))));

        assertEquals(infrastructureFailure, failure);
    }

    private PersonResolverConfigurationValidationRequest validationRequest(
            PersonResolveUsage usage,
            String assignmentMode,
            boolean multiInstance,
            Map<String, Object> extraParams) {
        return new PersonResolverConfigurationValidationRequest(
                usage, assignmentMode, multiInstance, extraParams);
    }

    private PersonResolveRequest request(
            String entityCode,
            String entityDataId,
            Map<String, Object> extraParams) {
        return new PersonResolveRequest(
                1,
                "trace-1",
                "ASSIGNEE:task-1",
                PersonResolveUsage.ASSIGNEE,
                "process-config-1",
                "process-definition-1",
                "process-instance-1",
                entityDataId,
                "approve",
                "审批",
                "task-1",
                entityCode,
                entityDataId,
                "starter",
                null,
                Map.of(),
                Map.of("approver", "forged-value"),
                extraParams);
    }

    private Map<String, Object> config(
            String entityCode,
            String fieldCode) {
        return Map.of(
                "schemaVersion", 1,
                "entityCode", entityCode,
                "fieldCode", fieldCode);
    }
}
