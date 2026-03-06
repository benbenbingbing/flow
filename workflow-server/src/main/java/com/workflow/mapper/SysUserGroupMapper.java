package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.SysUserGroup;
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
     * 根据用户ID查询组ID列表
     */
    @Select("SELECT group_id FROM sys_user_group WHERE user_id = #{userId}")
    List<String> selectGroupIdsByUserId(@Param("userId") String userId);
    
    /**
     * 根据组ID查询用户ID列表
     */
    @Select("SELECT user_id FROM sys_user_group WHERE group_id = #{groupId}")
    List<String> selectUserIdsByGroupId(@Param("groupId") String groupId);
    
    /**
     * 删除用户的所有组关联
     */
    @Select("DELETE FROM sys_user_group WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") String userId);
    
    /**
     * 删除组的所有用户关联
     */
    @Select("DELETE FROM sys_user_group WHERE group_id = #{groupId}")
    void deleteByGroupId(@Param("groupId") String groupId);
}
