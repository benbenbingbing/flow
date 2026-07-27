package com.workflow.entity.definition.api.request;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import lombok.Data;

/**
 * 实体生命周期模式切换请求。
 */
@Data
public class EntityLifecycleModeRequest {
    /** 生命周期模式：STANDALONE / WORKFLOW */
    private EntityDefinition.LifecycleMode lifecycleMode;
}
