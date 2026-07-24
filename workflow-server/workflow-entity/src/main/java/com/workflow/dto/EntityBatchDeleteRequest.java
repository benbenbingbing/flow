package com.workflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 实体数据批量删除请求。
 */
@Data
public class EntityBatchDeleteRequest {
    /** 待删除数据 ID 列表 */
    private List<String> ids;
    /** 来源列表标识 */
    private String listKey;
}
