package com.workflow.entity.definition.application;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemEntityFieldPolicyTest {

    private final SystemEntityFieldPolicy policy =
            new SystemEntityFieldPolicy();

    @Test
    void sensitiveFieldsAreNeverConfigurableOrReadable() {
        EntityDefinition entity = systemEntity("sys_user");

        for (String fieldCode : List.of(
                "password",
                "password_hash",
                "token_version",
                "api_token",
                "client_secret",
                "private_key",
                "credential",
                "salt",
                "otp")) {
            EntityField field = field(fieldCode);
            assertTrue(policy.isSensitive(fieldCode));
            assertFalse(policy.isUiConfigurable(entity, field));
            assertFalse(policy.isRuntimeReadable(entity, field));
        }
    }

    @Test
    void dynamicEntitySystemFieldsRemainAvailableToUiConfiguration() {
        EntityDefinition entity = new EntityDefinition();
        entity.setEntityCode("requirement");
        entity.setStorageMode(
                EntityDefinition.StorageMode.DYNAMIC);

        EntityField field = field("status");
        field.setIsSystem(true);

        assertTrue(policy.isUiConfigurable(entity, field));
        assertTrue(policy.isRuntimeReadable(entity, field));
    }

    @Test
    void relationshipTablesRequireBothEndpointPermissions() {
        assertEquals(
                List.of(
                        "system:user:view",
                        "system:role:view"),
                policy.requiredPermissions("sys_user_role"));
        assertEquals(
                List.of(
                        "system:role:view",
                        "system:menu:view"),
                policy.requiredPermissions("sys_role_menu"));
        assertTrue(policy.isSupportedEntity("sys_user"));
        assertFalse(policy.isSupportedEntity("sys_refresh_token"));
    }

    @Test
    void relationFieldsResolveToPlatformReferenceTypes() {
        assertEquals(
                EntityField.RefEntityType.USER,
                policy.referenceType(
                        "sys_user_role", "user_id"));
        assertEquals(
                EntityField.RefEntityType.ROLE,
                policy.referenceType(
                        "sys_user_role", "role_id"));
        assertEquals(
                EntityField.RefEntityType.MENU,
                policy.referenceType(
                        "sys_role_menu", "menu_id"));
        assertEquals(
                EntityField.RefEntityType.DEPT,
                policy.referenceType(
                        "sys_organization", "parent_id"));
    }

    private EntityDefinition systemEntity(String entityCode) {
        EntityDefinition entity = new EntityDefinition();
        entity.setEntityCode(entityCode);
        entity.setStorageMode(
                EntityDefinition.StorageMode.SYSTEM);
        return entity;
    }

    private EntityField field(String fieldCode) {
        EntityField field = new EntityField();
        field.setFieldCode(fieldCode);
        return field;
    }
}
