package com.workflow.entity.version.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体变更前置操作草稿。
 */
@Data
@TableName("entity_version_step")
public class EntityVersionStep {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String configId;
    private String scenarioId;
    private String phase;
    private String stepType;
    private String stepName;
    private String providerCode;
    private String configDocument;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
