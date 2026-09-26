package com.workflow.contracts.process.action.model;

import java.util.List;

/**
 * 策略参数表单契约。type 支持 string、number（整数）、boolean、select；
 * number 使用 min/max 限制预算相关参数，select 使用 options 限定业务选择。
 * 一期采用有限表单类型，避免参数配置演变为在线脚本执行入口。
 */
public record FailureStrategyParameter(String key, String label, String type, boolean required,
                                       Object defaultValue, Long min, Long max,
                                       List<String> options, String description) {
    public FailureStrategyParameter {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
