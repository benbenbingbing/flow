package com.workflow.entity.ui.application;

import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 负责界面数据来源提供者策略的业务处理；协调校验、状态变化及后续结果传递。
 */
final class UiDataSourceProviderPolicy {

    /**
     * 初始化界面数据来源提供者策略，保存构造参数供后续方法使用。
     */
    private UiDataSourceProviderPolicy() {
    }

    /**
     * 校验界面数据来源提供者策略；不满足约束时阻止后续处理。
     *
     * @param sourceType 来源类型标识，决定后续界面数据来源提供者策略采用的处理分支
     * @param providerCode 提供者编码，后续用于校验界面数据来源提供者策略时定位或关联目标
     * @param configuration 配置内容，决定后续界面数据来源提供者策略的处理规则
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    static void validate(
            String sourceType,
            String providerCode,
            Map<String, Object> configuration) {
        if ("REGISTERED_PROVIDER".equals(sourceType)
                && !StringUtils.hasText(providerCode)) {
            throw new IllegalArgumentException(
                    "Provider 编码不能为空");
        }
    }
}
