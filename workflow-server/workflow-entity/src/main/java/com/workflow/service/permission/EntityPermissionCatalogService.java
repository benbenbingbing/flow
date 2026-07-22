package com.workflow.service.permission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.dto.permission.EntityPermissionOptionDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityStatus;
import com.workflow.entity.SysMenu;
import com.workflow.entity.SysRole;
import com.workflow.entity.SysRoleMenu;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityStatusMapper;
import com.workflow.mapper.SysMenuMapper;
import com.workflow.mapper.SysRoleMapper;
import com.workflow.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 实体标准权限资源目录和历史配置同步。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityPermissionCatalogService {

    private static final String ROOT_PATH = "/__entity_permissions__";

    private final EntityDefinitionMapper definitionMapper;
    private final EntityListConfigMapper listConfigMapper;
    private final EntityStatusMapper statusMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final EntityListActionConfigService actionConfigService;
    private final List<EntityPermissionOptionProvider> optionProviders;

    public List<EntityPermissionOptionDTO> getOptions(String entityCode) {
        String normalizedCode = EntityPermissionAction.normalizeEntityCode(entityCode);
        EntityDefinition entity = definitionMapper.findByEntityCode(entityCode).orElse(null);
        if (entity != null && entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            return List.of();
        }
        List<EntityPermissionOptionDTO> options = new ArrayList<>();
        for (EntityPermissionAction action : EntityPermissionAction.values()) {
            if (!supportsAction(entity, action)) {
                continue;
            }
            options.add(new EntityPermissionOptionDTO(
                    action.getCode(),
                    action.permissionCode(normalizedCode),
                    action.getLabel(),
                    action.getDescription(),
                    "STANDARD"));
        }
        options.add(new EntityPermissionOptionDTO(
                "scope-bypass",
                scopeBypassPermission(normalizedCode),
                "绕过数据范围",
                "访问该实体全部数据，仅应授予超级管理员",
                "SYSTEM"));
        Set<String> existingPerms = menuMapper.selectPermsByEntityCode(entityCode);
        existingPerms.stream()
                .filter(StringUtils::hasText)
                .filter(perm -> options.stream().noneMatch(option -> option.getCode().equals(perm)))
                .sorted()
                .forEach(perm -> options.add(new EntityPermissionOptionDTO(
                        "custom",
                        perm,
                        "自定义权限",
                        "实体已有的自定义按钮权限",
                        "CUSTOM")));
        for (EntityPermissionOptionProvider provider : optionProviders) {
            List<EntityPermissionOptionDTO> provided = provider.getOptions(entityCode);
            if (provided == null) {
                continue;
            }
            for (EntityPermissionOptionDTO option : provided) {
                if (option != null && StringUtils.hasText(option.getCode())
                        && options.stream().noneMatch(existing -> existing.getCode().equals(option.getCode()))) {
                    options.add(option);
                }
            }
        }
        return options;
    }

    @Transactional(rollbackFor = Exception.class)
    public void synchronizeAll() {
        List<EntityDefinition> entities = definitionMapper.selectList(
                new LambdaQueryWrapper<EntityDefinition>().orderByAsc(EntityDefinition::getCreatedAt));
        if (entities.isEmpty()) {
            return;
        }

        List<SysRole> administratorRoles = roleMapper.selectAdministratorRoles();
        if (administratorRoles == null || administratorRoles.isEmpty()) {
            throw new IllegalStateException("实体权限初始化失败：系统不存在 super_admin 或 admin 管理员角色");
        }

        SysMenu root = ensureRootMenu();
        for (EntityDefinition entity : entities) {
            if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
                continue;
            }
            synchronizeEntity(entity, root, administratorRoles);
        }

        List<EntityListConfig> configs = listConfigMapper.selectList(null);
        for (EntityListConfig config : configs) {
            if (!StringUtils.hasText(config.getEntityCode())) {
                continue;
            }
            if (actionConfigService.normalizeForMigration(config)) {
                listConfigMapper.updateById(config);
            }
            synchronizeCustomPermissions(config);
        }
        log.info("实体标准权限初始化完成: entities={}, listConfigs={}", entities.size(), configs.size());
    }

    @Transactional(rollbackFor = Exception.class)
    public void synchronizeEntity(EntityDefinition entity) {
        if (entity == null || !StringUtils.hasText(entity.getEntityCode())) {
            return;
        }
        if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            return;
        }
        List<SysRole> administratorRoles = roleMapper.selectAdministratorRoles();
        if (administratorRoles == null || administratorRoles.isEmpty()) {
            throw new IllegalStateException("实体权限初始化失败：系统不存在 super_admin 或 admin 管理员角色");
        }
        synchronizeEntity(entity, ensureRootMenu(), administratorRoles);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disableEntityPermissions(String entityCode) {
        if (!StringUtils.hasText(entityCode)) {
            return;
        }
        for (EntityPermissionAction action : EntityPermissionAction.values()) {
            SysMenu menu = menuMapper.selectByPerm(action.permissionCode(entityCode));
            if (menu != null) {
                menu.setStatus("1");
                menu.setUpdateTime(LocalDateTime.now());
                menuMapper.updateById(menu);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void synchronizeCustomPermissions(EntityListConfig config) {
        if (config == null || !StringUtils.hasText(config.getEntityCode())) {
            return;
        }
        EntityDefinition entity = definitionMapper.findByEntityCode(config.getEntityCode()).orElse(null);
        if (entity == null) {
            return;
        }
        if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            return;
        }
        List<SysRole> administratorRoles = roleMapper.selectAdministratorRoles();
        SysMenu container = ensureEntityContainer(ensureRootMenu(), entity);
        List<java.util.Map<String, Object>> buttons = new ArrayList<>();
        buttons.addAll(actionConfigService.resolveToolbarButtons(config, config.getEntityCode()));
        buttons.addAll(actionConfigService.resolveRowButtons(config, config.getEntityCode()));
        for (java.util.Map<String, Object> button : buttons) {
            if (!"custom".equals(String.valueOf(button.get("type")))
                    || Boolean.FALSE.equals(button.get("enabled"))) {
                continue;
            }
            String permissionCode = button.get("perm") == null ? null : String.valueOf(button.get("perm"));
            if (!StringUtils.hasText(permissionCode)) {
                continue;
            }
            String label = button.get("label") == null ? "自定义操作" : String.valueOf(button.get("label"));
            SysMenu permissionMenu = ensureCustomPermissionMenu(
                    container, entity, permissionCode, label);
            grantToAdministrators(permissionMenu.getId(), administratorRoles);
        }
        if (StringUtils.hasText(config.getAccessPermissionCode())) {
            SysMenu permissionMenu = ensureCustomPermissionMenu(
                    container,
                    entity,
                    config.getAccessPermissionCode(),
                    config.getListName() + "访问");
            grantToAdministrators(permissionMenu.getId(), administratorRoles);
        }
    }

    private void synchronizeEntity(
            EntityDefinition entity,
            SysMenu root,
            List<SysRole> administratorRoles) {
        SysMenu container = ensureEntityContainer(root, entity);
        for (EntityPermissionAction action : EntityPermissionAction.values()) {
            if (!supportsAction(entity, action)) {
                disablePermission(entity, action);
                continue;
            }
            SysMenu permissionMenu = ensurePermissionMenu(container, entity, action);
            grantToAdministrators(permissionMenu.getId(), administratorRoles);
        }
        SysMenu bypassMenu = ensureScopeBypassPermissionMenu(container, entity);
        grantToSuperAdministrators(bypassMenu.getId(), administratorRoles);
        ensureInitialStatus(entity);
        if (entity.getLifecycleMode() == EntityDefinition.LifecycleMode.WORKFLOW) {
            ensureWorkflowStatuses(entity);
        }
    }

    private SysMenu ensureScopeBypassPermissionMenu(
            SysMenu container,
            EntityDefinition entity) {
        String perm = scopeBypassPermission(entity.getEntityCode());
        SysMenu menu = menuMapper.selectByPerm(perm);
        if (menu == null) {
            menu = new SysMenu();
            menu.setParentId(container.getId());
            menu.setMenuType("F");
            menu.setPath("");
            menu.setComponent("");
            menu.setVisible("1");
            menu.setSort(EntityPermissionAction.values().length + 1);
            menu.setIsFrame("0");
            menu.setIsCache("0");
            menu.setCreateTime(LocalDateTime.now());
        }
        menu.setMenuName("绕过数据范围");
        menu.setPerm(perm);
        menu.setEntityCode(entity.getEntityCode());
        menu.setStatus("0");
        menu.setUpdateTime(LocalDateTime.now());
        if (StringUtils.hasText(menu.getId())) {
            menuMapper.updateById(menu);
        } else {
            menuMapper.insert(menu);
        }
        return menu;
    }

    private void grantToSuperAdministrators(
            String menuId,
            List<SysRole> administratorRoles) {
        administratorRoles.stream()
                .filter(role -> "super_admin".equals(role.getRoleCode()))
                .forEach(role -> {
                    if (!roleMenuMapper.existsRoleMenu(role.getId(), menuId)) {
                        SysRoleMenu relation = new SysRoleMenu();
                        relation.setRoleId(role.getId());
                        relation.setMenuId(menuId);
                        relation.setCreateTime(LocalDateTime.now());
                        roleMenuMapper.insert(relation);
                    }
                });
    }

    private String scopeBypassPermission(String entityCode) {
        return "entity:" + EntityPermissionAction.normalizeEntityCode(entityCode)
                + ":scope:bypass";
    }

    private SysMenu ensureRootMenu() {
        SysMenu root = menuMapper.selectByPathAndType(ROOT_PATH, "M");
        if (root != null) {
            return root;
        }
        root = new SysMenu();
        root.setParentId("0");
        root.setMenuName("实体数据权限");
        root.setMenuType("M");
        root.setPath(ROOT_PATH);
        root.setComponent("");
        root.setStatus("0");
        root.setVisible("1");
        root.setSort(9990);
        root.setIsFrame("0");
        root.setIsCache("0");
        root.setCreateTime(LocalDateTime.now());
        root.setUpdateTime(LocalDateTime.now());
        menuMapper.insert(root);
        return root;
    }

    private SysMenu ensureEntityContainer(SysMenu root, EntityDefinition entity) {
        String path = ROOT_PATH + "/" + EntityPermissionAction.normalizeEntityCode(entity.getEntityCode());
        SysMenu container = menuMapper.selectByPathAndType(path, "C");
        if (container == null) {
            container = new SysMenu();
            container.setParentId(root.getId());
            container.setMenuType("C");
            container.setPath(path);
            container.setComponent("");
            container.setVisible("1");
            container.setStatus("0");
            container.setSort(0);
            container.setIsFrame("0");
            container.setIsCache("0");
            container.setCreateTime(LocalDateTime.now());
        }
        container.setMenuName(entity.getEntityName() + "权限");
        container.setEntityCode(entity.getEntityCode());
        container.setUpdateTime(LocalDateTime.now());
        if (StringUtils.hasText(container.getId())) {
            menuMapper.updateById(container);
        } else {
            menuMapper.insert(container);
        }
        return container;
    }

    private SysMenu ensurePermissionMenu(
            SysMenu container,
            EntityDefinition entity,
            EntityPermissionAction action) {
        String perm = action.permissionCode(entity.getEntityCode());
        SysMenu menu = menuMapper.selectByPerm(perm);
        if (menu == null) {
            menu = new SysMenu();
            menu.setParentId(container.getId());
            menu.setMenuType("F");
            menu.setPath("");
            menu.setComponent("");
            menu.setVisible("1");
            menu.setSort(action.ordinal() + 1);
            menu.setIsFrame("0");
            menu.setIsCache("0");
            menu.setCreateTime(LocalDateTime.now());
        }
        menu.setMenuName(action.getLabel());
        menu.setPerm(perm);
        menu.setEntityCode(entity.getEntityCode());
        menu.setStatus("0");
        menu.setUpdateTime(LocalDateTime.now());
        if (StringUtils.hasText(menu.getId())) {
            menuMapper.updateById(menu);
        } else {
            menuMapper.insert(menu);
        }
        return menu;
    }

    private SysMenu ensureCustomPermissionMenu(
            SysMenu container,
            EntityDefinition entity,
            String permissionCode,
            String label) {
        SysMenu menu = menuMapper.selectByPerm(permissionCode);
        if (menu == null) {
            menu = new SysMenu();
            menu.setParentId(container.getId());
            menu.setMenuType("F");
            menu.setPath("");
            menu.setComponent("");
            menu.setVisible("1");
            menu.setSort(100);
            menu.setIsFrame("0");
            menu.setIsCache("0");
            menu.setCreateTime(LocalDateTime.now());
        }
        menu.setMenuName(label);
        menu.setPerm(permissionCode);
        menu.setEntityCode(entity.getEntityCode());
        menu.setStatus("0");
        menu.setUpdateTime(LocalDateTime.now());
        if (StringUtils.hasText(menu.getId())) {
            menuMapper.updateById(menu);
        } else {
            menuMapper.insert(menu);
        }
        return menu;
    }

    private void grantToAdministrators(String menuId, List<SysRole> roles) {
        roles.stream()
                .sorted(Comparator.comparing(SysRole::getRoleCode))
                .forEach(role -> {
                    if (!roleMenuMapper.existsRoleMenu(role.getId(), menuId)) {
                        SysRoleMenu relation = new SysRoleMenu();
                        relation.setRoleId(role.getId());
                        relation.setMenuId(menuId);
                        relation.setCreateTime(LocalDateTime.now());
                        roleMenuMapper.insert(relation);
                    }
                });
    }

    private boolean supportsAction(EntityDefinition entity, EntityPermissionAction action) {
        if (entity != null && entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            return false;
        }
        return action != EntityPermissionAction.APPROVE
                || entity == null
                || entity.getLifecycleMode() == EntityDefinition.LifecycleMode.WORKFLOW;
    }

    private void disablePermission(EntityDefinition entity, EntityPermissionAction action) {
        SysMenu menu = menuMapper.selectByPerm(action.permissionCode(entity.getEntityCode()));
        if (menu == null) {
            return;
        }
        menu.setStatus("1");
        menu.setUpdateTime(LocalDateTime.now());
        menuMapper.updateById(menu);
    }

    private void ensureInitialStatus(EntityDefinition entity) {
        ensureStatus(entity, "NEW", "DRAFT", "草稿", 10, "新建或尚未处理的数据", "info");
    }

    private void ensureWorkflowStatuses(EntityDefinition entity) {
        ensureStatus(entity, "PROCESSING", "PENDING", "处理中", 20, "流程处理中", "warning");
        ensureStatus(entity, "COMPLETED", "APPROVED", "已完成", 30, "流程已正常完成", "success");
        ensureStatus(entity, "TERMINATED", "TERMINATED", "已终止", 40, "流程已终止", "danger");
        ensureStatus(entity, "WITHDRAWN", "WITHDRAWN", "已撤回", 50, "流程由发起人撤回", "info");
    }

    private void ensureStatus(
            EntityDefinition entity,
            String category,
            String code,
            String name,
            int sort,
            String description,
            String color) {
        List<EntityStatus> existing = statusMapper.findByCategory(entity.getEntityCode(), category);
        if (existing != null && !existing.isEmpty()) {
            return;
        }
        EntityStatus status = new EntityStatus();
        status.setEntityCode(entity.getEntityCode());
        status.setStatusCode(code);
        status.setStatusName(name);
        status.setStatusCategory(category);
        status.setSortOrder(sort);
        status.setDescription(description);
        status.setColor(color);
        status.setDeleted(0);
        statusMapper.insert(status);
    }
}
