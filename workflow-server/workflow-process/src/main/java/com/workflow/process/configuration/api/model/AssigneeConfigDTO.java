package com.workflow.process.configuration.api.model;

import com.workflow.process.configuration.infrastructure.persistence.record.AssigneeConfig;
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
    
    /**
     * 读取ID；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的ID文本，供调用方比较或展示
     */
    public String getId() {
        return id;
    }
    
    /**
     * 设置ID；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     */
    public void setId(String id) {
        this.id = id;
    }
    
    /**
     * 读取办理人类型；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的办理人{@code config.assignee}类型结果，供调用方继续处理
     */
    public AssigneeConfig.AssigneeType getAssigneeType() {
        return assigneeType;
    }
    
    /**
     * 设置办理人类型；后续读取或执行将使用更新后的状态。
     *
     * @param assigneeType 办理人类型标识，决定后续办理人类型采用的处理分支
     */
    public void setAssigneeType(AssigneeConfig.AssigneeType assigneeType) {
        this.assigneeType = assigneeType;
    }
    
    /**
     * 读取办理人值；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的办理人值文本，供调用方比较或展示
     */
    public String getAssigneeValue() {
        return assigneeValue;
    }
    
    /**
     * 设置办理人值；后续读取或执行将使用更新后的状态。
     *
     * @param assigneeValue 办理人值，供本方法设置办理人值时使用
     */
    public void setAssigneeValue(String assigneeValue) {
        this.assigneeValue = assigneeValue;
    }
    
    /**
     * 读取办理人名称；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的办理人名称文本，供调用方比较或展示
     */
    public String getAssigneeName() {
        return assigneeName;
    }
    
    /**
     * 设置办理人名称；后续读取或执行将使用更新后的状态。
     *
     * @param assigneeName 办理人名称，后续用于设置办理人名称时匹配或展示
     */
    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
    }
    
    /**
     * 读取优先级；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的办理人配置DTO结果，供调用方继续处理
     */
    public Integer getPriority() {
        return priority;
    }
    
    /**
     * 设置优先级；后续读取或执行将使用更新后的状态。
     *
     * @param priority 优先级，供本方法设置优先级时使用
     */
    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
