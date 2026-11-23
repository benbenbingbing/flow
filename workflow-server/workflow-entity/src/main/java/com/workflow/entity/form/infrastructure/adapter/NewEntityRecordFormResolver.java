package com.workflow.entity.form.infrastructure.adapter;

import com.workflow.port.entity.EntityFormPort.ResolvedForm;
import java.util.Optional;

/** 由流程宿主提供新增表单选择规则，允许回退到已发布流程的首节点表单。 */
@FunctionalInterface
public interface NewEntityRecordFormResolver {
    Optional<ResolvedForm> resolveNewRecordForm(String entityCode);
}
