package com.workflow.entity.ui.application.model;

/**
 * 可用性查询和运行校验共用的标准 UI 绑定位置。
 *
 * @param ownerType 归属方类型标识，决定后续界面绑定{@code point}采用的处理分支
 * @param ownerId 归属方ID，后续用于处理界面绑定{@code point}时定位或关联目标
 * @param targetType 目标类型标识，决定后续界面绑定{@code point}采用的处理分支
 * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
 * @param bindingCode 绑定编码，后续用于处理界面绑定{@code point}时定位或关联目标
 * @param extensionId 扩展ID，后续用于处理界面绑定{@code point}时定位或关联目标
 * @param providerOperationCode 提供者操作编码，后续用于处理界面绑定{@code point}时定位或关联目标
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
