package com.workflow.openapi.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("integration_application")
public class IntegrationApplicationRecord {

    @TableId(type = IdType.INPUT)
    private String id;
    private String clientId;
    private String applicationName;
    private String description;
    private String ownerOrganizationId;
    private String status;
    private Integer rateLimitPerMinute;
    private Integer maxConcurrency;
    private String allowedSourceCidrs;
    private LocalDateTime expiresAt;
    private Long version;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
