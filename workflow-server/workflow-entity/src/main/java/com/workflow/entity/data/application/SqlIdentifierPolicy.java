package com.workflow.entity.data.application;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared policy for identifiers emitted by the dynamic-schema subsystem.
 */
final class SqlIdentifierPolicy {

    static final int MAX_LENGTH = 63;
    private static final Pattern IDENTIFIER =
            Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final Set<String> RESERVED = Set.of(
            "alter", "and", "as", "asc", "between", "by", "case", "column",
            "constraint", "create", "database", "default", "delete", "desc",
            "distinct", "drop", "else", "exists", "false", "from", "group",
            "having", "index", "insert", "into", "join", "key", "like", "limit",
            "lock", "not", "null", "on", "or", "order", "primary", "references",
            "rename", "schema", "select", "set", "table", "then", "true", "union",
            "unique", "update", "user", "using", "values", "when", "where", "with");

    /**
     * 初始化SQL标识符策略，保存构造参数供后续方法使用。
     */
    private SqlIdentifierPolicy() {
    }

    /**
     * 校验SQL标识符策略；不满足约束时阻止后续处理。
     *
     * @param identifier 标识符，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 校验后的SQL标识符策略文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    static String validate(String identifier) {
        if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("SQL 标识符不合法: " + identifier);
        }
        if (RESERVED.contains(identifier.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("SQL 标识符不能使用保留字: " + identifier);
        }
        return identifier;
    }
}
