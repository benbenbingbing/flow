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
    private EntityData.DataStatus status;
    private String processInstanceId;
    private String currentTaskId;
    private String currentTaskName;
    private Map<String, Object> data;
    private String submitterId;
    private String submitterName;
    private LocalDateTime submitTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * 是否同时发起流程
     */
    private Boolean startProcess;
}
