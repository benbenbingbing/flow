package com.workflow.dto;

import lombok.Data;

import java.util.Map;

/**
 * UI 数据源保存请求。
 */
@Data
public class UiDataSourceSaveRequest {

    /** 数据源 ID（更新时传入） */
    private String id;
    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
    /** 数据源编码 */
    private String sourceCode;
    /** 数据源名称 */
    private String sourceName;
    /** 数据源类型 */
    private String sourceType;
    /** 数据提供者编码 */
    private String providerCode;
    /** 作用域类型（如 ENTITY_LIST） */
    private String scopeType;
    /** 作用域 ID */
    private String scopeId;
    /** 数据源配置 */
    private Map<String, Object> config;
    /** 输入参数 Schema */
    private Map<String, Object> inputSchema;
    /** 输出结果 Schema */
    private Map<String, Object> outputSchema;
    /** 执行策略（缓存/超时等） */
    private Map<String, Object> executionPolicy;
    /** 是否启用 */
    private Boolean enabled;
}
