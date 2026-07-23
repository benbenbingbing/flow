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
 * 流程抄送/知会服务
 *
 * 负责流程审批过程中的抄送（知会）记录管理，包括抄送记录的创建、查询、
 * 已读标记与数量统计。抄送记录用于在流程流转到特定节点时通知相关用户关注，
 * 接收人可在"抄送我的"列表中查看，未读数量会展示在页签徽标上。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessCcService {
    
    private final ProcessCcRecordMapper ccRecordMapper;
    
    /**
     * 创建抄送记录（单条）。
     *
     * <p>新建的抄送记录默认为"未读"状态，并记录创建时间。
     *
     * @param record 抄送记录信息（含流程实例ID、抄送用户ID等）
     * @return 已持久化的抄送记录
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
     * 批量创建抄送记录。
     *
     * <p>用于一次性给多个用户发送知会，内部循环调用 {@link #createCcRecord}。
     *
     * @param records 抄送记录列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchCreateCcRecords(List<ProcessCcRecord> records) {
        records.forEach(this::createCcRecord);
    }
    
    /**
     * 获取用户的抄送列表（分页）。
     *
     * <p>用于"抄送我的"列表展示，按分页参数返回当前用户收到的抄送记录。
     *
     * @param userId  抄送接收用户ID
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页条数
     * @return 抄送记录列表
     */
    public List<ProcessCcRecord> getUserCcList(String userId, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        return ccRecordMapper.findByCcUserId(userId, offset, pageSize);
    }
    
    /**
     * 获取指定流程的抄送记录。
     *
     * <p>用于查看某个流程实例向哪些用户发送过知会。
     *
     * @param processInstanceId 流程实例ID
     * @return 该流程的抄送记录列表
     */
    public List<ProcessCcRecord> getProcessCcRecords(String processInstanceId) {
        return ccRecordMapper.findByProcessInstanceId(processInstanceId);
    }
    
    /**
     * 标记单条抄送记录为已读。
     *
     * <p>仅允许抄送接收人本人标记，若记录不存在或不属于该用户则抛出权限异常。
     * 标记后该条记录不再计入"抄送我的"页签的未读数量。
     *
     * @param ccId   抄送记录ID
     * @param userId 当前用户ID（用于权限校验）
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(String ccId, String userId) {
        if (ccRecordMapper.markAsRead(ccId, userId) == 0) {
            throw new com.workflow.common.ForbiddenException("无权读取该知会记录");
        }
        log.info("抄送记录标记为已读: {}", ccId);
    }
    
    /**
     * 将当前用户的所有抄送记录标记为已读。
     *
     * <p>用于"全部已读"操作，执行后该用户的未读抄送数归零。
     *
     * @param userId 当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(String userId) {
        ccRecordMapper.markAllAsRead(userId);
        log.info("用户 {} 的所有抄送标记为已读", userId);
    }
    
    /**
     * 统计用户的抄送记录总数（含已读与未读）。
     *
     * @param userId 用户ID
     * @return 抄送记录总数
     */
    public long countUserCc(String userId) {
        return ccRecordMapper.countByCcUserId(userId);
    }
    
    /**
     * 统计用户的未读抄送数。
     *
     * <p>仅统计状态为"未读"的抄送记录，用于首页"抄送我的"页签徽标显示数量。
     * 用户标记已读后该数量会相应减少。
     *
     * @param userId 用户ID
     * @return 未读抄送记录数
     */
    public long countUnreadCc(String userId) {
        return ccRecordMapper.countUnreadByUserId(userId);
    }
}
