package com.workflow.contracts.audit;

/**
 * 系统审计写入端口。
 *
 * <p>业务模块只依赖该端口，不依赖审计表、Mapper 或 Outbox 实现。</p>
 */
public interface SystemAuditPort {

    void record(SystemAuditEvent event);
}
