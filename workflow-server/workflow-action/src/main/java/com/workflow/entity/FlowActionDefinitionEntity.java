package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程动作定义可见实体关系实体。
 *
 * <p>对应 process_action_definition_entity 表，记录动作定义与可见实体编码的多对多关系，
 * 用于实体可见范围（ENTITY）的处理器的实体绑定。</p>
 */
@Data
@TableName("process_action_definition_entity")
public class FlowActionDefinitionEntity {

    /** 主键（UUID） */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 动作定义 ID */
    private String actionDefinitionId;
    /** 可见的实体编码 */
    private String entityCode;

    @TableField("create_time")
    private LocalDateTime createdAt;
}
