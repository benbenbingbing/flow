package com.workflow.admin.authorization.menu.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu;
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
    default List<SysMenu> selectChildrenByParentId(String parentId) {
        return selectList(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getParentId, parentId)
                .orderByAsc(SysMenu::getSort));
    }
    
    /**
     * 查询最大排序值
     *
     * @param parentId 父菜单ID
     * @return 当前最大排序值，无记录返回 null
     */
    default Integer selectMaxSortByParentId(String parentId) {
        // 只投影聚合值，空集合仍返回 null；不把整批菜单读入内存计算排序。
        List<Object> values = selectObjs(Wrappers.<SysMenu>query()
                .select("MAX(sort)").eq("parent_id", parentId));
        return values.isEmpty() || values.get(0) == null ? null : ((Number) values.get(0)).intValue();
    }
    
    /**
     * 检查权限标识是否已存在
     *
     * @param perm      权限标识
     * @param excludeId 排除的ID（更新时传入自身ID，新增传空串或 null）
     * @return 存在返回 true，否则 false
     */
    default boolean existsPerm(String perm, String excludeId) {
        return selectCount(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getPerm, perm)
                .ne(excludeId != null && !excludeId.isEmpty(), SysMenu::getId, excludeId)) > 0;
    }
    
    /**
     * 检查是否有子菜单
     *
     * @param parentId 父菜单ID
     * @return 有子菜单返回 true，否则 false
     */
    default boolean hasChildren(String parentId) {
        return selectCount(Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getParentId, parentId)) > 0;
    }

    /**
     * 根据用户ID查询权限标识集合（F类型按钮菜单）。
     * NULLIF 同时排除空串和 NULL，避免 Oracle 中与空串比较导致所有权限被过滤。
     *
     * @param userId 用户ID
     * @return 权限标识集合
     */
    @Select("SELECT DISTINCT granted.permission FROM (" +
            "SELECT m.perm AS permission FROM sys_menu m " +
            "JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} " +
            "AND r.status = '0' AND r.deleted = 0 " +
            "AND m.menu_type = 'F' AND m.status = '0' AND m.deleted = 0 " +
            "AND NULLIF(m.perm, '') IS NOT NULL " +
            "UNION ALL " +
            "SELECT '*' AS permission FROM sys_role r " +
            "JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} " +
            "AND r.role_code = 'super_admin' " +
            "AND r.status = '0' AND r.deleted = 0" +
            ") granted")
    Set<String> selectPermsByUserId(@Param("userId") String userId);

    /**
     * 根据用户ID查询已分配的菜单资源ID集合。
     *
     * @param userId 用户ID
     * @return 用户通过启用角色获得的菜单资源ID集合
     */
    @Select("SELECT DISTINCT rm.menu_id FROM sys_role_menu rm " +
            "JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = '0' AND r.deleted = 0")
    Set<String> selectMenuIdsByUserId(@Param("userId") String userId);

    /**
     * 根据实体编码查询F类型按钮菜单的权限标识集合
     *
     * @param entityCode 实体编码
     * @return 权限标识集合
     */
    default Set<String> selectPermsByEntityCode(String entityCode) {
        // DISTINCT 按数据库排序规则去重，不能改由 Java 区分大小写/重音后去重；Set 仅保留返回类型。
        // 固定权限列投影不接受外部 SQL；NULLIF 同时保留 Oracle 的空串和 NULL 过滤语义。
        return new java.util.HashSet<>(this.<String>selectObjs(Wrappers.<SysMenu>query()
                .select("DISTINCT perm").lambda()
                .eq(SysMenu::getEntityCode, entityCode)
                .eq(SysMenu::getMenuType, "F")
                .eq(SysMenu::getStatus, "0")
                .apply("NULLIF(perm, '') IS NOT NULL")));
    }

    /**
     * 根据权限标识查询菜单
     *
     * @param perm 权限标识
     * @return 菜单对象，不存在返回 null
     */
    default SysMenu selectByPerm(String perm) {
        // 保留原查询仅取一行的语义，由分页插件生成目标数据库的限制语法。
        return selectPage(new Page<SysMenu>(1, 1, false), Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getPerm, perm)).getRecords().stream().findFirst().orElse(null);
    }

    /**
     * 根据路由地址和菜单类型查询菜单
     *
     * @param path     路由地址
     * @param menuType 菜单类型（M-目录 C-菜单 F-按钮）
     * @return 菜单对象，不存在返回 null
     */
    default SysMenu selectByPathAndType(String path, String menuType) {
        // 保留原查询仅取一行的语义，由分页插件生成目标数据库的限制语法。
        return selectPage(new Page<SysMenu>(1, 1, false), Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getPath, path)
                .eq(SysMenu::getMenuType, menuType)).getRecords().stream().findFirst().orElse(null);
    }
}
