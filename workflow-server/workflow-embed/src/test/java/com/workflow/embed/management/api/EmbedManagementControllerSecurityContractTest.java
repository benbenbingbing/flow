package com.workflow.embed.management.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workflow.core.security.PublicApi;
import com.workflow.core.security.RequiresPermission;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class EmbedManagementControllerSecurityContractTest {

    @Test
    void controllersRemainOnOrdinaryFlowAuthenticationAndPermissionChain() {
        List<Class<?>> controllers = List.of(
                EmbedViewManagementController.class,
                EmbedGrantManagementController.class,
                EmbedIdentityManagementController.class,
                EmbedOptionsManagementController.class,
                EmbedOperationsManagementController.class);

        for (Class<?> controller : controllers) {
            RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
            assertNotNull(mapping);
            assertFalse(controller.isAnnotationPresent(PublicApi.class));
            assertNotNull(controller.getAnnotation(RequiresPermission.class));
            for (Method method : controller.getDeclaredMethods()) {
                assertFalse(method.isAnnotationPresent(PublicApi.class));
            }
        }
        assertArrayEquals(new String[]{"/api/embed-management/v1/views"},
                EmbedViewManagementController.class.getAnnotation(RequestMapping.class).value());
    }

    @Test
    void nameOptionsAllowEitherWorkspaceWithoutWeakeningIdentityAdministration() throws Exception {
        RequiresPermission optionPermission = EmbedOptionsManagementController.class
                .getAnnotation(RequiresPermission.class);
        assertArrayEquals(new String[]{
                        "system:embed:view",
                        "system:embed:identity-manage"},
                optionPermission.value());
        assertTrue(optionPermission.any());
        assertArrayEquals(new String[]{"/api/embed-management/v1/options"},
                EmbedOptionsManagementController.class.getAnnotation(RequestMapping.class).value());
        for (String methodName : List.of("applications", "identityProviders")) {
            Method method = EmbedOptionsManagementController.class.getMethod(
                    methodName, String.class, String.class, int.class, int.class);
            assertFalse(method.isAnnotationPresent(RequiresPermission.class));
            assertArrayEquals(new String[]{methodName.equals("applications")
                            ? "/applications" : "/identity-providers"},
                    method.getAnnotation(GetMapping.class).value());
        }
        assertArrayEquals(new String[]{"system:embed:identity-manage"},
                EmbedIdentityManagementController.class.getAnnotation(RequiresPermission.class).value());
    }

    @Test
    void currentConfigurationSaveUsesManagementPermission() throws Exception {
        RequiresPermission update = EmbedViewManagementController.class
                .getMethod("updateDraft", String.class,
                        EmbedManagementRequests.UpdateDraftRequest.class)
                .getAnnotation(RequiresPermission.class);

        assertEquals("system:embed:manage", update.value()[0]);
    }

    @Test
    void currentValidationIsReadOnlyAndInheritsViewPermission() throws Exception {
        Method validation = EmbedViewManagementController.class
                .getMethod("validateCurrentActive", String.class);

        assertFalse(validation.isAnnotationPresent(RequiresPermission.class));
        assertArrayEquals(new String[]{"/{viewId}/validation"},
                validation.getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"viewStatus", "valid", "violations"},
                java.util.Arrays.stream(EmbedManagementViews.ViewValidation.class
                                .getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toArray(String[]::new),
                "只读检查不能暴露 resolved 快照、canonicalConfig 或 configHash");
    }

    @Test
    void lifecycleQueriesAndRevocationsUseExactDedicatedPermissions() throws Exception {
        RequiresPermission classPermission = EmbedOperationsManagementController.class
                .getAnnotation(RequiresPermission.class);
        assertEquals("system:embed:view", classPermission.value()[0]);

        for (String methodName : List.of(
                "revokeLaunch", "revokeSession",
                "revokeViewSessions", "revokeApplicationSessions")) {
            Method method = java.util.Arrays.stream(
                            EmbedOperationsManagementController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            RequiresPermission permission = method.getAnnotation(RequiresPermission.class);
            assertNotNull(permission, methodName);
            assertEquals("system:embed:session-revoke", permission.value()[0], methodName);
        }
        for (String methodName : List.of("launches", "sessions")) {
            Method method = java.util.Arrays.stream(
                            EmbedOperationsManagementController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            assertFalse(method.isAnnotationPresent(RequiresPermission.class), methodName);
        }
    }

    @Test
    void operationsControllerKeepsVersionedInterfacePathsStable() throws Exception {
        assertArrayEquals(new String[]{"/api/embed-management/v1"},
                EmbedOperationsManagementController.class
                        .getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[]{"/launches"},
                path("launches").getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"/sessions"},
                path("sessions").getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"/launches/{launchId}/revoke"},
                path("revokeLaunch").getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/sessions/{sessionId}/revoke"},
                path("revokeSession").getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/views/{viewId}/sessions/revoke"},
                path("revokeViewSessions").getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/applications/{applicationId}/sessions/revoke"},
                path("revokeApplicationSessions").getAnnotation(PostMapping.class).value());
    }

    private static Method path(String name) {
        return java.util.Arrays.stream(
                        EmbedOperationsManagementController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
