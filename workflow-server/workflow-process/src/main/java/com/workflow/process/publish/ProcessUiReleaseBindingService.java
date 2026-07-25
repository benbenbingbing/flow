package com.workflow.process.publish;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.ui.runtime.UiPublishedFormReference;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.entity.ProcessUiReleaseBinding;
import com.workflow.entity.ProcessVersionHistory;
import com.workflow.mapper.ProcessUiReleaseBindingMapper;
import com.workflow.service.UiConfigReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 流程发布版本与 UI 发布版本规范化绑定服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessUiReleaseBindingService {

    private final ProcessUiReleaseBindingMapper bindingMapper;
    private final ObjectMapper objectMapper;
    private final UiConfigReleaseService releaseService;

    /**
     * 使用流程发布快照替换该流程版本的全部 UI 绑定。
     */
    @Transactional(rollbackFor = Exception.class)
    public int replaceBindings(
            ProcessVersionHistory history,
            List<ProcessNodeForm> nodeForms) {
        if (history == null || !StringUtils.hasText(history.getId())) {
            throw new IllegalArgumentException("流程发布历史不能为空");
        }
        bindingMapper.deleteByHistoryId(history.getId());
        int inserted = 0;
        for (ProcessUiReleaseBinding binding
                : expectedBindings(history, nodeForms)) {
            bindingMapper.insert(binding);
            inserted++;
        }
        return inserted;
    }

    /**
     * 幂等回填一条历史发布记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public BackfillResult backfill(ProcessVersionHistory history) {
        if (history == null || !StringUtils.hasText(history.getId())) {
            return BackfillResult.empty();
        }
        String snapshot = history.getNodeFormsSnapshot();
        if (!StringUtils.hasText(snapshot)) {
            return BackfillResult.empty();
        }
        try {
            List<ProcessNodeForm> nodeForms = objectMapper.readValue(
                    snapshot,
                    new TypeReference<List<ProcessNodeForm>>() {});
            int missingRelease = (int) nodeForms.stream()
                    .filter(item -> StringUtils.hasText(item.getFormId()))
                    .filter(item -> !StringUtils.hasText(item.getFormReleaseId())
                            || item.getFormReleaseVersion() == null)
                    .count();
            Map<String, ProcessUiReleaseBinding> existing =
                    new LinkedHashMap<>();
            for (ProcessUiReleaseBinding binding
                    : bindingMapper.findByHistoryId(history.getId())) {
                existing.put(bindingKey(
                        binding.getNodeId(),
                        binding.getConfigId()), binding);
            }
            List<ProcessUiReleaseBinding> expectedBindings =
                    expectedBindings(history, nodeForms);
            int inserted = 0;
            int updated = 0;
            for (ProcessUiReleaseBinding value
                    : expectedBindings) {
                ProcessUiReleaseBinding current = existing.get(
                        bindingKey(
                                value.getNodeId(),
                                value.getConfigId()));
                if (current == null) {
                    bindingMapper.insert(value);
                    inserted++;
                    continue;
                }
                if (!sameBinding(current, value)) {
                    value.setId(current.getId());
                    value.setCreateTime(current.getCreateTime());
                    bindingMapper.updateById(value);
                    updated++;
                }
            }
            boolean skipped = inserted == 0
                    && updated == 0
                    && existing.size() >= expectedBindings.size();
            return new BackfillResult(
                    inserted,
                    updated,
                    missingRelease,
                    0,
                    skipped);
        } catch (Exception exception) {
            log.warn(
                    "流程UI发布绑定回填失败: historyId={}, processKey={}, error={}",
                    history.getId(),
                    history.getProcessKey(),
                    exception.getMessage());
            return new BackfillResult(0, 0, 0, 1, false);
        }
    }

    public record BackfillResult(
            int inserted,
            int updated,
            int missingRelease,
            int invalidSnapshot,
            boolean skippedExisting) {

        static BackfillResult empty() {
            return new BackfillResult(0, 0, 0, 0, false);
        }
    }

    private ProcessUiReleaseBinding buildBinding(
            ProcessVersionHistory history,
            ProcessNodeForm nodeForm) {
        return buildBinding(
                history,
                nodeForm.getNodeId(),
                nodeForm.getNodeName(),
                nodeForm.getFormId(),
                nodeForm.getFormReleaseId(),
                nodeForm.getFormReleaseVersion());
    }

    private ProcessUiReleaseBinding buildBinding(
            ProcessVersionHistory history,
            String nodeId,
            String nodeName,
            String formId,
            String releaseId,
            Integer releaseVersion) {
        ProcessUiReleaseBinding binding =
                new ProcessUiReleaseBinding();
        binding.setProcessVersionHistoryId(history.getId());
        binding.setProcessConfigId(history.getProcessConfigId());
        binding.setProcessKey(history.getProcessKey());
        binding.setProcessVersion(history.getVersion());
        binding.setDeploymentId(history.getDeploymentId());
        binding.setNodeId(nodeId);
        binding.setNodeName(nodeName);
        binding.setConfigType("FORM");
        binding.setConfigId(formId);
        binding.setPinnedReleaseId(releaseId);
        binding.setPinnedReleaseVersion(releaseVersion);
        binding.setCreateTime(LocalDateTime.now());
        return binding;
    }

    private List<ProcessUiReleaseBinding> expectedBindings(
            ProcessVersionHistory history,
            List<ProcessNodeForm> nodeForms) {
        Map<String, ProcessUiReleaseBinding> expected =
                new LinkedHashMap<>();
        for (ProcessNodeForm nodeForm : nodeForms == null
                ? List.<ProcessNodeForm>of()
                : nodeForms) {
            if (!StringUtils.hasText(nodeForm.getFormId())
                    || !StringUtils.hasText(
                            nodeForm.getFormReleaseId())
                    || nodeForm.getFormReleaseVersion() == null) {
                continue;
            }
            ProcessUiReleaseBinding root =
                    buildBinding(history, nodeForm);
            expected.put(
                    bindingKey(
                            root.getNodeId(),
                            root.getConfigId()),
                    root);
            expandChildBindings(
                    history,
                    nodeForm,
                    nodeForm.getFormId(),
                    nodeForm.getFormReleaseId(),
                    nodeForm.getFormReleaseVersion(),
                    1,
                    new HashSet<>(),
                    expected);
        }
        return new ArrayList<>(expected.values());
    }

    private void expandChildBindings(
            ProcessVersionHistory history,
            ProcessNodeForm root,
            String formId,
            String releaseId,
            Integer releaseVersion,
            int depth,
            Set<String> path,
            Map<String, ProcessUiReleaseBinding> expected) {
        if (depth > 8 || !path.add(releaseId)) {
            return;
        }
        try {
            for (UiPublishedFormReference reference
                    : releaseService.childFormReferences(
                            formId,
                            releaseId,
                            releaseVersion)) {
                ProcessUiReleaseBinding binding = buildBinding(
                        history,
                        nestedNodeId(
                                root.getNodeId(),
                                depth,
                                reference),
                        root.getNodeName() + " / 子表单",
                        reference.formId(),
                        reference.releaseId(),
                        reference.releaseVersion());
                expected.put(
                        bindingKey(
                                binding.getNodeId(),
                                binding.getConfigId()),
                        binding);
                expandChildBindings(
                        history,
                        root,
                        reference.formId(),
                        reference.releaseId(),
                        reference.releaseVersion(),
                        depth + 1,
                        new HashSet<>(path),
                        expected);
            }
        } finally {
            path.remove(releaseId);
        }
    }

    private String nestedNodeId(
            String rootNodeId,
            int depth,
            UiPublishedFormReference reference) {
        String seed = String.join(
                "|",
                String.valueOf(rootNodeId),
                String.valueOf(depth),
                reference.formId(),
                reference.releaseId(),
                String.valueOf(reference.releaseVersion()));
        String hash;
        try {
            hash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(seed.getBytes(
                                    StandardCharsets.UTF_8)))
                    .substring(0, 16);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "子表单流程绑定标识生成失败",
                    exception);
        }
        String root = String.valueOf(rootNodeId);
        if (root.length() > 70) {
            root = root.substring(0, 70);
        }
        return root + "#sub:" + hash;
    }

    private boolean sameBinding(
            ProcessUiReleaseBinding current,
            ProcessUiReleaseBinding expected) {
        return Objects.equals(
                        current.getProcessConfigId(),
                        expected.getProcessConfigId())
                && Objects.equals(
                        current.getProcessKey(),
                        expected.getProcessKey())
                && Objects.equals(
                        current.getProcessVersion(),
                        expected.getProcessVersion())
                && Objects.equals(
                        current.getDeploymentId(),
                        expected.getDeploymentId())
                && Objects.equals(
                        current.getNodeName(),
                        expected.getNodeName())
                && Objects.equals(
                        current.getPinnedReleaseId(),
                        expected.getPinnedReleaseId())
                && Objects.equals(
                        current.getPinnedReleaseVersion(),
                        expected.getPinnedReleaseVersion());
    }

    private String bindingKey(String nodeId, String configId) {
        return String.valueOf(nodeId) + "|" + String.valueOf(configId);
    }
}
