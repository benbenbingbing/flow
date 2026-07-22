package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ui_data_source_definition")
public class UiDataSourceDefinition {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String sourceCode;
    private String sourceName;
    private String sourceType;
    private String providerCode;
    private String scopeType;
    private String scopeId;
    private String configDocument;
    private String inputSchemaDocument;
    private String outputSchemaDocument;
    private String executionPolicyDocument;
    private Integer revision;
    private Boolean enabled;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
