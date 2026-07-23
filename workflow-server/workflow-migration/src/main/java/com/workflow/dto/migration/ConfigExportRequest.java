package com.workflow.dto.migration;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置导出请求。
 *
 * <p>封装用户发起配置导出时的参数，包含待导出资产清单、迁移标签、
 * 每个资产的快照选择配置以及仅做依赖校验而不打包的资源集合。</p>
 */
@Data
public class ConfigExportRequest {

    private List<String> assetIds;                              // 待导出的迁移资产ID列表
    private String migrationTag;                                // 迁移标签(为空时自动生成)
    private Map<String, Object> selections = new LinkedHashMap<>();   // 资产ID -> 快照选择配置
    private Set<String> validateOnlyDependencies = new LinkedHashSet<>(); // 仅校验存在性不打包的依赖(type:key)
}
