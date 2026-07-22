package com.workflow.vo;

import lombok.Data;

import java.util.Date;
import java.util.Map;

/**
 * 任务VO
 */
@Data
public class TaskVO {
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 任务名称
     */
    private String taskName;

    private String nodeType;
    
    /**
     * 流程实例ID
     */
    private String processInstanceId;
    
    /**
     * 流程定义ID
     */
    private String processDefinitionId;
    
    /**
     * 流程名称
     */
    private String processName;
    
    /**
     * 发起人
     */
    private String startUserName;
    
    /**
     * 任务创建时间
     */
    private Date createTime;
    
    /**
     * 任务完成时间（已办）
     */
    private Date endTime;
    
    /**
     * 处理时长（毫秒）
     */
    private Long duration;
    
    /**
     * 处理结果（已办）
     */
    private String result;
    
    /**
     * 审批意见
     */
    private String comment;
    
    /**
     * 优先级
     */
    private Integer priority;
    
    /**
     * 任务处理人
     */
    private String assignee;
    
    /**
     * 任务处理人姓名
     */
    private String assigneeName;

    /**
     * 任务处理人类型：user/group/role。
     */
    private String assigneeType;

    /**
     * 当前待办是否需要候选用户先认领。
     */
    private Boolean claimRequired;

    
    /**
     * 业务Key
     */
    private String businessKey;
    
    /**
     * 数据标题（实体数据的name字段）
     */
    private String dataName;
    
    /**
     * 数据编码（实体数据的code字段）
     */
    private String code;
    
    /**
     * 数据名称（实体数据的name字段，用于显示标题）
     */
    private String name;
    
    /**
     * 当前任务名称（实体数据的current_task_name字段）
     */
    private String currentTaskName;
    
    /**
     * 流程状态
     */
    private String processStatus;
    
    // ========== 表单和实体数据扩展字段 ==========
    
    /**
     * 实体编码
     */
    private String entityCode;
    
    /**
     * 实体数据ID
     */
    private String entityDataId;
    
    /**
     * 表单标识
     */
    private String formKey;
    
    /**
     * 实体表单ID
     */
    private String entityFormId;
    
    /**
     * 表单数据（JSON）
     */
    private String formData;
    
    /**
     * 表单是否只读
     */
    private Boolean formReadonly;
    
    /**
     * 表单字段列表（JSON）
     */
    private String formFields;
    
    /**
     * 表单配置对象（用于前端展示）
     */
    private Map<String, Object> formConfig;
    
    /**
     * 实体数据对象（用于前端展示）
     */
    private Map<String, Object> entityData;
}
