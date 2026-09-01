package com.workflow.entity.ui.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.contracts.embed.EmbedDelegatedRuntimeApi;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

        EmbedDelegatedRuntimeApi delegated =
                AnnotatedElementUtils.findMergedAnnotation(
                        UiEventRuntimeController.class,
                        EmbedDelegatedRuntimeApi.class);
        assertNotNull(delegated);
        assertEquals(
                EmbedDelegatedRuntimeApi.Capability.NONE,
                delegated.requiredCapability());
        assertEquals(
                EmbedDelegatedRuntimeApi.TargetBinding.FORM_EVENT_BODY,
                delegated.targetBinding());
    }
}
