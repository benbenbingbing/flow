package com.workflow.entity.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.ui.api.web.UiDataSourceController;
import com.workflow.entity.ui.api.web.UiEventBindingController;
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
    void interfaceServiceEndpointsUseMenuPermissions() {
        assertPermission(
                UiDataSourceController.class,
                "catalog",
                "system:interface-service:list");
        assertPermission(
                UiDataSourceController.class,
                "list",
                "system:interface-service:list");
        assertPermission(
                UiDataSourceController.class,
                "operations",
                "system:interface-service:list");
        assertPermission(
                UiDataSourceController.class,
                "references",
                "system:interface-service:list");
        assertPermission(
                UiDataSourceController.class,
                "create",
                "system:interface-service:update");
        assertPermission(
                UiDataSourceController.class,
                "update",
                "system:interface-service:update");
        assertPermission(
                UiDataSourceController.class,
                "delete",
                "system:interface-service:update");
        assertPermission(
                UiDataSourceController.class,
                "validateBinding",
                "system:interface-service:update");
        assertPermission(
                UiDataSourceController.class,
                "preview",
                "system:interface-service:test");
        assertPermission(
                UiDataSourceController.class,
                "previewOperation",
                "system:interface-service:test");
    }

    @Test
    void eventBindingsDeclareObjectAuthorization() {
        AuthenticatedApi policy =
                AnnotatedElementUtils.findMergedAnnotation(
                        UiEventBindingController.class,
                        AuthenticatedApi.class);

        assertNotNull(policy);
        assertTrue(policy.objectAuthorization());
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
