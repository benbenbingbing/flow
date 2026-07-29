package com.workflow.openapi.webhook.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("webhook_endpoint")
public class WebhookEndpointRecord {

    @TableId
    private String id;
    private String applicationId;
    private String endpointName;
    private String endpointUrl;
    private String endpointHash;
    private String status;
    private String secretCiphertext;
    private Long secretVersion;
    private String secretHint;
    private String previousSecretCiphertext;
    private Long previousSecretVersion;
    private LocalDateTime previousSecretValidUntil;
    private Long version;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
