package com.workflow.entity.version.application.model;

import java.util.List;

/**
 * 数据版本候选配置校验结果。结构错误仍通过统一异常响应返回。
 *
 * @param valid 有效，后续用于处理实体版本校验结果时定位或关联目标
 * @param warnings {@code warnings}，保存在对象中供后续校验、查询或展示
 */
public record EntityVersionValidationResult(
        boolean valid,
        List<String> warnings) {

    /**
     * 处理有效，并将结果传给后续步骤。
     *
     * @param warnings {@code warnings}，作为 {@code EntityVersionValidationResult} 的输入影响后续处理
     * @return 处理后的有效结果，供调用方继续处理
     */
    public static EntityVersionValidationResult valid(
            List<String> warnings) {
        return new EntityVersionValidationResult(
                true,
                warnings == null ? List.of() : List.copyOf(warnings));
    }
}
