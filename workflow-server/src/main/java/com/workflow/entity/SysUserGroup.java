package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户组关联实体
 */
@Data
@TableName("sys_user_group")
public class SysUserGroup {
    
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 组ID
     */
    private String groupId;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
