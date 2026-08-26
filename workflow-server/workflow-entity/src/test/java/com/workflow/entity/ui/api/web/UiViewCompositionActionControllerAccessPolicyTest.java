package com.workflow.entity.ui.api.web;

import com.workflow.core.security.AuthenticatedApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiViewCompositionActionControllerAccessPolicyTest {

    @Test
    void runtimeActionEndpointRequiresAuthenticatedObjectAuthorization() {
        AuthenticatedApi policy = UiViewCompositionActionController.class
                .getAnnotation(AuthenticatedApi.class);

        assertNotNull(policy);
        assertTrue(policy.objectAuthorization());
    }
}
