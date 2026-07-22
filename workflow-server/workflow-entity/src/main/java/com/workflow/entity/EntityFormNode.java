package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("entity_form_node")
public class EntityFormNode {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String formId;
    private String parentId;
    private String nodeKey;
    private String nodeType;
    private String bindingType;
    private String bindingRef;
    private String componentName;
    private Integer componentVersion;
    private Integer snapshotVersion;
    private String propsDocument;
    private String rulesDocument;
    private String dataSourceBindingsDocument;
    private String legacyPropsDocument;
    private Long orderKey;
    private Integer revision;
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
