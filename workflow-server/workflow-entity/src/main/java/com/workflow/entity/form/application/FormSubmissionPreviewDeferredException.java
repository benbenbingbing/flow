package com.workflow.entity.form.application;

/**
 * 表单提交前处理包含不能无副作用预执行的绑定。
 *
 * <p>该异常只表示预览能力需要延迟到 Flowable 正式提交阶段，不表示正式
 * 提交失败。调用方应将它转换为 {@code DEFERRED}，且不得继续提供人工改选
 * 下一审批人的能力。</p>
 */
public class FormSubmissionPreviewDeferredException
        extends RuntimeException {

    public FormSubmissionPreviewDeferredException(String message) {
        super(message);
    }
}
