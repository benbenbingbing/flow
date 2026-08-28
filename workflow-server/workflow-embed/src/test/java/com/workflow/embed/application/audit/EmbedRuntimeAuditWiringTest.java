package com.workflow.embed.application.audit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.workflow.contracts.audit.SystemAuditPort;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class EmbedRuntimeAuditWiringTest {

    @Test
    void springSelectsTheProductionConstructorWhenTheClockTestSeamExists() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    SystemAuditPort.class,
                    () -> mock(SystemAuditPort.class));
            context.register(EmbedRuntimeAudit.class);

            context.refresh();

            assertNotNull(context.getBean(EmbedRuntimeAudit.class));
        }
    }
}
