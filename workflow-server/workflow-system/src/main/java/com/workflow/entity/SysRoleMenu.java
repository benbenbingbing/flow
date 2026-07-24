package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色菜单关联实体（权限）
 * <p>
 * 对应 sys_role_menu 表，维护角色与菜单（权限）的多对多关联关系。
 * </p>
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu {
    
    /** 主键ID（雪花算法分配） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 角色ID
     */
    private String roleId;
    
    /**
     * 菜单ID
     */
    private String menuId;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
