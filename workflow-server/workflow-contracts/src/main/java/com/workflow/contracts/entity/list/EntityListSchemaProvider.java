package com.workflow.contracts.entity.list;

import java.util.Map;

/**
 * 自定义列表结构扩展。
 */
public interface EntityListSchemaProvider {

    /**
     * 返回结构扩展编码。
     *
     * @return 扩展编码
     */
    String getCode();

    /**
     * 返回结构扩展展示名称。
     *
     * @return 展示名称
     */
    String getDisplayName();

    /**
     * 增强列表结构（Schema）。
     *
     * @param context 列表运行时上下文
     * @param schema  原始结构
     * @return 增强后的结构
     */
    Map<String, Object> enhance(
            EntityListRuntimeContext context,
            Map<String, Object> schema);
}
