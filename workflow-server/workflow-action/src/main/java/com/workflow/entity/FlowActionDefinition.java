package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_action_definition")
public class FlowActionDefinition {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String actionCode;
    private String displayName;
    private String description;
    private String handlerName;
    private String visibilityScope;
    private String entityCodesJson;
    private Boolean enabled;
    private String createdBy;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    private Integer deleted;
}
