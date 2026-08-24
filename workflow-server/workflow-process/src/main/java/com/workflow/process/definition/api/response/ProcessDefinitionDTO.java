package com.workflow.process.definition.api.response;

import com.workflow.process.configuration.api.model.NodeConfigDTO;

import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
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

    /** 当前流程草稿修订号，由服务端维护 */
    private Long revision;

    /** 更新时客户端读取到的修订号，服务端据此执行 CAS */
    private Long expectedRevision;

    /** 当前流程草稿内容哈希 */
    private String draftHash;

    /** 最近发布对应的草稿修订号 */
    private Long publishedRevision;

    /** 当前草稿基于的已发布版本 */
    private Integer basePublishedVersion;

    /** 是否存在尚未发布的草稿修改 */
    private Boolean hasUnpublishedChanges;

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
