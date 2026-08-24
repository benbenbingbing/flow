package com.workflow.process.configtest.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.configtest.application.ConfigTestModels.ExecutionResult;
import com.workflow.process.configtest.application.ConfigTestModels.QuickRunRequest;
import com.workflow.process.configtest.application.ConfigTestModels.ResultStatus;
import com.workflow.process.configtest.application.ConfigTestModels.RunReport;
import com.workflow.process.configtest.application.ConfigTestModels.RunStatus;
import com.workflow.process.configtest.application.ConfigTestModels.SaveCaseRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigTestCenterServiceTest {

    @Test
    void quickRunAggregatesPassWarningAndFailureWithoutPersistence() {
        ConfigTestCaseExecutor executor = mock(ConfigTestCaseExecutor.class);
        when(executor.execute(any()))
                .thenReturn(ExecutionResult.pass("PASS", "通过", Map.of()))
                .thenReturn(ExecutionResult.warning("WARN", "警告", Map.of()))
                .thenReturn(ExecutionResult.fail("FAIL", "失败", Map.of()));
        ConfigTestCenterService service = new ConfigTestCenterService(
                mock(JdbcTemplate.class), new ObjectMapper(), executor);
        SaveCaseRequest baseline = new SaveCaseRequest(
                null, "baseline", "基础", "CONFIG_JSON", null, "BASELINE",
                Map.of("document", Map.of()), Map.of(), true, 0);

        RunReport report = service.quickRun(new QuickRunRequest(
                "预览", "CONFIG_JSON", "draft", List.of(
                        baseline,
                        new SaveCaseRequest(null, "warning", "警告", "CONFIG_JSON", null,
                                "WARNING", Map.of("document", Map.of()), Map.of(), true, 1),
                        new SaveCaseRequest(null, "failure", "失败", "CONFIG_JSON", null,
                                "FAILURE", Map.of("document", Map.of()), Map.of(), true, 2))));

        assertEquals(RunStatus.FAIL, report.run().status());
        assertEquals(1, report.run().passedCount());
        assertEquals(1, report.run().warningCount());
        assertEquals(1, report.run().failedCount());
        assertEquals(33.33D, report.passRate());
        assertEquals(List.of(ResultStatus.PASS, ResultStatus.WARNING, ResultStatus.FAIL),
                report.results().stream().map(item -> item.status()).toList());
    }
}
