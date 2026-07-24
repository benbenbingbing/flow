package com.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程版本历史DTO
 */
@Data
public class ProcessVersionHistoryDTO {

    /**
     * 版本历史记录ID
     */
    private String id;

    /**
     * 所属流程配置ID
     */
    private String processConfigId;

    /**
     * 流程标识
     */
    private String processKey;

    /**
     * 流程名称
     */
    private String processName;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 版本说明/描述
     */
    private String versionDescription;

    /**
     * 该版本对应的 BPMN XML 内容
     */
    private String bpmnXml;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 发布人
     */
    private String publishedBy;

    /**
     * Flowable 部署ID
     */
    private String deploymentId;

    /**
     * 版本状态
     */
    private String status;
}
