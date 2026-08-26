package com.workflow.entity.ui.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** UI HOTFIX 发布审计、观察与回滚持久化记录；复核字段仅供历史兼容。 */
@Data
@TableName("ui_config_hotfix_request")
public class UiConfigHotfixRequest {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String configType;
    private String configId;
    private String draftHash;
    private String activeReleaseId;
    private String targetHash;
    private String impactTokenHash;
    private String riskLevel;
    private String reason;
    private String ticketRef;
    private String impactDocument;
    private String applicantId;
    private String applicantName;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private Integer reviewRequired;
    private String status;
    private String reviewerId;
    private String reviewerName;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    private String releaseId;
    private LocalDateTime publishedAt;
    private LocalDateTime observationStart;
    private LocalDateTime observationEnd;
    private String observationStatus;
    private String rolledBackBy;
    private LocalDateTime rolledBackAt;
    private String rollbackReason;
    private String cancelledBy;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    @TableField("create_time")
    private LocalDateTime createdAt;
    @TableField("update_time")
    private LocalDateTime updatedAt;
}
