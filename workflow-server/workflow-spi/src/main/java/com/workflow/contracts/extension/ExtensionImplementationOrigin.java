package com.workflow.contracts.extension;

/**
 * 扩展实现的归属。
 *
 * <p>该维度只回答“由谁提供实现”，不替代扩展能力类型或实现方式。</p>
 */
public enum ExtensionImplementationOrigin {

    /** 流程平台随产品交付的内置实现。 */
    PLATFORM,

    /** 项目、客户或二次开发代码提供的实现。 */
    CUSTOM,

    /** 当前实现未加载、未声明或声明冲突，无法可靠识别归属。 */
    UNKNOWN
}
