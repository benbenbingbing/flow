package com.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 列表字段数据源选项 DTO。
 * 描述一种可选的数据源类型及其能力，用于列表字段配置时的选择项展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListFieldDataSourceOptionDTO {

    /** 选项值（数据源类型标识） */
    private String value;

    /** 选项显示名称 */
    private String label;

    /** 选项描述 */
    private String description;

    /** 是否支持虚拟字段 */
    private boolean supportsVirtualField;

    /** 是否支持查询 */
    private boolean supportsQuery;

    /** 数据源配置项的 Schema 描述 */
    @Builder.Default
    private List<Map<String, Object>> configSchema = new ArrayList<>();
}
