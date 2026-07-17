package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuService {
    
    private final SysMenuMapper menuMapper;
    private final EntityCodeCatalogPort entityCodeCatalogPort;
    
    /**
     * 查询菜单树
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
     * 查询运行态侧栏菜单树。
     * 管理端菜单树保留全部菜单；侧栏菜单会过滤已经指向不存在实体的动态数据列表菜单。
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
     * 保存菜单
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
     * 更新菜单排序
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

    private boolean isMissingEntityListMenu(SysMenu menu, Set<String> entityCodes) {
        String entityCode = resolveEntityListCode(menu);
        return StringUtils.hasText(entityCode) && !entityCodes.contains(entityCode);
    }

    private String resolveEntityListCode(SysMenu menu) {
        if (menu == null) {
            return null;
        }
        if (StringUtils.hasText(menu.getEntityCode())) {
            return menu.getEntityCode();
        }
        String path = menu.getPath();
        String prefix = "/entity/list/";
        if (!StringUtils.hasText(path) || !path.startsWith(prefix)) {
            return null;
        }
        String code = path.substring(prefix.length());
        int slashIndex = code.indexOf('/');
        return slashIndex >= 0 ? code.substring(0, slashIndex) : code;
    }
    
    /**
     * 导出菜单数据
     */
    public List<SysMenu> exportMenus() {
        return menuMapper.selectList(
            new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSort)
        );
    }
    
    /**
     * 导入菜单数据
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
     */
    public List<Map<String, String>> getMenuTypeOptions() {
        List<Map<String, String>> options = new ArrayList<>();
        options.add(Map.of("value", "M", "label", "目录"));
        options.add(Map.of("value", "C", "label", "菜单"));
        options.add(Map.of("value", "F", "label", "按钮"));
        return options;
    }
}
