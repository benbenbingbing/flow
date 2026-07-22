package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("entity_list_scene")
public class EntityListScene {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String listConfigId;
    private String sceneCode;
    private Integer sortOrder;
    private Integer revision;

    @TableField("create_time")
    private LocalDateTime createdAt;
}
