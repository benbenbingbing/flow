package com.workflow.dto.permission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体列表数据范围配置 DTO。
 * 聚合某实体的全部数据范围策略与绑定关系，用于配置页面整体展示。
 */
@Data
public class EntityListScopeConfigurationDTO {
    /** 实体编码 */
    private String entityCode;
    /** 当前生效的发布版本号 */
    private Integer activeVersion;
    /** 数据范围策略列表 */
    private List<EntityListScopePolicyDTO> policies = new ArrayList<>();
    /** 列表与策略绑定关系列表 */
    private List<EntityListScopeBindingDTO> bindings = new ArrayList<>();
}
