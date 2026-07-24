package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户角色关联实体
 * <p>
 * 对应 sys_user_role 表，维护用户与角色的多对多关联关系。
 * </p>
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {
    
    /** 主键ID（雪花算法分配） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 角色ID
     */
    private String roleId;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
