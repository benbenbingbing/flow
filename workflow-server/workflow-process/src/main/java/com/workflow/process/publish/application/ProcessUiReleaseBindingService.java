package com.workflow.process.publish.application;

import com.workflow.contracts.ui.runtime.UiPublishedFormReference;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.publish.infrastructure.persistence.record.ProcessUiReleaseBinding;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.publish.infrastructure.persistence.mapper.ProcessUiReleaseBindingMapper;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import lombok.RequiredArgsConstructor;
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
import java.util.Set;

/**
 * 流程发布版本与 UI 发布版本规范化绑定服务。
 */
@Service
@RequiredArgsConstructor
public class ProcessUiReleaseBindingService {

    private final ProcessUiReleaseBindingMapper bindingMapper;
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

    private String bindingKey(String nodeId, String configId) {
        return String.valueOf(nodeId) + "|" + String.valueOf(configId);
    }
}
