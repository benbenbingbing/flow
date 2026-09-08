package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiEventBindingSnapshotServiceTest {

    @Test
    void snapshotOwnerOnlyReadsNormalizedLocalOwner() {
        UiEventBindingMapper bindingMapper =
                mock(UiEventBindingMapper.class);
        UiDataSourceDefinitionMapper dataSourceMapper =
                mock(UiDataSourceDefinitionMapper.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        UiEventBindingSnapshotService service =
                new UiEventBindingSnapshotService(
                        bindingMapper,
                        dataSourceMapper,
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
        UiDataSourceDefinitionMapper dataSourceMapper =
                mock(UiDataSourceDefinitionMapper.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        UiEventBindingSnapshotService service =
                new UiEventBindingSnapshotService(
                        bindingMapper,
                        dataSourceMapper,
                        codec);
        UiDataSourceDefinition source = new UiDataSourceDefinition();
        source.setId("source-mixed");
        source.setOperationsDocument(codec.write(
                List.of(
                        Map.of(
                                "code", "formDetail",
                                "contextType", "FORM"),
                        Map.of(
                                "code", "listDetail",
                                "contextType", "LIST")),
                "测试接口操作"));
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
        when(dataSourceMapper.selectById("source-mixed"))
                .thenReturn(source);
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
}
