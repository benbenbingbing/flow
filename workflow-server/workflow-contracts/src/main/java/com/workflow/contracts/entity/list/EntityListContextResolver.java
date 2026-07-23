package com.workflow.contracts.entity.list;

import java.util.Map;

/**
 * 将来源记录解析为服务端可信的列表过滤条件。
 */
public interface EntityListContextResolver {

    /**
     * 返回该解析器对应的关联关系Key。
     *
     * @return 关联关系Key
     */
    String getRelationKey();

    /**
     * 返回展示名称，默认与关联关系Key相同。
     *
     * @return 展示名称
     */
    default String getDisplayName() {
        return getRelationKey();
    }

    /**
     * 将列表运行时上下文解析为服务端可信的过滤条件。
     *
     * @param context 列表运行时上下文
     * @return 解析得到的过滤条件
     */
    Map<String, Object> resolve(EntityListRuntimeContext context);
}
