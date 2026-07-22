package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ui_config_release")
public class UiConfigRelease {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String configType;
    private String configId;
    private Integer version;
    private String snapshotDocument;
    private String contentHash;
    private String status;
    private String description;
    private String publishedBy;

    @TableField("published_at")
    private LocalDateTime publishedAt;
}
