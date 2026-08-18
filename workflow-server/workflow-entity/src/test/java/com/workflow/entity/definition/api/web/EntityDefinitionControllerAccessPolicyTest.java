package com.workflow.entity.definition.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityDefinitionControllerAccessPolicyTest {

    @Test
    void managementApisKeepDefinitionViewPermission() {
        RequiresPermission classPolicy =
                AnnotatedElementUtils.findMergedAnnotation(
                        EntityDefinitionController.class,
                        RequiresPermission.class);
        assertNotNull(classPolicy);
        assertArrayEquals(
                new String[]{"entity:definition:view"},
                classPolicy.value());
    }

    @Test
    void lookupByCodeIsRuntimeObjectAuthorized() throws Exception {
        Method getByCode = EntityDefinitionController.class.getDeclaredMethod(
                "getByCode", String.class);
        AuthenticatedApi policy = AnnotatedElementUtils.findMergedAnnotation(
                getByCode, AuthenticatedApi.class);
        assertNotNull(policy);
        assertTrue(
                policy.objectAuthorization(),
                "按编码读取实体定义必须做对象级授权，不能沿用菜单管理权限");
    }
}
