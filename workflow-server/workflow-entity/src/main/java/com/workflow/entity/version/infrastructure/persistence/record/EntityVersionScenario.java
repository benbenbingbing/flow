package com.workflow.entity.version.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体版本场景草稿。
 */
@Data
@TableName("entity_version_scenario")
public class EntityVersionScenario {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String configId;
    private String scenarioCode;
    private String scenarioName;
    private String sourceTypesDocument;
    private String operationTypesDocument;
    private String businessIntentsDocument;
    private String conditionDocument;
    private Integer priority;
    private String versionTitleTemplate;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
