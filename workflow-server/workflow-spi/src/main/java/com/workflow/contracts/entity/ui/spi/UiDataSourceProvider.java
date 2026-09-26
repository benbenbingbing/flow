package com.workflow.contracts.entity.ui.spi;

import com.workflow.contracts.entity.list.model.DataScopePlan;
import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import com.workflow.contracts.entity.ui.context.UiInvocationContext;
import com.workflow.contracts.entity.ui.UiProviderArtifactIdentity;

import java.util.Map;

/**
 * UI 数据源提供者。
 * 由实体 UI 宿主发现并按编码标识选择具体实现。
 */
public interface UiDataSourceProvider {

    /**
     * 返回扩展实现归属。
     *
     * <p>第三方 Provider 默认视为项目自定义；平台实现必须显式覆盖。</p>
     *
     * @return 处理后的实现来源结果，供调用方继续处理
     */
    default ExtensionImplementationOrigin implementationOrigin() {
        return ExtensionImplementationOrigin.CUSTOM;
    }

    /**
     * @return 数据源编码
     *
     * @return 读取后的编码文本，供调用方比较或展示
     */
    String getCode();

    /**
     * @return 数据源展示名称
     *
     * @return 读取后的展示名称文本，供调用方比较或展示
     */
    String getDisplayName();

    /**
     * @return 可并存的 Provider 版本；存量实现默认视为 v1
     *
     * @return 符合条件的界面数据来源提供者结果，供调用方继续处理
     */
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

    /**
     * @return 该数据源的配置项 Schema；默认空
     *
     * @return 配置结构键值结果，供调用方继续处理
     */
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
     * @param input 按接口 Schema 校验的业务输入，允许 userId/deptId 等业务字段。
     *              这些值不代表认证身份；身份只能读取 context，数据权限只能读取
     *              dataScopePlan，不得用 input 中的同名值覆盖或放宽授权。
     * @return 查询结果
     */
    Object execute(
            UiInvocationContext context,
            DataScopePlan dataScopePlan,
            Map<String, Object> configuration,
            Map<String, Object> input);

    /**
     * 带执行预算的新入口。旧 Provider 继续调用四参数方法；涉及自建客户端或长循环的实现
     * 应覆盖本方法，传递 remainingMillis 并注册资源取消，不能自行创建无界后台任务。
     * 平台 HTTP 和 MyBatis 调用还会自动继承当前线程的预算。
     */
    default Object execute(UiInvocationContext context, DataScopePlan dataScopePlan,
            Map<String, Object> configuration, Map<String, Object> input,
            com.workflow.contracts.execution.port.ExecutionControlPort control) {
        control.check();
        Object result = execute(context, dataScopePlan, configuration, input);
        control.check();
        return result;
    }
}
