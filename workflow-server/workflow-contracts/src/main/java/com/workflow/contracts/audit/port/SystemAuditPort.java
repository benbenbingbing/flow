package com.workflow.contracts.audit.port;

import com.workflow.contracts.audit.SystemAuditEvent;

/**
 * 系统审计写入端口。
 *
 * <p>业务模块通过此端口提交稳定审计事件，具体持久化和异步投递策略由实现方决定。</p>
 */
public interface SystemAuditPort {

    /**
     * 记录一个系统审计事件。
     *
     * @param event 已构造的稳定审计事件
     */
    void record(SystemAuditEvent event);
}
