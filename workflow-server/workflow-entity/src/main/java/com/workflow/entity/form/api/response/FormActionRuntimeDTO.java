package com.workflow.entity.form.api.response;

import lombok.Data;

import java.util.Map;

/**
 * 当前用户和当前数据上下文下的表单按钮。
 */
@Data
public class FormActionRuntimeDTO {

    private String ownerFormId;
    private String runtimeKey;
    private String key;
    private String type;
    /** 发布配置中的执行方式；缺省 event 保持历史按钮的事件链行为。 */
    private String customMode;
    /** 仅由已鉴权的发布按钮提供目标，客户端不从设计草稿补充执行参数。 */
    private Map<String, Object> execution;
    private String label;
    private String icon;
    private String buttonType;
    /** 自定义按钮外观；内置按钮与缺省配置统一返回 DEFAULT。 */
    private String buttonAppearance;
    private Integer sort;
    private String placement;
    private String slotKey;
    private boolean visible;
    private boolean enabled;
    private String reason;
    private Map<String, Object> confirm;
    private boolean validateBeforeExecute;
}
