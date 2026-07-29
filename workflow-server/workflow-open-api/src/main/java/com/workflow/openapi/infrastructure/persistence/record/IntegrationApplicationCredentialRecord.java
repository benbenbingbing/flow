package com.workflow.openapi.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("integration_application_credential")
public class IntegrationApplicationCredentialRecord {

    @TableId(type = IdType.INPUT)
    private String id;
    private String applicationId;
    private String secretHash;
    private String credentialHint;
    private String status;
    private Long credentialVersion;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private String createdBy;
    private String revokedBy;
    private LocalDateTime revokedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String activeApplicationId;
}
