package com.workflow.entity.ui.application;

/**
 * 可用性查询和运行校验共用的标准 UI 绑定位置。
 */
public record UiBindingPoint(
        /** 绑定所有者类型：FORM、LIST 或 ENTITY。 */
        String ownerType,
        /** 绑定所有者 ID。 */
        String ownerId,
        /** 精确目标类型。 */
        String targetType,
        /** 精确目标稳定编码。 */
        String targetKey,
        /** 绑定位置或事件编码。 */
        String bindingCode,
        /** 可调用接口扩展 ID。 */
        String extensionId,
        /** Provider 内部路由编码，不属于设计器绑定身份。 */
        String providerOperationCode) {
}
