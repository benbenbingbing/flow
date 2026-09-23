package com.workflow.admin.identity.group.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysUserGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户组关联 Mapper
 */
@Mapper
public interface SysUserGroupMapper extends BaseMapper<SysUserGroup> {
    
    /**
     * 查询用户当前有效的组ID列表。
     *
     * <p>该方法用于运行时权限匹配，禁用或已删除的用户组不得继续扩大
     * 用户的数据权限范围。管理端成员回显应使用独立的成员查询，不应复用此方法。</p>
     *
     * @param userId 用户ID
     * @return 启用且未删除的组ID列表
     */
    @Select("SELECT ug.group_id FROM sys_user_group ug "
            + "INNER JOIN sys_group g ON g.id = ug.group_id "
            + "WHERE ug.user_id = #{userId} AND g.deleted = 0 AND g.status = '0'")
    List<String> selectGroupIdsByUserId(@Param("userId") String userId);
    
    /**
     * 根据组ID查询用户ID列表
     *
     * @param groupId 组ID
     * @return 用户ID列表
     */
    default List<String> selectUserIdsByGroupId(String groupId) {
        return selectObjs(Wrappers.<SysUserGroup>lambdaQuery()
                .select(SysUserGroup::getUserId).eq(SysUserGroup::getGroupId, groupId));
    }
    
    /**
     * 删除用户的所有组关联
     *
     * @param userId 用户ID
     */
    default void deleteByUserId(String userId) {
        // 关联表没有逻辑删除字段，BaseMapper 保留物理删除关联关系的行为。
        delete(Wrappers.<SysUserGroup>lambdaQuery().eq(SysUserGroup::getUserId, userId));
    }
    
    /**
     * 删除组的所有用户关联
     *
     * @param groupId 组ID
     */
    default void deleteByGroupId(String groupId) {
        // 关联表没有逻辑删除字段，BaseMapper 保留物理删除关联关系的行为。
        delete(Wrappers.<SysUserGroup>lambdaQuery().eq(SysUserGroup::getGroupId, groupId));
    }
}
