package com.workflow.migration.application;

import com.workflow.contracts.identity.port.OrganizationPositionDirectoryPort;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolverConfigurationValidationRequest;
import com.workflow.contracts.process.assignment.spi.PersonResolverConfigurationValidator;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 动态规则只检查目标契约，且同一解析器的各个用途都必须满足。 */
class ConfigMigrationAssignmentTargetValidatorTest {
    @Test
    void resolverRequiresTargetRegistrationForEveryUsageWithoutExecutingIt() {
        PersonResolverRuntimeService runtime = mock(PersonResolverRuntimeService.class);
        OrganizationPositionDirectoryPort directory = mock(OrganizationPositionDirectoryPort.class);
        var validator = new ConfigMigrationAssignmentTargetValidator(runtime, directory, List.of());
        Map<String, Object> dependency = resolver("people", List.of(reference("ASSIGNEE", Map.of()),
                reference("MULTI_INSTANCE", Map.of())));
        when(runtime.supportsConfigured("targetPeople", PersonResolveUsage.ASSIGNEE)).thenReturn(true);
        assertFalse(validator.resolved(dependency, "PERSON_RESOLVER", "targetPeople",
                (type, key) -> "PERSON_RESOLVER".equals(type) ? "targetPeople" : key, key -> Map.of()));
        when(runtime.supportsConfigured("targetPeople", PersonResolveUsage.MULTI_INSTANCE)).thenReturn(true);
        assertTrue(validator.resolved(dependency, "PERSON_RESOLVER", "targetPeople",
                (type, key) -> "PERSON_RESOLVER".equals(type) ? "targetPeople" : key, key -> Map.of()));
        verify(runtime, never()).resolveUsernames(anyString(), any());
        verifyNoInteractions(directory);
    }

    @Test
    void parameterValidatorReceivesMappedCoordinates() {
        PersonResolverRuntimeService runtime = mock(PersonResolverRuntimeService.class);
        PersonResolverConfigurationValidator rules = mock(PersonResolverConfigurationValidator.class);
        when(rules.resolverCode()).thenReturn("relativeOrgPosition");
        when(runtime.supportsConfigured("relativeOrgPosition", PersonResolveUsage.ASSIGNEE)).thenReturn(true);
        var validator = new ConfigMigrationAssignmentTargetValidator(runtime,
                mock(OrganizationPositionDirectoryPort.class), List.of(rules));
        Map<String, Object> params = Map.of("positionCode", "SOURCE_POSITION", "hierarchy",
                Map.of("mode", "BUSINESS_LEVEL", "businessLevelCode", "SOURCE_LEVEL"));
        var dependency = resolver("relativeOrgPosition", List.of(reference("ASSIGNEE", params)));
        assertTrue(validator.resolved(dependency, "PERSON_RESOLVER", "relativeOrgPosition",
                (type, key) -> key.replace("SOURCE_", "TARGET_"), key -> Map.of()));
        verify(rules).validate(argThat((PersonResolverConfigurationValidationRequest request) ->
                "TARGET_POSITION".equals(request.extraParams().get("positionCode"))
                        && "TARGET_LEVEL".equals(((Map<?, ?>) request.extraParams().get("hierarchy")).get("businessLevelCode"))));
        verify(runtime, never()).resolveUsernames(anyString(), any());
    }

    @Test
    void fieldMappingIsAppliedOnceAndRequiresUserFieldCardinality() {
        PersonResolverRuntimeService runtime = mock(PersonResolverRuntimeService.class);
        when(runtime.supportsConfigured("entityUserReferenceField", PersonResolveUsage.ASSIGNEE)).thenReturn(true);
        var validator = new ConfigMigrationAssignmentTargetValidator(runtime,
                mock(OrganizationPositionDirectoryPort.class), List.of());
        var dependency = resolver("entityUserReferenceField", List.of(reference("ASSIGNEE",
                Map.of("schemaVersion", 1, "entityCode", "expense", "fieldCode", "reviewer"))));
        assertTrue(validator.resolved(dependency, "PERSON_RESOLVER", "entityUserReferenceField",
                (type, key) -> "ENTITY_USER_FIELD".equals(type) ? "expense_target/owner"
                        : "ENTITY".equals(type) ? "WRONG_DOUBLE_MAPPING" : key,
                key -> "expense_target/owner".equals(key) ? Map.of("fieldType", "USER") : Map.of()));
        assertFalse(validator.resolved(dependency, "PERSON_RESOLVER", "entityUserReferenceField",
                (type, key) -> key, key -> Map.of()));
        assertThrows(IllegalArgumentException.class, () -> validator.resolved(dependency,
                "PERSON_RESOLVER", "entityUserReferenceField", (type, key) -> key,
                key -> Map.of("fieldType", "MULTI_REFERENCE")));
    }

    @Test
    void plannedEntityFieldIsValidatedBeforeItExistsInTargetDatabase() {
        ConfigMigrationPackageService service = mock(ConfigMigrationPackageService.class, CALLS_REAL_METHODS);
        PersonResolverRuntimeService runtime = mock(PersonResolverRuntimeService.class);
        var validator = new ConfigMigrationAssignmentTargetValidator(runtime,
                mock(OrganizationPositionDirectoryPort.class), List.of());
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        ReflectionTestUtils.setField(service, "documents", new ConfigMigrationPackageDocumentSupport(json));
        ReflectionTestUtils.setField(service, "assignmentTargetValidator", validator);
        ReflectionTestUtils.setField(service, "environmentMappingMapper",
                mock(com.workflow.migration.infrastructure.persistence.mapper.ConfigEnvironmentMappingMapper.class));
        var process = new com.workflow.migration.infrastructure.persistence.record.ConfigImportItem();
        process.setAssetType("PROCESS");
        process.setBusinessKey("approval");
        process.setSnapshotJson("{}");
        process.setDependenciesJson(ConfigMigrationAssignmentSupport.write(List.of(Map.of(
                "type", "ENTITY_USER_FIELD", "key", "expense/reviewer", "required", true, "targetOnly", true))));
        var entity = new com.workflow.migration.infrastructure.persistence.record.ConfigImportItem();
        entity.setAssetType("ENTITY");
        entity.setBusinessKey("expense");
        entity.setDependenciesJson("[]");
        entity.setSnapshotJson("{\"fields\":[{\"fieldCode\":\"reviewer\",\"fieldType\":\"USER\"}]}");
        assertDoesNotThrow(() -> service.requireResolvedDependencies(List.of(process, entity)));
        entity.setSnapshotJson("{\"fields\":[{\"fieldCode\":\"reviewer\",\"fieldType\":\"STRING\"}]}");
        assertThrows(IllegalStateException.class, () -> service.requireResolvedDependencies(List.of(process, entity)));
    }

    private Map<String, Object> resolver(String code, List<Map<String, Object>> references) {
        return Map.of("type", "PERSON_RESOLVER", "key", code, "references", references);
    }

    private Map<String, Object> reference(String usage, Map<String, Object> params) {
        return Map.of("nodeId", "review", "usage", usage, "assignmentMode", "DIRECT",
                "multiInstance", "MULTI_INSTANCE".equals(usage), "extraParams", params);
    }
}
