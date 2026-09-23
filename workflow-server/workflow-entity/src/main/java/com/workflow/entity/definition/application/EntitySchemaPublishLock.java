package com.workflow.entity.definition.application;

import com.workflow.core.logging.LogValue;
import com.workflow.core.database.port.DatabaseLockPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/** 实体发布锁的业务入口；独立会话及厂商锁语法由数据库端口负责。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntitySchemaPublishLock {
    private final DatabaseLockPort locks;
    private final ThreadLocal<Map<String, DatabaseLockPort.Handle>> held = new ThreadLocal<>();

    /** 非阻塞获取；相同线程重复发布同一实体返回忙，防止覆盖句柄后泄露锁。 */
    public boolean tryAcquire(String entityId) {
        Map<String, DatabaseLockPort.Handle> handles = held.get();
        if (handles != null && handles.containsKey(entityId)) return false;
        var acquired = locks.tryAcquire("flow:entity", entityId);
        if (acquired.isEmpty()) return false;
        if (handles == null) {
            handles = new HashMap<>();
            held.set(handles);
        }
        handles.put(entityId, acquired.get());
        return true;
    }

    /** 只释放当前线程持有的指定实体句柄；释放异常不覆盖原始发布结果。 */
    public void release(String entityId) {
        var handles = held.get();
        if (handles == null) return;
        var handle = handles.remove(entityId);
        if (handles.isEmpty()) held.remove();
        if (handle == null) return;
        try { handle.close(); }
        catch (RuntimeException exception) {
            log.warn("释放实体结构发布锁失败: entityId={}, type={}", LogValue.safe(entityId), LogValue.failureType(exception));
        }
    }
}
