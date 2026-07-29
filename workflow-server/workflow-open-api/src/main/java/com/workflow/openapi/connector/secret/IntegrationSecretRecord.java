package com.workflow.openapi.connector.secret;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("integration_secret")
class IntegrationSecretRecord {

    @TableId
    private String id;
    private String applicationId;
    private String secretName;
    private Long secretVersion;
    private String status;
    private String keyVersion;
    private String encryptedDataKey;
    private String dataKeyNonce;
    private String secretCiphertext;
    private String secretNonce;
    private String secretHint;
    private String createdBy;
    private String revokedBy;
    private LocalDateTime revokedAt;
    private String destroyedBy;
    private LocalDateTime destroyedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    IntegrationSecretEnvelope envelope() {
        return new IntegrationSecretEnvelope(
                keyVersion,
                encryptedDataKey,
                dataKeyNonce,
                secretCiphertext,
                secretNonce);
    }
}
