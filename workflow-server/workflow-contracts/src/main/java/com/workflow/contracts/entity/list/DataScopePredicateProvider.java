package com.workflow.contracts.entity.list;

import java.util.List;
import java.util.Map;

/**
 * 自定义数据范围条件扩展。禁止返回包含未绑定用户输入的 SQL。
 */
public interface DataScopePredicateProvider {

    /**
     * 返回数据范围条件类型标识。
     *
     * @return 类型标识
     */
    String getType();

    /**
     * 返回数据范围条件的展示名称。
     *
     * @return 展示名称
     */
    String getDisplayName();

    /**
     * 返回该条件类型的描述说明。
     *
     * @return 描述说明，默认空字符串
     */
    default String getDescription() {
        return "";
    }

    /**
     * 返回支持的字段类型列表。
     *
     * @return 支持的字段类型列表，默认空
     */
    default List<String> getSupportedFieldTypes() {
        return List.of();
    }

    /**
     * 返回该条件类型的配置项 Schema。
     *
     * @return 配置项 Schema，默认空
     */
    default Map<String, Object> getConfigSchema() {
        return Map.of();
    }

    /**
     * 校验配置合法性。
     *
     * @param entityCode 实体编码
     * @param config     条件配置
     */
    void validate(String entityCode, Map<String, Object> config);

    /**
     * 将配置编译为数据范围查询计划。
     *
     * @param entityCode  实体编码
     * @param config      条件配置
     * @param userContext 用户上下文
     * @return 编译后的数据范围查询计划
     */
    DataScopePlan compile(
            String entityCode,
            Map<String, Object> config,
            Map<String, Object> userContext);
}
