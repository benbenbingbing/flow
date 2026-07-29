package com.workflow.config;

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

    @Override
    public void run(ApplicationArguments args) {
        applicationContext.close();
    }
}
