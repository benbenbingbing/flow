package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * 菜单管理 Mapper
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
    
    /**
     * 根据父ID查询子菜单
     */
    @Select("SELECT * FROM sys_menu WHERE parent_id = #{parentId} AND deleted = 0 ORDER BY sort ASC")
    List<SysMenu> selectChildrenByParentId(@Param("parentId") String parentId);
    
    /**
     * 查询最大排序值
     */
    @Select("SELECT MAX(sort) FROM sys_menu WHERE parent_id = #{parentId} AND deleted = 0")
    Integer selectMaxSortByParentId(@Param("parentId") String parentId);
    
    /**
     * 检查权限标识是否已存在
     */
    @Select("SELECT COUNT(*) > 0 FROM sys_menu WHERE perm = #{perm} AND deleted = 0 AND (#{excludeId} = '' OR id != #{excludeId})")
    boolean existsPerm(@Param("perm") String perm, @Param("excludeId") String excludeId);
    
    /**
     * 检查是否有子菜单
     */
    @Select("SELECT COUNT(*) > 0 FROM sys_menu WHERE parent_id = #{parentId} AND deleted = 0")
    boolean hasChildren(@Param("parentId") String parentId);

    /**
     * 根据用户ID查询权限标识集合（F类型按钮菜单）
     */
    @Select("SELECT DISTINCT m.perm FROM sys_menu m " +
            "JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} " +
            "AND m.menu_type = 'F' AND m.status = '0' AND m.deleted = 0 " +
            "AND m.perm IS NOT NULL AND m.perm != ''")
    Set<String> selectPermsByUserId(@Param("userId") String userId);

    /**
     * 根据实体编码查询F类型按钮菜单的权限标识集合
     */
    @Select("SELECT DISTINCT perm FROM sys_menu " +
            "WHERE entity_code = #{entityCode} AND menu_type = 'F' AND status = '0' AND deleted = 0 " +
            "AND perm IS NOT NULL AND perm != ''")
    Set<String> selectPermsByEntityCode(@Param("entityCode") String entityCode);

    @Select("SELECT * FROM sys_menu WHERE perm = #{perm} AND deleted = 0 LIMIT 1")
    SysMenu selectByPerm(@Param("perm") String perm);

    @Select("SELECT * FROM sys_menu WHERE path = #{path} AND menu_type = #{menuType} AND deleted = 0 LIMIT 1")
    SysMenu selectByPathAndType(@Param("path") String path, @Param("menuType") String menuType);
}
