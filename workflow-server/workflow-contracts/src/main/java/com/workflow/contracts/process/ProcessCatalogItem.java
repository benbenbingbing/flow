package com.workflow.contracts.process;

/**
 * 面向其他模块的流程目录信息，不暴露流程持久化实体。
 */
public record ProcessCatalogItem(
        String id,
        String processKey,
        String processName,
        String status) {
}
