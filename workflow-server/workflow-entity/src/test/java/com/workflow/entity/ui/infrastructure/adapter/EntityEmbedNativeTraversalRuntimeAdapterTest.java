package com.workflow.entity.ui.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ui.application.UiViewCompositionRuntimeService;
import com.workflow.entity.ui.api.response.UiViewCompositionResolveResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityEmbedNativeTraversalRuntimeAdapterTest {
    @Test
    void derivesTargetFromRevalidatedBusinessPath() {
        UiViewCompositionRuntimeService runtime=mock(UiViewCompositionRuntimeService.class);
        when(runtime.resolve(any())).thenReturn(UiViewCompositionResolveResponse.builder().targetContentType("FORM")
                .targetContentId("child").targetReleaseId("hash").targetReleaseVersion(1).targetRecordId("child-record")
                .targetEntityCode("customer").build());
        var adapter=new EntityEmbedNativeTraversalRuntimeAdapter(runtime,new ObjectMapper());
        var result=adapter.resolve(Map.of("ownerType","FORM","ownerId","root","recordId","root-record","compositionKey","customer"));
        assertEquals("root",result.rootOwnerId()); assertEquals("customer",result.targetEntityCode());
        assertEquals("child-record",result.targetRecordId()); verify(runtime).resolve(any());
    }
}
