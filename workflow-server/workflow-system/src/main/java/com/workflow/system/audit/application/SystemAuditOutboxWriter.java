package com.workflow.system.audit.application;

import com.workflow.system.audit.domain.SystemAuditOutbox;
import com.workflow.system.audit.infrastructure.SystemAuditOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 使用独立事务写入普通操作的审计 Outbox。
 */
@Service
@RequiredArgsConstructor
public class SystemAuditOutboxWriter {

    private final SystemAuditOutboxMapper outboxMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(String eventId, String payloadJson) {
        try {
            outboxMapper.insert(newOutbox(eventId, payloadJson));
        } catch (DuplicateKeyException ignored) {
            // event_id 唯一约束保证重试入队幂等。
        }
    }

    static SystemAuditOutbox newOutbox(String eventId, String payloadJson) {
        LocalDateTime now = LocalDateTime.now();
        SystemAuditOutbox outbox = new SystemAuditOutbox();
        outbox.setEventId(eventId);
        outbox.setPayloadJson(payloadJson);
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        return outbox;
    }
}
