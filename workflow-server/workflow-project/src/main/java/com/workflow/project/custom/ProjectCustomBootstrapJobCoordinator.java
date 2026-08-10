package com.workflow.project.custom;

import com.workflow.contracts.bootstrap.BootstrapJobCoordinator;
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
        implements BootstrapJobCoordinator {

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
