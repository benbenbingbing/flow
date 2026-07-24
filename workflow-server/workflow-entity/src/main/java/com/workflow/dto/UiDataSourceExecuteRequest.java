package com.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Map;

/**
 * UI 数据源执行请求。
 * 用于在运行时调用某个已发布数据源并返回结果。
 */
@Data
public class UiDataSourceExecuteRequest {

    /** 用途（如 LIST_FIELD_OPTIONS） */
    private String usage;
    /** 配置类型（如 ENTITY_LIST） */
    private String configType;
    /** 配置 ID */
    private String configId;
    /** 发布 ID */
    private String releaseId;
    /** 发布版本 */
    private Integer releaseVersion;
    /** 实体编码 */
    private String entityCode;
    /** 列表标识 */
    private String listKey;
    /** 输入参数 */
    private Map<String, Object> input;
    /** 上下文参数 */
    private Map<String, Object> context;
    /** 页码 */
    private Integer pageNum;
    /** 每页大小 */
    private Integer pageSize;

    /** 服务端幂等键（不序列化给前端） */
    @JsonIgnore
    private String serverIdempotencyKey;

    /** 是否固定使用服务端指定的发布版本（不序列化给前端） */
    @JsonIgnore
    private boolean serverPinnedRelease;
}
