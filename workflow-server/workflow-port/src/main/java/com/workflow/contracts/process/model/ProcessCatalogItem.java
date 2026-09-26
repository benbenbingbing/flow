package com.workflow.contracts.process.model;

/**
 * 面向其他模块的流程目录信息，不暴露流程持久化实体。
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param processKey 流程键，后续用于授权校验、关联或幂等去重
 * @param processName 流程名称，后续用于处理流程目录条目时匹配或展示
 * @param status 状态标识，决定后续流程目录条目采用的处理分支
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
