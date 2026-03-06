package com.workflow.service;

import com.workflow.entity.FlowAction;
import com.workflow.mapper.FlowActionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程动作服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowActionService {
    
    private final FlowActionMapper flowActionMapper;
    
    /**
     * 查询流程配置下所有草稿动作
     */
    public List<FlowAction> findDraftActions(String processConfigId) {
        return flowActionMapper.findDraftActionsByProcessConfigId(processConfigId);
    }
    
    /**
     * 查询顺序流下所有草稿动作（按排序）
     */
    public List<FlowAction> findDraftActionsBySequenceFlow(String processConfigId, String sequenceFlowId) {
        return flowActionMapper.findDraftActionsBySequenceFlowId(processConfigId, sequenceFlowId);
    }
    
    /**
     * 保存动作（草稿状态）
     */
    @Transactional
    public FlowAction saveAction(FlowAction action) {
        action.setStatus(FlowAction.Status.DRAFT.name());
        action.setUpdatedAt(LocalDateTime.now());
        
        if (action.getId() == null) {
            action.setCreatedAt(LocalDateTime.now());
            flowActionMapper.insert(action);
        } else {
            flowActionMapper.updateById(action);
        }
        return action;
    }
    
    /**
     * 删除动作（仅可删除草稿状态）
     */
    @Transactional
    public void deleteAction(String actionId) {
        FlowAction action = flowActionMapper.selectById(actionId);
        if (action != null && FlowAction.Status.DRAFT.name().equals(action.getStatus())) {
            flowActionMapper.logicDeleteById(actionId);
        } else {
            throw new RuntimeException("只能删除草稿状态的动作");
        }
    }
    
    /**
     * 发布动作 - 将当前草稿动作复制到版本
     * 这是关键点：发布时复制，草稿和已发布分离
     */
    @Transactional
    public void publishActions(String processConfigId, String versionId) {
        // 1. 查询所有草稿动作
        List<FlowAction> draftActions = flowActionMapper.findDraftActionsByProcessConfigId(processConfigId);
        
        if (draftActions.isEmpty()) {
            log.info("流程 {} 没有草稿动作需要发布", processConfigId);
            return;
        }
        
        // 2. 复制每个动作到新版本，状态改为已发布
        for (FlowAction draft : draftActions) {
            FlowAction published = new FlowAction();
            published.setProcessConfigId(draft.getProcessConfigId());
            published.setSequenceFlowId(draft.getSequenceFlowId());
            published.setActionName(draft.getActionName());
            published.setDescription(draft.getDescription());
            published.setInterfaceName(draft.getInterfaceName());
            published.setMethodName(draft.getMethodName());
            published.setParamsJson(draft.getParamsJson());
            published.setSortOrder(draft.getSortOrder());
            published.setEnabled(draft.getEnabled());
            published.setStatus(FlowAction.Status.PUBLISHED.name());
            published.setVersionId(versionId);
            published.setCreatedAt(LocalDateTime.now());
            published.setUpdatedAt(LocalDateTime.now());
            published.setCreatedBy(draft.getCreatedBy());
            
            flowActionMapper.insert(published);
        }
        
        log.info("流程 {} 发布了 {} 个动作到版本 {}", processConfigId, draftActions.size(), versionId);
    }
    
    /**
     * 查询版本下所有已发布动作
     */
    public List<FlowAction> findPublishedActions(String versionId) {
        return flowActionMapper.findPublishedActionsByVersionId(versionId);
    }
    
    /**
     * 查询版本下特定顺序流的动作
     */
    public List<FlowAction> findPublishedActionsBySequenceFlow(String versionId, String sequenceFlowId) {
        return flowActionMapper.findPublishedActionsBySequenceFlowId(versionId, sequenceFlowId);
    }
    
    /**
     * 更新动作排序
     */
    @Transactional
    public void updateSortOrder(List<String> actionIds) {
        for (int i = 0; i < actionIds.size(); i++) {
            FlowAction action = new FlowAction();
            action.setId(actionIds.get(i));
            action.setSortOrder(i);
            action.setUpdatedAt(LocalDateTime.now());
            flowActionMapper.updateById(action);
        }
    }
    
    /**
     * 切换动作启用状态
     */
    @Transactional
    public void toggleEnabled(String actionId) {
        FlowAction action = flowActionMapper.selectById(actionId);
        if (action == null) {
            throw new RuntimeException("动作不存在");
        }
        
        // 只能修改草稿状态的动作
        if (!FlowAction.Status.DRAFT.name().equals(action.getStatus())) {
            throw new RuntimeException("只能修改草稿状态的动作");
        }
        
        action.setEnabled(!action.getEnabled());
        action.setUpdatedAt(LocalDateTime.now());
        flowActionMapper.updateById(action);
    }
    
    /**
     * 根据版本ID删除动作
     */
    @Transactional
    public void deleteActionsByVersionId(String versionId) {
        // 逻辑删除该版本的所有动作
        flowActionMapper.logicDeleteByVersionId(versionId);
        log.info("Logic deleted actions for version {}", versionId);
    }
}
