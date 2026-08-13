package com.workflow.entity.list.api.request;

import com.workflow.entity.list.api.response.EntityListRuntimeContextDTO;

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
    /** 请求使用的发布ID；无签名上下文时必须与当前 ACTIVE 一致 */
    private String releaseId;
    /** 请求使用的发布版本 */
    private Integer releaseVersion;
    /** 父表单签发的列表版本解析令牌 */
    private String releaseResolutionToken;
    /** 查询过滤条件 */
    private Map<String, Object> filters = new LinkedHashMap<>();
    /** 列表运行时上下文（关联来源、参数等） */
    private EntityListRuntimeContextDTO context;
}
