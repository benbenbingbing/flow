package com.workflow.dto.permission;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体列表数据范围发布快照 DTO。
 * 记录某次发布版本下的策略、绑定以及各列表的数据范围模式。
 */
@Data
public class EntityListScopeSnapshotDTO {
    /** 实体编码 */
    private String entityCode;
    /** 发布版本号 */
    private Integer version;
    /** 该版本下的策略列表 */
    private List<EntityListScopePolicyDTO> policies = new ArrayList<>();
    /** 该版本下的绑定关系列表 */
    private List<EntityListScopeBindingDTO> bindings = new ArrayList<>();
    /** 列表标识 -> 数据范围模式 的映射 */
    private Map<String, String> listModes = new LinkedHashMap<>();
}
