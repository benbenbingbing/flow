package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色管理 Mapper
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
    
    /**
     * 检查角色编码是否存在
     *
     * @param roleCode  角色编码
     * @param excludeId 排除的ID（更新时传入自身ID，新增传空串）
     * @return 存在返回 true，否则 false
     */
    @Select("SELECT COUNT(*) > 0 FROM sys_role WHERE role_code = #{roleCode} AND deleted = 0 AND (#{excludeId} = '' OR id != #{excludeId})")
    boolean existsRoleCode(@Param("roleCode") String roleCode, @Param("excludeId") String excludeId);
    
    /**
     * 查询角色的菜单ID列表
     *
     * @param roleId 角色ID
     * @return 菜单ID列表
     */
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<String> selectRoleMenuIds(@Param("roleId") String roleId);
    
    /**
     * 查询用户的角色列表
     *
     * @param userId 用户ID
     * @return 用户的角色列表
     */
    @Select("SELECT r.* FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<SysRole> selectRolesByUserId(@Param("userId") String userId);

    /**
     * 查询管理员角色集合（super_admin、admin）
     *
     * @return 管理员角色列表
     */
    @Select("SELECT * FROM sys_role WHERE role_code IN ('super_admin', 'admin') AND deleted = 0")
    List<SysRole> selectAdministratorRoles();
}
