package com.workflow.entity.version.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务实体记录的不可变完整版本。
 */
@Data
@TableName("entity_record_version")
public class EntityRecordVersion {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String entityCode;
    private String recordId;
    private Integer versionNo;
    private String versionTitle;
    private String scenarioCode;
    private String scenarioName;
    private String operationType;
    private String sourceType;
    private String sourceId;
    private String businessIntentCode;
    private String businessIntentName;
    private String sourceEntityCode;
    private String sourceRecordId;
    private String processDefinitionId;
    private String processInstanceId;
    private String taskId;
    private String operatorId;
    private String operatorName;
    private String businessTraceKey;
    private String idempotencyKey;
    private String entityReleaseId;
    private Integer entityReleaseVersion;
    private String snapshotHash;
    private String snapshotDocument;
    private LocalDateTime createTime;
}
