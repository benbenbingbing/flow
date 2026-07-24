package com.workflow.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实体列表数据查询请求。
 */
@Data
public class EntityListQueryRequest {
    /** 页码，默认 1 */
    private long pageNum = 1;
    /** 每页大小，默认 10 */
    private long pageSize = 10;
    /** 场景（PAGE/EXPORT 等），默认 PAGE */
    private String scene = "PAGE";
    /** 查询过滤条件 */
    private Map<String, Object> filters = new LinkedHashMap<>();
    /** 列表运行时上下文（关联来源、参数等） */
    private EntityListRuntimeContextDTO context;
}
