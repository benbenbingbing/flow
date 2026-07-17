package com.workflow.dto;

import lombok.Data;

/**
 * 流程定义查询条件
 */
@Data
public class ProcessDefinitionQueryDTO {

    /**
     * 关键词（匹配流程名称、流程标识）
     */
    private String keyword;

    /**
     * 状态：DRAFT、PUBLISHED、DISABLED
     */
    private String status;

    /**
     * 分类
     */
    private String category;

    /**
     * 页码，默认 1
     */
    private Integer pageNum = 1;

    /**
     * 每页大小，默认 10
     */
    private Integer pageSize = 10;
}
