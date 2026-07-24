package com.workflow.dto;

import com.workflow.entity.EntityDefinition;
import lombok.Data;

/**
 * 实体生命周期模式切换请求。
 */
@Data
public class EntityLifecycleModeRequest {
    /** 生命周期模式：STANDALONE / WORKFLOW */
    private EntityDefinition.LifecycleMode lifecycleMode;
}
