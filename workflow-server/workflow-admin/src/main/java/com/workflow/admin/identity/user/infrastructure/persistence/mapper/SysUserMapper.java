package com.workflow.admin.identity.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户管理 Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * Atomically activates the disabled built-in account only while it still
     * carries the historical public password hash.
     */
    @Update("UPDATE sys_user SET password = #{passwordHash}, "
            + "password_reset_required = 0, status = '0', update_time = CURRENT_TIMESTAMP "
            + "WHERE id = '1' AND username = 'admin' AND deleted = 0 AND status = '1' "
            + "AND password = #{expectedPasswordHash}")
    int activateBootstrapAdministrator(
            @Param("passwordHash") String passwordHash,
            @Param("expectedPasswordHash") String expectedPasswordHash);

    @Select("SELECT COUNT(*) > 0 FROM sys_user WHERE id = '1' AND username = 'admin' "
            + "AND deleted = 0 AND status = '1' AND password = #{expectedPasswordHash}")
    boolean isBootstrapAdministratorPending(
            @Param("expectedPasswordHash") String expectedPasswordHash);
    
    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户对象，不存在返回 null
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0")
    SysUser selectByUsername(@Param("username") String username);
    
    /**
     * 检查用户名是否存在
     *
     * @param username  用户名
     * @param excludeId 排除的ID（更新时传入自身ID，新增传空串）
     * @return 存在返回 true，否则 false
     */
    @Select("SELECT COUNT(*) > 0 FROM sys_user WHERE username = #{username} AND deleted = 0 AND (#{excludeId} = '' OR id != #{excludeId})")
    boolean existsUsername(@Param("username") String username, @Param("excludeId") String excludeId);

    /**
     * 分页查询已分配指定角色的用户
     *
     * @param page    分页参数
     * @param roleId  角色ID
     * @param keyword 用户名、昵称、邮箱或手机号关键字
     * @return 用户分页结果
     */
    @Select({
            "<script>",
            "SELECT u.* FROM sys_user u",
            "INNER JOIN sys_user_role ur ON ur.user_id = u.id",
            "WHERE u.deleted = 0 AND ur.role_id = #{roleId}",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (u.username LIKE CONCAT('%', #{keyword}, '%')",
            "OR u.nickname LIKE CONCAT('%', #{keyword}, '%')",
            "OR u.email LIKE CONCAT('%', #{keyword}, '%')",
            "OR u.phone LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "ORDER BY u.create_time DESC",
            "</script>"
    })
    Page<SysUser> selectPageByRoleId(
            Page<SysUser> page,
            @Param("roleId") String roleId,
            @Param("keyword") String keyword);

    @Select({
            "<script>",
            "SELECT DISTINCT u.* FROM sys_user u",
            "<if test='roleId != null and roleId != \"\"'>",
            "INNER JOIN sys_user_role ur ON ur.user_id = u.id",
            "</if>",
            "WHERE u.deleted = 0",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (u.username LIKE CONCAT('%', #{keyword}, '%')",
            "OR u.nickname LIKE CONCAT('%', #{keyword}, '%')",
            "OR u.email LIKE CONCAT('%', #{keyword}, '%')",
            "OR u.phone LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "<if test='status != null and status != \"\"'>AND u.status = #{status}</if>",
            "<if test='orgId != null and orgId != \"\"'>AND u.org_id = #{orgId}</if>",
            "<if test='deptId != null and deptId != \"\"'>AND u.dept_id = #{deptId}</if>",
            "<if test='roleId != null and roleId != \"\"'>AND ur.role_id = #{roleId}</if>",
            "ORDER BY u.create_time DESC",
            "</script>"
    })
    Page<SysUser> selectUserPage(
            Page<SysUser> page,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("orgId") String orgId,
            @Param("deptId") String deptId,
            @Param("roleId") String roleId);
    
    /**
     * 查询用户的角色列表
     *
     * @param userId 用户ID
     * @return 用户的角色列表
     */
    @Select("SELECT r.id, r.role_name, r.role_code FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = 0 AND r.status = '0'")
    List<SysUser> selectUserRoles(@Param("userId") String userId);
}
