package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UI 热修复作用于某个流程发布版本后的完整有效快照。
 */
@Data
@TableName("ui_config_hotfix_target")
public class UiConfigHotfixTarget {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String hotfixReleaseId;
    private String configType;
    private String configId;
    private String processVersionHistoryId;
    private String pinnedReleaseId;
    private Integer pinnedReleaseVersion;
    private String previousTargetId;
    private String effectiveSnapshotDocument;
    private String effectiveContentHash;
    private String status;
    @TableField(exist = false)
    private Integer activeSlot;
    private String activatedBy;
    private LocalDateTime activatedAt;
    private String rolledBackBy;
    private LocalDateTime rolledBackAt;
}
