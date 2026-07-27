/**
 * 数据库事务 Outbox 基础设施。
 *
 * <p>业务模块通过 {@code com.workflow.outbox.api} 发布事件并注册处理器，
 * 本模块统一负责持久化、并发认领、重试、死信、超时恢复和保留期清理。</p>
 */
package com.workflow.outbox;
