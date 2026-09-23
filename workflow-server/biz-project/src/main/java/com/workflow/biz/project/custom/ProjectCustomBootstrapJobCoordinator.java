package com.workflow.biz.project.custom;

import com.workflow.contracts.bootstrap.port.BootstrapJobPort;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 启动任务协调器替换示例。
 *
 * <p>平台已有数据库分布式协调器，因此该类不注册为 Spring Bean。
 * 示例记录任务名称和版本后拒绝执行，避免在多实例环境中假装已经实现互斥。</p>
 */
@Slf4j
public class ProjectCustomBootstrapJobCoordinator
        implements BootstrapJobPort {

    /**
     * 执行{@code once}，并将结果传给后续步骤。
     *
     * @param jobName {@code job}名称，后续用于执行{@code once}时匹配或展示
     * @param requiredVersion 必填版本，供本方法执行{@code once}时使用
     * @param action 动作标识，决定后续{@code once}采用的处理分支
     * @return 匹配的{@code once}；未找到时为空
     * @throws UnsupportedOperationException 当前实现不支持指定操作时抛出
     */
    @Override
    public <T> Optional<T> executeOnce(
            String jobName,
            int requiredVersion,
            Supplier<T> action) {
        log.info(
                "项目启动任务协调器被调用: jobName={}, requiredVersion={}, actionPresent={}",
                LogValue.safe(jobName),
                requiredVersion,
                action != null);
        throw new UnsupportedOperationException(
                "项目启动任务协调示例未实现跨实例互斥");
    }
}
