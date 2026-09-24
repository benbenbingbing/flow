package com.workflow.admin.identity.group.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户组管理 Mapper
 */
@Mapper
public interface SysGroupMapper extends BaseMapper<SysGroup> {

    /**
     * 分批读取候选组展示信息；调用方须传入非空且最多 200 个编码。
     * 保留原始查询编码，使大小写、重音等比较沿用数据库规则，避免内存按编码匹配丢失结果。
     */
    @Select("""
            <script>
            <foreach collection="codes" item="code" separator=" UNION ALL ">
              SELECT CONCAT(#{code,jdbcType=VARCHAR}, '') AS lookup_code, id, group_name
              FROM sys_group WHERE deleted = 0 AND group_code = #{code,jdbcType=VARCHAR}
            </foreach>
            </script>
            """)
    List<GroupDisplayRow> selectDisplayGroupsByCodes(@Param("codes") List<String> codes);

    /** 原始候选编码用于回填展示；id 用于下一步批量读取成员，空组回退到 groupName。 */
    @lombok.Data
    class GroupDisplayRow {
        private String lookupCode;
        private String id;
        private String groupName;
    }
    
    /**
     * 检查组编码是否存在
     *
     * @param groupCode  组编码
     * @param excludeId 排除的ID（更新时传入自身ID，新增传空串或 null）
     * @return 存在返回 true，否则 false；逻辑删除记录也计入，保持与数据库唯一约束一致
     */
    @Select("<script>SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END FROM sys_group WHERE group_code = #{groupCode}<if test=\"excludeId != null and excludeId != ''\"> AND id != #{excludeId}</if></script>")
    boolean existsGroupCode(@Param("groupCode") String groupCode, @Param("excludeId") String excludeId);
    
    /**
     * 根据组编码查询组信息
     *
     * @param groupCode 组编码
     * @return 用户组对象，不存在返回 null
     */
    default SysGroup selectByGroupCode(String groupCode) {
        // 保留原查询仅取一行的语义，由分页插件生成目标数据库的限制语法。
        return selectPage(new Page<SysGroup>(1, 1, false), Wrappers.<SysGroup>lambdaQuery()
                .eq(SysGroup::getGroupCode, groupCode)).getRecords().stream().findFirst().orElse(null);
    }
    
    /**
     * 查询组下的用户列表
     *
     * @param groupId 组ID
     * @return 组内启用状态的用户列表
     */
    @Select("SELECT u.* FROM sys_user u " +
            "INNER JOIN sys_user_group ug ON u.id = ug.user_id " +
            "WHERE ug.group_id = #{groupId} AND u.deleted = 0 AND u.status = '0'")
    List<SysUser> selectGroupUsers(@Param("groupId") String groupId);

    /**
     * 查询组内全部未删除用户，供成员管理回显使用。
     *
     * <p>禁用用户仍保留组成员关系，避免管理员保存其他成员时误删其关系；
     * 流程运行时应继续使用 {@link #selectGroupUsers(String)}，只解析启用用户。</p>
     *
     * @param groupId 组ID
     * @return 组内未删除用户列表（包含禁用用户）
     */
    @Select("SELECT u.* FROM sys_user u " +
            "INNER JOIN sys_user_group ug ON u.id = ug.user_id " +
            "WHERE ug.group_id = #{groupId} AND u.deleted = 0")
    List<SysUser> selectGroupMembers(@Param("groupId") String groupId);

    /**
     * 批量查询用户组成员 ID，用于列表页成员数和分配成员回显。
     *
     * @param groupIds 组 ID 列表
     * @return 组与用户 ID 关系
     */
    @Select({
            "<script>",
            "SELECT ug.group_id AS groupId, ug.user_id AS userId",
            "FROM sys_user_group ug",
            "INNER JOIN sys_user u ON u.id = ug.user_id",
            "WHERE u.deleted = 0",
            "AND ug.group_id IN",
            "<foreach collection='groupIds' item='groupId' open='(' separator=',' close=')'>",
            "#{groupId}",
            "</foreach>",
            "</script>"
    })
    List<GroupUserIdRow> selectGroupUserIdsByGroupIds(@Param("groupIds") List<String> groupIds);
    
    /**
     * 查询用户的组列表
     *
     * @param userId 用户ID
     * @return 用户所属的启用状态的组列表
     */
    @Select("SELECT g.* FROM sys_group g " +
            "INNER JOIN sys_user_group ug ON g.id = ug.group_id " +
            "WHERE ug.user_id = #{userId} AND g.deleted = 0 AND g.status = '0'")
    List<SysGroup> selectGroupsByUserId(@Param("userId") String userId);

    /**
     * 用户组成员 ID 查询结果。
     */
    class GroupUserIdRow {
        private String groupId;
        private String userId;

        /**
         * 读取分组ID；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的分组ID文本，供调用方比较或展示
         */
        public String getGroupId() {
            return groupId;
        }

        /**
         * 设置分组ID；后续读取或执行将使用更新后的状态。
         *
         * @param groupId 分组ID，后续用于设置分组ID时定位或关联目标
         */
        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        /**
         * 读取用户ID；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的用户ID文本，供调用方比较或展示
         */
        public String getUserId() {
            return userId;
        }

        /**
         * 设置用户ID；后续读取或执行将使用更新后的状态。
         *
         * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
         */
        public void setUserId(String userId) {
            this.userId = userId;
        }
    }
}
