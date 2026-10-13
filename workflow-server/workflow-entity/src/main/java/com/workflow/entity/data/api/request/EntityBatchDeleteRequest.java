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
    /** 页面配置摘要与业务定位信息；权限由后端实时校验。 */
    private String configHash;
    private java.util.Map<String, Object> runtimeContext;
}
