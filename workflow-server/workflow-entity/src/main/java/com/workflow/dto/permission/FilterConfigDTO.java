package com.workflow.dto.permission;

import lombok.Data;

import java.util.List;

/**
 * 权限规则 - 数据过滤配置
 */
@Data
public class FilterConfigDTO {

    /** 结构化配置版本 */
    private Integer version = 1;

    /** 过滤类型：ALL / PERSONAL / SUBMITTER / CURRENT_ASSIGNEE / DEPT / DEPT_TREE / RULE */
    private String type;

    /** 是否包含子部门 */
    private Boolean includeSubDept;

    /** 指定部门ID列表 */
    private List<String> deptIds;

    /** 字段映射 */
    private FieldMappingDTO fieldMapping;

    /** 状态限制配置 */
    private StatusLimitDTO statusLimit;

    /**
     * 结构化根节点。type=RULE 时优先生效；
     * 为空时兼容旧版简单类型。
     */
    private EntityActionRuleDTO.RuleNode root;

    /**
     * 旧版自定义 SQL，已废弃。
     */
    private String customSql;

    /**
     * 旧版表达式，已废弃。
     */
    private String expression;

    /**
     * 字段映射
     */
    @Data
    public static class FieldMappingDTO {
        private String deptField = "dept_id";
        // 实体数据表使用 create_by 作为创建人字段
        private String userField = "create_by";
        private String statusField = "status";
    }

    /**
     * 状态限制
     */
    @Data
    public static class StatusLimitDTO {
        private Boolean enabled = false;
        /** IN / NOT_IN */
        private String mode = "IN";
        private List<String> values;
    }
}
