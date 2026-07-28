package com.workflow.process.instance.infrastructure.persistence.record;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Durable serialization point between an entity record and its process instance.
 */
@Data
public class EntityProcessLink {

    private String id;
    private String entityCode;
    private String entityRecordId;
    private Integer generation;
    private String processDefinitionKey;
    private String processInstanceId;
    private String state;
    private String requestId;
    private String entityStatus;
    private LocalDateTime endedAt;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
