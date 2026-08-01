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
    private String label;
    private String icon;
    private String buttonType;
    private Integer sort;
    private String placement;
    private String slotKey;
    private boolean visible;
    private boolean enabled;
    private String reason;
    private Map<String, Object> confirm;
    private boolean validateBeforeExecute;
}
