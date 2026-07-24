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

    /** 新版实体业务表前缀。 */
    public static final String BUSINESS_PREFIX = "biz_";
    /** 历史实体数据表前缀。 */
    public static final String LEGACY_PREFIX = "entity_data_";
    private static final int MYSQL_IDENTIFIER_LIMIT = 64;
    private static final int HASH_LENGTH = 8;
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    /**
     * 根据实体编码生成物理业务表名，超长时截断并追加哈希后缀。
     *
     * @param entityCode 实体编码
     * @return 物理业务表名
     * @throws IllegalArgumentException 实体编码为空时抛出
     */
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

    /**
     * 生成历史命名风格的实体数据表名（entity_data_ 前缀）。
     *
     * @param entityCode 实体编码
     * @return 历史风格表名
     * @throws IllegalArgumentException 实体编码为空或包含不安全字符时抛出
     */
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

    /**
     * 校验存储的物理表名是否合法且使用 biz_ 前缀。
     *
     * @param tableName 物理表名
     * @return 规范化后的表名
     * @throws IllegalArgumentException 表名为空、格式不合法或缺少前缀时抛出
     */
    public String validateStoredName(String tableName) {
        String normalized = validateIdentifier(tableName);
        if (!normalized.startsWith(BUSINESS_PREFIX)) {
            throw new IllegalArgumentException("实体物理业务表必须使用 biz_ 前缀: " + tableName);
        }
        return normalized;
    }

    /**
     * 校验迁移表名是否合法，允许 entity_data_ 或 biz_ 前缀。
     *
     * @param tableName 物理表名
     * @return 规范化后的表名
     * @throws IllegalArgumentException 表名为空、格式不合法或缺少允许的前缀时抛出
     */
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

    /**
     * 判断表名是否为业务表（biz_ 前缀）。
     *
     * @param tableName 表名
     * @return 是业务表返回 true
     */
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
