package com.workflow.admin.authorization.menu.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.core.result.PageResult;
import com.workflow.contracts.entity.EntityCodeCatalogPort;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单管理服务
 * <p>
 * 提供菜单的树形查询、分页子菜单查询、子树查询、增删改、状态/显示/排序更新、导入导出等能力。
 * 菜单类型分为 M-目录、C-菜单、F-按钮。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuService {
    
    /** 菜单 Mapper */
    private final SysMenuMapper menuMapper;
    /** 角色 Mapper，用于识别超级管理员并应用菜单授权 */
    private final SysRoleMapper roleMapper;
    /** 实体编码目录端口，用于校验动态实体列表菜单引用的实体是否存在 */
    private final EntityCodeCatalogPort entityCodeCatalogPort;
    
    /**
     * 查询菜单树
     *
     * @return 树形结构的菜单列表
     */
    public List<SysMenu> getMenuTree() {
        List<SysMenu> allMenus = menuMapper.selectList(
            new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getCreateTime)
        );
        return buildTree(allMenus);
    }

    /**
     * 分页查询指定父菜单下的直接子菜单。
     *
     * @param parentId 父菜单ID，为空时取 "0"
     * @param pageNum  页码，为空或非正时取 1
     * @param pageSize 每页条数，为空或非正时取 10
     * @return 分页结果，每条记录已回填 hasChildren 字段
     */
    public PageResult<SysMenu> getChildrenPage(String parentId, Integer pageNum, Integer pageSize) {
        if (parentId == null) {
            parentId = "0";
        }
        Page<SysMenu> page = new Page<>(
            pageNum != null && pageNum > 0 ? pageNum : 1,
            pageSize != null && pageSize > 0 ? pageSize : 10
        );

        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, parentId)
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getCreateTime);

        Page<SysMenu> resultPage = menuMapper.selectPage(page, wrapper);
        for (SysMenu menu : resultPage.getRecords()) {
            menu.setHasChildren(menuMapper.hasChildren(menu.getId()));
        }

        return new PageResult<>(resultPage.getRecords(), resultPage.getTotal(), resultPage.getCurrent(), resultPage.getSize());
    }

    /**
     * 查询以指定节点为根的完整子树（递归所有后代）。
     *
     * @param parentId 父菜单ID，为空时取 "0"
     * @return 子树菜单列表，已逐层填充 children
     */
    public List<SysMenu> getSubtree(String parentId) {
        if (parentId == null) {
            parentId = "0";
        }
        List<SysMenu> children = menuMapper.selectChildrenByParentId(parentId);
        for (SysMenu child : children) {
            child.setChildren(getSubtree(child.getId()));
        }
        return children;
    }

    /**
     * 查询当前用户的运行态侧栏菜单树。
     * 管理端菜单树保留全部菜单；侧栏菜单会过滤未授权菜单和已经指向不存在实体的动态数据列表菜单。
     * 超级管理员默认拥有全部菜单；普通用户仅返回角色直接授权的目录/菜单及其祖先目录。
     *
     * @param userId 当前用户ID
     * @return 过滤后构建的侧栏菜单树
     */
    public List<SysMenu> getSidebarMenuTree(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Collections.emptyList();
        }
        List<SysMenu> allMenus = menuMapper.selectList(
            new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getCreateTime)
        );
        Set<String> entityCodes = entityCodeCatalogPort.findAllEntityCodes();
        List<SysMenu> validMenus = allMenus.stream()
                .filter(menu -> !isMissingEntityListMenu(menu, entityCodes))
                .collect(Collectors.toList());
        if (isSuperAdministrator(userId)) {
            return buildTree(validMenus);
        }

        Set<String> assignedMenuIds = menuMapper.selectMenuIdsByUserId(userId);
        if (assignedMenuIds == null || assignedMenuIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> visibleMenuIds = collectAuthorizedNavigationIds(
                validMenus,
                assignedMenuIds);
        return buildTree(validMenus.stream()
                .filter(menu -> visibleMenuIds.contains(menu.getId()))
                .collect(Collectors.toList()));
    }
    
    /**
     * 根据ID查询菜单
     *
     * @param id 菜单ID
     * @return 菜单对象（已回填父菜单名称），不存在返回 null
     */
    public SysMenu getById(String id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu != null && menu.getParentId() != null && !"0".equals(menu.getParentId())) {
            SysMenu parent = menuMapper.selectById(menu.getParentId());
            if (parent != null) {
                menu.setParentName(parent.getMenuName());
            }
        }
        return menu;
    }
    
    /**
     * 保存菜单（新增或更新）
     *
     * @param menu 菜单对象
     * @return 保存后的菜单对象
     * @throws RuntimeException 权限标识已存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.UPSERT,
            operation = "保存菜单",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "SYS_MENU",
            captureArguments = true,
            captureResult = true)
    public SysMenu saveMenu(SysMenu menu) {
        normalizeEntityListMenu(menu);
        // 校验权限标识唯一性
        if (StringUtils.hasText(menu.getPerm())) {
            String excludeId = menu.getId() != null ? menu.getId() : "";
            if (menuMapper.existsPerm(menu.getPerm(), excludeId)) {
                throw new RuntimeException("权限标识已存在：" + menu.getPerm());
            }
        }
        
        // 设置默认值
        if (!StringUtils.hasText(menu.getParentId())) {
            menu.setParentId("0");
        }
        if (menu.getSort() == null) {
            Integer maxSort = menuMapper.selectMaxSortByParentId(menu.getParentId());
            menu.setSort(maxSort != null ? maxSort + 1 : 0);
        }
        if (!StringUtils.hasText(menu.getStatus())) {
            menu.setStatus(SysMenu.Status.ENABLED.getValue());
        }
        if (!StringUtils.hasText(menu.getVisible())) {
            menu.setVisible(SysMenu.Visible.SHOW.getValue());
        }
        if (!StringUtils.hasText(menu.getIsFrame())) {
            menu.setIsFrame("0");
        }
        if (!StringUtils.hasText(menu.getIsCache())) {
            menu.setIsCache("0");
        }
        
        // 按钮类型不需要path
        if ("F".equals(menu.getMenuType())) {
            menu.setPath("");
            menu.setComponent("");
            menu.setIcon("");
        }
        
        menu.setUpdateTime(LocalDateTime.now());
        
        if (!StringUtils.hasText(menu.getId())) {
            // 新增
            menu.setCreateTime(LocalDateTime.now());
            menuMapper.insert(menu);
            log.info("新增菜单：{}", menu.getMenuName());
        } else {
            // 更新
            menuMapper.updateById(menu);
            log.info("更新菜单：{}", menu.getMenuName());
        }
        
        return menu;
    }

    /**
     * 实体列表菜单的侧栏可见性由角色菜单关系控制，列表数据访问权限由列表配置控制。
     * 因此菜单记录不重复保存列表访问权限码，避免与隐藏的 F 类型权限资源冲突。
     *
     * @param menu 待保存菜单
     */
    private void normalizeEntityListMenu(SysMenu menu) {
        if (menu == null || !"C".equals(menu.getMenuType())) {
            return;
        }
        if ("ENTITY_LIST".equalsIgnoreCase(menu.getResourceType())
                || (StringUtils.hasText(menu.getEntityCode())
                && StringUtils.hasText(menu.getListKey()))) {
            menu.setResourceType("ENTITY_LIST");
            menu.setPerm(null);
        }
    }
    
    /**
     * 删除菜单（逻辑删除，同时删除子菜单）
     *
     * @param id 菜单ID
     * @throws RuntimeException 菜单不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.DELETE,
            operation = "删除菜单",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "SYS_MENU",
            targetIdArg = 0)
    public void deleteMenu(String id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new RuntimeException("菜单不存在");
        }
        
        // 递归删除子菜单
        deleteChildrenRecursively(id);
        
        // 删除当前菜单
        menuMapper.deleteById(id);
        log.info("删除菜单：{}", menu.getMenuName());
    }
    
    /**
     * 递归删除子菜单
     *
     * @param parentId 父菜单ID
     */
    private void deleteChildrenRecursively(String parentId) {
        List<SysMenu> children = menuMapper.selectChildrenByParentId(parentId);
        for (SysMenu child : children) {
            deleteChildrenRecursively(child.getId());
            menuMapper.deleteById(child.getId());
        }
    }
    
    /**
     * 更新菜单状态
     *
     * @param id     菜单ID
     * @param status 状态值：0-启用 1-禁用
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.UPDATE,
            operation = "更新菜单状态",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "SYS_MENU",
            targetIdArg = 0,
            captureArguments = true)
    public void updateStatus(String id, String status) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setStatus(status);
        menu.setUpdateTime(LocalDateTime.now());
        menuMapper.updateById(menu);
    }
    
    /**
     * 更新菜单显示状态
     *
     * @param id      菜单ID
     * @param visible 显示状态：0-显示 1-隐藏
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.UPDATE,
            operation = "更新菜单可见性",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "SYS_MENU",
            targetIdArg = 0,
            captureArguments = true)
    public void updateVisible(String id, String visible) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setVisible(visible);
        menu.setUpdateTime(LocalDateTime.now());
        menuMapper.updateById(menu);
    }
    
    /**
     * 更新菜单排序（按列表顺序设置 sort 为索引值）
     *
     * @param menuIds 按目标顺序排列的菜单ID列表
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.UPDATE,
            operation = "调整菜单排序",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "SYS_MENU",
            captureArguments = true)
    public void updateSort(List<String> menuIds) {
        for (int i = 0; i < menuIds.size(); i++) {
            SysMenu menu = new SysMenu();
            menu.setId(menuIds.get(i));
            menu.setSort(i);
            menu.setUpdateTime(LocalDateTime.now());
            menuMapper.updateById(menu);
        }
    }
    
    /**
     * 构建菜单树
     *
     * @param menus 平铺的菜单列表
     * @return 树形结构的菜单列表
     */
    private List<SysMenu> buildTree(List<SysMenu> menus) {
        Map<String, SysMenu> menuMap = menus.stream()
                .collect(Collectors.toMap(SysMenu::getId, m -> m, (m1, m2) -> m1));
        
        List<SysMenu> tree = new ArrayList<>();
        
        for (SysMenu menu : menus) {
            if ("0".equals(menu.getParentId()) || menu.getParentId() == null) {
                // 顶级菜单
                tree.add(menu);
            } else {
                // 子菜单
                SysMenu parent = menuMap.get(menu.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(menu);
                }
            }
        }
        
        // 按排序值排序
        tree.sort(Comparator.comparingInt(SysMenu::getSort));
        
        return tree;
    }

    /**
     * 判断菜单是否为指向不存在实体的动态数据列表菜单
     *
     * @param menu        菜单对象
     * @param entityCodes 当前有效的实体编码集合
     * @return 是缺失实体的列表菜单返回 true，否则 false
     */
    private boolean isMissingEntityListMenu(SysMenu menu, Set<String> entityCodes) {
        String entityCode = resolveEntityListCode(menu);
        return StringUtils.hasText(entityCode) && !entityCodes.contains(entityCode);
    }

    /**
     * 解析动态数据列表菜单所引用的实体编码
     * <p>
     * 优先取 menu.entityCode；否则从 path 的 /entity-list/ 前缀中提取首段作为实体编码。
     * </p>
     *
     * @param menu 菜单对象
     * @return 实体编码，无法解析返回 null
     */
    private String resolveEntityListCode(SysMenu menu) {
        if (menu == null) {
            return null;
        }
        if (StringUtils.hasText(menu.getEntityCode())) {
            return menu.getEntityCode();
        }
        String path = menu.getPath();
        String prefix = "/entity-list/";
        if (!StringUtils.hasText(path) || !path.startsWith(prefix)) {
            return null;
        }
        String code = path.substring(prefix.length());
        int slashIndex = code.indexOf('/');
        return slashIndex >= 0 ? code.substring(0, slashIndex) : code;
    }

    /**
     * 判断用户是否具有启用的超级管理员角色。
     *
     * @param userId 用户ID
     * @return 是超级管理员返回 true
     */
    private boolean isSuperAdministrator(String userId) {
        List<SysRole> roles = roleMapper.selectRolesByUserId(userId);
        return roles != null && roles.stream().anyMatch(role ->
                role != null
                        && "super_admin".equals(role.getRoleCode())
                        && !"1".equals(role.getStatus()));
    }

    /**
     * 收集用户已授权的可导航菜单及其祖先目录。
     * F 类型按钮只提供功能权限，不应因为被授权而单独撑起侧栏目录。
     *
     * @param menus           有效菜单全集
     * @param assignedMenuIds 用户角色直接关联的菜单ID
     * @return 应出现在侧栏树中的菜单ID集合
     */
    private Set<String> collectAuthorizedNavigationIds(
            List<SysMenu> menus,
            Set<String> assignedMenuIds) {
        Map<String, SysMenu> menuMap = menus.stream()
                .collect(Collectors.toMap(
                        SysMenu::getId,
                        menu -> menu,
                        (left, right) -> left));
        Set<String> result = new HashSet<>();
        for (SysMenu menu : menus) {
            if ("F".equals(menu.getMenuType())
                    || !assignedMenuIds.contains(menu.getId())) {
                continue;
            }
            SysMenu current = menu;
            Set<String> visited = new HashSet<>();
            while (current != null
                    && StringUtils.hasText(current.getId())
                    && visited.add(current.getId())) {
                result.add(current.getId());
                current = menuMap.get(current.getParentId());
            }
        }
        return result;
    }
    
    /**
     * 导出菜单数据
     *
     * @return 按排序升序的全部菜单列表
     */
    public List<SysMenu> exportMenus() {
        return menuMapper.selectList(
            new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSort)
        );
    }
    
    /**
     * 导入菜单数据（权限标识已存在的菜单会被跳过）
     *
     * @param menus 待导入的菜单列表
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.IMPORT,
            operation = "导入菜单",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "SYS_MENU",
            captureArguments = true)
    public void importMenus(List<SysMenu> menus) {
        for (SysMenu menu : menus) {
            // 检查权限标识是否已存在
            if (StringUtils.hasText(menu.getPerm()) && menuMapper.existsPerm(menu.getPerm(), "")) {
                log.warn("权限标识已存在，跳过导入：{}", menu.getPerm());
                continue;
            }
            
            menu.setId(null);
            menu.setCreateTime(LocalDateTime.now());
            menu.setUpdateTime(LocalDateTime.now());
            menuMapper.insert(menu);
        }
    }
    
    /**
     * 获取菜单类型列表
     *
     * @return 菜单类型选项列表（value-label 键值对）
     */
    public List<Map<String, String>> getMenuTypeOptions() {
        List<Map<String, String>> options = new ArrayList<>();
        options.add(Map.of("value", "M", "label", "目录"));
        options.add(Map.of("value", "C", "label", "菜单"));
        options.add(Map.of("value", "F", "label", "按钮"));
        return options;
    }
}
