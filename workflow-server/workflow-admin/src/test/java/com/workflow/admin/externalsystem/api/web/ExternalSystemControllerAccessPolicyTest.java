package com.workflow.admin.externalsystem.api.web;

import com.workflow.admin.externalsystem.api.request.ExternalSystemRequests;
import com.workflow.core.security.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 保护外部系统管理接口的读写权限边界。
 */
class ExternalSystemControllerAccessPolicyTest {

    @Test
    void controllerAllowsViewOrManagePermissionForTheList() {
        RequiresPermission policy =
                AnnotatedElementUtils.findMergedAnnotation(
                        ExternalSystemController.class,
                        RequiresPermission.class);

        assertNotNull(policy);
        assertArrayEquals(
                new String[]{
                        "system:external-system:view",
                        "system:external-system:manage"
                },
                policy.value());
        assertTrue(policy.any());
    }

    @Test
    void pageInheritsListPolicyWhileSensitiveDetailRequiresManage()
            throws Exception {
        Method page = ExternalSystemController.class.getDeclaredMethod(
                "page", int.class, int.class, String.class,
                String.class, String.class);
        Method get = ExternalSystemController.class.getDeclaredMethod(
                "get", String.class);

        assertNull(AnnotatedElementUtils.findMergedAnnotation(
                page, RequiresPermission.class));
        assertManagePermission(get);
    }

    @Test
    void everyWriteEndpointRequiresManagePermission() throws Exception {
        List<Method> methods = List.of(
                ExternalSystemController.class.getDeclaredMethod(
                        "create",
                        ExternalSystemRequests.CreateExternalSystem.class),
                ExternalSystemController.class.getDeclaredMethod(
                        "update",
                        String.class,
                        ExternalSystemRequests.UpdateExternalSystem.class),
                ExternalSystemController.class.getDeclaredMethod(
                        "changeStatus",
                        String.class,
                        ExternalSystemRequests
                                .ChangeExternalSystemStatus.class),
                ExternalSystemController.class.getDeclaredMethod(
                        "delete", String.class,
                        ExternalSystemRequests.DeleteExternalSystem.class));

        for (Method method : methods) {
            assertManagePermission(method);
        }
    }

    @Test
    void exposesOnlyTheApprovedGetAndPostRoutes() throws Exception {
        Method page = ExternalSystemController.class.getDeclaredMethod(
                "page", int.class, int.class, String.class,
                String.class, String.class);
        Method get = ExternalSystemController.class.getDeclaredMethod(
                "get", String.class);
        Method create = ExternalSystemController.class.getDeclaredMethod(
                "create",
                ExternalSystemRequests.CreateExternalSystem.class);
        Method update = ExternalSystemController.class.getDeclaredMethod(
                "update", String.class,
                ExternalSystemRequests.UpdateExternalSystem.class);
        Method status = ExternalSystemController.class.getDeclaredMethod(
                "changeStatus", String.class,
                ExternalSystemRequests.ChangeExternalSystemStatus.class);
        Method delete = ExternalSystemController.class.getDeclaredMethod(
                "delete", String.class,
                ExternalSystemRequests.DeleteExternalSystem.class);

        assertArrayEquals(new String[]{"/page"},
                page.getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"/{id}"},
                get.getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{},
                create.getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/update"},
                update.getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/status"},
                status.getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/delete"},
                delete.getAnnotation(PostMapping.class).value());
    }

    private void assertManagePermission(Method method) {
        RequiresPermission policy =
                AnnotatedElementUtils.findMergedAnnotation(
                        method, RequiresPermission.class);
        assertNotNull(policy, method.getName());
        assertArrayEquals(
                new String[]{"system:external-system:manage"},
                policy.value(),
                method.getName());
    }
}
