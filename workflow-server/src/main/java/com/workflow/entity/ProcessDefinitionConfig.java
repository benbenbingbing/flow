package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程定义配置实体类
 * 
 * @description 存储流程定义的基本信息，包括流程标识、名称、BPMN XML等
 *              对应数据库表：process_definition_config
 * @author Workflow Team
 * @version 1.0.0
 */
@Data
@TableName("process_definition_config")
public class ProcessDefinitionConfig {

    /**
     * 主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private String id;

    /**
     * 流程标识（唯一），用于流程引擎识别
     * 例如：leave_process, purchase_request 等
     */
    @TableField("process_key")
    private String processKey;

    /**
     * 流程显示名称
     * 例如：请假流程、采购申请流程
     */
    @TableField("process_name")
    private String processName;

    /**
     * 流程描述说明
     */
    @TableField("description")
    private String description;

    /**
     * 流程分类，用于分组管理
     * 例如：人事流程、财务流程、行政流程
     */
    @TableField("category")
    private String category;

    /**
     * 版本号，每次发布自动递增
     */
    @TableField("version")
    private Integer version;

    /**
     * 流程状态
     * DRAFT: 草稿，可编辑
     * PUBLISHED: 已发布，正在使用
     * DISABLED: 已禁用
     */
    @TableField("status")
    private ProcessStatus status;

    /**
     * BPMN 2.0 XML 内容
     * 存储流程图的完整XML定义
     */
    @TableField("bpmn_xml")
    private String bpmnXml;

    /**
     * 创建时间，自动填充
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间，自动填充
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 创建人ID
     */
    @TableField("created_by")
    private String createdBy;
    
    /**
     * 关联的节点配置列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<NodeConfig> nodes;

    /**
     * 是否删除 0-未删除 1-已删除
     */
    @TableField("deleted")
    private Integer deleted;

    /**
     * 流程状态枚举
     */
    public enum ProcessStatus {
        /** 草稿状态，可编辑修改 */
        DRAFT,
        /** 已发布，流程可用 */
        PUBLISHED,
        /** 已禁用，流程不可用 */
        DISABLED
    }
}
