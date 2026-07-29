package com.workflow.process.definition;

import com.workflow.process.engine.infrastructure.flowable.ConfiguredScriptTaskDelegate;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Historical script deployments must remain non-executable.
 */
class ConfiguredScriptTaskRuntimeTest {

    @Test
    void historicalDelegateFailsClosedWithoutEvaluatingConfiguration() {
        ConfiguredScriptTaskDelegate delegate =
                new ConfiguredScriptTaskDelegate();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> delegate.execute(mock(DelegateExecution.class)));

        assertTrue(exception.getMessage().contains("SCRIPT_TASK_DISABLED"));
    }
}
