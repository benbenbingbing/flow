package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

/**
 * 一个发布上下文中最终生效的事件步骤。
 *
 * <p>保留步骤来源绑定，便于管理端解释实体默认、页面级和精确目标级
 * 绑定在继承计算后的真实执行顺序。</p>
 */
@Value
@Builder
public class UiDataSourceReferenceEffectiveStepDTO {

    String bindingId;
    String ownerType;
    String ownerId;
    String targetType;
    String targetKey;
    String inheritanceSource;
    Integer stepIndex;
    String stepCode;
    String stepName;
    Integer stepOrder;
    String stepStrategy;
    String serviceId;
    String operationCode;
}
