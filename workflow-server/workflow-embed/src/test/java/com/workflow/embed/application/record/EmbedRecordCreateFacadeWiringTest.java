package com.workflow.embed.application.record;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.application.form.EmbedRuntimeFormFacade;
import com.workflow.embed.application.port.EmbedIdempotencyPort;
import com.workflow.embed.application.port.EmbedOperationReceiptPort;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class EmbedRecordCreateFacadeWiringTest {

    @Test
    void springSelectsTheProductionConstructorWhenTheClockTestSeamExists() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    EmbedRuntimeFormFacade.class,
                    () -> mock(EmbedRuntimeFormFacade.class));
            context.registerBean(
                    EmbedCanonicalRequestHasher.class,
                    () -> mock(EmbedCanonicalRequestHasher.class));
            context.registerBean(
                    EmbedIdempotencyPort.class,
                    () -> mock(EmbedIdempotencyPort.class));
            context.registerBean(
                    EmbedOperationReceiptPort.class,
                    () -> mock(EmbedOperationReceiptPort.class));
            context.registerBean(
                    EmbedRecordCreateTransactionService.class,
                    () -> mock(EmbedRecordCreateTransactionService.class));
            context.registerBean(
                    ObjectMapper.class,
                    () -> new ObjectMapper());
            context.register(EmbedRecordCreateFacade.class);

            context.refresh();

            assertNotNull(context.getBean(EmbedRecordCreateFacade.class));
        }
    }
}
