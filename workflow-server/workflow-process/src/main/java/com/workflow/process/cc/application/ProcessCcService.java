package com.workflow.process.cc.application;

import com.workflow.core.logging.LogValue;
import com.workflow.core.database.JdbcWriteAttempt;
import com.workflow.core.result.PageResult;
import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;
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
    private final ProcessCcSnapshotService snapshotService;
    private final JdbcWriteAttempt writeAttempt;
    
    /**
     * 创建抄送记录（单条）。
     *
     * <p>插入前保存流程及业务数据名称快照；新记录默认为"未读"，并记录创建时间。
     *
     * @param record 抄送记录信息（含流程实例ID、抄送用户ID等）
     * @return 已持久化的抄送记录
     */
    @Transactional(rollbackFor = Exception.class)
    public ProcessCcRecord createCcRecord(ProcessCcRecord record) {
        prepareRecord(record);
        ccRecordMapper.insert(record);
        log.info("创建抄送记录: processInstanceId={}, ccUserId={}",
                record.getProcessInstanceId(), record.getCcUserId());
        return record;
    }

    /**
     * 按非空 uniqueKey 幂等创建；true 表示本次新增，调用方才可发布通知。
     * 重复键必须在本事务代理内部恢复和捕获，避免 REQUIRED 将外层标记为 rollback-only。
     * 只吞掉已存在目标业务键的冲突，其他约束失败继续回滚整笔业务。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean createCcRecordIfAbsent(ProcessCcRecord record) {
        if (record == null || !StringUtils.hasText(record.getUniqueKey())) {
            throw new IllegalArgumentException("幂等抄送必须提供 uniqueKey");
        }
        prepareRecord(record);
        try {
            if (writeAttempt.execute(() -> ccRecordMapper.insert(record)) != 1) {
                throw new IllegalStateException("抄送记录未成功写入");
            }
            return true;
        } catch (DuplicateKeyException conflict) {
            if (ccRecordMapper.findIdByUniqueKeyForReplay(record.getUniqueKey()) == null) throw conflict;
            return false;
        }
    }

    private void prepareRecord(ProcessCcRecord record) {
        // 自动、人工和显式知会共用写入口，名称只在创建时补齐；读列表不回查业务表。
        snapshotService.captureNames(record);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setReadStatus("UNREAD");
        record.setDeleted(0);
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

    public PageResult<ProcessCcRecord> getUserCcPage(
            String userId,
            int requestedPage,
            int requestedSize,
            String keyword,
            String operatorName,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        int pageNum = Math.max(1, requestedPage);
        int pageSize = Math.min(100, Math.max(1, requestedSize));
        int offset = (pageNum - 1) * pageSize;
        List<ProcessCcRecord> records = ccRecordMapper.findByCcUserIdFiltered(
                userId, keyword, operatorName, startTime, endTime, offset, pageSize);
        long total = ccRecordMapper.countByCcUserIdFiltered(
                userId, keyword, operatorName, startTime, endTime);
        return new PageResult<>(records, total, pageNum, pageSize);
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
            throw new com.workflow.core.error.ForbiddenException("无权读取该知会记录");
        }
        log.info("抄送记录标记为已读: {}", LogValue.safe(ccId));
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
