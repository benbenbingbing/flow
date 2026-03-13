package com.workflow.dto;

import com.workflow.entity.AssigneeConfig;
import lombok.Data;

/**
 * 审批人配置数据传输对象
 * 
 * @description 用于前后端传输审批人配置数据的DTO
 *              定义谁可以处理当前节点任务
 * @author Workflow Team
 * @version 1.0.0
 */
@Data
public class AssigneeConfigDTO {

    /**
     * 审批人配置ID
     */
    private String id;

    /**
     * 审批人类型
     * USER: 指定用户
     * ROLE: 指定角色
     * DEPT: 指定部门
     * LEADER: 上级领导
     * EXPRESSION: 表达式
     */
    private AssigneeConfig.AssigneeType assigneeType;

    /**
     * 审批人值
     * 根据类型不同存储不同值
     */
    private String assigneeValue;

    /**
     * 审批人显示名称
     */
    private String assigneeName;

    /**
     * 优先级
     */
    private Integer priority;
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public AssigneeConfig.AssigneeType getAssigneeType() {
        return assigneeType;
    }
    
    public void setAssigneeType(AssigneeConfig.AssigneeType assigneeType) {
        this.assigneeType = assigneeType;
    }
    
    public String getAssigneeValue() {
        return assigneeValue;
    }
    
    public void setAssigneeValue(String assigneeValue) {
        this.assigneeValue = assigneeValue;
    }
    
    public String getAssigneeName() {
        return assigneeName;
    }
    
    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
    }
    
    public Integer getPriority() {
        return priority;
    }
    
    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
