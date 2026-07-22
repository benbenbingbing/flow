package com.workflow.dto;

import lombok.Data;

/**
 * 实体定义查询条件
 */
@Data
public class EntityDefinitionQueryDTO {

    /**
     * 关键词（匹配实体名称、实体编码）
     */
    private String keyword;

    /**
     * 状态：DRAFT、PUBLISHED、DISABLED
     */
    private String status;

    /**
     * 生命周期模式：STANDALONE、WORKFLOW
     */
    private com.workflow.entity.EntityDefinition.LifecycleMode lifecycleMode;

    /**
     * 存储模式：DYNAMIC、SYSTEM
     */
    private com.workflow.entity.EntityDefinition.StorageMode storageMode;

    /**
     * 页码，默认 1
     */
    private Integer pageNum = 1;

    /**
     * 每页大小，默认 10
     */
    private Integer pageSize = 10;
}
