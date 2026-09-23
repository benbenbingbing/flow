package com.workflow.entity.data.infrastructure.schema;

import java.util.Optional;

/** 专用结构发布队列；入队独立提交，不能参与等待其完成的业务事务。 */
public interface SchemaChangeQueuePort {
    /** 校验并入队，返回本次请求或相同 DDL 的活跃请求 ID；完成后的请求不参与去重。 */
    String enqueue(String ddl);

    Optional<State> state(String requestId);

    record State(String status, String error) {}
}
