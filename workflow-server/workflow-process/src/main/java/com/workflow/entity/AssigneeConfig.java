package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点审批人配置实体类
 * 
 * @description 定义流程节点的审批人/处理人规则
 *              支持多种分配类型：指定用户、角色、部门、上级领导、表达式
 *              对应数据库表：process_node_assignee
 * @author Workflow Team
 * @version 1.0.0
 */
@Data
@TableName("process_node_assignee")
public class AssigneeConfig {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 所属节点配置ID
     */
    @TableField("node_config_id")
    private String nodeConfigId;

    /**
     * 审批人类型
     * USER: 指定用户（如：张三）
     * ROLE: 指定角色（如：部门经理）
     * DEPT: 指定部门（如：财务部）
     * LEADER: 上级领导（动态获取申请人上级）
     * EXPRESSION: 表达式（如：${manager}）
     */
    @TableField("assignee_type")
    private AssigneeType assigneeType;

    /**
     * 审批人值
     * 根据类型存储不同的值：
     * USER类型存储用户ID
     * ROLE类型存储角色编码
     * DEPT类型存储部门ID
     * LEADER类型存储层级（如：1表示直属上级）
     * EXPRESSION类型存储表达式
     */
    @TableField("assignee_value")
    private String assigneeValue;

    /**
     * 审批人显示名称
     * 用于前端展示
     */
    @TableField("assignee_name")
    private String assigneeName;

    /**
     * 优先级，数字越小优先级越高
     * 用于多人审批时的顺序控制
     */
    @TableField("priority")
    private Integer priority;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 审批人类型枚举
     */
    public enum AssigneeType {
        /** 指定用户 */
        USER,
        /** 指定角色 */
        ROLE,
        /** 指定部门 */
        DEPT,
        /** 上级领导（动态计算） */
        LEADER,
        /** 表达式（SpEL） */
        EXPRESSION
    }
}
