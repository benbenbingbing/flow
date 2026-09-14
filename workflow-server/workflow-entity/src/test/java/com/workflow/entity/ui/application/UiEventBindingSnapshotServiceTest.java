package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiEventBindingSnapshotServiceTest {

    @Test
    void lazyDataSourceDependencyBreaksReleaseConstructionCycle() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.register(
                    UiEventBindingSnapshotService.class,
                    LazyCycleConfiguration.class);
            context.refresh();

            assertNotNull(context.getBean(
                    UiEventBindingSnapshotService.class));
            assertNotNull(context.getBean(UiInterfaceExtensionService.class));
        }
    }

    @Test
    void snapshotOwnerOnlyReadsNormalizedLocalOwner() {
        UiEventBindingMapper bindingMapper =
                mock(UiEventBindingMapper.class);
        UiExtensionDefinitionMapper dataSourceMapper =
                mock(UiExtensionDefinitionMapper.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        UiEventBindingSnapshotService service =
                new UiEventBindingSnapshotService(
                        bindingMapper,
                        dataSourceMapper,
                        mock(UiInterfaceExtensionService.class),
                        codec);
        UiEventBinding binding = new UiEventBinding();
        binding.setId("binding-1");
        binding.setOwnerType("FORM");
        binding.setOwnerId("form-1");
        binding.setTargetType("BUTTON");
        binding.setTargetKey("submit");
        binding.setEventCode("CLICK");
        binding.setInheritanceMode("OVERRIDE");
        binding.setRevision(3);
        when(bindingMapper.findByOwner(
                "FORM", "form-1"))
                .thenReturn(List.of(binding));

        List<Map<String, Object>> result =
                service.snapshotOwner("form", "form-1");

        assertEquals(1, result.size());
        assertEquals("binding-1", result.get(0).get("id"));
        assertEquals("FORM", result.get(0).get("ownerType"));
        assertEquals("form-1", result.get(0).get("ownerId"));
        assertEquals(List.of(), result.get(0).get("steps"));
        verify(bindingMapper).findByOwner("FORM", "form-1");
    }

    @Test
    void sharedEntityEventKeepsOnlyStepsForPublishedPageContext() {
        UiEventBindingMapper bindingMapper =
                mock(UiEventBindingMapper.class);
        UiExtensionDefinitionMapper dataSourceMapper =
                mock(UiExtensionDefinitionMapper.class);
        UiInterfaceExtensionService dataSourceService =
                mock(UiInterfaceExtensionService.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        UiEventBindingSnapshotService service =
                new UiEventBindingSnapshotService(
                        bindingMapper,
                        dataSourceMapper,
                        dataSourceService,
                        codec);
        UiExtensionDefinition formInterface = new UiExtensionDefinition();
        formInterface.setId("form-interface");
        formInterface.setExtensionType("INTERFACE");
        formInterface.setInterfaceContextType("FORM");
        UiExtensionDefinition listInterface = new UiExtensionDefinition();
        listInterface.setId("list-interface");
        listInterface.setExtensionType("INTERFACE");
        listInterface.setInterfaceContextType("LIST");
        UiEventBinding binding = new UiEventBinding();
        binding.setId("binding-entity");
        binding.setOwnerType("ENTITY");
        binding.setOwnerId("entity-1");
        binding.setTargetType("OWNER");
        binding.setTargetKey("");
        binding.setEventCode("DETAIL_LOAD");
        binding.setInheritanceMode("INHERIT");
        binding.setRevision(1);
        binding.setStepsDocument(codec.write(
                List.of(
                        Map.of(
                                "stepCode", "form-step",
                                "serviceId", "source-mixed",
                                "operationCode", "formDetail"),
                        Map.of(
                                "stepCode", "list-step",
                                "serviceId", "source-mixed",
                                "operationCode", "listDetail"),
                        Map.of(
                                "stepCode", "mapping-step",
                                "outputMapping", Map.of("name", "result.name"))),
                "测试事件步骤"));
        when(dataSourceService.resolveDefinitionReference(
                "source-mixed", "formDetail"))
                .thenReturn(formInterface);
        when(dataSourceService.resolveDefinitionReference(
                "source-mixed", "listDetail"))
                .thenReturn(listInterface);
        when(dataSourceService.requireExecutableDefinition(
                "form-interface", null)).thenReturn(formInterface);
        when(dataSourceService.requireExecutableDefinition(
                "list-interface", null)).thenReturn(listInterface);
        when(bindingMapper.findForSnapshot(
                "FORM", "form-1", "entity-1"))
                .thenReturn(List.of(binding));
        when(bindingMapper.findForSnapshot(
                "LIST", "list-1", "entity-1"))
                .thenReturn(List.of(binding));

        List<Map<String, Object>> formSnapshot = service.snapshot(
                "FORM", "form-1", "entity-1");
        List<Map<String, Object>> listSnapshot = service.snapshot(
                "LIST", "list-1", "entity-1");

        assertEquals(
                List.of("form-step", "mapping-step"),
                stepCodes(formSnapshot.get(0)));
        assertEquals(
                List.of("list-step", "mapping-step"),
                stepCodes(listSnapshot.get(0)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishedSnapshotPinsExecutableOperationAndIdentity() {
        UiEventBindingMapper bindingMapper =
                mock(UiEventBindingMapper.class);
        UiExtensionDefinitionMapper dataSourceMapper =
                mock(UiExtensionDefinitionMapper.class);
        UiInterfaceExtensionService dataSourceService =
                mock(UiInterfaceExtensionService.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        UiEventBindingSnapshotService service =
                new UiEventBindingSnapshotService(
                        bindingMapper,
                        dataSourceMapper,
                        dataSourceService,
                        codec);
        UiEventBinding binding = new UiEventBinding();
        binding.setId("binding-button");
        binding.setOwnerType("FORM");
        binding.setOwnerId("form-1");
        binding.setTargetType("BUTTON");
        binding.setTargetKey("generate-report");
        binding.setEventCode("FORM_BUTTON_CLICK");
        binding.setInheritanceMode("INHERIT");
        binding.setRevision(2);
        binding.setStepsDocument(codec.write(
                List.of(Map.of(
                        "extensionId", "interface-1",
                        "strategy", "REPLACE")),
                "测试事件步骤"));
        when(bindingMapper.findForSnapshot(
                "FORM", "form-1", "entity-1"))
                .thenReturn(List.of(binding));
        UiInterfaceExtensionService.PublishedOperationSnapshot operation =
                new UiInterfaceExtensionService.PublishedOperationSnapshot(
                        "interface-1",
                        "report-service",
                        7,
                        "generate",
                        "{\"schemaVersion\":2}",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setId("interface-1");
        definition.setExtensionType("INTERFACE");
        definition.setInterfaceContextType("FORM");
        when(dataSourceService.resolveDefinitionReference(
                "interface-1", null)).thenReturn(definition);
        when(dataSourceService.freezeExtension("interface-1"))
                .thenReturn(operation);

        List<Map<String, Object>> snapshot = service.snapshot(
                "FORM", "form-1", "entity-1", true);

        Map<String, Object> step = (Map<String, Object>)
                ((List<?>) snapshot.get(0).get("steps")).get(0);
        assertEquals(2, step.get("operationSnapshotVersion"));
        assertEquals("interface-1", step.get("extensionId"));
        assertEquals("report-service", step.get("extensionKey"));
        assertEquals(7, step.get("extensionRevision"));
        assertEquals(false, step.containsKey("operationCode"));
        assertEquals(false, step.containsKey("sourceCode"));
        assertEquals(false, step.containsKey("serviceRevision"));
        assertEquals(operation.document(), step.get("executableSnapshot"));
        assertEquals(operation.hash(), step.get("definitionHash"));
        verify(dataSourceService).validatePinnedReadExtension(
                operation.document(),
                operation.hash(),
                "interface-1",
                "report-service",
                7,
                "FORM");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonFormButtonPublishedSnapshotNormalizesLegacyReference() {
        UiEventBindingMapper bindingMapper =
                mock(UiEventBindingMapper.class);
        UiInterfaceExtensionService dataSourceService =
                mock(UiInterfaceExtensionService.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        UiEventBinding binding = new UiEventBinding();
        binding.setId("binding-list-load");
        binding.setOwnerType("LIST");
        binding.setOwnerId("list-1");
        binding.setTargetType("OWNER");
        binding.setEventCode("LIST_LOAD");
        binding.setInheritanceMode("INHERIT");
        binding.setRevision(1);
        binding.setStepsDocument(codec.write(
                List.of(Map.of(
                        "serviceId", "service-write",
                        "operationCode", "write-op",
                        "strategy", "REPLACE",
                        "operationSnapshotVersion", 99,
                        "sourceCode", "legacy-extension",
                        "serviceRevision", 12,
                        "executableSnapshot", "legacy-document",
                        "definitionHash", "legacy-hash")),
                "测试事件步骤"));
        when(bindingMapper.findForSnapshot(
                "LIST", "list-1", "entity-1"))
                .thenReturn(List.of(binding));
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setId("interface-write");
        definition.setExtensionType("INTERFACE");
        definition.setInterfaceContextType("LIST");
        when(dataSourceService.resolveDefinitionReference(
                "service-write", "write-op"))
                .thenReturn(definition);
        UiEventBindingSnapshotService service =
                new UiEventBindingSnapshotService(
                        bindingMapper,
                        mock(UiExtensionDefinitionMapper.class),
                        dataSourceService,
                        codec);

        List<Map<String, Object>> snapshot = service.snapshot(
                "LIST", "list-1", "entity-1", true);

        Map<String, Object> step = (Map<String, Object>)
                ((List<?>) snapshot.get(0).get("steps")).get(0);
        assertEquals("interface-write", step.get("extensionId"));
        assertEquals(false, step.containsKey("serviceId"));
        assertEquals(false, step.containsKey("operationCode"));
        assertEquals(false, step.containsKey("operationSnapshotVersion"));
        assertEquals(false, step.containsKey("sourceCode"));
        assertEquals(false, step.containsKey("serviceRevision"));
        assertEquals(false, step.containsKey("executableSnapshot"));
        assertEquals(false, step.containsKey("definitionHash"));
        verify(dataSourceService, never()).freezeExtension(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void editableSnapshotStripsPublishedOperationFields() {
        UiEventBindingMapper bindingMapper =
                mock(UiEventBindingMapper.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        UiEventBinding binding = new UiEventBinding();
        binding.setId("binding-button");
        binding.setOwnerType("FORM");
        binding.setOwnerId("form-1");
        binding.setEventCode("FORM_BUTTON_CLICK");
        binding.setStepsDocument(codec.write(
                List.of(Map.ofEntries(
                        Map.entry("serviceId", "service-1"),
                        Map.entry("operationCode", "generate"),
                        Map.entry("serviceName", "历史服务名称"),
                        Map.entry("operationName", "历史操作名称"),
                        Map.entry("providerOperationCode", "internal-route"),
                        Map.entry("legacyServiceId", "legacy-service"),
                        Map.entry("operationSnapshotVersion", 1),
                        Map.entry("sourceCode", "published-source"),
                        Map.entry("serviceRevision", 7),
                        Map.entry("executableSnapshot", "published-snapshot"),
                        Map.entry("definitionHash", "published-hash"))),
                "测试事件步骤"));
        when(bindingMapper.findForSnapshot(
                "FORM", "form-1", "entity-1"))
                .thenReturn(List.of(binding));
        UiInterfaceExtensionService dataSourceService =
                mock(UiInterfaceExtensionService.class);
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setId("interface-1");
        definition.setExtensionType("INTERFACE");
        when(dataSourceService.resolveDefinitionReference(
                "service-1", "generate"))
                .thenReturn(definition);
        UiEventBindingSnapshotService service =
                new UiEventBindingSnapshotService(
                        bindingMapper,
                        mock(UiExtensionDefinitionMapper.class),
                        dataSourceService,
                        codec);

        List<Map<String, Object>> snapshot = service.snapshot(
                "FORM", "form-1", "entity-1", false);

        Map<String, Object> step = (Map<String, Object>)
                ((List<?>) snapshot.get(0).get("steps")).get(0);
        assertEquals("interface-1", step.get("extensionId"));
        assertEquals(false, step.containsKey("serviceId"));
        assertEquals(false, step.containsKey("operationCode"));
        assertEquals(false, step.containsKey("operationSnapshotVersion"));
        assertEquals(false, step.containsKey("sourceCode"));
        assertEquals(false, step.containsKey("serviceRevision"));
        assertEquals(false, step.containsKey("executableSnapshot"));
        assertEquals(false, step.containsKey("definitionHash"));
        assertEquals(false, step.containsKey("serviceName"));
        assertEquals(false, step.containsKey("operationName"));
        assertEquals(false, step.containsKey("providerOperationCode"));
        assertEquals(false, step.containsKey("legacyServiceId"));
    }

    @Test
    void restoringReleaseStripsPublishedOperationFieldsFromDraftStorage() {
        UiEventBindingMapper bindingMapper =
                mock(UiEventBindingMapper.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        UiInterfaceExtensionService dataSourceService =
                mock(UiInterfaceExtensionService.class);
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setId("interface-1");
        definition.setExtensionType("INTERFACE");
        when(dataSourceService.resolveDefinitionReference(
                "service-1", "generate"))
                .thenReturn(definition);
        UiEventBindingSnapshotService service =
                new UiEventBindingSnapshotService(
                        bindingMapper,
                        mock(UiExtensionDefinitionMapper.class),
                        dataSourceService,
                        codec);

        service.restoreLocalBindingsForRelease(
                "FORM",
                "form-1",
                List.of(Map.of(
                        "id", "binding-1",
                        "ownerType", "FORM",
                        "ownerId", "form-1",
                        "targetType", "BUTTON",
                        "targetKey", "generate_report",
                        "eventCode", "FORM_BUTTON_CLICK",
                        "inheritanceMode", "INHERIT",
                        "steps", List.of(Map.of(
                                "serviceId", "service-1",
                                "operationCode", "generate",
                                "operationSnapshotVersion", 1,
                                "sourceCode", "published-source",
                                "serviceRevision", 7,
                                "executableSnapshot", "published-snapshot",
                                "definitionHash", "published-hash")))));

        ArgumentCaptor<UiEventBinding> restored =
                ArgumentCaptor.forClass(UiEventBinding.class);
        verify(bindingMapper).insert(restored.capture());
        Map<?, ?> step = (Map<?, ?>) codec.readArray(
                restored.getValue().getStepsDocument(),
                "恢复步骤测试").get(0);
        assertEquals("interface-1", step.get("extensionId"));
        assertEquals(false, step.containsKey("serviceId"));
        assertEquals(false, step.containsKey("operationCode"));
        assertEquals(false, step.containsKey("operationSnapshotVersion"));
        assertEquals(false, step.containsKey("sourceCode"));
        assertEquals(false, step.containsKey("serviceRevision"));
        assertEquals(false, step.containsKey("executableSnapshot"));
        assertEquals(false, step.containsKey("definitionHash"));
    }

    private List<String> stepCodes(Map<String, Object> binding) {
        if (!(binding.get("steps") instanceof List<?> steps)) {
            return List.of();
        }
        return steps.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(step -> String.valueOf(step.get("stepCode")))
                .toList();
    }

    /** 用直接反向依赖模拟生产中经发布服务形成的构造环。 */
    @Configuration(proxyBeanMethods = false)
    static class LazyCycleConfiguration {

        @Bean
        UiEventBindingMapper eventBindingMapper() {
            return mock(UiEventBindingMapper.class);
        }

        @Bean
        UiExtensionDefinitionMapper dataSourceDefinitionMapper() {
            return mock(UiExtensionDefinitionMapper.class);
        }

        @Bean
        JsonDocumentCodec jsonDocumentCodec() {
            return new JsonDocumentCodec(new ObjectMapper());
        }

        @Bean
        UiInterfaceExtensionService uiDataSourceService(
                UiEventBindingSnapshotService snapshotService) {
            assertNotNull(snapshotService);
            return mock(UiInterfaceExtensionService.class);
        }

        @Bean
        UiPublishedDataSourceReferenceGuard publishedReferenceGuard() {
            return mock(UiPublishedDataSourceReferenceGuard.class);
        }
    }
}
