package com.workflow.entity.version.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已完成实体变更的持久化幂等回执。
 */
@Data
@TableName("entity_mutation_receipt")
public class EntityMutationReceipt {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String idempotencyKey;
    private String commandHash;
    private String operationId;
    private String entityCode;
    private String recordId;
    private String operationType;
    private String status;
    private String resultDocument;
    private Integer versionNo;
    private String versionScenarioCode;
    private Boolean changed;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
