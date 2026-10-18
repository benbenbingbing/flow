package com.workflow.entity.history;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.workflow.core.error.BusinessConflictException;

/** 归档专属实体在服务端禁止变更；共享业务实体不因历史引用而锁定。 */
@Component
@RequiredArgsConstructor
public class EntityHistoryWriteGuard implements com.workflow.contracts.history.EntityHistoryWritePolicyPort {
    private final com.workflow.entity.history.infrastructure.persistence.EntityHistorySourceMapper sources;
    @Override
    public void requireWritable(String entityCode, String recordId) {
        if (recordId != null && sources.readonlyCount(entityCode, recordId) > 0)
            throw new BusinessConflictException("HISTORICAL_RECORD_READONLY", "历史迁入记录只允许查看，请复制后开展新业务");
    }
}
