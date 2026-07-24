package com.workflow.service.permission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 实体权限动作枚举测试。
 *
 * <p>被测对象：{@link EntityPermissionAction}，覆盖权限码规范化生成与不安全实体编码拒绝场景。
 */
class EntityPermissionActionTest {

    /** 测试生成规范化权限码：验证大小写归一化与按钮 key 到枚举的解析 */
    @Test
    void generatesCanonicalPermissionCodes() {
        assertEquals(
                "entity:purchase_order:batch-delete",
                EntityPermissionAction.BATCH_DELETE.permissionCode("Purchase_Order"));
        assertEquals(
                EntityPermissionAction.EXPORT_ALL,
                EntityPermissionAction.fromButtonKey("exportAll"));
    }

    /** 测试拒绝不安全的实体编码：验证含 SQL 注入字符的实体编码抛出 IllegalArgumentException */
    @Test
    void rejectsUnsafeEntityCodes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityPermissionAction.VIEW.permissionCode("purchase-order;drop"));
    }
}
