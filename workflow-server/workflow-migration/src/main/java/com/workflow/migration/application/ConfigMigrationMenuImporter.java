package com.workflow.migration.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRoleMenu;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Applies imported navigation menus and administrator bindings.
 */
@Component
@RequiredArgsConstructor
class ConfigMigrationMenuImporter {

    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final ObjectMapper objectMapper;

    void apply(
            EntityDefinition entity,
            List<Map<String, Object>> values) {
        List<SysRole> administrators =
                roleMapper.selectAdministratorRoles();
        for (Map<String, Object> value : values) {
            SysMenu menu = convert(value, SysMenu.class);
            ConfigMigrationImportApplyService.normalizeImportedMenu(
                    menu,
                    entity.getEntityCode());
            List<SysMenu> identityMatches =
                    findEntityListMenus(menu);
            SysMenu existing = identityMatches.isEmpty()
                    ? null
                    : identityMatches.get(0);
            if (existing == null
                    && StringUtils.hasText(menu.getPerm())) {
                existing = menuMapper.selectByPerm(menu.getPerm());
            }
            if (existing == null
                    && StringUtils.hasText(menu.getPath())) {
                existing = menuMapper.selectByPathAndType(
                        menu.getPath(),
                        menu.getMenuType());
            }
            menu.setId(existing == null ? null : existing.getId());
            String parentPath = text(value.get("parentPath"));
            if (StringUtils.hasText(parentPath)) {
                SysMenu parent = findParentMenu(parentPath);
                if (parent == null) {
                    throw new IllegalStateException(
                            "菜单父级不存在: " + parentPath);
                }
                menu.setParentId(parent.getId());
            } else if (existing != null) {
                menu.setParentId(existing.getParentId());
            }
            menu.setDeleted(0);
            menu.setCreateTime(existing == null
                    ? LocalDateTime.now()
                    : existing.getCreateTime());
            menu.setUpdateTime(LocalDateTime.now());
            if (menu.getId() == null) {
                menuMapper.insert(menu);
            } else {
                menuMapper.updateById(menu);
                clearNullableMenuColumns(menu);
            }
            removeDuplicateMenus(
                    identityMatches,
                    menu.getId());
            bindAdministrators(administrators, menu.getId());
        }
    }

    private void bindAdministrators(
            List<SysRole> administrators,
            String menuId) {
        for (SysRole role : administrators) {
            if (roleMenuMapper.existsRoleMenu(
                    role.getId(),
                    menuId)) {
                continue;
            }
            SysRoleMenu relation = new SysRoleMenu();
            relation.setRoleId(role.getId());
            relation.setMenuId(menuId);
            relation.setCreateTime(LocalDateTime.now());
            roleMenuMapper.insert(relation);
        }
    }

    private List<SysMenu> findEntityListMenus(SysMenu menu) {
        if (!StringUtils.hasText(
                ConfigMigrationImportApplyService
                        .entityListIdentity(menu))) {
            return List.of();
        }
        return menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getMenuType, "C")
                        .eq(
                                SysMenu::getEntityCode,
                                menu.getEntityCode())
                        .eq(
                                SysMenu::getResourceType,
                                "ENTITY_LIST")
                        .eq(
                                SysMenu::getListKey,
                                menu.getListKey())
                        .orderByAsc(SysMenu::getCreateTime)
                        .orderByAsc(SysMenu::getId));
    }

    private void removeDuplicateMenus(
            List<SysMenu> matches,
            String retainedMenuId) {
        for (SysMenu duplicate : matches) {
            if (Objects.equals(
                    duplicate.getId(),
                    retainedMenuId)) {
                continue;
            }
            roleMenuMapper.delete(
                    new LambdaQueryWrapper<SysRoleMenu>()
                            .eq(
                                    SysRoleMenu::getMenuId,
                                    duplicate.getId()));
            menuMapper.deleteById(duplicate.getId());
        }
    }

    private SysMenu findParentMenu(String parentPath) {
        for (String menuType :
                ConfigMigrationImportApplyService.parentMenuTypes(
                        parentPath)) {
            SysMenu parent = menuMapper.selectByPathAndType(
                    parentPath,
                    menuType);
            if (parent != null) {
                return parent;
            }
        }
        return null;
    }

    private void clearNullableMenuColumns(SysMenu menu) {
        LambdaUpdateWrapper<SysMenu> update =
                new LambdaUpdateWrapper<SysMenu>()
                        .eq(SysMenu::getId, menu.getId());
        boolean required = false;
        if (ConfigMigrationImportApplyService
                .isEntityListMenu(menu)) {
            update.set(SysMenu::getPerm, null);
            required = true;
        }
        if ("M".equals(menu.getMenuType())) {
            update.set(SysMenu::getEntityCode, null);
            required = true;
        }
        if (required) {
            menuMapper.update(null, update);
        }
    }

    private <T> T convert(
            Map<String, Object> value,
            Class<T> type) {
        ObjectMapper tolerant = objectMapper.copy()
                .configure(
                        DeserializationFeature
                                .FAIL_ON_UNKNOWN_PROPERTIES,
                        false);
        return tolerant.convertValue(value, type);
    }

    private String text(Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }
}
