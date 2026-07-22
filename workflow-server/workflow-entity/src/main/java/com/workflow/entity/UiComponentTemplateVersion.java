package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ui_component_template_version")
public class UiComponentTemplateVersion {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String templateId;
    private Integer version;
    private String snapshotDocument;
    private String contentHash;
    private String description;
    private String createdBy;

    @TableField("create_time")
    private LocalDateTime createdAt;
}
