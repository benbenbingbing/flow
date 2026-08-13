package com.workflow.entity.form.api.request;

import lombok.Data;

/**
 * 复制表单时指定的新表单名称和稳定标识。
 */
@Data
public class EntityFormCopyRequest {

    /** 新表单名称；为空时由服务端生成默认名称 */
    private String formName;
    /** 新表单标识；为空时由服务端生成不冲突的可读标识 */
    private String formKey;
}
