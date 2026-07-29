package com.workflow.entity.ui.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UI 事件绑定链。
 *
 * <p>一个作用域下的一个事件只保存一条记录，具体的 BEFORE/REPLACE/AFTER
 * 步骤统一放在 steps_document 中，避免为每种页面事件建立独立配置表。</p>
 */
@Data
@TableName("ui_event_binding")
public class UiEventBinding {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** ENTITY/FORM/LIST */
    private String ownerType;
    /** 实体、表单或列表的稳定 ID */
    private String ownerId;
    /** OWNER/FIELD/BUTTON */
    private String targetType;
    /** FIELD/BUTTON 的稳定节点或按钮编码，OWNER 时为空 */
    private String targetKey;
    /** LIST_LOAD、DATA_CREATE、ENTITY_SELECTED 等事件编码 */
    private String eventCode;
    /** INHERIT/REPLACE/DISABLE */
    private String inheritanceMode;
    /** 有序事件步骤 JSON 数组 */
    private String stepsDocument;
    /** 乐观锁修订号 */
    private Integer revision;
    private Boolean enabled;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
