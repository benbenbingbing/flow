package com.workflow.process.publish.application;

import com.workflow.contracts.entity.ui.model.UiPublishedFormReference;
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
     *
     * @param history 历史，作为 {@code bindingMapper.deleteByHistoryId} 的输入影响后续处理
     * @param nodeForms 节点表单集合，供本方法处理替换绑定集合时使用
     * @return 处理后的替换绑定集合结果，供调用方继续处理
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
     * 构建绑定；结果供后续流程传递或持久化。
     *
     * @param history 历史，供本方法构建绑定时使用
     * @param nodeForm 节点表单，供本方法构建绑定时使用
     * @return 构建后的绑定结果，供调用方继续处理
     */
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

    /**
     * 构建绑定；结果供后续流程传递或持久化。
     *
     * @param history 历史，作为 {@code binding.setProcessVersionHistoryId} 的输入影响后续处理
     * @param nodeId 节点ID，后续用于构建绑定时定位或关联目标
     * @param nodeName 节点名称，后续用于构建绑定时匹配或展示
     * @param formId 表单ID，后续用于构建绑定时定位或关联目标
     * @param releaseId 发布版本ID，后续用于构建绑定时定位或关联目标
     * @param releaseVersion 发布版本，作为 {@code binding.setPinnedReleaseVersion} 的输入影响后续处理
     * @return 构建后的绑定结果，供调用方继续处理
     */
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

    /**
     * 整理预期绑定集合数据，供调用方遍历或继续处理。
     *
     * @param history 历史，作为 {@code buildBinding} 的输入影响后续处理
     * @param nodeForms 节点表单集合，供本方法处理预期绑定集合时使用
     * @return 流程界面发布版本绑定集合，供调用方遍历或展示
     */
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

    /**
     * 处理{@code expand}子级绑定集合，并将结果传给后续步骤。
     *
     * @param history 历史，作为 {@code buildBinding} 的输入影响后续处理
     * @param root 根，作为 {@code buildBinding} 的输入影响后续处理
     * @param formId 表单ID，后续用于处理{@code expand}子级绑定集合时定位或关联目标
     * @param releaseId 发布版本ID，后续用于处理{@code expand}子级绑定集合时定位或关联目标
     * @param releaseVersion 发布版本，供本方法处理{@code expand}子级绑定集合时使用
     * @param depth 深度，供本方法处理{@code expand}子级绑定集合时使用
     * @param path 路径，供本方法处理{@code expand}子级绑定集合时使用
     * @param expected 预期，供本方法处理{@code expand}子级绑定集合时使用
     */
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

    /**
     * 生成{@code nested}节点ID文本，供后续匹配或展示。
     *
     * @param rootNodeId 根节点ID，后续用于处理{@code nested}节点ID时定位或关联目标
     * @param depth 深度，供本方法处理{@code nested}节点ID时使用
     * @param reference 引用，供本方法处理{@code nested}节点ID时使用
     * @return 处理后的{@code nested}节点ID文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 生成绑定键文本，供后续匹配或展示。
     *
     * @param nodeId 节点ID，后续用于处理绑定键时定位或关联目标
     * @param configId 配置ID，后续用于处理绑定键时定位或关联目标
     * @return 处理后的绑定键文本，供调用方比较或展示
     */
    private String bindingKey(String nodeId, String configId) {
        return String.valueOf(nodeId) + "|" + String.valueOf(configId);
    }
}
