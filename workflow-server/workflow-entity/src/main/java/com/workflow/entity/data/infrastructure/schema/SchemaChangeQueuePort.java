package com.workflow.entity.data.infrastructure.schema;

import java.util.Optional;

/** 专用结构发布队列；入队独立提交，不能参与等待其完成的业务事务。 */
public interface SchemaChangeQueuePort {
    /**
     * 校验并入队，返回本次请求或相同 DDL 的活跃请求 ID；完成后的请求不参与去重。
     *
     * @param ddl DDL，供本方法入队结构变更队列时使用
     * @return 入队后的结构变更队列文本，供调用方比较或展示
     */
    String enqueue(String ddl);

    /**
     * 处理状态，并将结果传给后续步骤。
     *
     * @param requestId 请求ID，后续用于处理状态时定位或关联目标
     * @return 匹配的状态；未找到时为空
     */
    Optional<State> state(String requestId);

    /**
     * 封装状态的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param status 状态标识，决定后续状态采用的处理分支
     * @param error 错误，保存在对象中供后续校验、查询或展示
     */
    record State(String status, String error) {}
}
