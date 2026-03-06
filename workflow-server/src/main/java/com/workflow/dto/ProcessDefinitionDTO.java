package com.workflow.dto;

import com.workflow.entity.ProcessDefinitionConfig;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程定义数据传输对象
 * 
 * @description 用于前后端传输流程定义数据的DTO
 *              包含流程基本信息和关联的节点配置列表
 * @author Workflow Team
 * @version 1.0.0
 */
@Data
public class ProcessDefinitionDTO {

    /**
     * 流程ID
     */
    private String id;

    /**
     * 流程标识（唯一）
     */
    private String processKey;

    /**
     * 流程名称
     */
    private String processName;

    /**
     * 流程描述
     */
    private String description;

    /**
     * 流程分类
     */
    private String category;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 流程状态
     * DRAFT: 草稿
     * PUBLISHED: 已发布
     * DISABLED: 已禁用
     */
    private ProcessDefinitionConfig.ProcessStatus status;

    /**
     * BPMN XML 内容
     */
    private String bpmnXml;

    /**
     * 节点配置列表
     */
    private List<NodeConfigDTO> nodes;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 创建人
     */
    private String createdBy;
}
