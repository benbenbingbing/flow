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
     * 是否启用流程
     */
    private Boolean enableProcess;

    /**
     * 页码，默认 1
     */
    private Integer pageNum = 1;

    /**
     * 每页大小，默认 10
     */
    private Integer pageSize = 10;
}
