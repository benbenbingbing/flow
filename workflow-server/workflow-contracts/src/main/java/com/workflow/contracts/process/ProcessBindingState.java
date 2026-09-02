package com.workflow.contracts.process;

/**
 * 流程与实体绑定变更所需的锁定状态。
 *
 * @param process 流程目录快照；流程物理不存在时端口不返回该条目
 * @param available 流程是否仍可用于建立新绑定
 * @param hasPublishedVersion 是否已经存在不可变的已发布版本
 */
public record ProcessBindingState(
        ProcessCatalogItem process,
        boolean available,
        boolean hasPublishedVersion) {
}
