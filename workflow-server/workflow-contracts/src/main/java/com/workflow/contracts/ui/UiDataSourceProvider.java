package com.workflow.contracts.ui;

import com.workflow.contracts.entity.list.DataScopePlan;

import java.util.Map;

public interface UiDataSourceProvider {

    String getCode();

    String getDisplayName();

    default Map<String, Object> configurationSchema() {
        return Map.of();
    }

    Object execute(
            UiDataSourceContext context,
            DataScopePlan dataScopePlan,
            Map<String, Object> configuration,
            Map<String, Object> input);
}
