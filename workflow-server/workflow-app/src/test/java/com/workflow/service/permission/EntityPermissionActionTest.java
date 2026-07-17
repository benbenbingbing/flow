package com.workflow.service.permission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityPermissionActionTest {

    @Test
    void generatesCanonicalPermissionCodes() {
        assertEquals(
                "entity:purchase_order:batch-delete",
                EntityPermissionAction.BATCH_DELETE.permissionCode("Purchase_Order"));
        assertEquals(
                EntityPermissionAction.EXPORT_ALL,
                EntityPermissionAction.fromButtonKey("exportAll"));
    }

    @Test
    void rejectsUnsafeEntityCodes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityPermissionAction.VIEW.permissionCode("purchase-order;drop"));
    }
}
