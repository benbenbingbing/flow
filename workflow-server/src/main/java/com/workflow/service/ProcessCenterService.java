package com.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.entity.*;
import com.workflow.mapper.*;
import com.workflow.vo.ProcessStatisticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程中心服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessCenterService {
    
    private final ProcessTaskMapper processTaskMapper;
    private final ProcessCommonOpinionMapper opinionMapper;
    private final ProcessDraftMapper draftMapper;
    private final ProcessOperationLogMapper operationLogMapper;
    
    // ==================== 待办任务 ====================
    
    /**
     * 查询待办任务列表
     */
    public Page<ProcessTask> getTodoList(String userId, String processKey, String keyword, 
                                                   Integer priority, int pageNum, int pageSize) {
        Page<ProcessTask> page = new Page<>(pageNum, pageSize);
        // 使用 ProcessTaskMapper 查询 process_task 表
        List<ProcessTask> list = processTaskMapper.selectTodoByUser(userId);
        page.setRecords(list);
        page.setTotal(list.size());
        return page;
    }
    
    /**
     * 标记任务为已读
     */
    @Transactional
    public void markTaskAsRead(String taskId, String userId) {
        // process_task 表没有 is_read 字段，这里可以扩展或忽略
        log.info("标记任务已读: taskId={}, userId={}", taskId, userId);
    }
    
    // ==================== 已办任务 ====================
    
    /**
     * 查询已办任务列表
     */
    public Page<ProcessTask> getDoneList(String userId, String processKey, String actionType,
                                                  int pageNum, int pageSize) {
        Page<ProcessTask> page = new Page<>(pageNum, pageSize);
        // 使用 ProcessTaskMapper 查询 process_task 表
        List<ProcessTask> list = processTaskMapper.selectDoneByUser(userId);
        page.setRecords(list);
        page.setTotal(list.size());
        return page;
    }
    
    // ==================== 抄送/知会 ====================
    
    /**
     * 查询抄送列表 (process_task 表没有抄送功能，返回空列表)
     */
    public Page<ProcessTask> getCcList(String userId, Boolean isRead, int pageNum, int pageSize) {
        Page<ProcessTask> page = new Page<>(pageNum, pageSize);
        page.setRecords(List.of());
        page.setTotal(0);
        return page;
    }
    
    /**
     * 标记抄送为已读
     */
    @Transactional
    public void markCcAsRead(String id) {
        log.info("标记抄送已读: id={}", id);
    }
    
    // ==================== 常用意见 ====================
    
    /**
     * 获取常用意见列表
     */
    public List<ProcessCommonOpinion> getCommonOpinions(String userId, String opinionType) {
        return opinionMapper.findByUserId(userId, opinionType);
    }
    
    /**
     * 保存常用意见
     */
    @Transactional
    public void saveCommonOpinion(ProcessCommonOpinion opinion) {
        if (opinion.getId() == null) {
            opinion.setUseCount(0);
            opinionMapper.insert(opinion);
        } else {
            opinionMapper.updateById(opinion);
        }
    }
    
    /**
     * 删除常用意见
     */
    @Transactional
    public void deleteCommonOpinion(String id) {
        opinionMapper.deleteById(id);
    }
    
    /**
     * 使用意见（增加使用次数）
     */
    @Transactional
    public void useCommonOpinion(String id) {
        opinionMapper.incrementUseCount(id);
    }
    
    // ==================== 草稿箱 ====================
    
    /**
     * 获取草稿列表
     */
    public Page<ProcessDraft> getDraftList(String userId, String status, int pageNum, int pageSize) {
        Page<ProcessDraft> page = new Page<>(pageNum, pageSize);
        return draftMapper.selectByUserId(page, userId, status);
    }
    
    /**
     * 保存草稿
     */
    @Transactional
    public ProcessDraft saveDraft(ProcessDraft draft) {
        if (draft.getId() == null) {
            draft.setStatus("ACTIVE");
            draft.setCreatedAt(LocalDateTime.now());
            draftMapper.insert(draft);
        } else {
            draft.setUpdatedAt(LocalDateTime.now());
            draftMapper.updateById(draft);
        }
        return draft;
    }
    
    /**
     * 删除草稿
     */
    @Transactional
    public void deleteDraft(String id) {
        ProcessDraft draft = new ProcessDraft();
        draft.setId(id);
        draft.setStatus("DELETED");
        draft.setUpdatedAt(LocalDateTime.now());
        draftMapper.updateById(draft);
    }
    
    /**
     * 提交草稿
     */
    @Transactional
    public void submitDraft(String id) {
        ProcessDraft draft = new ProcessDraft();
        draft.setId(id);
        draft.setStatus("SUBMITTED");
        draft.setUpdatedAt(LocalDateTime.now());
        draftMapper.updateById(draft);
    }
    
    // ==================== 统计 ====================
    
    /**
     * 获取流程中心统计
     */
    public ProcessStatisticsVO getStatistics(String userId) {
        ProcessStatisticsVO vo = new ProcessStatisticsVO();
        vo.setTodoCount(processTaskMapper.countTodoByUser(userId));
        vo.setDoneTodayCount(processTaskMapper.countDoneByUser(userId));
        vo.setUnreadCcCount(0L); // process_task 表没有抄送功能
        return vo;
    }
}
