package com.workflow.entity.form.api.response;

import com.workflow.entity.form.application.model.FormUniqueCheck;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 表单字段唯一性提前检查结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormUniquePrecheckResponse {

    /** 是否实际执行了数据库查重。 */
    private boolean checked;
    /** 当前值是否可用；未启用规则或条件不成立时为 true。 */
    private boolean available;
    private String ruleId;
    private String fieldCode;
    /** 重复时的字段级提示；可用或跳过时为空。 */
    private String message;

    public static FormUniquePrecheckResponse from(
            FormUniqueCheck result) {
        return new FormUniquePrecheckResponse(
                result.checked(),
                result.available(),
                result.ruleId(),
                result.fieldCode(),
                result.message());
    }
}
