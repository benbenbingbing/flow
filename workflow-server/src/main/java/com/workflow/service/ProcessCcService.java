package com.workflow.service;

import com.workflow.entity.ProcessCcRecord;
import com.workflow.mapper.ProcessCcRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程抄送服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessCcService {
    
    private final ProcessCcRecordMapper ccRecordMapper;
    
    /**
     * 创建抄送记录
     */
    @Transactional(rollbackFor = Exception.class)
    public ProcessCcRecord createCcRecord(ProcessCcRecord record) {
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setReadStatus("UNREAD");
        record.setDeleted(0);
        
        ccRecordMapper.insert(record);
        log.info("创建抄送记录: processInstanceId={}, ccUserId={}", 
                record.getProcessInstanceId(), record.getCcUserId());
        return record;
    }
    
    /**
     * 批量创建抄送记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchCreateCcRecords(List<ProcessCcRecord> records) {
        records.forEach(this::createCcRecord);
    }
    
    /**
     * 获取用户的抄送列表
     */
    public List<ProcessCcRecord> getUserCcList(String userId, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        return ccRecordMapper.findByCcUserId(userId, offset, pageSize);
    }
    
    /**
     * 获取流程的抄送记录
     */
    public List<ProcessCcRecord> getProcessCcRecords(String processInstanceId) {
        return ccRecordMapper.findByProcessInstanceId(processInstanceId);
    }
    
    /**
     * 标记抄送为已读
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(String ccId) {
        ccRecordMapper.markAsRead(ccId);
        log.info("抄送记录标记为已读: {}", ccId);
    }
    
    /**
     * 批量标记为已读
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(String userId) {
        ccRecordMapper.markAllAsRead(userId);
        log.info("用户 {} 的所有抄送标记为已读", userId);
    }
    
    /**
     * 统计用户抄送数
     */
    public long countUserCc(String userId) {
        return ccRecordMapper.countByCcUserId(userId);
    }
    
    /**
     * 统计用户未读抄送数
     */
    public long countUnreadCc(String userId) {
        return ccRecordMapper.countUnreadByUserId(userId);
    }
}
