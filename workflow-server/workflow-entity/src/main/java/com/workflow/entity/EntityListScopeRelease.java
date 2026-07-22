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

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String entityCode;

    private Integer version;

    private String snapshotJson;

    private String contentHash;

    private String status;

    private String description;

    private String publishedBy;

    private LocalDateTime publishedAt;
}
