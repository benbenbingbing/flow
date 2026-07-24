package com.workflow.service.listfield;

import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityListField;

import java.util.List;
import java.util.Map;

/**
 * 列表字段数据提供者接口
 * 
 * 实现此接口并标记为 Spring Component，即可自动注册为列表字段数据补充处理器。
 * 用于在基础实体数据查询完成后，补充自定义列数据（关联查询、聚合统计、业务计算等）。
 */
public interface ListFieldDataProvider {

    /**
     * 返回支持的数据源类型
     * 对应 EntityListField.dataSourceType 的值
     */
    String getDataSourceType();

    /** 数据源在选项清单中展示的名称，默认与数据源类型相同 */
    default String getDisplayName() {
        return getDataSourceType();
    }

    /** 数据源描述文本，默认为空 */
    default String getDescription() {
        return "";
    }

    /** 是否支持虚拟字段（无对应实体字段的计算列），默认支持 */
    default boolean supportsVirtualField() {
        return true;
    }

    /** 是否支持作为查询条件使用，默认不支持 */
    default boolean supportsQuery() {
        return false;
    }

    /** 返回数据源配置项 schema（key/label/type/required/defaultValue），默认无配置项 */
    default List<Map<String, Object>> getConfigSchema() {
        return List.of();
    }

    /** 对字段配置进行自定义校验，默认不做任何校验 */
    default void validateConfig(EntityListField field, Map<String, Object> config) {
    }

    /**
     * 补充自定义列数据
     *
     * @param records    基础查询结果列表（会被直接修改，补充的数据放入 record.data 或 record.extData）
     * @param fields     当前需要补充的字段配置列表（已按 getDataSourceType 过滤）
     * @param context    上下文参数，可包含 entityCode、listKey、userId 等
     */
    void enrich(List<EntityDataDTO> records, List<EntityListField> fields, Map<String, Object> context);
}
