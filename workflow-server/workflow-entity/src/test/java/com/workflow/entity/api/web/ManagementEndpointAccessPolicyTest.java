package com.workflow.entity.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.ui.api.web.UiEventBindingController;
import com.workflow.entity.ui.api.web.UiExtensionDefinitionController;
import com.workflow.entity.ui.api.web.UiExtensionRuntimeController;
import com.workflow.entity.version.api.web.EntityRecordVersionController;
import com.workflow.entity.version.api.web.EntityVersionConfigurationController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagementEndpointAccessPolicyTest {

    @Test
    void interfaceExtensionManagementEndpointsUseExtensionPermissions() {
        assertPermission(
                UiExtensionDefinitionController.class,
                "catalog",
                "system:extension:list");
        assertPermission(
                UiExtensionDefinitionController.class,
                "list",
                "system:extension:list");
        assertPermission(
                UiExtensionDefinitionController.class,
                "create",
                "system:extension:update");
        assertPermission(
                UiExtensionDefinitionController.class,
                "update",
                "system:extension:update");
        assertPermission(
                UiExtensionDefinitionController.class,
                "delete",
                "system:extension:update");
        assertPermission(
                UiExtensionDefinitionController.class,
                "preview",
                "system:extension:test");
    }

    @Test
    void interfaceExtensionSelectionAndRuntimeUseObjectAuthorization() {
        assertObjectAuthorization(UiExtensionDefinitionController.class);
        assertObjectAuthorization(UiExtensionRuntimeController.class);
    }

    @Test
    void eventBindingsDeclareObjectAuthorization() {
        assertObjectAuthorization(UiEventBindingController.class);
    }

    @Test
    void entityVersionEndpointsUseVersionPermissions() {
        assertClassPermission(
                EntityVersionConfigurationController.class,
                "entity:version:config:list");
        assertPermission(
                EntityVersionConfigurationController.class,
                "save",
                "entity:version:config:update");
        assertPermission(
                EntityVersionConfigurationController.class,
                "current",
                "entity:version:config:list");
        assertPermission(
                EntityVersionConfigurationController.class,
                "saveCurrent",
                "entity:version:config:update");
        assertPermission(
                EntityVersionConfigurationController.class,
                "legacySaveDraft",
                "entity:version:config:update");
        assertPermission(
                EntityVersionConfigurationController.class,
                "legacyPublish",
                "entity:version:config:publish");
        assertPermission(
                EntityVersionConfigurationController.class,
                "legacyCreateRelease",
                "entity:version:config:publish");
        assertClassPermission(
                EntityRecordVersionController.class,
                "entity:version:record:view");
        assertPermission(
                EntityRecordVersionController.class,
                "capture",
                "entity:version:record:view",
                "entity:version:record:capture");
    }

    private void assertClassPermission(
            Class<?> controller,
            String... expected) {
        RequiresPermission policy =
                AnnotatedElementUtils.findMergedAnnotation(
                        controller,
                        RequiresPermission.class);
        assertNotNull(policy);
        assertArrayEquals(expected, policy.value());
    }

    private void assertObjectAuthorization(Class<?> controller) {
        AuthenticatedApi policy =
                AnnotatedElementUtils.findMergedAnnotation(
                        controller,
                        AuthenticatedApi.class);
        assertNotNull(policy);
        assertTrue(policy.objectAuthorization());
    }

    private void assertPermission(
            Class<?> controller,
            String methodName,
            String... expected) {
        Method method = Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate ->
                        candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        RequiresPermission policy =
                AnnotatedElementUtils.findMergedAnnotation(
                        method,
                        RequiresPermission.class);
        assertNotNull(policy);
        assertArrayEquals(expected, policy.value());
    }
}
