package com.workflow.contracts.entity;

import com.workflow.contracts.ui.runtime.UiRuntimePurpose;

import java.util.Map;
import java.util.Optional;

/**
 * 流程节点解析表单时使用的实体表单只读端口。
 */
public interface EntityFormRuntimePort {

    Optional<EntityFormRuntimeContext> findContext(String entityCode);

    Map<String, Object> findFormById(String formId);

    Map<String, Object> findFormByBinding(
            EntityFormBinding binding,
            String processVersionHistoryId,
            UiRuntimePurpose purpose);

    void requireCurrentBindingForNewData(
            EntityFormBinding binding,
            String processVersionHistoryId);
}
