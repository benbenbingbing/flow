package com.workflow.embed.management.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void highRiskViewOperationsUseDedicatedPermissions() throws Exception {
        RequiresPermission publish = EmbedViewManagementController.class
                .getMethod("publish", String.class,
                        EmbedManagementRequests.PublishViewRequest.class)
                .getAnnotation(RequiresPermission.class);
        RequiresPermission update = EmbedViewManagementController.class
                .getMethod("updateDraft", String.class,
                        EmbedManagementRequests.UpdateDraftRequest.class)
                .getAnnotation(RequiresPermission.class);

        assertEquals("system:embed:publish", publish.value()[0]);
        assertEquals("system:embed:manage", update.value()[0]);
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
