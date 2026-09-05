package com.workflow.contracts.bootstrap.port;

import java.util.Optional;
import java.util.function.Supplier;

/** 在应用 Pod 间串行执行版本化启动任务。 */
public interface BootstrapJobCoordinator {

    /**
     * 在当前所需版本尚未完成时执行一次任务。
     *
     * @param jobName 稳定任务名称
     * @param requiredVersion 所需任务版本
     * @param action 实际初始化动作
     * @param <T> 初始化动作返回值类型
     * @return 由当前节点执行时返回动作结果，否则为空
     */
    <T> Optional<T> executeOnce(
            String jobName,
            int requiredVersion,
            Supplier<T> action);
}
