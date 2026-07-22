package com.workflow.contracts.entity.list;

import java.util.List;
import java.util.Map;

/**
 * 自定义数据范围条件扩展。禁止返回包含未绑定用户输入的 SQL。
 */
public interface DataScopePredicateProvider {

    String getType();

    String getDisplayName();

    default String getDescription() {
        return "";
    }

    default List<String> getSupportedFieldTypes() {
        return List.of();
    }

    default Map<String, Object> getConfigSchema() {
        return Map.of();
    }

    void validate(String entityCode, Map<String, Object> config);

    DataScopePlan compile(
            String entityCode,
            Map<String, Object> config,
            Map<String, Object> userContext);
}
