package com.workflow.entity.ui.api.request;

import lombok.Data;

import java.util.Map;

/**
 * 通过已验证 UI 绑定执行接口操作的公开请求。
 */
@Data
public class UiInterfaceOperationExecuteRequest {

    /** 绑定所有者类型：FORM、LIST 或 ENTITY。 */
    private String ownerType;
    /** 绑定所有者 ID。 */
    private String ownerId;
    /** 绑定位置编码，例如 FIELD_OPTIONS、LIST_COLUMN。 */
    private String bindingCode;
    /** 精确目标类型，例如 FIELD、COLUMN、BUTTON 或 OWNER。 */
    private String targetType;
    /** 精确目标稳定编码，例如字段编码或按钮编码。 */
    private String targetKey;
    /** 接口服务 ID。 */
    private String serviceId;
    /** 接口操作编码。 */
    private String operationCode;
    /** 客户端可提交的业务输入，不包含可信身份元数据。 */
    private Map<String, Object> input;
}
