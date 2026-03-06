package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.SysGroup;
import com.workflow.entity.SysUser;
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
     * 检查组编码是否存在
     */
    @Select("SELECT COUNT(*) > 0 FROM sys_group WHERE group_code = #{groupCode} AND deleted = 0 AND (#{excludeId} = '' OR id != #{excludeId})")
    boolean existsGroupCode(@Param("groupCode") String groupCode, @Param("excludeId") String excludeId);
    
    /**
     * 查询组下的用户列表
     */
    @Select("SELECT u.* FROM sys_user u " +
            "INNER JOIN sys_user_group ug ON u.id = ug.user_id " +
            "WHERE ug.group_id = #{groupId} AND u.deleted = 0 AND u.status = '0'")
    List<SysUser> selectGroupUsers(@Param("groupId") String groupId);
    
    /**
     * 查询用户的组列表
     */
    @Select("SELECT g.* FROM sys_group g " +
            "INNER JOIN sys_user_group ug ON g.id = ug.group_id " +
            "WHERE ug.user_id = #{userId} AND g.deleted = 0 AND g.status = '0'")
    List<SysGroup> selectGroupsByUserId(@Param("userId") String userId);
}
