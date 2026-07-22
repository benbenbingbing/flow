package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ui_extension_definition")
public class UiExtensionDefinition {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String extensionType;
    private String extensionKey;
    private String displayName;
    private Integer version;
    private Integer snapshotVersion;
    private String supportedModesDocument;
    private String supportedNodeTypesDocument;
    private String supportedBindingsDocument;
    private String configSchemaDocument;
    private String capabilitiesDocument;
    private String status;
    private Integer revision;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
