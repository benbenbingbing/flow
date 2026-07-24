package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体列表场景实体，对应 entity_list_scene 表。
 * 记录列表允许出现的运行场景（如 PC、移动端、弹窗选择等），用于场景过滤。
 */
@Data
@TableName("entity_list_scene")
public class EntityListScene {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 所属列表配置ID */
    private String listConfigId;
    /** 场景编码（如 pc/mobile/picker 等） */
    private String sceneCode;
    /** 排序号 */
    private Integer sortOrder;
    /** 草稿元数据修订号 */
    private Integer revision;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;
}
