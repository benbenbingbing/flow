package com.workflow.admin.authorization.role.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 角色菜单关联 Mapper
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {
    
    /**
     * 根据角色ID查询菜单ID列表
     *
     * @param roleId 角色ID
     * @return 菜单ID列表
     */
    default List<String> selectMenuIdsByRoleId(String roleId) {
        return selectObjs(Wrappers.<SysRoleMenu>lambdaQuery()
                .select(SysRoleMenu::getMenuId).eq(SysRoleMenu::getRoleId, roleId));
    }
    
    /**
     * 根据菜单ID查询角色ID列表
     *
     * @param menuId 菜单ID
     * @return 角色ID列表
     */
    default List<String> selectRoleIdsByMenuId(String menuId) {
        return selectObjs(Wrappers.<SysRoleMenu>lambdaQuery()
                .select(SysRoleMenu::getRoleId).eq(SysRoleMenu::getMenuId, menuId));
    }
    
    /**
     * 删除角色的所有菜单权限
     *
     * @param roleId 角色ID
     */
    default void deleteByRoleId(String roleId) {
        // 关联表没有逻辑删除字段，BaseMapper 保留物理删除关联关系的行为。
        delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId));
    }

    /**
     * 检查角色是否已分配指定菜单权限
     *
     * @param roleId 角色ID
     * @param menuId 菜单ID
     * @return 已分配返回 true，否则 false
     */
    default boolean existsRoleMenu(String roleId, String menuId) {
        return selectCount(Wrappers.<SysRoleMenu>lambdaQuery()
                .eq(SysRoleMenu::getRoleId, roleId).eq(SysRoleMenu::getMenuId, menuId)) > 0;
    }
}
