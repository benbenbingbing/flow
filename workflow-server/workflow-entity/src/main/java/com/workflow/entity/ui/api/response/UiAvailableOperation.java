package com.workflow.entity.ui.api.response;

/**
 * 指定 UI 绑定位置可选择的接口操作。
 */
public record UiAvailableOperation(
        /** 接口服务 ID。 */
        String serviceId,
        /** 接口服务稳定编码。 */
        String serviceCode,
        /** 接口服务名称。 */
        String serviceName,
        /** 接口服务实现类型。 */
        String sourceType,
        /** 接口服务作用域类型。 */
        String scopeType,
        /** 接口服务作用域对象 ID。 */
        String scopeId,
        /** 操作编码。 */
        String operationCode,
        /** 操作名称。 */
        String operationName,
        /** 操作数据影响类型：READ 或 WRITE。 */
        String kind,
        /** 操作所需上下文类型：FORM、LIST 或 ENTITY。 */
        String contextType) {
}
