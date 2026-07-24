package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户组关联实体
 * <p>
 * 对应 sys_user_group 表，维护用户与用户组的多对多关联关系。
 * </p>
 */
@Data
@TableName("sys_user_group")
public class SysUserGroup {
    
    /** 主键ID（雪花算法分配） */
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
