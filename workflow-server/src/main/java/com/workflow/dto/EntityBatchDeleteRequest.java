package com.workflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 实体数据批量删除请求。
 */
@Data
public class EntityBatchDeleteRequest {
    private List<String> ids;
    private String listKey;
}
