package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.entity.SysMenu;
import com.workflow.entity.SysRole;
import com.workflow.entity.SysRoleMenu;
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
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色管理服务
 * <p>
 * 提供角色的增删改查、状态切换、菜单权限分配及角色菜单权限树查询等能力。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleService {
    
    /** 角色 Mapper */
    private final SysRoleMapper roleMapper;
    /** 菜单 Mapper，用于构建角色菜单权限树 */
    private final SysMenuMapper menuMapper;
    /** 角色菜单关联 Mapper，维护角色与菜单权限的关系 */
    private final SysRoleMenuMapper roleMenuMapper;
    
    /**
     * 查询角色列表（已填充菜单权限ID）
     *
     * @return 角色列表，按排序升序
     */
    public List<SysRole> getRoleList() {
        List<SysRole> roles = roleMapper.selectList(
            new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getSort)
        );
        // 填充菜单权限
        roles.forEach(this::fillRoleMenus);
        return roles;
    }
    
    /**
     * 查询所有启用的角色
     *
     * @return 启用状态的角色列表，按排序升序
     */
    public List<SysRole> getEnabledRoles() {
        return roleMapper.selectList(
            new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getStatus, SysRole.Status.ENABLED.getValue())
                .orderByAsc(SysRole::getSort)
        );
    }
    
    /**
     * 根据ID查询角色
     *
     * @param id 角色ID
     * @return 角色对象（已填充菜单权限ID），不存在返回 null
     */
    public SysRole getById(String id) {
        SysRole role = roleMapper.selectById(id);
        if (role != null) {
            fillRoleMenus(role);
        }
        return role;
    }
    
    /**
     * 保存角色（新增或更新），并同步角色菜单权限
     *
     * @param role 角色对象，menuIds 为关联的菜单ID列表
     * @return 保存后的角色对象
     * @throws RuntimeException 角色编码已存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public SysRole saveRole(SysRole role) {
        // 校验角色编码唯一性
        if (StringUtils.hasText(role.getRoleCode())) {
            String excludeId = role.getId() != null ? role.getId() : "";
            if (roleMapper.existsRoleCode(role.getRoleCode(), excludeId)) {
                throw new RuntimeException("角色编码已存在：" + role.getRoleCode());
            }
        }
        
        // 设置默认值
        if (!StringUtils.hasText(role.getStatus())) {
            role.setStatus(SysRole.Status.ENABLED.getValue());
        }
        if (role.getSort() == null) {
            role.setSort(0);
        }
        
        role.setUpdateTime(LocalDateTime.now());
        
        if (!StringUtils.hasText(role.getId())) {
            // 新增
            role.setCreateTime(LocalDateTime.now());
            roleMapper.insert(role);
            log.info("新增角色：{}", role.getRoleName());
        } else {
            // 更新
            roleMapper.updateById(role);
            log.info("更新角色：{}", role.getRoleName());
        }
        
        // 保存菜单权限
        if (role.getMenuIds() != null) {
            saveRoleMenus(role.getId(), role.getMenuIds());
        }
        
        return role;
    }
    
    /**
     * 删除角色（先删除菜单权限关联，再逻辑删除角色）
     *
     * @param id 角色ID
     * @throws RuntimeException 角色不存在或为超级管理员角色时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(String id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }
        
        // 不能删除超级管理员角色
        if ("super_admin".equals(role.getRoleCode())) {
            throw new RuntimeException("不能删除超级管理员角色");
        }
        
        // 删除菜单权限关联
        roleMenuMapper.deleteByRoleId(id);
        
        // 逻辑删除角色
        roleMapper.deleteById(id);
        log.info("删除角色：{}", role.getRoleName());
    }
    
    /**
     * 更新角色状态
     *
     * @param id     角色ID
     * @param status 状态值：0-启用 1-禁用
     * @throws RuntimeException 禁用超级管理员角色时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String id, String status) {
        // 不能禁用超级管理员角色
        if ("1".equals(status)) {
            SysRole role = roleMapper.selectById(id);
            if (role != null && "super_admin".equals(role.getRoleCode())) {
                throw new RuntimeException("不能禁用超级管理员角色");
            }
        }
        
        SysRole role = new SysRole();
        role.setId(id);
        role.setStatus(status);
        role.setUpdateTime(LocalDateTime.now());
        roleMapper.updateById(role);
    }
    
    /**
     * 保存角色菜单权限（先删除原有权限，再批量插入新权限）
     *
     * @param roleId  角色ID
     * @param menuIds 菜单ID列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRoleMenus(String roleId, List<String> menuIds) {
        // 删除原有权限
        roleMenuMapper.deleteByRoleId(roleId);
        
        // 添加新权限
        if (menuIds != null && !menuIds.isEmpty()) {
            for (String menuId : menuIds) {
                SysRoleMenu roleMenu = new SysRoleMenu();
                roleMenu.setRoleId(roleId);
                roleMenu.setMenuId(menuId);
                roleMenu.setCreateTime(LocalDateTime.now());
                roleMenuMapper.insert(roleMenu);
            }
        }
    }
    
    /**
     * 获取角色的菜单权限树
     *
     * @param roleId 角色ID
     * @return 角色拥有的菜单权限树，无权限返回空列表
     */
    public List<SysMenu> getRoleMenuTree(String roleId) {
        List<String> menuIds = roleMenuMapper.selectMenuIdsByRoleId(roleId);
        if (menuIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 查询所有菜单
        List<SysMenu> allMenus = menuMapper.selectList(
            new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getId, menuIds)
                .orderByAsc(SysMenu::getSort)
        );
        
        // 构建树形结构
        return buildMenuTree(allMenus);
    }
    
    /**
     * 填充角色菜单信息（回填 menuIds 字段）
     *
     * @param role 待填充的角色对象
     */
    private void fillRoleMenus(SysRole role) {
        List<String> menuIds = roleMenuMapper.selectMenuIdsByRoleId(role.getId());
        role.setMenuIds(menuIds);
    }
    
    /**
     * 构建菜单树
     *
     * @param menus 平铺的菜单列表
     * @return 树形结构的菜单列表
     */
    private List<SysMenu> buildMenuTree(List<SysMenu> menus) {
        java.util.Map<String, SysMenu> menuMap = menus.stream()
                .collect(Collectors.toMap(SysMenu::getId, m -> m));
        
        List<SysMenu> tree = new ArrayList<>();
        
        for (SysMenu menu : menus) {
            if ("0".equals(menu.getParentId()) || menu.getParentId() == null) {
                tree.add(menu);
            } else {
                SysMenu parent = menuMap.get(menu.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(menu);
                }
            }
        }
        
        tree.sort(java.util.Comparator.comparingInt(SysMenu::getSort));
        return tree;
    }
}
