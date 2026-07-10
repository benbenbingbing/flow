package com.workflow.dto.permission;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 权限规则 - 数据过滤配置
 */
@Data
public class FilterConfigDTO {

    /** 过滤类型：ALL / PERSONAL / DEPT / DEPT_TREE / ROLE / EXPRESSION */
    private String type;

    /** 是否包含子部门 */
    private Boolean includeSubDept;

    /** 指定部门ID列表 */
    private List<String> deptIds;

    /** 字段映射 */
    private FieldMappingDTO fieldMapping;

    /** 状态限制配置 */
    private StatusLimitDTO statusLimit;

    /** 自定义SQL片段 */
    private String customSql;

    /** Groovy表达式 */
    private String expression;

    /**
     * 字段映射
     */
    @Data
    public static class FieldMappingDTO {
        private String deptField = "dept_id";
        private String userField = "create_by";
        private String statusField = "status";
    }

    /**
     * 状态限制
     */
    @Data
    public static class StatusLimitDTO {
        private Boolean enabled = false;
        /** IN / NOT_IN / EXPRESSION */
        private String mode = "IN";
        private List<String> values;
        private String expression;
    }
}
