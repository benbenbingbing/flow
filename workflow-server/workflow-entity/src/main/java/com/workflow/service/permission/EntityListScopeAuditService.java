package com.workflow.service.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityListScopeAuditLog;
import com.workflow.mapper.EntityListScopeAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntityListScopeAuditService {

    private final EntityListScopeAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

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
