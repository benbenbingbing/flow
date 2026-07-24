package com.workflow.dto;

import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * 实体列表按钮（操作项）保存请求。
 * 描述一个列表行/工具栏按钮的完整配置，支持整包或局部更新。
 */
@Data
public class EntityListActionSaveRequest {

    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
    /** 按钮位置：TOOLBAR / ROW */
    private String position;
    /** 按钮唯一标识 */
    private String buttonKey;
    /** 按钮类型（如内置动作、自定义动作等） */
    private String buttonType;
    /** 按钮显示文案 */
    private String buttonLabel;
    /** 图标标识 */
    private String icon;
    /** 样式类型（primary/default/danger 等） */
    private String styleType;
    /** 是否链接模式 */
    private Boolean linkMode;
    /** 自定义模式标识 */
    private String customMode;
    /** 处理器编码（服务端动作处理入口） */
    private String handlerCode;
    /** 按钮对应的权限码 */
    private String permissionCode;
    /** 是否启用 */
    private Boolean enabled;
    /** 不可用时的行为：HIDE / DISABLE */
    private String unavailableBehavior;
    /** 排序序号 */
    private Integer sortOrder;
    /** 动作参数（透传给处理器） */
    private Map<String, Object> actionParams;
    /** 可见性/可用性规则配置 */
    private Map<String, Object> availabilityRule;
    /** 排序键（数值型，用于稳定排序） */
    private Long orderKey;
    /** 来源模板 ID */
    private String templateId;
    /** 来源模板版本 */
    private Integer templateVersion;
    /** 模板本地覆盖配置（JSON 文档） */
    private Object localOverridesDocument;
    /** 需要清空的字段集合（局部更新时置空指定字段） */
    private Set<String> clearFields;
}
