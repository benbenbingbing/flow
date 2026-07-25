package com.workflow.process.publish;

import com.workflow.contracts.ui.hotfix.UiHotfixProcessImpact;
import com.workflow.contracts.ui.hotfix.UiHotfixProcessTarget;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessUiReleaseBinding;
import com.workflow.entity.ProcessVersionHistory;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.ProcessUiReleaseBindingMapper;
import com.workflow.mapper.ProcessVersionHistoryMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 流程 UI 热修复影响范围分析测试。
 */
class ProcessUiHotfixImpactAdapterTest {

    @Test
    void analyzeIncludesStartableAndRunningVersionsAndBuildsStableHash() {
        ProcessUiReleaseBindingMapper bindingMapper =
                mock(ProcessUiReleaseBindingMapper.class);
        ProcessVersionHistoryMapper historyMapper =
                mock(ProcessVersionHistoryMapper.class);
        ProcessDefinitionConfigMapper processConfigMapper =
                mock(ProcessDefinitionConfigMapper.class);
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        RuntimeService runtimeService =
                mock(RuntimeService.class);
        HistoryService historyService =
                mock(HistoryService.class);

        ProcessVersionHistory version1 = history(
                "history-1",
                1,
                "deployment-1");
        ProcessVersionHistory version2 = history(
                "history-2",
                2,
                "deployment-2");
        ProcessVersionHistory version3 = history(
                "history-3",
                3,
                "deployment-3");
        Map<String, ProcessVersionHistory> histories = Map.of(
                version1.getId(), version1,
                version2.getId(), version2,
                version3.getId(), version3);
        when(historyMapper.selectById(anyString()))
                .thenAnswer(invocation -> histories.get(
                        invocation.getArgument(0)));
        when(historyMapper.findLatestByProcessKey("expense_flow"))
                .thenReturn(version3);

        ProcessDefinitionConfig processConfig =
                new ProcessDefinitionConfig();
        processConfig.setId("process-1");
        processConfig.setStatus(
                ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
        when(processConfigMapper.selectById("process-1"))
                .thenReturn(processConfig);

        List<ProcessUiReleaseBinding> bindings = new ArrayList<>(
                List.of(
                        binding(
                                "history-3",
                                3,
                                "deployment-3",
                                "task-z",
                                "release-3",
                                3),
                        binding(
                                "history-2",
                                2,
                                "deployment-2",
                                "task-old",
                                "release-2",
                                2),
                        binding(
                                "history-1",
                                1,
                                "deployment-1",
                                "task-complete",
                                "release-1",
                                1),
                        binding(
                                "history-3",
                                3,
                                "deployment-3",
                                "task-a",
                                "release-3",
                                3)));
        List<ProcessUiReleaseBinding> reversed =
                new ArrayList<>(bindings);
        Collections.reverse(reversed);
        when(bindingMapper.findByFormId("form-1"))
                .thenReturn(bindings, reversed);

        mockRuntimeCounts(
                repositoryService,
                runtimeService,
                historyService,
                Map.of(
                        "deployment-1", 0L,
                        "deployment-2", 4L,
                        "deployment-3", 0L),
                Map.of(
                        "deployment-1", 5L,
                        "deployment-2", 3L,
                        "deployment-3", 2L));

        ProcessUiHotfixImpactAdapter adapter =
                new ProcessUiHotfixImpactAdapter(
                        bindingMapper,
                        historyMapper,
                        processConfigMapper,
                        repositoryService,
                        runtimeService,
                        historyService);

        UiHotfixProcessImpact first =
                adapter.analyzeFormImpact("form-1");
        UiHotfixProcessImpact second =
                adapter.analyzeFormImpact("form-1");

        assertEquals(first, second);
        assertEquals(2, first.processVersionCount());
        assertEquals(4L, first.activeInstanceCount());
        assertEquals(10L, first.skippedHistoricalInstanceCount());
        assertNotNull(first.targetHash());
        assertFalse(first.targetHash().isBlank());

        UiHotfixProcessTarget current = target(
                first,
                "history-3");
        assertTrue(current.currentStartable());
        assertEquals(0L, current.activeInstanceCount());
        assertEquals(2L, current.completedInstanceCount());
        assertEquals(List.of("task-a", "task-z"), current.nodeIds());

        UiHotfixProcessTarget historicalRunning = target(
                first,
                "history-2");
        assertFalse(historicalRunning.currentStartable());
        assertEquals(4L, historicalRunning.activeInstanceCount());
        assertEquals(3L, historicalRunning.completedInstanceCount());

        assertTrue(first.targets().stream().noneMatch(target ->
                "history-1".equals(
                        target.processVersionHistoryId())));
    }

    private static void mockRuntimeCounts(
            RepositoryService repositoryService,
            RuntimeService runtimeService,
            HistoryService historyService,
            Map<String, Long> activeByDeployment,
            Map<String, Long> completedByDeployment) {
        Map<String, ProcessDefinition> definitions =
                new LinkedHashMap<>();
        activeByDeployment.keySet().forEach(deploymentId -> {
            ProcessDefinition definition =
                    mock(ProcessDefinition.class);
            when(definition.getId()).thenReturn(
                    "definition-" + deploymentId);
            definitions.put(deploymentId, definition);
        });

        AtomicReference<String> currentDeployment =
                new AtomicReference<>();
        ProcessDefinitionQuery definitionQuery =
                mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery())
                .thenReturn(definitionQuery);
        when(definitionQuery.deploymentId(anyString()))
                .thenAnswer(invocation -> {
                    currentDeployment.set(invocation.getArgument(0));
                    return definitionQuery;
                });
        when(definitionQuery.list()).thenAnswer(invocation ->
                List.of(definitions.get(currentDeployment.get())));

        Map<String, Long> activeByDefinition =
                byDefinitionId(activeByDeployment);
        AtomicReference<String> currentActiveDefinition =
                new AtomicReference<>();
        ProcessInstanceQuery processInstanceQuery =
                mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery())
                .thenReturn(processInstanceQuery);
        when(processInstanceQuery.processDefinitionId(anyString()))
                .thenAnswer(invocation -> {
                    currentActiveDefinition.set(
                            invocation.getArgument(0));
                    return processInstanceQuery;
                });
        when(processInstanceQuery.count()).thenAnswer(invocation ->
                activeByDefinition.get(
                        currentActiveDefinition.get()));

