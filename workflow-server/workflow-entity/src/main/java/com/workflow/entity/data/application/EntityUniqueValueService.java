package com.workflow.entity.data.application;

import com.workflow.core.error.BusinessConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * 以数据库唯一键预留动态实体的唯一字段值，关闭“先查询、后写入”的并发竞态窗口。
 * 调用方必须与业务记录写入处于同一事务；事务回滚时预留也会同步回滚。
 */
@Service
@RequiredArgsConstructor
public class EntityUniqueValueService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 原子替换一条记录持有的全部唯一值。
     *
     * @param entityCode 实体编码
     * @param recordId 业务记录ID，创建记录时也必须预先生成
     * @param values 字段编码到非空字段值的映射
     */
    public void replace(String entityCode, String recordId, Map<String, Object> values) {
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("唯一值预留必须提供记录ID");
        }
        release(entityCode, recordId);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String normalized = normalize(entry.getValue());
            if (normalized == null) {
                continue;
            }
            try {
                jdbcTemplate.update(
                        "INSERT INTO entity_unique_value "
                                + "(entity_code, field_code, value_hash, normalized_value, record_id) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        entityCode,
                        entry.getKey(),
                        sha256(normalized),
                        abbreviate(normalized),
                        recordId);
            } catch (DuplicateKeyException exception) {
                throw new BusinessConflictException(
                        "ENTITY_UNIQUE_VALUE_CONFLICT",
                        "字段值已被其他记录占用: " + entry.getKey());
            }
        }
    }

    /**
     * 释放逻辑删除或物理删除记录持有的唯一值，使该值可以再次使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    public void release(String entityCode, String recordId) {
        jdbcTemplate.update(
                "DELETE FROM entity_unique_value WHERE entity_code = ? AND record_id = ?",
                entityCode,
                recordId);
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化实体唯一值的原始输入，结果供调用方继续使用
     * @return 规范化后的实体唯一值文本，供调用方比较或展示
     */
    static String normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text.toLowerCase(Locale.ROOT);
    }

    /**
     * 截断实体唯一值；结果供调用方的后续步骤使用。
     *
     * @param value 待截断实体唯一值的原始输入，结果供调用方继续使用
     * @return 截断后的实体唯一值文本，供调用方比较或展示
     */
    private static String abbreviate(String value) {
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    /**
     * 计算输入内容的 SHA-256 摘要，供后续签名或幂等键使用。
     *
     * @param value 待处理{@code sha256}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code sha256}文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
