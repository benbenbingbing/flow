package com.workflow.contracts.identity.port;

import com.workflow.contracts.identity.CurrentActor;

/**
 * 获取当前操作人，隔离具体认证上下文实现的稳定端口。
 */
public interface CurrentActorPort {

    /**
     * 返回当前请求绑定的操作人。
     *
     * @return 当前操作人
     */
    CurrentActor current();
}
