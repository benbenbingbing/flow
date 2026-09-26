package com.workflow.entity.permission.application;

import com.workflow.contracts.entity.permission.spi.EntityPermissionOptionProvider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.entity.permission.api.response.EntityPermissionOptionDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRoleMenu;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMenuMapper;
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

    /**
     * 返回实体的所有可选权限选项，包含标准动作、绕过权限、已有自定义权限和扩展提供器注入的权限。
     *
     * @param entityCode 实体编码
     * @return 权限选项列表，系统实体返回空列表
     */
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
            var provided = provider.getOptions(entityCode);
            if (provided == null) {
                continue;
            }
            for (var option : provided) {
                if (option != null && StringUtils.hasText(option.getCode())
                        && options.stream().noneMatch(existing -> existing.getCode().equals(option.getCode()))) {
                    // SPI 模型独立于 API DTO，保持原有 HTTP 字段及按权限码去重的行为。
                    options.add(new EntityPermissionOptionDTO(option.getAction(), option.getCode(),
                            option.getLabel(), option.getDescription(), option.getCategory()));
                }
            }
        }
        return options;
    }

    /**
     * 全量同步所有动态实体的标准权限菜单、状态及列表自定义权限。
     *
     * <p>为每个实体创建权限菜单容器和按钮权限，授予管理员角色，
     * 并初始化流程实体的状态数据。</p>
     */
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

    /**
     * 升级时仅补齐流程实体的重新发起权限。独立的 bootstrap 版本避免重跑旧的
     * 状态初始化或重写历史按钮配置；普通角色仍需管理员授权，按钮仍默认关闭。
     */
    @Transactional(rollbackFor = Exception.class)
    public void synchronizeRestartPermissions() {
        List<EntityDefinition> entities = definitionMapper.selectList(
                new LambdaQueryWrapper<EntityDefinition>().orderByAsc(EntityDefinition::getCreatedAt));
        if (entities.isEmpty()) return;
        List<SysRole> administrators = roleMapper.selectAdministratorRoles();
        if (administrators == null || administrators.isEmpty()) {
            throw new IllegalStateException("重新发起权限初始化失败：系统缺少管理员角色");
        }
        SysMenu root = ensureRootMenu();
        for (EntityDefinition entity : entities) {
            if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM
                    || entity.getLifecycleMode() != EntityDefinition.LifecycleMode.WORKFLOW) continue;
            SysMenu permission = ensurePermissionMenu(ensureEntityContainer(root, entity), entity,
                    EntityPermissionAction.RESTART_PROCESS);
            grantToAdministrators(permission.getId(), administrators);
        }
    }

    /**
     * 同步单个实体的标准权限菜单与初始状态。
     *
     * @param entity 实体定义，为空或系统实体直接返回
     */
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

    /**
     * 禁用实体的所有标准权限菜单（标记为停用状态）。
     *
     * @param entityCode 实体编码
     */
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

    /**
     * 同步列表配置中的自定义按钮权限和访问权限码到菜单资源。
     *
     * @param config 列表配置，为空或实体不存在直接返回
     */
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

    /**
     * 处理{@code synchronize}实体，并将结果传给后续步骤。
     *
     * @param entity 实体，作为 {@code ensureEntityContainer} 的输入影响后续处理
     * @param root 根，作为 {@code ensureEntityContainer} 的输入影响后续处理
     * @param administratorRoles 管理员角色集合，供本方法处理{@code synchronize}实体时使用
     */
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

    /**
     * 确保作用域{@code bypass}权限菜单；不满足约束时阻止后续处理。
     *
     * @param container {@code container}，作为 {@code menu.setParentId} 的输入影响后续处理
     * @param entity 实体，作为 {@code scopeBypassPermission} 的输入影响后续处理
     * @return 确保后的作用域{@code bypass}权限菜单结果，供调用方继续处理
     */
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

    /**
     * 处理授权截止{@code super}{@code administrators}，并将结果传给后续步骤。
     *
     * @param menuId 菜单ID，后续用于处理授权截止{@code super}{@code administrators}时定位或关联目标
     * @param administratorRoles 管理员角色集合，供本方法处理授权截止{@code super}{@code administrators}时使用
     */
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

    /**
     * 生成作用域{@code bypass}权限文本，供后续匹配或展示。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 处理后的作用域{@code bypass}权限文本，供调用方比较或展示
     */
    private String scopeBypassPermission(String entityCode) {
        return "entity:" + EntityPermissionAction.normalizeEntityCode(entityCode)
                + ":scope:bypass";
    }

    /**
     * 确保根菜单；不满足约束时阻止后续处理。
     *
     * @return 确保后的根菜单结果，供调用方继续处理
     */
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

    /**
     * 确保实体{@code container}；不满足约束时阻止后续处理。
     *
     * @param root 根，作为 {@code container.setParentId} 的输入影响后续处理
     * @param entity 实体，作为 {@code EntityPermissionAction.normalizeEntityCode} 的输入影响后续处理
     * @return 确保后的实体{@code container}结果，供调用方继续处理
     */
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

    /**
     * 确保权限菜单；不满足约束时阻止后续处理。
     *
     * @param container {@code container}，作为 {@code menu.setParentId} 的输入影响后续处理
     * @param entity 实体，作为 {@code action.permissionCode} 的输入影响后续处理
     * @param action 动作标识，决定后续权限菜单采用的处理分支
     * @return 确保后的权限菜单结果，供调用方继续处理
     */
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

    /**
     * 确保自定义权限菜单；不满足约束时阻止后续处理。
     *
     * @param container {@code container}，作为 {@code menu.setParentId} 的输入影响后续处理
     * @param entity 实体，作为 {@code menu.setEntityCode} 的输入影响后续处理
     * @param permissionCode 权限编码，后续用于确保自定义权限菜单时定位或关联目标
     * @param label 标签，后续用于确保自定义权限菜单时匹配或展示
     * @return 确保后的自定义权限菜单结果，供调用方继续处理
     */
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

    /**
     * 处理授权截止{@code administrators}，并将结果传给后续步骤。
     *
     * @param menuId 菜单ID，后续用于处理授权截止{@code administrators}时定位或关联目标
     * @param roles 角色集合，供本方法处理授权截止{@code administrators}时使用
     */
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

    /**
     * 判断是否支持动作；判断结果决定调用方的后续分支。
     *
     * @param entity 实体，供本方法判断是否支持动作时使用
     * @param action 动作标识，决定后续动作采用的处理分支
     * @return 动作条件成立时为 true，否则为 false
     */
    private boolean supportsAction(EntityDefinition entity, EntityPermissionAction action) {
        if (entity != null && entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            return false;
        }
        return (action != EntityPermissionAction.APPROVE && action != EntityPermissionAction.RESTART_PROCESS)
                || entity == null
                || entity.getLifecycleMode() == EntityDefinition.LifecycleMode.WORKFLOW;
    }

    /**
     * 停用权限；结果供调用方的后续步骤使用。
     *
     * @param entity 实体，作为 {@code menuMapper.selectByPerm} 的输入影响后续处理
     * @param action 动作标识，决定后续权限采用的处理分支
     */
    private void disablePermission(EntityDefinition entity, EntityPermissionAction action) {
        SysMenu menu = menuMapper.selectByPerm(action.permissionCode(entity.getEntityCode()));
        if (menu == null) {
            return;
        }
        menu.setStatus("1");
        menu.setUpdateTime(LocalDateTime.now());
        menuMapper.updateById(menu);
    }

    /**
     * 确保初始状态；不满足约束时阻止后续处理。
     *
     * @param entity 实体，作为 {@code ensureStatus} 的输入影响后续处理
     */
    private void ensureInitialStatus(EntityDefinition entity) {
        ensureStatus(entity, "NEW", "DRAFT", "草稿", 10, "新建或尚未处理的数据", "info");
    }

    /**
     * 确保工作流{@code statuses}；不满足约束时阻止后续处理。
     *
     * @param entity 实体，作为 {@code ensureStatus} 的输入影响后续处理
     */
    private void ensureWorkflowStatuses(EntityDefinition entity) {
        ensureStatus(entity, "PROCESSING", "PENDING", "处理中", 20, "流程处理中", "warning");
        ensureStatus(entity, "COMPLETED", "APPROVED", "已完成", 30, "流程已正常完成", "success");
        ensureStatus(entity, "TERMINATED", "TERMINATED", "已终止", 40, "流程已终止", "danger");
        ensureStatus(entity, "WITHDRAWN", "WITHDRAWN", "已撤回", 50, "流程由发起人撤回", "info");
    }

    /**
     * 确保状态；不满足约束时阻止后续处理。
     *
     * @param entity 实体，作为 {@code statusMapper.findByCategory} 的输入影响后续处理
     * @param category 类别，决定后续状态或结果的归类
     * @param code 编码，后续用于确保状态时定位或关联目标
     * @param name 名称，后续用于确保状态时匹配或展示
     * @param sort 排序，作为 {@code status.setSortOrder} 的输入影响后续处理
     * @param description 描述，作为 {@code status.setDescription} 的输入影响后续处理
     * @param color {@code color}，作为 {@code status.setColor} 的输入影响后续处理
     */
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
