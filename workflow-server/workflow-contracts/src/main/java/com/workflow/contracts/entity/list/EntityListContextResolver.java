package com.workflow.contracts.entity.list;

import java.util.Map;

/**
 * 将来源记录解析为服务端可信的列表过滤条件。
 */
public interface EntityListContextResolver {

    String getRelationKey();

    default String getDisplayName() {
        return getRelationKey();
    }

    Map<String, Object> resolve(EntityListRuntimeContext context);
}
