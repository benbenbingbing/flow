package com.workflow.entity.ui.api.response;

import java.util.Map;

/**
 * 指定 UI 绑定位置可选择的完整接口扩展。
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
