package com.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程版本历史DTO
 */
@Data
public class ProcessVersionHistoryDTO {

    private String id;
    
    private String processConfigId;
    
    private String processKey;
    
    private String processName;
    
    private Integer version;
    
    private String versionDescription;
    
    private String bpmnXml;
    
    private LocalDateTime publishedAt;
    
    private String publishedBy;
    
    private String deploymentId;
    
    private String status;
}
