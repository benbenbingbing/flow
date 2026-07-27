package com.workflow.entity.ui.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UI 配置发布、风险覆盖和热修复回滚审计。
 */
@Data
@TableName("ui_config_release_audit")
public class UiConfigReleaseAudit {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String configType;
    private String configId;
    private String releaseId;
    private String operation;
    private String riskLevel;
    private String actorId;
    private String actorName;
    private String reason;
    private String traceId;
    private String detailDocument;
    private LocalDateTime createTime;
}
