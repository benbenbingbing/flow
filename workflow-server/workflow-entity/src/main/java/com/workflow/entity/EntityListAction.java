package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("entity_list_action")
public class EntityListAction {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String listConfigId;
    private String position;
    private String buttonKey;
    private String buttonType;
    private String buttonLabel;
    private String icon;
    private String styleType;
    private Boolean linkMode;
    private String customMode;
    private String handlerCode;
    private String permissionCode;
    private Integer sortOrder;
    private Long orderKey;
    private Integer revision;
    private Boolean enabled;
    private String unavailableBehavior;
    private String actionParamsDocument;
    private String availabilityRuleDocument;
    private String templateId;
    private Integer templateVersion;
    private String localOverridesDocument;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
