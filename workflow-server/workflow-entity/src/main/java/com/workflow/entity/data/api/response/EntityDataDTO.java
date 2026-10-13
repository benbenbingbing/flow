package com.workflow.entity.data.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;

/**
 * 实体数据DTO
 */
@Data
public class EntityDataDTO {
    /** 数据 ID */
    private String id;
    /** 实体编码 */
    private String entityCode;
    /** 实体名称 */
    private String entityName;
    private String name;                    // 数据名称（系统标准字段）
    private String code;                    // 业务编号（按实体编码规则生成的系统标准字段）
    private String status;                  // 实体业务状态，由连线或业务操作维护
    /** 当前关联流程的只读生命周期：NOT_STARTED / RUNNING / COMPLETED。 */
    private String processStatus;
    private String processInstanceId;       // 流程实例ID
    private LocalDateTime processStartTime; // 流程开始时间
    private LocalDateTime processEndTime;   // 流程结束时间
    private String currentTaskId;           // 当前任务ID
    private String currentTaskName;         // 当前任务名称
    private String currentTaskAssignee;     // 当前任务审批人
    private Map<String, Object> data;       // 自定义字段数据
    private String submitterId;             // 提交人ID
    private String submitterName;           // 提交人姓名
    private String deptId;                  // 所属部门ID
    private String deptName;                // 所属部门名称
    private LocalDateTime submitTime;       // 提交时间
    // 实体字段编码及 JSON 属性统一使用物理列名，不提供历史 created*/updated* 别名。
    @JsonProperty("create_time")
    private LocalDateTime createTime;       // 创建时间
    @JsonProperty("update_time")
    private LocalDateTime updateTime;       // 更新时间
    @JsonProperty("create_by")
    private String createBy;               // 创建人
    @JsonProperty("update_by")
    private String updateBy;               // 最后更新人
    private Boolean deleted;                // 删除标记（系统只读字段）
    
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

    /**
     * 当前操作来源列表标识，不持久化。
     */
    private String listKey;
    /** 页面配置摘要用于变更冲突检查，不授予权限。 */
    private String listConfigHash;
    private String formConfigHash;
    private Map<String, Object> runtimeContext;

    /**
     * 当前操作来源表单 ID，不持久化。
     */
    private String formId;

    /** 关联来源仅用于定位，每次提交按发布关系重新校验目标范围。 */
    private com.workflow.entity.ui.api.request.UiViewCompositionResolveRequest compositionContext;

    /**
     * 当前用户针对本行数据的按钮能力。
     */
    private Map<String, EntityActionCapabilityDTO> actionCapabilities = new LinkedHashMap<>();
}
