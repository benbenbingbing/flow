package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户管理 Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    
    /**
     * 根据用户名查询用户
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0")
    SysUser selectByUsername(@Param("username") String username);
    
    /**
     * 检查用户名是否存在
     */
    @Select("SELECT COUNT(*) > 0 FROM sys_user WHERE username = #{username} AND deleted = 0 AND (#{excludeId} = '' OR id != #{excludeId})")
    boolean existsUsername(@Param("username") String username, @Param("excludeId") String excludeId);
    
    /**
     * 查询用户的角色列表
     */
    @Select("SELECT r.id, r.role_name, r.role_code FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = 0 AND r.status = '0'")
    List<SysUser> selectUserRoles(@Param("userId") String userId);
}
