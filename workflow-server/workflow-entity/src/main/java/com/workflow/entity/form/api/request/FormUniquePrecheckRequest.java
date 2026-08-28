package com.workflow.entity.form.api.request;

import lombok.Data;

import java.util.Map;

/**
 * 已发布表单字段唯一性提前检查请求。
 *
 * <p>请求只标识表单发布上下文和待检查字段，不能携带或覆盖唯一规则本身。</p>
 */
@Data
public class FormUniquePrecheckRequest {

    private String releaseId;
    private Integer releaseVersion;
    private String releaseResolutionToken;
    private String ruleId;
    private String fieldCode;
    /** 编辑时传入，用于合并未提交字段并排除当前记录。 */
    private String recordId;
    /** 当前表单值或字段级补丁；支持业务字段放在 data 子对象。 */
    private Map<String, Object> formData;
}
