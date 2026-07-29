package com.workflow.architecture;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.PublicApi;
import com.workflow.core.security.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiAccessPolicyCoverageTest {

    private static final List<String> API_PACKAGES = List.of(
            "com.workflow.admin",
            "com.workflow.entity",
            "com.workflow.migration",
            "com.workflow.openapi",
            "com.workflow.process",
            "com.workflow.storage");

    @Test
    void everyProductionApiDeclaresAnAccessPolicy() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> unclassified = new ArrayList<>();

        for (String basePackage : API_PACKAGES) {
            for (var candidate : scanner.findCandidateComponents(basePackage)) {
                Class<?> controller = Class.forName(candidate.getBeanClassName());
                for (Method method : controller.getDeclaredMethods()) {
                    if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                        continue;
                    }
                    if (!hasPolicy(method) && !hasPolicy(controller)) {
                        unclassified.add(controller.getName() + "#" + method.getName());
                    }
                }
            }
        }

        assertTrue(unclassified.isEmpty(),
                "API endpoints without an explicit access policy: " + unclassified);
    }

    @Test
    void everyStateChangingApiHasFunctionalOrObjectAuthorization() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> unsafe = new ArrayList<>();

        for (String basePackage : API_PACKAGES) {
            for (var candidate : scanner.findCandidateComponents(basePackage)) {
                Class<?> controller = Class.forName(candidate.getBeanClassName());
                for (Method method : controller.getDeclaredMethods()) {
                    RequestMapping mapping =
                            AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                    if (mapping == null || !isStateChanging(mapping.method())) {
                        continue;
                    }
                    if (effectiveAnnotation(method, controller, PublicApi.class) != null
                            || effectiveAnnotation(method, controller, RequiresPermission.class) != null) {
                        continue;
                    }
                    AuthenticatedApi authenticated =
                            effectiveAnnotation(method, controller, AuthenticatedApi.class);
                    if (authenticated == null || !authenticated.objectAuthorization()) {
                        unsafe.add(controller.getName() + "#" + method.getName());
                    }
                }
            }
        }

        assertTrue(unsafe.isEmpty(),
                "State-changing APIs without functional or object authorization: " + unsafe);
    }

    private boolean isStateChanging(RequestMethod[] methods) {
        for (RequestMethod method : methods) {
            if (method == RequestMethod.POST
                    || method == RequestMethod.PUT
                    || method == RequestMethod.PATCH
                    || method == RequestMethod.DELETE) {
                return true;
            }
        }
        return false;
    }

    private <A extends java.lang.annotation.Annotation> A effectiveAnnotation(
            Method method,
            Class<?> controller,
            Class<A> annotationType) {
        A annotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType);
        return annotation != null
                ? annotation
                : AnnotatedElementUtils.findMergedAnnotation(controller, annotationType);
    }

    private boolean hasPolicy(java.lang.reflect.AnnotatedElement element) {
        return AnnotatedElementUtils.hasAnnotation(element, PublicApi.class)
                || AnnotatedElementUtils.hasAnnotation(element, AuthenticatedApi.class)
                || AnnotatedElementUtils.hasAnnotation(element, RequiresPermission.class);
    }
}
