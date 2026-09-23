package com.workflow.entity.definition.application;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.workflow.core.database.port.SchemaMetadataPort;
import com.workflow.integration.database.api.schema.SchemaColumnMetadata;
import java.sql.Types;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 将平台 sys_* 物理表同步为只读实体目录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemEntityCatalogService {

    private static final Map<String, String> SYSTEM_TABLE_NAMES = Map.ofEntries(
            Map.entry("sys_user", "系统用户"),
            Map.entry("sys_role", "系统角色"),
            Map.entry("sys_organization", "组织部门"),
            Map.entry("sys_group", "用户组"),
            Map.entry("sys_menu", "菜单权限"),
            Map.entry("sys_dict", "字典类型"),
            Map.entry("sys_dict_item", "字典明细"),
            Map.entry("sys_user_role", "用户角色关系"),
            Map.entry("sys_role_menu", "角色菜单关系"),
            Map.entry("sys_user_group", "用户组成员关系")
    );

    private final SchemaMetadataPort metadata;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper fieldMapper;

    /**
     * 扫描数据库中以 sys_ 开头的系统表，登记为只读系统实体目录并同步列字段。
     *
     * @return 本次同步登记的系统表数量
     */
    @Transactional
    public int synchronize() {
        var tables = metadata.tables();
        int synchronizedCount = 0;
        for (var table : tables) {
            String tableName = table.name();
            if (!SYSTEM_TABLE_NAMES.containsKey(tableName)) {
                continue;
            }
            String tableComment = text(table.comment());
            EntityDefinition definition = definitionMapper.findByEntityCode(tableName).orElse(null);
            if (definition == null) {
                definition = new EntityDefinition();
                definition.setId(stableId("SYSTEM_ENTITY:" + tableName));
                definition.setEntityCode(tableName);
                definition.setEntityName(resolveTableName(tableName, tableComment));
                definition.setDescription("平台系统表目录：" + tableName);
                definition.setPhysicalTableName(tableName);
                definition.setLifecycleMode(EntityDefinition.LifecycleMode.STANDALONE);
                definition.setStorageMode(EntityDefinition.StorageMode.SYSTEM);
                definition.setStatus(EntityDefinition.Status.PUBLISHED);
                definition.setCreatedBy("system");
                definition.setCreatedAt(LocalDateTime.now());
                definition.setUpdatedAt(LocalDateTime.now());
                definitionMapper.insert(definition);
            } else if (definition.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
                definition.setEntityName(resolveTableName(tableName, tableComment));
                definition.setDescription("平台系统表目录：" + tableName);
                definition.setPhysicalTableName(tableName);
                definition.setLifecycleMode(EntityDefinition.LifecycleMode.STANDALONE);
                definition.setProcessDefinitionId(null);
                definition.setStatus(EntityDefinition.Status.PUBLISHED);
                definitionMapper.updateById(definition);
            } else {
                log.warn("系统表实体编码与动态实体冲突，跳过登记: {}", tableName);
                continue;
            }
            synchronizeFields(definition, tableName);
            synchronizedCount++;
        }
        return synchronizedCount;
    }

    /**
     * 读取系统表列信息并同步为实体字段（标记为系统字段、不可编辑）。
     *
     * @param definition 定义，作为 {@code fieldMapper.findByEntityIdAndFieldCode} 的输入影响后续处理
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     */
    private void synchronizeFields(EntityDefinition definition, String tableName) {
        var columns = metadata.columns(tableName);
        for (var column : columns) {
            String columnName = column.name();
            EntityField field = fieldMapper.findByEntityIdAndFieldCode(definition.getId(), columnName);
            if (field == null) {
                field = new EntityField();
                field.setId(stableId("SYSTEM_FIELD:" + tableName + ":" + columnName));
                field.setEntityId(definition.getId());
                field.setFieldCode(columnName);
            }
            String comment = text(column.comment());
            field.setFieldName(resolveComment(comment, columnName));
            field.setFieldType(resolveFieldType(column));
            field.setDbType(column.typeName());
            field.setFieldLength(integer(column.length()));
            field.setFieldPrecision(column.scale());
            field.setDbColumnName(columnName);
            field.setIsRequired(!column.nullable());
            field.setIsUnique(column.singleColumnUnique());
            field.setSortOrder(column.ordinal());
            field.setIsSystem(true);
            field.setEditable(false);
            field.setIsPublished(true);
            if (fieldMapper.findByEntityIdAndFieldCode(definition.getId(), columnName) == null) {
                fieldMapper.insert(field);
            } else {
                fieldMapper.updateById(field);
            }
        }
    }

    /**
     * JDBC 标准类型避免把 Oracle NUMBER、CLOB 等误识别为普通字符串。
     *
     * @param column 列，作为 {@code equalsIgnoreCase} 的输入影响后续处理
     * @return 解析后的字段类型结果，供调用方继续处理
     */
    private EntityField.FieldType resolveFieldType(SchemaColumnMetadata column) {
        return switch (column.jdbcType()) {
            case Types.BOOLEAN, Types.BIT, Types.TINYINT -> EntityField.FieldType.BOOLEAN;
            case Types.SMALLINT, Types.INTEGER -> EntityField.FieldType.INTEGER;
            case Types.BIGINT -> EntityField.FieldType.LONG;
            // NUMBER(1) 不必然是布尔值，NUMBER(10) 也可能超过 Integer；目录同步不猜测窄类型。
            case Types.DECIMAL, Types.NUMERIC -> EntityField.FieldType.DECIMAL;
            case Types.FLOAT, Types.DOUBLE, Types.REAL -> EntityField.FieldType.DECIMAL;
            case Types.DATE -> EntityField.FieldType.DATE;
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> EntityField.FieldType.DATETIME;
            case Types.CLOB, Types.NCLOB, Types.LONGVARCHAR, Types.LONGNVARCHAR -> EntityField.FieldType.TEXT;
            default -> "json".equalsIgnoreCase(column.typeName()) ? EntityField.FieldType.TEXT : EntityField.FieldType.STRING;
        };
    }

    /**
     * 生成稳定ID文本，供后续匹配或展示。
     *
     * @param source 待处理稳定ID的原始输入，结果供调用方继续使用
     * @return 处理后的稳定ID文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private String stableId(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return new BigInteger(1, digest).shiftRight(196).toString();
        } catch (Exception exception) {
            throw new IllegalStateException("生成系统实体稳定ID失败", exception);
        }
    }

    /**
     * 解析表名称；输出作为后续校验或处理的输入。
     *
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     * @param tableComment 表注释，作为 {@code SYSTEM_TABLE_NAMES.getOrDefault} 的输入影响后续处理
     * @return 解析后的表名称文本，供调用方比较或展示
     */
    private String resolveTableName(String tableName, String tableComment) {
        return SYSTEM_TABLE_NAMES.getOrDefault(tableName, resolveComment(tableComment, tableName));
    }

    /**
     * 解析注释；输出作为后续校验或处理的输入。
     *
     * @param comment 注释，作为 {@code text} 的输入影响后续处理
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 解析后的注释文本，供调用方比较或展示
     */
    private String resolveComment(String comment, String fallback) {
        String normalized = text(comment);
        if (normalized == null) {
            return fallback;
        }
        if (!containsMojibakeMarker(normalized)) {
            return normalized;
        }
        String repaired = repairMojibake(normalized);
        return repaired == null ? fallback : repaired;
    }

    /**
     * 判断是否包含{@code mojibake}{@code marker}；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否包含{@code mojibake}{@code marker}的原始输入，结果供调用方继续使用
     * @return {@code mojibake}{@code marker}条件成立时为 true，否则为 false
     */
    private boolean containsMojibakeMarker(String value) {
        return value.chars().anyMatch(character -> "çèæåéäïð".indexOf(character) >= 0);
    }

    /**
     * 生成{@code repair}{@code mojibake}文本，供后续匹配或展示。
     *
     * @param value 待处理{@code repair}{@code mojibake}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code repair}{@code mojibake}文本，供调用方比较或展示
     */
    private String repairMojibake(String value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
        for (int index = 0; index < value.length(); index++) {
            Integer legacyByte = legacyByte(value.charAt(index));
            if (legacyByte == null) {
                return null;
            }
            bytes.write(legacyByte);
        }
        String repaired = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        return repaired.indexOf('\uFFFD') >= 0 ? null : repaired;
    }

    /**
     * 处理旧版{@code byte}，并将结果传给后续步骤。
     *
     * @param character {@code character}，供本方法处理旧版{@code byte}时使用
     * @return 处理后的旧版{@code byte}结果，供调用方继续处理
     */
    private Integer legacyByte(char character) {
        if (character <= 0xFF) {
            return (int) character;
        }
        return switch (character) {
            case '\u20AC' -> 0x80;
            case '\u201A' -> 0x82;
            case '\u0192' -> 0x83;
            case '\u201E' -> 0x84;
            case '\u2026' -> 0x85;
            case '\u2020' -> 0x86;
            case '\u2021' -> 0x87;
            case '\u02C6' -> 0x88;
            case '\u2030' -> 0x89;
            case '\u0160' -> 0x8A;
            case '\u2039' -> 0x8B;
            case '\u0152' -> 0x8C;
            case '\u017D' -> 0x8E;
            case '\u2018' -> 0x91;
            case '\u2019' -> 0x92;
            case '\u201C' -> 0x93;
            case '\u201D' -> 0x94;
            case '\u2022' -> 0x95;
            case '\u2013' -> 0x96;
            case '\u2014' -> 0x97;
            case '\u02DC' -> 0x98;
            case '\u2122' -> 0x99;
            case '\u0161' -> 0x9A;
            case '\u203A' -> 0x9B;
            case '\u0153' -> 0x9C;
            case '\u017E' -> 0x9E;
            case '\u0178' -> 0x9F;
            default -> null;
        };
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     */
    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        long number = ((Number) value).longValue();
        return number > Integer.MAX_VALUE ? null : (int) number;
    }
}
