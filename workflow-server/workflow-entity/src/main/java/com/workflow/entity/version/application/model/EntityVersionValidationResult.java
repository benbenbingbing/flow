package com.workflow.entity.version.application.model;

import java.util.List;

/** 数据版本草稿校验结果。结构错误仍通过统一异常响应返回。 */
public record EntityVersionValidationResult(
        boolean valid,
        List<String> warnings) {

    public static EntityVersionValidationResult valid(
            List<String> warnings) {
        return new EntityVersionValidationResult(
                true,
                warnings == null ? List.of() : List.copyOf(warnings));
    }
}
