package com.workflow.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 实体物理业务表命名规则。
 */
@Component
public class EntityPhysicalTableNaming {

    public static final String BUSINESS_PREFIX = "biz_";
    public static final String LEGACY_PREFIX = "entity_data_";
    private static final int MYSQL_IDENTIFIER_LIMIT = 64;
    private static final int HASH_LENGTH = 8;
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    public String generate(String entityCode) {
        String normalizedCode = normalizeEntityCode(entityCode);
        String candidate = BUSINESS_PREFIX + normalizedCode;
        if (candidate.length() <= MYSQL_IDENTIFIER_LIMIT) {
            return candidate;
        }
        String hash = sha256(candidate).substring(0, HASH_LENGTH);
        int bodyLength = MYSQL_IDENTIFIER_LIMIT - HASH_LENGTH - 1;
        return candidate.substring(0, bodyLength) + "_" + hash;
    }

    public String legacyName(String entityCode) {
        if (!StringUtils.hasText(entityCode)) {
            throw new IllegalArgumentException("实体编码不能为空");
        }
        String normalized = entityCode.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("历史实体编码包含不安全字符: " + entityCode);
        }
        return LEGACY_PREFIX + normalized;
    }

    public String validateStoredName(String tableName) {
        String normalized = validateIdentifier(tableName);
        if (!normalized.startsWith(BUSINESS_PREFIX)) {
            throw new IllegalArgumentException("实体物理业务表必须使用 biz_ 前缀: " + tableName);
        }
        return normalized;
    }

    public String validateMigrationName(String tableName) {
        String normalized = validateIdentifier(tableName);
        if (normalized.startsWith(LEGACY_PREFIX)
                || normalized.startsWith(BUSINESS_PREFIX)) {
            return normalized;
        }
        throw new IllegalArgumentException("实体迁移表名必须使用 entity_data_ 或 biz_ 前缀: " + tableName);
    }

    private String validateIdentifier(String tableName) {
        if (!StringUtils.hasText(tableName)) {
            throw new IllegalArgumentException("实体物理表名不能为空");
        }
        String normalized = tableName.trim().toLowerCase(Locale.ROOT);
        if (!IDENTIFIER_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("实体物理表名不合法: " + tableName);
        }
        return normalized;
    }

    public boolean isBusinessTable(String tableName) {
        return StringUtils.hasText(tableName)
                && tableName.toLowerCase(Locale.ROOT).startsWith(BUSINESS_PREFIX);
    }

    private String normalizeEntityCode(String entityCode) {
        if (!StringUtils.hasText(entityCode)) {
            throw new IllegalArgumentException("实体编码不能为空");
        }
        String normalized = entityCode.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("实体编码无法生成物理表名: " + entityCode);
        }
        if (!Character.isLetter(normalized.charAt(0))) {
            normalized = "e_" + normalized;
        }
        return normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
