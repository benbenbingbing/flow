package com.workflow.entity.ui.api.request;

import lombok.Data;

import java.util.Map;

/**
 * 通过已验证 UI 绑定执行接口扩展的公开请求。
 */
@Data
public class UiBoundExtensionExecuteRequest {

    /** 绑定所有者类型：FORM、LIST 或 ENTITY。 */
    private String ownerType;
    /** 绑定所有者 ID。 */
    private String ownerId;
    /** 当前页面配置摘要，仅检测配置变更；业务权限仍由运行来源重新核验。 */
    private String configHash;
    private Map<String, Object> runtimeContext = Map.of();
    private String entityCode;
    private String listKey;
    private String recordId;
    /** 绑定位置编码，例如 FIELD_OPTIONS、LIST_COLUMN。 */
    private String bindingCode;
    /** 精确目标类型，例如 FIELD、COLUMN、BUTTON 或 OWNER。 */
    private String targetType;
    /** 精确目标稳定编码，例如字段编码或按钮编码。 */
    private String targetKey;
    /** 可调用接口扩展 ID。 */
    private String extensionId;
    /**
     * 仅用于不可变历史快照的 serviceId + operationCode 解析；新绑定不提交。
     * 解析得到的接口仍须通过发布绑定和权限校验，不能据此任意选择 Provider 方法。
     */
    private String legacyOperationCode;
    /** 客户端业务输入；同名用户/部门字段不构成身份声明，不能覆盖服务端授权上下文。 */
    private Map<String, Object> input;

    /** 顶层记录只是业务定位，合并后由流程与父子关系授权服务重新验证。 */
    public Map<String, Object> getRuntimeContext() {
        Map<String, Object> result = new java.util.LinkedHashMap<>(runtimeContext == null ? Map.of() : runtimeContext);
        if (recordId != null && !recordId.isBlank()) {
            boolean nested = result.containsKey("rootFormId")
                    && result.get("componentPath") instanceof java.util.List<?> path && !path.isEmpty();
            result.put(nested ? "childRecordId" : "recordId", recordId);
        }
        return result;
    }
}
