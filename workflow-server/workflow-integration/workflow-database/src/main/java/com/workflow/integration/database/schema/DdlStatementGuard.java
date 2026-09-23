package com.workflow.integration.database.schema;

import java.util.regex.Pattern;

/** 专用发布通道校验：普通 DDL 限定单条；程序体只允许内部审计触发器的精确模板。 */
public final class DdlStatementGuard {
    private DdlStatementGuard() {}
    private static final Pattern PREFIX = Pattern.compile(
            "^(CREATE\\s+(TABLE|INDEX|UNIQUE\\s+INDEX)|ALTER\\s+TABLE|DROP\\s+TABLE|COMMENT\\s+ON\\s+(TABLE|COLUMN))\\b",
            Pattern.CASE_INSENSITIVE);

    public static void requireSafe(String ddl, boolean mysqlBackslashEscapes) {
        if (ddl == null || ddl.isBlank()) throw new IllegalArgumentException("DDL statement must not be blank");
        String sql = ddl.trim();
        if (!mysqlBackslashEscapes && AuditTimestampDdl.isGenerated(sql)) return;
        if (!PREFIX.matcher(sql).find()) throw new IllegalArgumentException("Unsupported schema statement");
        boolean literal = false;
        boolean escapeString = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\0') throw new IllegalArgumentException("DDL contains NUL");
            if (literal && c == '\\' && (mysqlBackslashEscapes || escapeString)) { i++; continue; }
            if (c == '\'') {
                if (literal && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') { i++; continue; }
                if (!literal) escapeString = i > 0 && (sql.charAt(i - 1) == 'E' || sql.charAt(i - 1) == 'e');
                literal = !literal;
                continue;
            }
            if (literal) continue;
            if (c == '#' || (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-')
                    || (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*')) {
                throw new IllegalArgumentException("DDL comments are not allowed");
            }
            if (c == ';' && !sql.substring(i + 1).isBlank()) throw new IllegalArgumentException("Only one DDL statement is allowed");
        }
        if (literal) throw new IllegalArgumentException("DDL contains an unterminated literal");
    }
}
