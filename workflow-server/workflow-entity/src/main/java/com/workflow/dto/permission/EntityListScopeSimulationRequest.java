package com.workflow.dto.permission;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实体列表数据范围模拟请求。
 */
@Data
public class EntityListScopeSimulationRequest {
    /** 模拟的用户 ID */
    private String userId;
    /** 模拟场景（PAGE/EXPORT 等） */
    private String scene;
    /** 模拟使用的过滤条件 */
    private Map<String, Object> filters = new LinkedHashMap<>();
}
