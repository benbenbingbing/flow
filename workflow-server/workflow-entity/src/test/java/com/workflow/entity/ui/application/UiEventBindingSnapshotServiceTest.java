package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 实体级 UI 事件继承到发布快照时的运行上下文筛选测试。
 */
class UiEventBindingSnapshotServiceTest {

    @Test
    void includesEntityBindingOnlyInMatchingOperationContextSnapshot() {
        JsonDocumentCodec codec = codec();
        UiEventBindingMapper bindingMapper =
                mock(UiEventBindingMapper.class);
        UiDataSourceDefinitionMapper sourceMapper =
                mock(UiDataSourceDefinitionMapper.class);
        UiEventBinding binding = binding(
                codec,
                "ENTITY",
                "FORM_OPEN",
                "service-form",
                "FORM_OPEN");
        UiDataSourceDefinition source = source(
                codec,
                "service-form",
                "FORM_OPEN",
                "FORM");

        when(bindingMapper.findForSnapshot(
                "FORM", "form-1", "entity-1"))
                .thenReturn(List.of(binding));
        when(bindingMapper.findForSnapshot(
                "LIST", "list-1", "entity-1"))
                .thenReturn(List.of(binding));
        when(sourceMapper.selectById("service-form"))
                .thenReturn(source);

        UiEventBindingSnapshotService service =
                new UiEventBindingSnapshotService(
                        bindingMapper,
                        sourceMapper,
                        codec);

        assertEquals(
                1,
                service.snapshot(
                        "FORM",
                        "form-1",
                        "entity-1").size());
        assertTrue(
                service.snapshot(
                        "LIST",
                        "list-1",
                        "entity-1").isEmpty());
    }

    @Test
    void keepsLocalBindingForStrictPublishValidation() {
        JsonDocumentCodec codec = codec();
        UiEventBindingMapper bindingMapper =
                mock(UiEventBindingMapper.class);
        UiDataSourceDefinitionMapper sourceMapper =
                mock(UiDataSourceDefinitionMapper.class);
        UiEventBinding binding = binding(
                codec,
                "LIST",
                "LIST_LOAD",
                "service-form",
                "FORM_OPEN");

        when(bindingMapper.findForSnapshot(
                "LIST", "list-1", "entity-1"))
                .thenReturn(List.of(binding));

        UiEventBindingSnapshotService service =
                new UiEventBindingSnapshotService(
                        bindingMapper,
                        sourceMapper,
                        codec);

        assertEquals(
                1,
                service.snapshot(
                        "LIST",
                        "list-1",
                        "entity-1").size());
    }

    private UiEventBinding binding(
            JsonDocumentCodec codec,
            String ownerType,
            String eventCode,
            String serviceId,
            String operationCode) {
        UiEventBinding binding = new UiEventBinding();
        binding.setId("binding-1");
        binding.setOwnerType(ownerType);
        binding.setOwnerId("entity-1");
        binding.setTargetType("OWNER");
        binding.setTargetKey("");
        binding.setEventCode(eventCode);
        binding.setInheritanceMode("INHERIT");
        binding.setStepsDocument(codec.write(
                List.of(Map.of(
                        "serviceId", serviceId,
                        "operationCode", operationCode,
                        "strategy", "BEFORE",
                        "order", 10)),
                "测试事件步骤"));
        binding.setRevision(1);
        return binding;
    }

    private UiDataSourceDefinition source(
            JsonDocumentCodec codec,
            String id,
            String operationCode,
            String contextType) {
        UiDataSourceDefinition source =
                new UiDataSourceDefinition();
        source.setId(id);
        source.setOperationsDocument(codec.write(
                List.of(Map.of(
                        "code", operationCode,
                        "contextType", contextType)),
                "测试接口操作"));
        return source;
    }

    private JsonDocumentCodec codec() {
        return new JsonDocumentCodec(
                new ObjectMapper().findAndRegisterModules());
    }
}
