package com.workflow.entity.form.api.request;

import lombok.Data;

/**
 * 表单运行时按钮解析请求。
 */
@Data
public class FormActionResolveRequest {

    private String formId;
    private String configHash;
    private java.util.Map<String, Object> runtimeContext;
    private String entityCode;
    private String listKey;
    private String mode;
    private String recordId;
    private String taskId;
    /** 顶层任务/记录坐标与嵌套来源一起交由后端核实，不从客户端接收授权结论。 */
    public java.util.Map<String, Object> getRuntimeContext() {
        var result = new java.util.LinkedHashMap<String, Object>(runtimeContext == null ? java.util.Map.of() : runtimeContext);
        if (taskId != null && !taskId.isBlank()) result.put("taskId", taskId);
        if (recordId != null && !recordId.isBlank()) {
            if (result.containsKey("rootFormId") && result.get("componentPath") instanceof java.util.List<?> path && !path.isEmpty())
                result.put("childRecordId", recordId);
            else result.put("recordId", recordId);
        }
        if (entityCode != null && !entityCode.isBlank()) result.put("entityCode", entityCode);
        return result;
    }
}
