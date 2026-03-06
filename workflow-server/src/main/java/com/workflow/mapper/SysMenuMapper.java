package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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
}
