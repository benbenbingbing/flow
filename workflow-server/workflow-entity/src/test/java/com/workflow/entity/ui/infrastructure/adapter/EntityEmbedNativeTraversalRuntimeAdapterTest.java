package com.workflow.entity.ui.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.workflow.contracts.embed.EmbedNativeTraversalRuntimePort;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.application.UiViewCompositionTokenService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityEmbedNativeTraversalRuntimeAdapterTest {

    @Test
    void derivesChildEntityFromSignedTargetFormInsteadOfBrowserCoordinates() {
        UiViewCompositionTokenService tokenService = mock(
                UiViewCompositionTokenService.class);
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityListConfigMapper listMapper = mock(
                EntityListConfigMapper.class);
        EntityDefinitionMapper definitionMapper = mock(
                EntityDefinitionMapper.class);
        UiViewCompositionTokenService.TraversalHop root =
                new UiViewCompositionTokenService.TraversalHop(
                        "FORM", "root-form", "root-release", 4,
                        "related-customer", "root-record");
        when(tokenService.verifyTraversalContext("signed-traversal"))
                .thenReturn(new UiViewCompositionTokenService.Claims(
                        "TRAVERSAL",
                        "FORM", "root-form", "root-release", 4,
                        "related-customer", null, "root-record",
                        null, null, null, null, Map.of(), false,
                        "FORM", "child-form", "child-release", 8,
                        "child-record", List.of(root),
                        "user-1", 1L, 9999999999L));
        EntityForm childForm = new EntityForm();
        childForm.setId("child-form");
        childForm.setEntityId("entity-customer");
        when(formMapper.selectById("child-form")).thenReturn(childForm);
        EntityDefinition customer = new EntityDefinition();
        customer.setId("entity-customer");
        customer.setEntityCode("customer");
        when(definitionMapper.selectById("entity-customer"))
                .thenReturn(customer);
        EntityEmbedNativeTraversalRuntimeAdapter adapter =
                new EntityEmbedNativeTraversalRuntimeAdapter(
                        tokenService, formMapper, listMapper,
                        definitionMapper);

        EmbedNativeTraversalRuntimePort.TraversalTarget result =
                adapter.resolve("signed-traversal");

        assertEquals("root-form", result.rootOwnerId());
        assertEquals("root-record", result.rootRecordId());
        assertEquals("child-form", result.targetOwnerId());
        assertEquals("child-release", result.targetReleaseId());
        assertEquals("child-record", result.targetRecordId());
        assertEquals("customer", result.targetEntityCode());
    }
}
