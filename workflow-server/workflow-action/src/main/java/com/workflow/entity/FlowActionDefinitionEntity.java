package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_action_definition_entity")
public class FlowActionDefinitionEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String actionDefinitionId;
    private String entityCode;

    @TableField("create_time")
    private LocalDateTime createdAt;
}
