package com.workflow.entity.definition.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.definition.application.EntityStatusService;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityStatusControllerAccessPolicyTest {

    @Test
    void managementApisKeepDefinitionViewPermission() {
        RequiresPermission classPolicy =
                AnnotatedElementUtils.findMergedAnnotation(
                        EntityStatusController.class,
                        RequiresPermission.class);
        assertNotNull(classPolicy);
        assertArrayEquals(
                new String[]{"entity:definition:view"},
                classPolicy.value());
    }

    @Test
    void listByEntityCodeIsRuntimeObjectAuthorized() throws Exception {
        assertRuntimeObjectAuthorized(
                "listByEntityCode", String.class);
    }

    @Test
    void listByCategoryIsRuntimeObjectAuthorized() throws Exception {
        assertRuntimeObjectAuthorized(
                "listByCategory", String.class, String.class);
    }

    @Test
    void runtimeListRequiresEntityMetadataAccess() {
        EntityStatusService statusService = mock(EntityStatusService.class);
        EntityActionCapabilityService capability =
                mock(EntityActionCapabilityService.class);
        EntityStatusController controller = new EntityStatusController(
                statusService, capability);
        when(statusService.findByEntityCode("ZDWREQ"))
                .thenReturn(List.of());

        controller.listByEntityCode("ZDWREQ");

        verify(capability).requireEntityMetadataAccess("ZDWREQ");
        verify(statusService).findByEntityCode("ZDWREQ");
    }

    private void assertRuntimeObjectAuthorized(
            String methodName,
            Class<?>... parameterTypes) throws Exception {
        Method method = EntityStatusController.class.getDeclaredMethod(
                methodName, parameterTypes);
        AuthenticatedApi policy = AnnotatedElementUtils.findMergedAnnotation(
                method, AuthenticatedApi.class);
        assertNotNull(policy);
        assertTrue(
                policy.objectAuthorization(),
                "运行态读取实体状态必须做对象级授权，不能沿用菜单管理权限");
    }
}
