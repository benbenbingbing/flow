package com.workflow.dto.permission;

import lombok.Data;

import java.util.List;

/**
 * 数据权限计算结果
 */
@Data
public class DataPermissionResult {

    /** 是否有权限（false = 无任何数据） */
    private boolean hasPermission = true;

    /** 生成的 SQL WHERE 条件（不含 WHERE 关键字） */
    private String sqlCondition;

    /** 是否需要附加条件（true = 需要拼接 AND sqlCondition） */
    private boolean needFilter = false;

    /** 匹配到的规则名称列表（用于日志/调试） */
    private List<String> matchedRuleNames;

    public static DataPermissionResult allowAll() {
        DataPermissionResult r = new DataPermissionResult();
        r.hasPermission = true;
        r.needFilter = false;
        return r;
    }

    public static DataPermissionResult denyAll() {
        DataPermissionResult r = new DataPermissionResult();
        r.hasPermission = false;
        r.needFilter = true;
        r.sqlCondition = "1=0";
        return r;
    }

    public static DataPermissionResult withCondition(String sql) {
        DataPermissionResult r = new DataPermissionResult();
        r.hasPermission = true;
        r.needFilter = true;
        r.sqlCondition = sql;
        return r;
    }
}
