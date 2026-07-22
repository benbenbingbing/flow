package com.workflow.contracts.entity.list;

import java.util.Map;

/**
 * 自定义列表结构扩展。
 */
public interface EntityListSchemaProvider {

    String getCode();

    String getDisplayName();

    Map<String, Object> enhance(
            EntityListRuntimeContext context,
            Map<String, Object> schema);
}
