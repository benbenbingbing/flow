package com.workflow.contracts.process.port;

/** 实体模块经此端口通知任务模块刷新摘要，不依赖任务模块实现。 */
public interface TaskBusinessSummaryPort {
    /**
     * 在业务变更事务中刷新关联任务的最新摘要；失败必须回滚业务变更。
     * 删除记录也调用本方法，以清空原先可见的名称和状态。没有关联任务时不读取聚合。
     */
    void refreshTaskBusinessSummary(String entityCode, String recordId);
}
