package com.workflow.entity.ui.api.web;

import com.workflow.core.security.AuthenticatedApi;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiViewCompositionControllerTest {

    @Test
    void designEndpointsDeclareObjectAuthorization() {
        AuthenticatedApi policy = AnnotatedElementUtils.findMergedAnnotation(
                UiViewCompositionController.class,
                AuthenticatedApi.class);

        assertNotNull(policy);
        assertTrue(policy.objectAuthorization());
    }

    @Test
    void exposesCollectionValidationAndRealDataTestRoutes() {
        Method[] methods = UiViewCompositionController.class.getDeclaredMethods();

        assertTrue(Arrays.stream(methods)
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(java.util.Objects::nonNull)
                .anyMatch(mapping -> mapping.value().length == 0));
        assertTrue(hasPostRoute(methods, "/validate"));
        assertTrue(hasPostRoute(methods, "/test"));
    }

    private boolean hasPostRoute(Method[] methods, String path) {
        return Arrays.stream(methods)
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .anyMatch(path::equals);
    }
}
