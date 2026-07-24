package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.common.PageResult;
import com.workflow.contracts.entity.EntityCodeCatalogPort;
import com.workflow.entity.SysMenu;
import com.workflow.mapper.SysMenuMapper;
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
     * 查询运行态侧栏菜单树。
     * 管理端菜单树保留全部菜单；侧栏菜单会过滤已经指向不存在实体的动态数据列表菜单。
     *
     * @return 过滤后构建的侧栏菜单树
     */
    public List<SysMenu> getSidebarMenuTree() {
        List<SysMenu> allMenus = menuMapper.selectList(
            new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getCreateTime)
        );
        Set<String> entityCodes = entityCodeCatalogPort.findAllEntityCodes();
        List<SysMenu> validMenus = allMenus.stream()
                .filter(menu -> !isMissingEntityListMenu(menu, entityCodes))
                .collect(Collectors.toList());
        return buildTree(validMenus);
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
    public SysMenu saveMenu(SysMenu menu) {
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
     * 删除菜单（逻辑删除，同时删除子菜单）
     *
     * @param id 菜单ID
     * @throws RuntimeException 菜单不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
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
