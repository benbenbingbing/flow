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

    /**
     * 应用配置迁移菜单{@code importer}，并将结果传给后续步骤。
     *
     * @param entity 实体，作为 {@code ConfigMigrationImportApplyService.normalizeImportedMenu} 的输入影响后续处理
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 处理绑定{@code administrators}，并将结果传给后续步骤。
     *
     * @param administrators {@code administrators}，供本方法处理绑定{@code administrators}时使用
     * @param menuId 菜单ID，后续用于处理绑定{@code administrators}时定位或关联目标
     */
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

    /**
     * 查询实体列表菜单集合；查询结果供调用方展示或继续处理。
     *
     * @param menu 菜单，作为 {@code eq} 的输入影响后续处理
     * @return 系统菜单集合，供调用方遍历或展示
     */
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

    /**
     * 移除{@code duplicate}菜单集合；后续读取或执行将使用更新后的状态。
     *
     * @param matches 匹配，供本方法移除{@code duplicate}菜单集合时使用
     * @param retainedMenuId {@code retained}菜单ID，后续用于移除{@code duplicate}菜单集合时定位或关联目标
     */
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

    /**
     * 查询父级菜单；查询结果供调用方展示或继续处理。
     *
     * @param parentPath 父级路径，作为 {@code menuMapper.selectByPathAndType} 的输入影响后续处理
     * @return 符合条件的系统菜单结果，供调用方继续处理
     */
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

    /**
     * 清理可空菜单列集合；后续读取或执行将使用更新后的状态。
     *
     * @param menu 菜单，作为 {@code eq} 的输入影响后续处理
     */
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

    /**
     * 转换配置迁移菜单{@code importer}；输出作为后续校验或处理的输入。
     *
     * @param value 待转换配置迁移菜单{@code importer}的原始输入，结果供调用方继续使用
     * @param type 类型标识，决定后续配置迁移菜单{@code importer}采用的处理分支
     * @return 转换后的配置迁移菜单{@code importer}结果，供调用方继续处理
     */
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

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }
}
