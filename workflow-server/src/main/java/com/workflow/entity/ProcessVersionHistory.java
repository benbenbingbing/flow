package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程版本历史记录
 * 每次发布流程时保存一个版本记录
 */
@Data
@TableName("process_version_history")
public class ProcessVersionHistory {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 流程定义ID（关联process_definition_config表）
     */
    @TableField("process_config_id")
    private String processConfigId;

    /**
     * 流程标识
     */
    @TableField("process_key")
    private String processKey;

    /**
     * 流程名称
     */
    @TableField("process_name")
    private String processName;

    /**
     * 版本号
     */
    @TableField("version")
    private Integer version;

    /**
     * 版本描述/发布说明
     */
    @TableField("version_description")
    private String versionDescription;

    /**
     * BPMN XML内容
     */
    @TableField("bpmn_xml")
    private String bpmnXml;

    /**
     * 节点表单绑定快照
     */
    @TableField("node_forms_snapshot")
    private String nodeFormsSnapshot;

    /**
     * 发布时间
     */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /**
     * 发布人ID
     */
    @TableField("published_by")
    private String publishedBy;

    /**
     * Flowable部署ID
     */
    @TableField("deployment_id")
    private String deploymentId;

    /**
     * 状态：ACTIVE-有效，ARCHIVED-已归档
     */
    @TableField("status")
    private String status;

    /**
     * 是否删除 0-未删除 1-已删除
     */
    @TableField("deleted")
    private Integer deleted;

    public enum Status {
        ACTIVE,   // 有效版本
        ARCHIVED  // 已归档
    }
}
