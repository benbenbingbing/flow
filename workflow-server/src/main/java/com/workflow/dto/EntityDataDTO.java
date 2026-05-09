package com.workflow.dto;

import com.workflow.entity.EntityData;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 实体数据DTO
 */
@Data
public class EntityDataDTO {
    private String id;
    private String entityCode;
    private String entityName;
    private String dataNo;
    private String title;
    private String name;                    // 数据名称（系统标准字段）
    private String code;                    // 数据编码（系统标准字段）
    private String status;                  // 状态（与流程节点同步）
    private String processInstanceId;       // 流程实例ID
    private LocalDateTime processStartTime; // 流程开始时间
    private LocalDateTime processEndTime;   // 流程结束时间
    private String currentTaskId;           // 当前任务ID
    private String currentTaskName;         // 当前任务名称
    private String currentTaskAssignee;     // 当前任务审批人
    private Map<String, Object> data;       // 自定义字段数据
    private String submitterId;             // 提交人ID
    private String submitterName;           // 提交人姓名
    private LocalDateTime submitTime;       // 提交时间
    private LocalDateTime createdAt;        // 创建时间
    private LocalDateTime updatedAt;        // 更新时间
    private String createdBy;               // 创建人
    private String updatedBy;               // 最后更新人
    
    /**
     * 是否同时发起流程
     */
    private Boolean startProcess;
    
    /**
     * 流程变量（用于传递额外参数，如会签人员列表等）
     */
    private Map<String, Object> processVariables;

    /**
     * 扩展数据（用于列表自定义字段数据补充，与 data 隔离避免冲突）
     */
    private Map<String, Object> extData;
}
