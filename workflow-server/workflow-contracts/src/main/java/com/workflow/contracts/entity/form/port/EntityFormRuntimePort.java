package com.workflow.contracts.entity.form.port;

import com.workflow.contracts.entity.EntityFormBinding;
import com.workflow.contracts.entity.EntityFormRuntimeContext;
import com.workflow.contracts.ui.runtime.UiRuntimePurpose;

import java.util.Map;
import java.util.Optional;

/**
 * 流程节点解析实体表单时使用的稳定只读能力。
 *
 * <p>调用方不依赖实体表单的存储实现，所有返回模型在兼容期内仍使用原有 FQCN。</p>
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
