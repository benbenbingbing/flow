package com.workflow.contracts.entity.list.spi;

import com.workflow.contracts.entity.list.model.ListFieldDataRecord;
import com.workflow.contracts.entity.list.model.ListFieldDataConfig;

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
     * 对应 ListFieldDataConfig.dataSourceType 的值
     *
     * @return 读取后的数据来源类型文本，供调用方比较或展示
     */
    String getDataSourceType();

    /**
     * 数据源在选项清单中展示的名称，默认与数据源类型相同
     *
     * @return 读取后的展示名称文本，供调用方比较或展示
     */
    default String getDisplayName() {
        return getDataSourceType();
    }

    /**
     * 数据源描述文本，默认为空
     *
     * @return 读取后的描述文本，供调用方比较或展示
     */
    default String getDescription() {
        return "";
    }

    /**
     * 是否支持虚拟字段（无对应实体字段的计算列），默认支持
     *
     * @return {@code virtual}字段条件成立时为 true，否则为 false
     */
    default boolean supportsVirtualField() {
        return true;
    }

    /**
     * 历史扩展能力标记，仅保留源代码兼容；平台统一禁止扩展列参与查询和排序。
     * 新实现只需补充当前页展示值，无需覆盖本方法。
     *
     * @return 查询条件成立时为 true，否则为 false
     */
    @Deprecated
    default boolean supportsQuery() {
        return false;
    }

    /**
     * 适用实体编码。空列表或包含 * 表示全部实体。
     * 只收窄设计器数据源下拉，不阻止已保存列在运行时继续补数。
     *
     * @return 列表字段数据提供者集合，供调用方遍历或展示
     */
    default List<String> getSupportedEntityCodes() {
        return List.of();
    }

    /**
     * 返回数据源配置项 schema（key/label/type/required/defaultValue），默认无配置项
     *
     * @return 列表字段数据提供者集合，供调用方遍历或展示
     */
    default List<Map<String, Object>> getConfigSchema() {
        return List.of();
    }

    /**
     * 对字段配置进行自定义校验，默认不做任何校验
     *
     * @param field 字段，供本方法校验配置时使用
     * @param config 配置内容，决定后续配置的处理规则
     */
    default void validateConfig(ListFieldDataConfig field, Map<String, Object> config) {
    }

    /**
     * 补充自定义列数据
     *
     * @param records    基础查询结果列表（会被直接修改，补充的数据放入 record.data 或 record.extData）
     * @param fields     当前需要补充的字段配置列表（已按 getDataSourceType 过滤）
     * @param context    上下文参数，可包含 entityCode、listKey、userId 等
     */
    void enrich(List<ListFieldDataRecord> records, List<ListFieldDataConfig> fields, Map<String, Object> context);
}
