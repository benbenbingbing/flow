package com.workflow.entity.ui.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表单或列表设计器中的“关联内容”草稿记录。
 *
 * <p>目标内容、关联方式、允许操作和特殊处理统一保存在经过严格校验的
 * {@code config_document} 中；独立记录使关联内容可以稳定排序、乐观并发更新，
 * 并能作为宿主发布快照的一部分参与差异比较和撤销。</p>
 */
@Data
@TableName("ui_view_composition")
public class UiViewComposition {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** FORM/LIST。 */
    private String ownerType;
    /** 表单或列表配置主键。 */
    private String ownerId;
    /** 宿主范围内稳定且唯一的关联内容编码。 */
    private String compositionKey;
    /** OWNER/FORM_NODE/PAGE_SECTION/ROW_EXPAND/TOOLBAR_ACTION/ROW_ACTION。 */
    private String anchorType;
    /** 非 OWNER 挂载点的稳定标识。 */
    private String anchorKey;
    /** 经白名单校验的关联内容 JSON。 */
    private String configDocument;
    /** 同一挂载点内的稳定排序键。 */
    private Long orderKey;
    /** 乐观锁修订号。 */
    private Integer revision;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
