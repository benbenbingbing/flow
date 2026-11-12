package com.workflow.admin.identity.provisioning.infrastructure.persistence.record;

import lombok.Data;
import java.sql.Timestamp;

/** 最新来源原文及其对应的业务快照；查询绑定时额外携带当前来源信息，避免跨查询混用版本。 */
@Data
public class IdentitySourceSnapshot {
    private String sourceKey;
    private Long sourceVersion;
    private String payloadHash;
    private String rawJson;
    private String rawHash;
    private Integer rawBytes;
    /** 显式状态防止 SERIAL 业务值恢复到旧值时误将旧原文标记为当前原文。 */
    private Integer isCurrent;
    private Timestamp recordedAt;
    private String recordedBy;
    private String sourceCode;
    private String externalId;
    private Long currentSourceVersion;
}
