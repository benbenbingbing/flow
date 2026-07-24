package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程动作定义实体。
 *
 * <p>对应 process_action_definition 表，即动作处理器目录配置：记录处理器 Bean 的中文展示名、
 * 描述、可见范围（GLOBAL/ENTITY）与启用状态，供前端选用处理器时展示。</p>
 */
@Data
@TableName("process_action_definition")
public class FlowActionDefinition {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 动作编码，通常与处理器 Bean 名称一致 */
    private String actionCode;
    /** 中文展示名 */
    private String displayName;
    /** 动作描述 */
    private String description;
    /** 处理器 Bean 名称 */
    private String handlerName;
    /** 可见范围：GLOBAL、ENTITY */
    private String visibilityScope;
    /** 可见实体编码 JSON（兼容旧数据；新数据走关系表） */
    private String entityCodesJson;
    /** 是否启用 */
    private Boolean enabled;
    /** 创建人 */
    private String createdBy;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    /** 逻辑删除标识：0-未删除，1-已删除 */
    private Integer deleted;
}
