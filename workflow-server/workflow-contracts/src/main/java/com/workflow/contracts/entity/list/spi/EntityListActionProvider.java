package com.workflow.contracts.entity.list.spi;

import com.workflow.contracts.entity.list.EntityListRuntimeContext;

import java.util.Map;

/**
 * 自定义实体列表动作扩展点。
 *
 * <p><strong>Incubating：</strong>当前平台尚未提供统一 Registry 或动作路由。声明 Bean 或
 * 实现本接口不会被自动发现、配置或调用，不能将其视为稳定插件扩展；待宿主能力落地后再升级为
 * 稳定 SPI。</p>
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
     * @param actionKey 动作 Key
     * @param payload   动作参数
     * @return 动作执行结果
     */
    Object execute(
            EntityListRuntimeContext context,
            String actionKey,
            Map<String, Object> payload);
}
