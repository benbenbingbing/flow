package com.workflow.process.publish.application;

import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.publish.infrastructure.persistence.record.ProcessUiReleaseBinding;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.publish.infrastructure.persistence.mapper.ProcessUiReleaseBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 流程发布只索引节点引用的稳定表单身份，供删除保护和依赖查询使用。 */
@Service
@RequiredArgsConstructor
public class ProcessUiReleaseBindingService {
    private final ProcessUiReleaseBindingMapper bindingMapper;

    /**
     * 原子替换流程版本的表单身份索引。表单内容由运行时读取当前发布配置；
     * 不保存内容坐标、不展开子表单，子表单删除保护由当前父配置负责。
     * @param history 已有 ID 的流程发布历史；为空时拒绝写入
     * @param nodeForms 发布快照中的节点绑定；空表单和重复节点身份跳过
     * @return 写入的稳定身份绑定数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int replaceBindings(ProcessVersionHistory history, List<ProcessNodeForm> nodeForms) {
        if (history == null || !StringUtils.hasText(history.getId()))
            throw new IllegalArgumentException("流程发布历史不能为空");
        Map<String, ProcessUiReleaseBinding> bindings = new LinkedHashMap<>();
        for (ProcessNodeForm form : nodeForms == null ? List.<ProcessNodeForm>of() : nodeForms) {
            if (!StringUtils.hasText(form.getFormId())) continue;
            ProcessUiReleaseBinding binding = new ProcessUiReleaseBinding();
            binding.setProcessVersionHistoryId(history.getId());
            binding.setProcessConfigId(history.getProcessConfigId());
            binding.setProcessKey(history.getProcessKey());
            binding.setProcessVersion(history.getVersion());
            binding.setDeploymentId(history.getDeploymentId());
            binding.setNodeId(form.getNodeId());
            binding.setNodeName(form.getNodeName());
            binding.setConfigType("FORM");
            binding.setConfigId(form.getFormId());
            // 旧表结构仍要求非空，此处是退役列的兼容占位，不参与配置选择。
            binding.setPinnedReleaseId("");
            binding.setPinnedReleaseVersion(0);
            binding.setCreateTime(LocalDateTime.now());
            bindings.put(form.getNodeId() + "|" + form.getFormId(), binding);
        }
        bindingMapper.deleteByHistoryId(history.getId());
        bindings.values().forEach(bindingMapper::insert);
        return bindings.size();
    }
}
