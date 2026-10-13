package com.workflow.entity.form.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityEmbedNativeFormRuntimeAdapterTest {
    @Test
    void childFormRequiresPublishedPathFromTrustedRoot() {
        EntityDefinitionMapper entities=mock(EntityDefinitionMapper.class);
        UiConfigReleaseService releases=mock(UiConfigReleaseService.class);
        EntityForm form=new EntityForm(); form.setId("child"); form.setEntityId("entity");
        EntityDefinition entity=new EntityDefinition(); entity.setId("entity"); entity.setEntityCode("customer");
        when(entities.selectById("entity")).thenReturn(entity);
        ResolvedEntityFormRelease current=new ResolvedEntityFormRelease(form,"hash",1,false);
        when(releases.resolveRuntimeFormRelease("child")).thenReturn(current);
        when(releases.resolveRuntimeFormRelease("child","hash",1,UiRuntimeResolutionContext.standalone())).thenReturn(current);
        EntityEmbedNativeFormRuntimeAdapter adapter=new EntityEmbedNativeFormRuntimeAdapter(entities,releases);
        var result=adapter.authorizeTarget("root","child",Map.of("componentPath",List.of("customer")));
        assertEquals("customer",result.entityCode());
        verify(releases).requireComponentPath("root","child",List.of("customer"));
        doThrow(new BusinessForbiddenException("CHILD_FORM_NOT_REFERENCED","路径不属于已发布配置"))
                .when(releases).requireComponentPath("root","other",List.of("customer"));
        assertThrows(BusinessForbiddenException.class,()->adapter.authorizeTarget("root","other",Map.of("componentPath",List.of("customer"))));
        verify(releases,never()).resolveRuntimeFormRelease("other");
    }
}
