package com.workflow.migration.application;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseCandidateExecutionEngineTest {

    @Test
    void failedDeployStopsAllLaterSteps() {
        ReleaseCandidateDeploymentPort deploymentPort = mock(ReleaseCandidateDeploymentPort.class);
        ReleaseCandidateExecutionEngine.StepJournal journal =
                mock(ReleaseCandidateExecutionEngine.StepJournal.class);
        ReleaseCandidateExecutionEngine engine =
                new ReleaseCandidateExecutionEngine(deploymentPort);
        ReleaseCandidateExecutionEngine.ExecutionStep verifyStep =
                new ReleaseCandidateExecutionEngine.ExecutionStep("verify", "VERIFY_ENTITY_SCHEMA");
        ReleaseCandidateExecutionEngine.ExecutionStep deployStep =
                new ReleaseCandidateExecutionEngine.ExecutionStep("deploy", "DEPLOY_IMPORT_PACKAGE");
        ReleaseCandidateExecutionEngine.ExecutionStep finalStep =
                new ReleaseCandidateExecutionEngine.ExecutionStep("final", "FINALIZE_REPORT");
        when(deploymentPort.publishImport("import-1"))
                .thenThrow(new IllegalStateException("ddl failed"));

        ReleaseCandidateExecutionEngine.StepExecutionException error = assertThrows(
                ReleaseCandidateExecutionEngine.StepExecutionException.class,
                () -> engine.execute(
                        "import-1", List.of(verifyStep, deployStep, finalStep), journal));

        assertEquals("deploy", error.getStepId());
        InOrder order = inOrder(journal, deploymentPort);
        order.verify(journal).started(verifyStep);
        order.verify(journal).completed(any(), any(), anyLong());
        order.verify(journal).started(deployStep);
        order.verify(deploymentPort).publishImport("import-1");
        order.verify(journal).failed(any(), any(), anyLong());
        verify(journal, never()).started(finalStep);
    }

    @Test
    void successfulPlanDeploysImportExactlyOnce() {
        ReleaseCandidateDeploymentPort deploymentPort = mock(ReleaseCandidateDeploymentPort.class);
        ReleaseCandidateExecutionEngine.StepJournal journal =
                mock(ReleaseCandidateExecutionEngine.StepJournal.class);
        ReleaseCandidateExecutionEngine engine =
                new ReleaseCandidateExecutionEngine(deploymentPort);
        when(deploymentPort.publishImport("import-1"))
                .thenReturn(Map.of("status", "PUBLISHED"));

        ReleaseCandidateExecutionEngine.ExecutionResult result = engine.execute(
                "import-1",
                List.of(
                        new ReleaseCandidateExecutionEngine.ExecutionStep("verify", "VERIFY_PROCESS"),
                        new ReleaseCandidateExecutionEngine.ExecutionStep("deploy", "DEPLOY_IMPORT_PACKAGE"),
                        new ReleaseCandidateExecutionEngine.ExecutionStep("final", "FINALIZE_REPORT")),
                journal);

        assertEquals("PUBLISHED", result.deploymentResult().get("status"));
        verify(deploymentPort).publishImport("import-1");
    }
}
