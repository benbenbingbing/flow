package com.workflow.contracts.entity.list;

import java.util.Map;

/**
 * 自定义列表动作扩展。
 */
public interface EntityListActionProvider {

    String getCode();

    String getDisplayName();

    Object execute(
            EntityListRuntimeContext context,
            String actionKey,
            Map<String, Object> payload);
}
