package com.workflow.admin.authorization.role.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
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
     * @param excludeId 排除的ID（更新时传入自身ID，新增传空串或 null）
     * @return 存在返回 true，否则 false
     */
    default boolean existsRoleCode(String roleCode, String excludeId) {
        return selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, roleCode)
                .ne(excludeId != null && !excludeId.isEmpty(), SysRole::getId, excludeId)) > 0;
    }
    

    
    /**
     * 查询用户的角色列表
     *
     * @param userId 用户ID
     * @return 用户的角色列表
     */
    @Select("SELECT r.* FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = '0' AND r.deleted = 0")
    List<SysRole> selectRolesByUserId(@Param("userId") String userId);

    /**
     * 查询管理员角色集合（super_admin、admin）
     *
     * @return 管理员角色列表
     */
    default List<SysRole> selectAdministratorRoles() {
        return selectList(Wrappers.<SysRole>lambdaQuery()
                .in(SysRole::getRoleCode, "super_admin", "admin")
                .eq(SysRole::getStatus, "0"));
    }


}
