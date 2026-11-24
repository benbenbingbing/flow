package com.workflow.port.process.model.query;

import com.workflow.port.process.model.ProcessBusinessReference;

/** 跨模块的活动任务详情，可按业务引用读取记录并根据 capabilities 决定可展示操作。 */
public record ProcessTaskDetail(ProcessTaskView task, ProcessBusinessReference businessReference,
                                FormSummary form, TaskCapabilities capabilities) {

    /** formId 为实体表单 ID；没有可解析的表单时，仅提供已知的表单键和只读提示。 */
    public record FormSummary(String formId, String formKey, String name, boolean readOnly) { }
}
