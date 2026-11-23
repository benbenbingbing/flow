package com.workflow.port.entity;
import java.util.List;
/** 模块提供稳定表单身份引用；历史流程详情仍会读取当前表单，因此历史引用也保护删除。 */
public interface EntityFormDeletionReferencePort {
    List<String> references(String formId);
}
