package com.workflow.admin.identity.position.api.web;

import com.workflow.core.security.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SysPositionControllerAccessPolicyTest {

    @Test
    void fullDefinitionResourcesStillRequirePositionView() {
        RequiresPermission policy = AnnotatedElementUtils.findMergedAnnotation(
                SysPositionController.class, RequiresPermission.class);

        assertNotNull(policy);
        assertArrayEquals(new String[]{"system:position:view"}, policy.value());
        assertFalse(policy.any());
    }

    @Test
    void assignmentOperatorCanReadOnlyEnabledPositionOptions()
            throws Exception {
        Method enabled = SysPositionController.class.getDeclaredMethod(
                "enabled", String.class);
        RequiresPermission policy = AnnotatedElementUtils.findMergedAnnotation(
                enabled, RequiresPermission.class);

        assertNotNull(policy);
        assertTrue(policy.any());
        assertArrayEquals(new String[]{
                        "system:position:view",
                        "process:definition:view",
                        "process:definition:manage",
                        "system:position:assign"},
                policy.value());
    }
}
