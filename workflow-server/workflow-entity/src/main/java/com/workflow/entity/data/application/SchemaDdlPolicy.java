package com.workflow.entity.data.application;

import java.util.regex.Pattern;

/**
 * Guards the dedicated schema channel against arbitrary SQL and statement chaining.
 */
public final class SchemaDdlPolicy {

    private static final Pattern ALLOWED_PREFIX = Pattern.compile(
            "^(CREATE\\s+(TABLE|INDEX|UNIQUE\\s+INDEX)|ALTER\\s+TABLE|DROP\\s+TABLE)\\b",
            Pattern.CASE_INSENSITIVE);

    private SchemaDdlPolicy() {
    }

    public static void requireSafe(String ddl) {
        if (ddl == null || ddl.isBlank()) {
            throw new IllegalArgumentException("DDL statement must not be blank");
        }
        String normalized = ddl.trim();
        if (!ALLOWED_PREFIX.matcher(normalized).find()) {
            throw new IllegalArgumentException("Only schema CREATE, ALTER, and DROP statements are allowed");
        }
        validateSingleStatement(normalized);
    }

    private static void validateSingleStatement(String ddl) {
        boolean inLiteral = false;
        for (int index = 0; index < ddl.length(); index++) {
            char character = ddl.charAt(index);
            if (inLiteral && character == '\\') {
                index++;
                continue;
            }
            if (character == '\'') {
                if (inLiteral && index + 1 < ddl.length() && ddl.charAt(index + 1) == '\'') {
                    index++;
                    continue;
                }
                inLiteral = !inLiteral;
                continue;
            }
            if (inLiteral) {
                continue;
            }
            if (character == '\0'
                    || character == '#'
                    || (character == '-' && index + 1 < ddl.length() && ddl.charAt(index + 1) == '-')
                    || (character == '/' && index + 1 < ddl.length() && ddl.charAt(index + 1) == '*')) {
                throw new IllegalArgumentException("DDL comments and control delimiters are not allowed");
            }
            if (character == ';' && !ddl.substring(index + 1).isBlank()) {
                throw new IllegalArgumentException("Only one DDL statement is allowed");
            }
        }
        if (inLiteral) {
            throw new IllegalArgumentException("DDL contains an unterminated literal");
        }
    }
}
