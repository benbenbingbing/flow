package com.workflow.config.flowable;

import com.workflow.entity.ui.infrastructure.config.UiExtensionExecutionConfiguration;
import com.workflow.outbox.infrastructure.config.OutboxExecutionConfiguration;
import com.workflow.process.action.infrastructure.config.FlowActionExecutionConfiguration;
import org.flowable.common.spring.async.SpringAsyncTaskExecutor;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.ProcessEngineAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 专用线程池并存时，验证 Boot 默认执行器与 Flowable 的实际自动装配契约。 */
class FlowableTaskExecutorWiringTest {

    @Test
    void dedicatedExecutorsKeepApplicationExecutorAvailableToFlowable() {
        // 不手动提供 applicationTaskExecutor，否则会掩盖专用线程池导致 Boot 自动配置退让的问题。
        // 仅装配引擎配置，不创建流程引擎或连接数据库。
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TaskExecutionAutoConfiguration.class, ProcessEngineAutoConfiguration.class))
                .withUserConfiguration(UiExtensionExecutionConfiguration.class,
                        FlowActionExecutionConfiguration.class, OutboxExecutionConfiguration.class)
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withPropertyValues("flowable.check-process-definitions=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasBean("applicationTaskExecutor")
                            .hasSingleBean(SpringProcessEngineConfiguration.class);
                    AsyncTaskExecutor applicationExecutor = context.getBean(
                            "applicationTaskExecutor", AsyncTaskExecutor.class);
                    assertThat(context.getBean("uiExtensionTaskExecutor"))
                            .isNotSameAs(applicationExecutor);
                    assertThat(context.getBean("flowActionTaskExecutor"))
                            .isNotSameAs(applicationExecutor);
                    assertThat(context.getBean("outboxTaskExecutor"))
                            .isNotSameAs(applicationExecutor);
                    var flowableExecutor = context.getBean(SpringProcessEngineConfiguration.class)
                            .getAsyncTaskExecutor();
                    assertThat(flowableExecutor).isInstanceOf(SpringAsyncTaskExecutor.class);
                    assertThat(((SpringAsyncTaskExecutor) flowableExecutor).getAsyncTaskExecutor())
                            .isSameAs(applicationExecutor);
                });
    }
}