        Map<String, Long> completedByDefinition =
                byDefinitionId(completedByDeployment);
        AtomicReference<String> currentCompletedDefinition =
                new AtomicReference<>();
        HistoricProcessInstanceQuery historicQuery =
                mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery())
                .thenReturn(historicQuery);
        when(historicQuery.processDefinitionId(anyString()))
                .thenAnswer(invocation -> {
                    currentCompletedDefinition.set(
                            invocation.getArgument(0));
                    return historicQuery;
                });
        when(historicQuery.finished()).thenReturn(historicQuery);
        when(historicQuery.count()).thenAnswer(invocation ->
                completedByDefinition.get(
                        currentCompletedDefinition.get()));
    }

    private static Map<String, Long> byDefinitionId(
            Map<String, Long> countsByDeployment) {
        Map<String, Long> result = new LinkedHashMap<>();
        countsByDeployment.forEach((deploymentId, count) ->
                result.put(
                        "definition-" + deploymentId,
                        count));
        return result;
    }

    private static ProcessVersionHistory history(
            String id,
            int version,
            String deploymentId) {
        ProcessVersionHistory history =
                new ProcessVersionHistory();
        history.setId(id);
        history.setProcessConfigId("process-1");
        history.setProcessKey("expense_flow");
        history.setProcessName("费用流程");
        history.setVersion(version);
        history.setDeploymentId(deploymentId);
        return history;
    }

    private static ProcessUiReleaseBinding binding(
            String historyId,
            int processVersion,
            String deploymentId,
            String nodeId,
            String releaseId,
            int releaseVersion) {
        ProcessUiReleaseBinding binding =
                new ProcessUiReleaseBinding();
        binding.setProcessVersionHistoryId(historyId);
        binding.setProcessConfigId("process-1");
        binding.setProcessKey("expense_flow");
        binding.setProcessVersion(processVersion);
        binding.setDeploymentId(deploymentId);
        binding.setNodeId(nodeId);
        binding.setConfigType("FORM");
        binding.setConfigId("form-1");
        binding.setPinnedReleaseId(releaseId);
        binding.setPinnedReleaseVersion(releaseVersion);
        return binding;
    }

    private static UiHotfixProcessTarget target(
            UiHotfixProcessImpact impact,
            String historyId) {
        return impact.targets().stream()
                .filter(target -> historyId.equals(
                        target.processVersionHistoryId()))
                .findFirst()
                .orElseThrow();
    }
}
