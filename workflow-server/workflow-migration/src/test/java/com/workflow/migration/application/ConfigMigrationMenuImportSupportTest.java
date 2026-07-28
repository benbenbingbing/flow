package com.workflow.migration.application;

import com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 配置迁移菜单导入规则测试。
 */
class ConfigMigrationMenuImportSupportTest {

    @Test
    void entityListNavigationDoesNotReuseHiddenFunctionPermission() {
        SysMenu menu = new SysMenu();
        menu.setMenuType("C");
        menu.setResourceType("ENTITY_LIST");
        menu.setListKey("all");
        menu.setPerm("entity:requirement:list");

        ConfigMigrationImportApplyService.normalizeImportedMenu(menu, "requirement");

        assertNull(menu.getPerm());
        assertEquals("ENTITY_LIST", menu.getResourceType());
        assertEquals("requirement", menu.getEntityCode());
    }

    @Test
    void directoryDoesNotBindToTheAssetEntity() {
        SysMenu menu = new SysMenu();
        menu.setMenuType("M");

        ConfigMigrationImportApplyService.normalizeImportedMenu(menu, "system_asset");

        assertNull(menu.getEntityCode());
    }

    @Test
    void entityListIdentityRemainsStableWhenRouteChanges() {
        SysMenu oldMenu = entityListMenu("/project-management/requirements");
        SysMenu newMenu = entityListMenu("/entity-list/requirement/all");

        assertEquals(
                ConfigMigrationImportApplyService.entityListIdentity(oldMenu),
                ConfigMigrationImportApplyService.entityListIdentity(newMenu));
    }

    @Test
    void parentPathSupportsDirectoryAndMenuParents() {
        assertEquals(
                List.of("M", "C"),
                ConfigMigrationImportApplyService.parentMenuTypes("/project-management"));
        assertEquals(
                List.of("M"),
                ConfigMigrationImportApplyService.parentMenuTypes("/__entity_permissions__"));
    }

    private SysMenu entityListMenu(String path) {
        SysMenu menu = new SysMenu();
        menu.setMenuType("C");
        menu.setEntityCode("requirement");
        menu.setResourceType("ENTITY_LIST");
        menu.setListKey("all");
        menu.setPath(path);
        return menu;
    }
}
