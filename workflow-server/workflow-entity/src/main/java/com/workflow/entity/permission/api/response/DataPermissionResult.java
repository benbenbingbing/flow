package com.workflow.entity.permission.api.response;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** 生效的发布版本号 */
    private Integer releaseVersion;

    /** 数据范围模式 */
    private String dataScopeMode;

    /** 权限结果解释（人类可读说明，用于调试/提示） */
    private String explanation;

    /** Bound values referenced by the generated permission SQL fragment. */
    private Map<String, Object> sqlParameters = new LinkedHashMap<>();

    /**
     * 构造"全部放行"结果：有权限且无需过滤。
     *
     * @return 放行结果
     */
    public static DataPermissionResult allowAll() {
        DataPermissionResult r = new DataPermissionResult();
        r.hasPermission = true;
        r.needFilter = false;
        return r;
    }

    /**
     * 构造"全部拒绝"结果：无权限，SQL 固定为 1=0。
     *
     * @return 拒绝结果
     */
    public static DataPermissionResult denyAll() {
        DataPermissionResult r = new DataPermissionResult();
        r.hasPermission = false;
        r.needFilter = true;
        r.sqlCondition = "1=0";
        return r;
    }

    /**
     * 构造"按条件过滤"结果：有权限但需附加 SQL 条件。
     *
     * @param sql 生成的 SQL WHERE 条件（不含 WHERE 关键字）
     * @return 带过滤条件的结果
     */
    public static DataPermissionResult withCondition(String sql) {
        DataPermissionResult r = new DataPermissionResult();
        r.hasPermission = true;
        r.needFilter = true;
        r.sqlCondition = sql;
        return r;
    }

    /**
     * 处理条件，并将结果传给后续步骤。
     *
     * @param sql SQL，供本方法处理条件时使用
     * @param parameters 参数集合，作为 {@code result.mergeSqlParameters} 的输入影响后续处理
     * @return 处理后的条件结果，供调用方继续处理
     */
    public static DataPermissionResult withCondition(String sql, Map<String, Object> parameters) {
        DataPermissionResult result = withCondition(sql);
        result.mergeSqlParameters(parameters);
        return result;
    }

    /**
     * 与另一个条件取并集（OR）
     *
     * @param sql SQL，供本方法处理{@code union}时使用
     * @return 处理后的{@code union}结果，供调用方继续处理
     */
    public DataPermissionResult union(String sql) {
        return union(sql, Map.of());
    }

    /**
     * 处理{@code union}，并将结果传给后续步骤。
     *
     * @param sql SQL，作为 {@code OR} 的输入影响后续处理
     * @param parameters 参数集合，作为 {@code mergeSqlParameters} 的输入影响后续处理
     * @return 处理后的{@code union}结果，供调用方继续处理
     */
    public DataPermissionResult union(String sql, Map<String, Object> parameters) {
        if (sql == null || sql.isBlank()) {
            return this;
        }
        mergeSqlParameters(parameters);
        if (!this.needFilter) {
            // 当前无过滤条件，直接采用新条件
            this.needFilter = true;
            this.sqlCondition = sql;
            return this;
        }
        this.sqlCondition = "(" + this.sqlCondition + ") OR (" + sql + ")";
        return this;
    }

    /**
     * 与另一个条件取交集（AND）
     *
     * @param sql SQL，供本方法处理{@code intersect}时使用
     * @return 处理后的{@code intersect}结果，供调用方继续处理
     */
    public DataPermissionResult intersect(String sql) {
        return intersect(sql, Map.of());
    }

    /**
     * 处理{@code intersect}，并将结果传给后续步骤。
     *
     * @param sql SQL，作为 {@code AND} 的输入影响后续处理
     * @param parameters 参数集合，作为 {@code mergeSqlParameters} 的输入影响后续处理
     * @return 处理后的{@code intersect}结果，供调用方继续处理
     */
    public DataPermissionResult intersect(String sql, Map<String, Object> parameters) {
        if (sql == null || sql.isBlank()) {
            return this;
        }
        mergeSqlParameters(parameters);
        if (!this.needFilter) {
            this.needFilter = true;
            this.sqlCondition = sql;
            return this;
        }
        this.sqlCondition = "(" + this.sqlCondition + ") AND (" + sql + ")";
        return this;
    }

    /**
     * 合并SQL参数集合；结果供后续流程传递或持久化。
     *
     * @param parameters 参数集合，作为 {@code sqlParameters.putAll} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void mergeSqlParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }
        // NULL 也是已占用的参数值。先验证全部键，再合并，避免冲突覆盖原权限或留下部分结果。
        parameters.forEach((key, value) -> {
            if (sqlParameters.containsKey(key) && !java.util.Objects.equals(sqlParameters.get(key), value)) {
                throw new IllegalArgumentException("数据权限参数冲突: " + key);
            }
        });
        sqlParameters.putAll(parameters);
    }
}
