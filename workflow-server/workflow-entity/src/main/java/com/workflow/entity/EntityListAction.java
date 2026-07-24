package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体列表操作按钮实体，对应 entity_list_action 表。
 * 定义列表工具栏与行操作按钮的展示、类型、处理逻辑、权限及可用性规则。
 */
@Data
@TableName("entity_list_action")
public class EntityListAction {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 所属列表配置ID */
    private String listConfigId;
    /** 按钮位置（如 toolbar/row 等） */
    private String position;
    /** 按钮唯一标识 */
    private String buttonKey;
    /** 按钮类型（如 built-in/custom/link 等） */
    private String buttonType;
    /** 按钮显示文案 */
    private String buttonLabel;
    /** 按钮图标 */
    private String icon;
    /** 按钮样式类型（如 primary/default/danger 等） */
    private String styleType;
    /** 是否以链接模式打开（true-跳转链接 false-执行处理） */
    private Boolean linkMode;
    /** 自定义模式（如 open-window/open-modal 等） */
    private String customMode;
    /** 处理器编码（自定义按钮的处理逻辑标识） */
    private String handlerCode;
    /** 按钮所需权限码 */
    private String permissionCode;
    /** 排序号 */
    private Integer sortOrder;
    /** 稀疏排序键 */
    private Long orderKey;
    /** 草稿元数据修订号 */
    private Integer revision;
    /** 是否启用 */
    private Boolean enabled;
    /** 按钮不可用时的行为（如 hidden/disabled 等） */
    private String unavailableBehavior;
    /** 按钮参数配置（JSON） */
    private String actionParamsDocument;
    /** 可用性规则配置（JSON） */
    private String availabilityRuleDocument;
    /** 按钮引用的模板ID */
    private String templateId;
    /** 按钮引用的模板版本号 */
    private Integer templateVersion;
    /** 模板本地覆盖配置（JSON） */
    private String localOverridesDocument;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updatedAt;

    /** 逻辑删除标志（0-未删除 1-已删除） */
    @TableLogic
    private Integer deleted;
}
