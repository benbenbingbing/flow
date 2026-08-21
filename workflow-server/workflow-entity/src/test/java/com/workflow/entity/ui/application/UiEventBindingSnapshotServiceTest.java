package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
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
}
