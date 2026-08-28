package com.workflow.entity.form.uniqueness.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表单字段唯一值的数据库占位记录。
 *
 * <p>占位键包含精确快照身份（热修复使用 target ID），使规则条件变更后的旧占位
 * 不会干扰新版规则；当前可信入口中实际适用的跨表单/跨发布规则先由稳定
 * entity-field sentinel 串行，再由具体 value gate 保护同值窗口。</p>
 */
@Data
@TableName("entity_form_unique_claim")
public class EntityFormUniqueClaim {

    private String constraintKey;
    private String valueHash;
    private String entityCode;
    private String formId;
    private String ruleId;
    private String fieldCode;
    private String normalizedValue;
    private String recordId;
    private String releaseId;
    private Integer releaseVersion;
    private String effectiveReleaseId;
    private String effectiveContentHash;
    private String hotfixTargetId;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    /** 当前发布规则配置的用户提示，不落库，只用于映射原子占位冲突。 */
    @TableField(exist = false)
    private String conflictMessage;
}
