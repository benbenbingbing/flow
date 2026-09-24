package com.workflow.entity.data.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 为最终业务编码追加数据库唯一占用，不替换或释放同记录的其他字段占用。
 * 业务事务回滚时占用一起回滚；已经成功保存的编码（包括已删除记录）不再重复分配。
 */
@Service
@RequiredArgsConstructor
public class EntityCodeReservationService {
    /** 独立于可编辑字段的命名空间，避免通用唯一字段重建/删除时释放业务编号。 */
    public static final String CODE_CLAIM_FIELD = "$record_code";
    private final JdbcTemplate jdbc;
    private final DatabaseQueryDialect dialect;
    private final DynamicTableService tables;

    /**
     * 先以唯一主键串行化同码写入，再检查上线前未建立占用的历史业务行。
     * 必须与 INSERT 使用同一事务；平台所有主/子新增入口都必须经过此方法。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void reserve(String entityCode, String recordId, String code) {
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        try {
            jdbc.update("INSERT INTO entity_unique_value (entity_code, field_code, value_hash, normalized_value, record_id) VALUES (?, ?, ?, ?, ?)",
                    entityCode, CODE_CLAIM_FIELD, hash(normalized), normalized, recordId);
        } catch (DuplicateKeyException exception) {
            throw conflict();
        }
        Long matches = jdbc.queryForObject("SELECT COUNT(*) FROM " + table(entityCode)
                + " WHERE LOWER(TRIM(" + dialect.quoteIdentifier("code") + ")) = ?", Long.class, normalized);
        if (matches != null && matches > 0) throw conflict();
    }

    /** 启用自定义模式前检查存量重复编码，不擅自修改历史编号；新建未发布实体无需扫描。 */
    public void validateExistingCodes(String entityCode) {
        if (!tables.tableExists(entityCode)) return;
        String column = "LOWER(TRIM(" + dialect.quoteIdentifier("code") + "))";
        var duplicates = jdbc.queryForList("SELECT " + column + " FROM " + table(entityCode)
                + " WHERE " + dialect.quoteIdentifier("code") + " IS NOT NULL AND " + column + " <> ''"
                + " GROUP BY " + column + " HAVING COUNT(*) > 1", String.class);
        if (!duplicates.isEmpty()) throw new BusinessConflictException("ENTITY_CODE_EXISTING_CONFLICT",
                "存在重复的历史编码，请先处理后再启用自定义生成");
    }

    private String table(String entityCode) {
        return dialect.quoteIdentifier(tables.getTableName(entityCode));
    }

    private BusinessConflictException conflict() {
        return new BusinessConflictException("ENTITY_CODE_CONFLICT", "生成的编码已被使用，请检查编码生成规则");
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
