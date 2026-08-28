package com.workflow.admin.organization.api.web;

import com.workflow.core.security.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SysOrganizationControllerAccessPolicyTest {

    @Test
    void businessLevelOptionsInheritOrganizationViewWithoutDictionaryView()
            throws Exception {
        RequiresPermission controllerPolicy =
                AnnotatedElementUtils.findMergedAnnotation(
                        SysOrganizationController.class,
                        RequiresPermission.class);
        Method endpoint = SysOrganizationController.class.getDeclaredMethod(
                "getBusinessLevelOptions");
        GetMapping mapping = endpoint.getDeclaredAnnotation(GetMapping.class);

        assertNotNull(controllerPolicy);
        assertArrayEquals(new String[]{"system:organization:view"},
                controllerPolicy.value());
        assertFalse(controllerPolicy.any());
        assertNotNull(mapping);
        assertEquals("/business-level-options", mapping.value()[0]);
        // 方法不覆盖权限，继续受控制器上的组织查看权限保护。
        assertNull(endpoint.getDeclaredAnnotation(RequiresPermission.class));
    }
}
