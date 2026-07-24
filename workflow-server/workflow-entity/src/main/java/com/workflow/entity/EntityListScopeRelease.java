package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运行时使用的不可变数据范围发布快照。
 */
@Data
@TableName("entity_list_scope_release")
public class EntityListScopeRelease {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 实体编码 */
    private String entityCode;

    /** 发布版本号 */
    private Integer version;

    /** 发布快照内容（JSON，保存当时的数据范围方案） */
    private String snapshotJson;

    /** 快照内容哈希（用于变更比对） */
    private String contentHash;

    /** 状态（如 DRAFT/PUBLISHED 等） */
    private String status;

    /** 版本描述 */
    private String description;

    /** 发布人ID */
    private String publishedBy;

    /** 发布时间 */
    private LocalDateTime publishedAt;
}
