package com.workflow.contracts.history;

/** 流程运行时只依赖写策略端口，避免依赖实体内部实现或形成创建链循环。 */
public interface EntityHistoryWritePolicyPort {
    /** 在任何流程监听器或业务写入前校验；只读归档专属记录会抛出业务冲突。 */
    void requireWritable(String entityCode, String recordId);
}
