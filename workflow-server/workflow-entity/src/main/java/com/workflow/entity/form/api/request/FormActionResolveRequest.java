package com.workflow.entity.form.api.request;

import lombok.Data;

/**
 * 表单运行时按钮解析请求。
 */
@Data
public class FormActionResolveRequest {

    private String formId;
    private String releaseId;
    private Integer releaseVersion;
    private String releaseResolutionToken;
    private String entityCode;
    private String listKey;
    private String mode;
    private String recordId;
    private String taskId;
}
