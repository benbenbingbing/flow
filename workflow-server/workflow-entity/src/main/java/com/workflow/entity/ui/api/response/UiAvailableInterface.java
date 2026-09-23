package com.workflow.entity.ui.api.response;

import java.util.Map;

/**
 * 指定 UI 绑定位置可选择的完整接口扩展。
 *
 * @param extensionId 扩展ID，后续用于处理界面可用接口时定位或关联目标
 * @param extensionKey 扩展键，后续用于授权校验、关联或幂等去重
 * @param displayName 用户可见名称，供界面和日志展示
 * @param implementationType 实现类型标识，决定后续界面可用接口采用的处理分支
 * @param providerCode 提供者编码，后续用于处理界面可用接口时定位或关联目标
 * @param scopeType 作用域类型标识，决定后续界面可用接口采用的处理分支
 * @param scopeId 作用域ID，后续用于处理界面可用接口时定位或关联目标
 * @param kind 类型，保存在对象中供后续校验、查询或展示
 * @param contextType 上下文类型标识，决定后续界面可用接口采用的处理分支
 * @param inputSchema 输入结构，保存在对象中供后续校验、查询或展示
 * @param outputSchema 输出结构，保存在对象中供后续校验、查询或展示
 */
public record UiAvailableInterface(
        /** 接口扩展 ID。 */
        String extensionId,
        /** 接口扩展稳定编码。 */
        String extensionKey,
        /** 接口扩展名称。 */
        String displayName,
        /** 接口实现类型。 */
        String implementationType,
        /** Provider 编码，仅用于展示和诊断。 */
        String providerCode,
        /** 接口作用域类型。 */
        String scopeType,
        /** 接口作用域对象 ID。 */
        String scopeId,
        /** 接口数据影响类型：READ 或 WRITE。 */
        String kind,
        /** 接口所需上下文类型：FORM、LIST 或 ENTITY。 */
        String contextType,
        /** 已解析的输入 Schema，供设计器编辑字段映射。 */
        Map<String, Object> inputSchema,
        /** 已解析的输出 Schema，供设计器编辑字段映射。 */
        Map<String, Object> outputSchema) {
}
