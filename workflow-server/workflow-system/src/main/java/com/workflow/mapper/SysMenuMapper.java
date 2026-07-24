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
     *
     * @param parentId 父菜单ID
     * @return 子菜单列表
     */
    @Select("SELECT * FROM sys_menu WHERE parent_id = #{parentId} AND deleted = 0 ORDER BY sort ASC")
    List<SysMenu> selectChildrenByParentId(@Param("parentId") String parentId);
    
    /**
     * 查询最大排序值
     *
     * @param parentId 父菜单ID
     * @return 当前最大排序值，无记录返回 null
     */
    @Select("SELECT MAX(sort) FROM sys_menu WHERE parent_id = #{parentId} AND deleted = 0")
    Integer selectMaxSortByParentId(@Param("parentId") String parentId);
    
    /**
     * 检查权限标识是否已存在
     *
     * @param perm      权限标识
     * @param excludeId 排除的ID（更新时传入自身ID，新增传空串）
     * @return 存在返回 true，否则 false
     */
    @Select("SELECT COUNT(*) > 0 FROM sys_menu WHERE perm = #{perm} AND deleted = 0 AND (#{excludeId} = '' OR id != #{excludeId})")
    boolean existsPerm(@Param("perm") String perm, @Param("excludeId") String excludeId);
    
    /**
     * 检查是否有子菜单
     *
     * @param parentId 父菜单ID
     * @return 有子菜单返回 true，否则 false
     */
    @Select("SELECT COUNT(*) > 0 FROM sys_menu WHERE parent_id = #{parentId} AND deleted = 0")
    boolean hasChildren(@Param("parentId") String parentId);

    /**
     * 根据用户ID查询权限标识集合（F类型按钮菜单）
     *
     * @param userId 用户ID
     * @return 权限标识集合
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
     *
     * @param entityCode 实体编码
     * @return 权限标识集合
     */
    @Select("SELECT DISTINCT perm FROM sys_menu " +
            "WHERE entity_code = #{entityCode} AND menu_type = 'F' AND status = '0' AND deleted = 0 " +
            "AND perm IS NOT NULL AND perm != ''")
    Set<String> selectPermsByEntityCode(@Param("entityCode") String entityCode);

    /**
     * 根据权限标识查询菜单
     *
     * @param perm 权限标识
     * @return 菜单对象，不存在返回 null
     */
    @Select("SELECT * FROM sys_menu WHERE perm = #{perm} AND deleted = 0 LIMIT 1")
    SysMenu selectByPerm(@Param("perm") String perm);

    /**
     * 根据路由地址和菜单类型查询菜单
     *
     * @param path     路由地址
     * @param menuType 菜单类型（M-目录 C-菜单 F-按钮）
     * @return 菜单对象，不存在返回 null
     */
    @Select("SELECT * FROM sys_menu WHERE path = #{path} AND menu_type = #{menuType} AND deleted = 0 LIMIT 1")
    SysMenu selectByPathAndType(@Param("path") String path, @Param("menuType") String menuType);
}
