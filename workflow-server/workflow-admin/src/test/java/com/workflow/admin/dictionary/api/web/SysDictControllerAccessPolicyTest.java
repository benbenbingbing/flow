package com.workflow.admin.dictionary.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SysDictControllerAccessPolicyTest {

    @Test
    void managementApisKeepDictionaryViewPermission() {
        RequiresPermission classPolicy =
                AnnotatedElementUtils.findMergedAnnotation(
                        SysDictController.class,
                        RequiresPermission.class);
        assertNotNull(classPolicy);
        assertArrayEquals(
                new String[]{"system:dictionary:view"},
                classPolicy.value());
    }

    @Test
    void itemTreeByCodeIsLoginOnly() throws Exception {
        Method method = SysDictController.class.getDeclaredMethod(
                "getItemTreeByDictCode", String.class);
        AuthenticatedApi policy = AnnotatedElementUtils.findMergedAnnotation(
                method, AuthenticatedApi.class);
        assertNotNull(policy);
        assertFalse(
                policy.objectAuthorization(),
                "按编码读取字典项树只需登录，不能要求字典管理权限");
        assertNull(AnnotatedElementUtils.findMergedAnnotation(
                method, RequiresPermission.class));
    }

    @Test
    void itemTreeByIdStillRequiresDictionaryView() throws Exception {
        Method method = SysDictController.class.getDeclaredMethod(
                "getItemTreeByDictId", String.class);
        assertNull(AnnotatedElementUtils.findMergedAnnotation(
                method, AuthenticatedApi.class));
        RequiresPermission policy = AnnotatedElementUtils.findMergedAnnotation(
                SysDictController.class, RequiresPermission.class);
        assertNotNull(policy);
        assertArrayEquals(
                new String[]{"system:dictionary:view"},
                policy.value());
    }
}
