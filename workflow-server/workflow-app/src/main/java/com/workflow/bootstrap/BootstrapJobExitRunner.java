package com.workflow.bootstrap;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Closes the application after ordered bootstrap runners finish.
 */
@Component
@ConditionalOnProperty(
        name = "workflow.bootstrap.exit-on-complete",
        havingValue = "true")
@Order(1000)
@RequiredArgsConstructor
public class BootstrapJobExitRunner implements ApplicationRunner {

    private final ConfigurableApplicationContext applicationContext;

    /**
     * 执行初始化{@code job}{@code exit}{@code runner}，并将结果传给后续步骤。
     *
     * @param args {@code args}，供本方法执行初始化{@code job}{@code exit}{@code runner}时使用
     */
    @Override
    public void run(ApplicationArguments args) {
        applicationContext.close();
    }
}
