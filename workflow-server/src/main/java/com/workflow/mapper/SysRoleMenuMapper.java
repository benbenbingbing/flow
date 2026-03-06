package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色菜单关联 Mapper
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {
    
    /**
     * 根据角色ID查询菜单ID列表
     */
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<String> selectMenuIdsByRoleId(@Param("roleId") String roleId);
    
    /**
     * 根据菜单ID查询角色ID列表
     */
    @Select("SELECT role_id FROM sys_role_menu WHERE menu_id = #{menuId}")
    List<String> selectRoleIdsByMenuId(@Param("menuId") String menuId);
    
    /**
     * 删除角色的所有菜单权限
     */
    @Select("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    void deleteByRoleId(@Param("roleId") String roleId);
}
