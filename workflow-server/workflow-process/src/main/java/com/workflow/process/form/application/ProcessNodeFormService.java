package com.workflow.process.form.application;

import com.workflow.core.logging.LogValue;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.process.form.infrastructure.persistence.mapper.ProcessNodeFormMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程节点表单绑定服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessNodeFormService {
    
    private final ProcessNodeFormMapper nodeFormMapper;
    private final EntityFormMapper formMapper;
    
    /**
     * 查询流程的节点表单绑定
     */
    public List<ProcessNodeForm> getByProcessConfigId(String processConfigId) {
        List<ProcessNodeForm> list = nodeFormMapper.selectByProcessConfigId(processConfigId);
        list.forEach(this::fillFormInfo);
        return list;
    }
    
    /**
     * 查询节点的表单绑定
     */
    public ProcessNodeForm getByNodeId(String processConfigId, String nodeId) {
        ProcessNodeForm nodeForm = nodeFormMapper.selectByNodeId(processConfigId, nodeId);
        if (nodeForm != null) {
            fillFormInfo(nodeForm);
        }
        return nodeForm;
    }

    /**
     * 查询节点表单绑定列表。
     *
     * <p>返回列表是为兼容已有接口，当前业务规则下最多返回一条。</p>
     */
    public List<ProcessNodeForm> getListByNodeId(String processConfigId, String nodeId) {
        ProcessNodeForm nodeForm = getByNodeId(processConfigId, nodeId);
        return nodeForm == null ? List.of() : List.of(nodeForm);
    }
    
    /**
     * 保存节点表单绑定
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.CONFIGURE,
            operation = "保存流程节点表单",
            risk = AuditRiskLevel.HIGH,
            targetType = "PROCESS_NODE_FORM",
            captureArguments = true,
            captureResult = true)
    public ProcessNodeForm saveNodeForm(ProcessNodeForm nodeForm) {
        nodeForm.setUpdateTime(LocalDateTime.now());
        
        // 检查是否已存在
        ProcessNodeForm existing = nodeFormMapper.selectByNodeId(
            nodeForm.getProcessConfigId(), 
            nodeForm.getNodeId()
        );
        
        if (existing != null) {
            // 更新
            nodeForm.setId(existing.getId());
            nodeFormMapper.updateById(nodeForm);
            log.info("更新节点表单绑定：process={}, node={}", 
                LogValue.safe(nodeForm.getProcessConfigId()), LogValue.safe(nodeForm.getNodeId()));
        } else {
            // 新增
            nodeForm.setCreateTime(LocalDateTime.now());
            nodeFormMapper.insert(nodeForm);
            log.info("新增节点表单绑定：process={}, node={}", 
                LogValue.safe(nodeForm.getProcessConfigId()), LogValue.safe(nodeForm.getNodeId()));
        }
        
        return nodeForm;
    }
    
    /**
     * 删除节点表单绑定
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.DELETE,
            operation = "删除流程节点表单",
            risk = AuditRiskLevel.HIGH,
            targetType = "PROCESS_NODE_FORM",
            targetIdArg = 0)
    public void deleteNodeForm(String id) {
        nodeFormMapper.deleteById(id);
    }
    
    /**
     * 批量保存节点表单绑定
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.CONFIGURE,
            operation = "批量保存流程节点表单",
            risk = AuditRiskLevel.HIGH,
            targetType = "PROCESS_NODE_FORM",
            targetIdArg = 0,
            captureArguments = true)
    public void saveNodeForms(String processConfigId, List<ProcessNodeForm> nodeForms) {
        // 删除原有绑定
        nodeFormMapper.deleteByProcessConfigId(processConfigId);
        
        // 保存新绑定
        if (nodeForms != null && !nodeForms.isEmpty()) {
            Map<String, ProcessNodeForm> bindingByNode = new LinkedHashMap<>();
            for (ProcessNodeForm nodeForm : nodeForms) {
                if (nodeForm != null && nodeForm.getNodeId() != null) {
                    bindingByNode.putIfAbsent(nodeForm.getNodeId(), nodeForm);
                }
            }
            for (ProcessNodeForm nodeForm : bindingByNode.values()) {
                nodeForm.setProcessConfigId(processConfigId);
                nodeForm.setSortOrder(0);
                nodeForm.setCreateTime(LocalDateTime.now());
                nodeForm.setUpdateTime(LocalDateTime.now());
                nodeFormMapper.insert(nodeForm);
            }
        }
    }
    
    /**
     * 填充表单信息
     */
    private void fillFormInfo(ProcessNodeForm nodeForm) {
        if (nodeForm.getFormId() != null) {
            EntityForm form = formMapper.selectById(nodeForm.getFormId());
            nodeForm.setForm(form);
        }
    }
}
