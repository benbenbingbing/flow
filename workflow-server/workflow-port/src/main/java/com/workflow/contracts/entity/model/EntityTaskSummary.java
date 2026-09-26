package com.workflow.contracts.entity.model;

/**
 * 任务列表使用的业务当前值；不包含表单、关系子表和权限配置。
 * dataName 保留旧 DTO 自定义 data.name 的独立口径，不与系统 name 混用。
 */
public record EntityTaskSummary(String name, String code, String dataName,
                                String currentTaskName, String status) {
    public static EntityTaskSummary empty() {
        return new EntityTaskSummary(null, null, null, null, null);
    }
}
