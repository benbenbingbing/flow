package com.workflow.entity.ui.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.entity.ui.api.request.UiViewCompositionResolveRequest;
import com.workflow.entity.ui.api.response.UiViewCompositionResolveResponse;
import com.workflow.entity.ui.application.UiViewCompositionRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiViewCompositionRuntimeControllerAccessPolicyTest {

    @Test
    void resolveEndpointDeclaresObjectAuthorization() {
        AuthenticatedApi policy =
                AnnotatedElementUtils.findMergedAnnotation(
                        UiViewCompositionRuntimeController.class,
                        AuthenticatedApi.class);

        assertNotNull(policy);
        assertTrue(policy.objectAuthorization());
    }

    @Test
    void resolveDelegatesOpaqueRequestWithoutAddingClientRowData() {
        UiViewCompositionRuntimeService service =
                mock(UiViewCompositionRuntimeService.class);
        UiViewCompositionRuntimeController controller =
                new UiViewCompositionRuntimeController(service);
        UiViewCompositionResolveRequest request =
                new UiViewCompositionResolveRequest();
        UiViewCompositionResolveResponse response =
                UiViewCompositionResolveResponse.builder()
                        .compositionKey("project-requirements")
                        .build();
        when(service.resolve(request)).thenReturn(response);

        assertEquals(response, controller.resolve(request).getData());
        verify(service).resolve(request);
    }
}
