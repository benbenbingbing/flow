package com.workflow.contracts.entity.ui.spi;

import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.contracts.ui.UiInvocationContext;
import com.workflow.contracts.ui.UiProviderArtifactIdentity;

import java.util.Map;

/**
 * UI 数据源提供者。
 * 由实体 UI 宿主发现并按编码标识选择具体实现。
 */
public interface UiDataSourceProvider {

    /** @return 数据源编码 */
    String getCode();

    /** @return 数据源展示名称 */
    String getDisplayName();

    /** @return 可并存的 Provider 版本；存量实现默认视为 v1 */
    default int getVersion() {
        return 1;
    }

    /**
     * 返回当前可执行制品摘要，供宿主发布版本精确固定实现。
     *
     * <p>存量实现默认对自身 class 字节码计算 SHA-256；实现依赖额外脚本、模型
     * 或资源时应覆盖此方法并返回整个受审制品的稳定摘要。</p>
     *
     * @return 64 位小写十六进制摘要
     */
    default String getArtifactDigest() {
        return UiProviderArtifactIdentity.defaultDigest(
                getClass(), getVersion());
    }

    /** @return 该数据源的配置项 Schema；默认空 */
    default Map<String, Object> configurationSchema() {
        return Map.of();
    }

    /**
     * 在数据范围计划约束下执行数据源查询。
     *
     * <p>当该 SPI 用于 {@code FORM_BUTTON_CLICK} 的 READ 路径时必须保持无外部
     * 副作用：只能读取、校验和计算 UI 返回值。表单按钮的实体写入应交给平台默认
     * 处理或受控命令计划；外部通知或集成调用应在业务事务中只写入最小化 Outbox
     * 事件，再异步投递。其他既有 WRITE 用法的契约不由本约束改变。</p>
     *
     * @param context UI 数据源上下文
     * @param dataScopePlan 数据范围查询计划
     * @param configuration 数据源配置
     * @param input 调用输入
     * @return 查询结果
     */
    Object execute(
            UiInvocationContext context,
            DataScopePlan dataScopePlan,
            Map<String, Object> configuration,
            Map<String, Object> input);
}
