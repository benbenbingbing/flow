package com.workflow.entity.data.application;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 实体物理业务表命名规则。
 */
@Component
public class EntityPhysicalTableNaming {

    /** 新版实体业务表前缀。 */
    public static final String BUSINESS_PREFIX = "biz_";
    private static final int DYNAMIC_TABLE_NAME_LIMIT =
            SqlIdentifierPolicy.MAX_LENGTH - "_multi".length();
    private static final int HASH_LENGTH = 8;

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
        if (candidate.length() <= DYNAMIC_TABLE_NAME_LIMIT) {
            return candidate;
        }
        String hash = sha256(candidate).substring(0, HASH_LENGTH);
        int bodyLength = DYNAMIC_TABLE_NAME_LIMIT - HASH_LENGTH - 1;
        return candidate.substring(0, bodyLength) + "_" + hash;
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
        if (normalized.length() > DYNAMIC_TABLE_NAME_LIMIT) {
            throw new IllegalArgumentException("实体物理业务表名未预留多值表后缀空间: " + tableName);
        }
        return normalized;
    }

    private String validateIdentifier(String tableName) {
        if (!StringUtils.hasText(tableName)) {
            throw new IllegalArgumentException("实体物理表名不能为空");
        }
        String normalized = tableName.trim().toLowerCase(Locale.ROOT);
        try {
            return SqlIdentifierPolicy.validate(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "实体物理表名不合法: " + tableName,
                    exception);
        }
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
