package com.workflow.admin.identity.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户角色关联 Mapper
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
    
    /**
     * 根据用户ID查询角色ID列表
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    default List<String> selectRoleIdsByUserId(String userId) {
        return selectObjs(Wrappers.<SysUserRole>lambdaQuery()
                .select(SysUserRole::getRoleId).eq(SysUserRole::getUserId, userId));
    }
    
    /**
     * 根据角色ID查询用户ID列表
     *
     * @param roleId 角色ID
     * @return 用户ID列表
     */
    default List<String> selectUserIdsByRoleId(String roleId) {
        return selectObjs(Wrappers.<SysUserRole>lambdaQuery()
                .select(SysUserRole::getUserId).eq(SysUserRole::getRoleId, roleId));
    }
    
    /**
     * 删除用户的所有角色
     *
     * @param userId 用户ID
     */
    default void deleteByUserId(String userId) {
        // 关联表没有逻辑删除字段，BaseMapper 保留物理删除关联关系的行为。
        delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
    }

    /**
     * 统计角色关联人数，包含禁用用户的既有关系，与角色管理页原口径保持一致。
     *
     * @param roleId 角色ID，后续用于统计用户集合角色ID时定位或关联目标
     * @return 符合条件的用户集合角色ID数量
     */
    default long countUsersByRoleId(String roleId) {
        return selectCount(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, roleId));
    }
}
