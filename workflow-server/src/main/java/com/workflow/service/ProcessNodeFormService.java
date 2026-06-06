package com.workflow.service;

import com.workflow.entity.EntityForm;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.ProcessNodeFormMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
     * 查询节点的全部表单绑定
     */
    public List<ProcessNodeForm> getListByNodeId(String processConfigId, String nodeId) {
        List<ProcessNodeForm> list = nodeFormMapper.selectListByNodeId(processConfigId, nodeId);
        list.forEach(this::fillFormInfo);
        return list;
    }
    
    /**
     * 保存节点表单绑定
     */
    @Transactional(rollbackFor = Exception.class)
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
                nodeForm.getProcessConfigId(), nodeForm.getNodeId());
        } else {
            // 新增
            nodeForm.setCreateTime(LocalDateTime.now());
            nodeFormMapper.insert(nodeForm);
            log.info("新增节点表单绑定：process={}, node={}", 
                nodeForm.getProcessConfigId(), nodeForm.getNodeId());
        }
        
        return nodeForm;
    }
    
    /**
     * 删除节点表单绑定
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteNodeForm(String id) {
        nodeFormMapper.deleteById(id);
    }
    
    /**
     * 批量保存节点表单绑定
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveNodeForms(String processConfigId, List<ProcessNodeForm> nodeForms) {
        // 删除原有绑定
        nodeFormMapper.deleteByProcessConfigId(processConfigId);
        
        // 保存新绑定
        if (nodeForms != null && !nodeForms.isEmpty()) {
            for (int i = 0; i < nodeForms.size(); i++) {
                ProcessNodeForm nodeForm = nodeForms.get(i);
                nodeForm.setProcessConfigId(processConfigId);
                if (nodeForm.getSortOrder() == null) {
                    nodeForm.setSortOrder(i);
                }
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
