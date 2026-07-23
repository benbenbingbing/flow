package com.workflow.contracts.process;

/**
 * 面向其他模块的流程目录信息，不暴露流程持久化实体。
 */
public record ProcessCatalogItem(
        /** 流程ID */
        String id,
        /** 流程定义Key */
        String processKey,
        /** 流程名称 */
        String processName,
        /** 流程状态 */
        String status) {
}
