package com.workflow.entity.definition.api.web;

import com.workflow.core.security.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EntityRelationControllerAccessPolicyTest {

    @Test
    void relationResourceUsesRestMethodsAndDefinitionPermissions() {
        RequiresPermission classPolicy =
                AnnotatedElementUtils.findMergedAnnotation(
                        EntityRelationController.class,
                        RequiresPermission.class);
        assertNotNull(classPolicy);
        assertArrayEquals(
                new String[]{"entity:definition:view"},
                classPolicy.value());

        Method create = method("create");
        assertNotNull(AnnotatedElementUtils.findMergedAnnotation(
                create, PostMapping.class));
        assertPermission(create, "entity:definition:manage");

        Method update = method("update");
        PutMapping put = AnnotatedElementUtils.findMergedAnnotation(
                update, PutMapping.class);
        assertNotNull(put);
        assertArrayEquals(new String[]{"/{relationId}"}, put.value());
        assertPermission(update, "entity:definition:manage");

        Method delete = method("delete");
        DeleteMapping deleteMapping =
                AnnotatedElementUtils.findMergedAnnotation(
                        delete, DeleteMapping.class);
        assertNotNull(deleteMapping);
        assertArrayEquals(
                new String[]{"/{relationId}"},
                deleteMapping.value());
        assertPermission(delete, "entity:definition:manage");
    }

    private Method method(String name) {
        return Arrays.stream(
                        EntityRelationController.class
                                .getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private void assertPermission(
            Method method,
            String permission) {
        RequiresPermission policy =
                AnnotatedElementUtils.findMergedAnnotation(
                        method, RequiresPermission.class);
        assertNotNull(policy);
        assertArrayEquals(
                new String[]{permission}, policy.value());
    }
}
