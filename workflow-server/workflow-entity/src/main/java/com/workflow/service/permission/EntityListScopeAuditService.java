package com.workflow.service.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityListScopeAuditLog;
import com.workflow.mapper.EntityListScopeAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 实体列表数据范围审计日志服务。
 *
 * <p>负责持久化数据范围相关的操作审计记录，例如方案保存、发布、回滚、绕过等，
 * 记录失败时仅打印告警日志而不中断主流程。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityListScopeAuditService {

    private final EntityListScopeAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 记录一条数据范围审计日志。
     *
     * @param entityCode 实体编码
     * @param listKey    列表编码，实体级操作可传 null
     * @param userId     操作人用户ID
     * @param operation  操作类型，如 SAVE、PUBLISH、ROLLBACK、BYPASS
     * @param result     操作结果，如 SUCCESS、FAILURE
     * @param detail     附加详情，将序列化为 JSON 存储，可为 null
     */
    public void record(
            String entityCode,
            String listKey,
            String userId,
            String operation,
            String result,
            Object detail) {
        try {
            EntityListScopeAuditLog logEntry = new EntityListScopeAuditLog();
            logEntry.setEntityCode(entityCode);
            logEntry.setListKey(listKey);
            logEntry.setUserId(userId);
            logEntry.setOperation(operation);
            logEntry.setResult(result);
            logEntry.setDetailJson(objectMapper.writeValueAsString(
                    detail == null ? Map.of() : detail));
            logEntry.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(logEntry);
        } catch (Exception exception) {
            log.warn("记录实体列表数据范围审计日志失败: entityCode={}, operation={}",
                    entityCode, operation, exception);
        }
    }
}
