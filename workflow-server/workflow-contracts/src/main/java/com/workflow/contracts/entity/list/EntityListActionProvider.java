package com.workflow.contracts.entity.list;

import java.util.Map;

/**
 * 自定义列表动作扩展。
 */
public interface EntityListActionProvider {

    /**
     * 返回列表动作编码。
     *
     * @return 动作编码
     */
    String getCode();

    /**
     * 返回列表动作展示名称。
     *
     * @return 展示名称
     */
    String getDisplayName();

    /**
     * 执行列表动作。
     *
     * @param context   列表运行时上下文
     * @param actionKey 动作Key
     * @param payload   动作参数
     * @return 动作执行结果
     */
    Object execute(
            EntityListRuntimeContext context,
            String actionKey,
            Map<String, Object> payload);
}
