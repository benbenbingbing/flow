package com.workflow.admin.identity.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.time.LocalDateTime;

/**
 * 用户管理 Mapper
 */
// 搜索模式在 MyBatis 中组装并以 VARCHAR 绑定，保留通配符语义，避免数据库 CONCAT 差异。
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 批量读取展示所需列，调用方每批最多 100 个键。比较交给数据库以保持大小写/排序规则，
     * 每个分支都从用户表查询，避免 Oracle 等产品对无 FROM 常量表的方言差异。
     * CONCAT 明确 UNION 输出参数的字符串类型，避免数据库在准备语句时无法推导类型。
     */
    @Select("""
            <script>
            <foreach collection="keys" item="key" separator=" UNION ALL ">
              SELECT CONCAT(#{key,jdbcType=VARCHAR}, '') AS lookup_key, username, nickname,
                CASE WHEN username = #{key,jdbcType=VARCHAR} THEN 0 ELSE 1 END AS match_rank
              FROM sys_user WHERE deleted = 0
                AND (username = #{key,jdbcType=VARCHAR} OR id = #{key,jdbcType=VARCHAR})
            </foreach>
            ORDER BY match_rank
            </script>
            """)
    List<com.workflow.admin.identity.user.infrastructure.persistence.record.UserDisplayNameRow> selectDisplayNameRows(
            @Param("keys") List<String> keys);

    /**
     * 批量查询仍存在的用户 ID，供用户组成员写入前校验。
     *
     * <p>禁用用户仍可保留成员关系，因此这里只排除已逻辑删除用户。</p>
     *
     * @param ids 待校验用户 ID
     * @return 未删除且存在的用户 ID
     */
    default List<String> selectExistingIdsByIds(List<String> ids) {
        // 空集合不能退化为无条件查询，否则成员校验可能扩大到所有用户。
        return ids == null || ids.isEmpty() ? List.of() : selectObjs(Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId).in(SysUser::getId, ids));
    }

    /**
     * Atomically activates the disabled built-in account only while it still
     * carries the historical public password hash.
     *
     * @param passwordHash 密码哈希，供本方法激活初始化管理员时使用
     * @param expectedPasswordHash 预期密码哈希，供本方法激活初始化管理员时使用
     * @return 激活后的初始化管理员结果，供调用方继续处理
     */
    @Update("UPDATE sys_user SET password = #{passwordHash}, "
            + "password_reset_required = 0, status = '0', update_time = CURRENT_TIMESTAMP "
            + "WHERE id = '1' AND username = 'admin' AND deleted = 0 AND status = '1' "
            + "AND password = #{expectedPasswordHash}")
    int activateBootstrapAdministrator(
            @Param("passwordHash") String passwordHash,
            @Param("expectedPasswordHash") String expectedPasswordHash);

    /**
     * 检查内置管理员是否仍处于可激活状态；密码哈希保持参数绑定。
     *
     * @param expectedPasswordHash 预期密码哈希，供本方法判断是否初始化管理员待处理时使用
     * @return 初始化管理员待处理条件成立时为 true，否则为 false
     */
    default boolean isBootstrapAdministratorPending(String expectedPasswordHash) {
        return selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getId, "1").eq(SysUser::getUsername, "admin")
                .eq(SysUser::getStatus, "1").eq(SysUser::getPassword, expectedPasswordHash)) > 0;
    }
    
    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户对象，不存在返回 null
     */
    default SysUser selectByUsername(String username) {
        return selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
    }

    /**
     * 查询更新；查询结果供调用方展示或继续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 查询后的更新结果，供调用方继续处理
     */
    @Select("SELECT * FROM sys_user WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SysUser selectForUpdate(@Param("id") String id);

    /**
     * 查询更新ID 集合；查询结果供调用方展示或继续处理。
     *
     * @param ids ID 集合，供本方法查询更新ID 集合时使用
     * @return 系统用户集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT * FROM sys_user
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
              AND deleted = 0
            ORDER BY id
            FOR UPDATE
            </script>
            """)
    List<SysUser> selectForUpdateByIds(@Param("ids") List<String> ids);

    /**
     * 处理{@code increment}令牌版本，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 处理后的{@code increment}令牌版本结果，供调用方继续处理
     */
    @Update("""
            UPDATE sys_user
            SET token_version = token_version + 1,
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND deleted = 0
            """)
    int incrementTokenVersion(@Param("id") String id);
    
    /**
     * 检查用户名是否存在
     *
     * @param username  用户名
     * @param excludeId 排除的ID（更新时传入自身ID，新增传空串或 null）
     * @return 存在返回 true，否则 false
     */
    default boolean existsUsername(String username, String excludeId) {
        return selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null && !excludeId.isEmpty(), SysUser::getId, excludeId)) > 0;
    }

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
            "<bind name=\"_contains_keyword\" value=\"keyword == null ? null : &quot;%&quot; + keyword + &quot;%&quot;\"/>",
            "SELECT u.* FROM sys_user u",
            "INNER JOIN sys_user_role ur ON ur.user_id = u.id",
            "WHERE u.deleted = 0 AND ur.role_id = #{roleId}",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (u.username LIKE #{_contains_keyword,jdbcType=VARCHAR}",
            "OR u.nickname LIKE #{_contains_keyword,jdbcType=VARCHAR}",
            "OR u.email LIKE #{_contains_keyword,jdbcType=VARCHAR}",
            "OR u.phone LIKE #{_contains_keyword,jdbcType=VARCHAR})",
            "</if>",
            "ORDER BY u.create_time DESC",
            "</script>"
    })
    Page<SysUser> selectPageByRoleId(
            Page<SysUser> page,
            @Param("roleId") String roleId,
            @Param("keyword") String keyword);

    /**
     * 查询用户分页；查询结果供调用方展示或继续处理。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param keyword 关键字，供本方法查询用户分页时使用
     * @param status 状态标识，决定后续用户分页采用的处理分支
     * @param orgId 组织ID，后续用于查询用户分页时定位或关联目标
     * @param deptId 部门ID，后续用于查询用户分页时定位或关联目标
     * @param roleId 角色ID，后续用于查询用户分页时定位或关联目标
     * @param positionCode 位置编码，后续用于查询用户分页时定位或关联目标
     * @param asOf {@code as}，供本方法查询用户分页时使用
     * @param assignmentUnitIds 分配单元ID 集合，供本方法查询用户分页时使用
     * @return 查询后的用户分页结果，供调用方继续处理
     */
    @Select({
            "<script>",
            "<bind name=\"_contains_keyword\" value=\"keyword == null ? null : &quot;%&quot; + keyword + &quot;%&quot;\"/>",
            "SELECT DISTINCT u.* FROM sys_user u",
            "<if test='roleId != null and roleId != \"\"'>",
            "INNER JOIN sys_user_role ur ON ur.user_id = u.id",
            "</if>",
            "WHERE u.deleted = 0",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (u.username LIKE #{_contains_keyword,jdbcType=VARCHAR}",
            "OR u.nickname LIKE #{_contains_keyword,jdbcType=VARCHAR}",
            "OR u.email LIKE #{_contains_keyword,jdbcType=VARCHAR}",
            "OR u.phone LIKE #{_contains_keyword,jdbcType=VARCHAR})",
            "</if>",
            "<if test='status != null and status != \"\"'>AND u.status = #{status}</if>",
            "<if test='orgId != null and orgId != \"\"'>AND u.org_id = #{orgId}</if>",
            "<if test='deptId != null and deptId != \"\"'>AND u.dept_id = #{deptId}</if>",
            "<if test='roleId != null and roleId != \"\"'>AND ur.role_id = #{roleId}</if>",
            "<if test='positionCode != null and positionCode != \"\"'>",
            "AND EXISTS (SELECT 1 FROM sys_position_assignment pa",
            "INNER JOIN sys_position p ON p.id = pa.position_id",
            "WHERE pa.user_id = u.id AND p.position_code = #{positionCode}",
            "AND p.status = 'ENABLED' AND p.deleted = 0",
            "AND pa.revoked_at IS NULL AND pa.effective_from &lt;= #{asOf}",
            "AND (pa.effective_to IS NULL OR pa.effective_to &gt; #{asOf})",
            "<choose>",
            "<when test='assignmentUnitIds == null'></when>",
            "<when test='assignmentUnitIds.size() &gt; 0'>",
            "AND pa.organization_unit_id IN",
            "<foreach collection='assignmentUnitIds' item='unitId' open='(' separator=',' close=')'>#{unitId}</foreach>",
            "</when>",
            "<otherwise>AND 1 = 0</otherwise>",
            "</choose>",
            ")",
            "</if>",
            "ORDER BY u.create_time DESC",
            "</script>"
    })
    Page<SysUser> selectUserPage(
            Page<SysUser> page,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("orgId") String orgId,
            @Param("deptId") String deptId,
            @Param("roleId") String roleId,
            @Param("positionCode") String positionCode,
            @Param("asOf") LocalDateTime asOf,
            @Param("assignmentUnitIds") List<String> assignmentUnitIds);
    
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

    /**
     * 统计关联到组织或部门的未删除用户；同一用户两个字段均匹配时只计一次。
     *
     * @param orgId 组织ID，后续用于统计组织时定位或关联目标
     * @return 符合条件的组织数量
     */
    default int countByOrganization(String orgId) {
        return selectCount(Wrappers.<SysUser>lambdaQuery()
                .and(organization -> organization.eq(SysUser::getOrgId, orgId)
                        .or().eq(SysUser::getDeptId, orgId))).intValue();
    }
}
