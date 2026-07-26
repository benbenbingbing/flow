package com.workflow.contracts.identity;

/**
 * 为业务模块提供当前操作人，隔离具体认证上下文实现。
 */
public interface CurrentActorProvider {

    CurrentActor current();
}
