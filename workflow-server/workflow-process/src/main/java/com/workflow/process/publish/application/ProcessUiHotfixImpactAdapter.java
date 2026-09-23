package com.workflow.process.publish.application;

import com.workflow.contracts.entity.ui.model.UiHotfixProcessImpact;
import com.workflow.contracts.entity.ui.port.UiHotfixProcessImpactPort;
import com.workflow.contracts.entity.ui.model.UiHotfixProcessTarget;
import com.workflow.process.publish.infrastructure.persistence.record.ProcessUiReleaseBinding;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.publish.infrastructure.persistence.mapper.ProcessUiReleaseBindingMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 基于流程发布历史与 Flowable 运行数据计算 UI 热修复影响范围。
 */
@Component
@RequiredArgsConstructor
public class ProcessUiHotfixImpactAdapter
        implements UiHotfixProcessImpactPort {

    private final ProcessUiReleaseBindingMapper bindingMapper;
    private final ProcessVersionHistoryMapper historyMapper;
    private final ProcessDefinitionConfigMapper processConfigMapper;
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    /**
     * 处理{@code analyze}表单影响，并将结果传给后续步骤。
     *
     * @param formId 表单ID，后续用于处理{@code analyze}表单影响时定位或关联目标
     * @return 处理后的{@code analyze}表单影响结果，供调用方继续处理
     */
    @Override
    public UiHotfixProcessImpact analyzeFormImpact(String formId) {
        List<ProcessUiReleaseBinding> bindings =
                bindingMapper.findByFormId(formId);
        if (bindings.isEmpty()) {
            return UiHotfixProcessImpact.empty();
        }
        Map<String, List<ProcessUiReleaseBinding>> byHistory =
                new LinkedHashMap<>();
        bindings.forEach(binding -> byHistory
                .computeIfAbsent(
                        binding.getProcessVersionHistoryId(),
                        ignored -> new ArrayList<>())
                .add(binding));

        List<UiHotfixProcessTarget> targets = new ArrayList<>();
        long skippedHistoricalInstances = 0L;
        for (Map.Entry<String, List<ProcessUiReleaseBinding>> entry
                : byHistory.entrySet()) {
            List<ProcessUiReleaseBinding> versionBindings = entry.getValue();
            ProcessUiReleaseBinding first = versionBindings.get(0);
            ProcessVersionHistory history = historyMapper.selectById(
                    first.getProcessVersionHistoryId());
            if (history == null) {
                continue;
            }
            ProcessVersionHistory latest =
                    historyMapper.findLatestByProcessKey(history.getProcessKey());
            ProcessDefinitionConfig processConfig =
                    processConfigMapper.selectById(
                            history.getProcessConfigId());
            boolean currentStartable = latest != null
                    && Objects.equals(latest.getId(), history.getId());
            currentStartable = currentStartable
                    && processConfig != null
                    && ProcessDefinitionConfig.ProcessStatus.PUBLISHED
                            .equals(processConfig.getStatus());
            RuntimeCounts counts = runtimeCounts(history.getDeploymentId());
            skippedHistoricalInstances += counts.completed();
            if (!currentStartable && counts.active() == 0) {
                continue;
            }
            Set<String> releaseIds = new LinkedHashSet<>();
            Set<Integer> releaseVersions = new LinkedHashSet<>();
            List<String> nodeIds = new ArrayList<>();
            for (ProcessUiReleaseBinding binding : versionBindings) {
                releaseIds.add(binding.getPinnedReleaseId());
                releaseVersions.add(binding.getPinnedReleaseVersion());
                nodeIds.add(binding.getNodeId());
            }
            String pinnedReleaseId = releaseIds.size() == 1
                    ? releaseIds.iterator().next() : null;
            Integer pinnedReleaseVersion = releaseVersions.size() == 1
                    ? releaseVersions.iterator().next() : null;
            targets.add(new UiHotfixProcessTarget(
                    history.getId(),
                    history.getProcessConfigId(),
                    history.getProcessKey(),
                    history.getProcessName(),
                    history.getVersion(),
                    history.getDeploymentId(),
                    pinnedReleaseId,
                    pinnedReleaseVersion,
                    nodeIds.stream().sorted().toList(),
                    currentStartable,
                    counts.active(),
                    counts.completed()));
        }
        targets.sort(Comparator
                .comparing(UiHotfixProcessTarget::processKey)
                .thenComparing(
                        UiHotfixProcessTarget::processVersion,
                        Comparator.nullsLast(Integer::compareTo)));
        long activeInstances = targets.stream()
                .mapToLong(UiHotfixProcessTarget::activeInstanceCount)
                .sum();
        return new UiHotfixProcessImpact(
                List.copyOf(targets),
                targets.size(),
                activeInstances,
                skippedHistoricalInstances,
                targetHash(targets));
    }

    /**
     * 处理运行时{@code counts}，并将结果传给后续步骤。
     *
     * @param deploymentId {@code deployment}ID，后续用于处理运行时{@code counts}时定位或关联目标
     * @return 处理后的运行时{@code counts}结果，供调用方继续处理
     */
    private RuntimeCounts runtimeCounts(String deploymentId) {
        if (deploymentId == null || deploymentId.isBlank()) {
            return new RuntimeCounts(0L, 0L);
        }
        List<ProcessDefinition> definitions = repositoryService
                .createProcessDefinitionQuery()
                .deploymentId(deploymentId)
                .list();
        long active = 0L;
        long completed = 0L;
        for (ProcessDefinition definition : definitions) {
            active += runtimeService.createProcessInstanceQuery()
                    .processDefinitionId(definition.getId())
                    .count();
            completed += historyService.createHistoricProcessInstanceQuery()
                    .processDefinitionId(definition.getId())
                    .finished()
                    .count();
        }
        return new RuntimeCounts(active, completed);
    }

    /**
     * 生成目标哈希文本，供后续匹配或展示。
     *
     * @param targets 目标集合，供本方法处理目标哈希时使用
     * @return 处理后的目标哈希文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private String targetHash(List<UiHotfixProcessTarget> targets) {
        StringBuilder value = new StringBuilder();
        for (UiHotfixProcessTarget target : targets) {
            value.append(target.processVersionHistoryId()).append('|')
                    .append(target.deploymentId()).append('|')
                    .append(target.pinnedReleaseId()).append('|')
                    .append(target.pinnedReleaseVersion()).append('|')
                    .append(String.join(",", target.nodeIds())).append('|')
                    .append(target.currentStartable()).append('|')
                    .append(target.activeInstanceCount()).append('|')
                    .append(target.completedInstanceCount()).append(';');
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.toString().getBytes(
                                    StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("热修复目标集合哈希计算失败", exception);
        }
    }

    /**
     * 封装运行时{@code counts}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param active 活动，保存在对象中供后续校验、查询或展示
     * @param completed {@code completed}，保存在对象中供后续校验、查询或展示
     */
    private record RuntimeCounts(long active, long completed) {
    }
}
