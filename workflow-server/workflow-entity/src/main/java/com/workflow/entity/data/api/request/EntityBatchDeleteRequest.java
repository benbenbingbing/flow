package com.workflow.entity.data.api.request;

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
    /** 请求使用的列表发布 ID */
    private String releaseId;
    /** 请求使用的列表发布版本 */
    private Integer releaseVersion;
    /** 父表单签发的历史列表版本解析令牌 */
    private String releaseResolutionToken;
}
