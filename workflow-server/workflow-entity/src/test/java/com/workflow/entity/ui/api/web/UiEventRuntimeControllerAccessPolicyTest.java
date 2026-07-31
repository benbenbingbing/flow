package com.workflow.entity.ui.api.web;

import com.workflow.core.security.AuthenticatedApi;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiEventRuntimeControllerAccessPolicyTest {

    @Test
    void runtimeEventEndpointDeclaresObjectAuthorization() {
        AuthenticatedApi policy =
                AnnotatedElementUtils.findMergedAnnotation(
                        UiEventRuntimeController.class,
                        AuthenticatedApi.class);

        assertNotNull(policy);
        assertTrue(policy.objectAuthorization());
    }
}
