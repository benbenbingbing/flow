package com.workflow.entity.data.application;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.jdbc.SQL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 动态表管理服务
 * 负责创建、修改实体数据表
 */
@Slf4j
@Service
public class DynamicTableService {

    private static final int MYSQL_IDENTIFIER_LIMIT = SqlIdentifierPolicy.MAX_LENGTH;
    private static final int MAX_VARCHAR_LENGTH = 4096;
    private static final int MAX_DECIMAL_PRECISION = 65;
    private static final int MAX_DECIMAL_SCALE = 30;
    private static final int MAX_DEFAULT_LENGTH = 4096;
    private static final Set<String> BASE_COLUMNS = Set.of(
            "id", "data_no", "title", "name", "code", "status",
            "process_instance_id", "process_start_time", "process_end_time",
            "current_task_id", "current_task_name", "current_task_assignee",
            "submitter_id", "submitter_name", "submit_time", "dept_id",
            "create_by", "update_by", "create_time", "update_time", "deleted");
    private static final Set<String> SYSTEM_FIELD_CODES = Set.of(
            "name", "code", "status", "processInstanceId", "processInstance_id",
            "processStartTime", "process_startTime", "processStart_time",
            "processEndTime", "process_endTime", "processEnd_time",
            "submitterId", "submitter_id", "submitterName", "submitter_name",
            "deptId", "dept_id");
    private final JdbcTemplate jdbcTemplate;
    private final EntityFieldMapper entityFieldMapper;
    private final EntityPhysicalTableResolver tableResolver;
    private final SchemaDdlExecutor schemaDdlExecutor;

    @Autowired
    public DynamicTableService(
            JdbcTemplate jdbcTemplate,
            EntityFieldMapper entityFieldMapper,
            EntityPhysicalTableResolver tableResolver,
            SchemaDdlExecutor schemaDdlExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityFieldMapper = entityFieldMapper;
        this.tableResolver = tableResolver;
        this.schemaDdlExecutor = schemaDdlExecutor;
    }

    /**
     * 获取当前数据库中表的列信息
     */
    public List<ColumnInfo> getTableColumns(String entityCode) {
        String tableName = getTableName(entityCode);
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE, COLUMN_DEFAULT " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            ColumnInfo col = new ColumnInfo();
            col.setName(rs.getString("COLUMN_NAME"));
            col.setType(rs.getString("DATA_TYPE"));
            long length = rs.getLong("CHARACTER_MAXIMUM_LENGTH");
            col.setLength(rs.wasNull() ? null : length);
            col.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
            col.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
            return col;
        }, tableName);
    }

    /**
     * 获取实体数据表名
     */
    public String getTableName(String entityCode) {
        return tableResolver.resolve(entityCode);
    }

    /**
     * 检查表是否存在
     */
    public boolean tableExists(String entityCode) {
        String tableName = getTableName(entityCode);
        String sql = "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE table_schema = DATABASE() AND table_name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        return count != null && count > 0;
    }

    /**
     * 创建实体数据表
     * @return 返回创建表的DDL语句
     */
    @Transactional(rollbackFor = Exception.class)
    public String createEntityTable(EntityDefinition entityDefinition) {
        String entityCode = entityDefinition.getEntityCode();
        String tableName = tableResolver.resolve(entityDefinition);

        if (tableExists(entityCode)) {
            ensureMultiValueTable(tableName);
            log.info("表 {} 已存在，跳过创建", tableName);
            return null;
        }

        // 获取实体字段定义
        List<EntityField> fields = entityFieldMapper.findByEntityId(entityDefinition.getId());

        // 构建建表SQL
        String createTableSql = buildCreateTableSql(tableName, fields, entityDefinition.getEntityName());
        
        log.info("创建实体数据表: {}", tableName);
        schemaDdlExecutor.execute(createTableSql);
        ensureMultiValueTable(tableName);
        
        log.info("实体数据表 {} 创建成功", tableName);
        return createTableSql;
    }
    
    /**
     * 同步实体字段变更到数据库表
     * 用于发布时同步新增或修改的字段
     * @return 返回执行的DDL语句列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> syncEntityTableStructure(EntityDefinition entityDefinition) {
        String tableName = tableResolver.resolve(entityDefinition);
        List<String> plan = planEntityTableStructure(entityDefinition);
        // 计划先完整固化，再按顺序执行；任何一步失败都向上抛出并保留失败状态，禁止“告警后继续发布”。
        for (String ddl : plan) {
            schemaDdlExecutor.execute(ddl);
        }
        ensureMultiValueTable(tableName);
        return plan;
    }

    /**
     * 生成一次不可变的结构变更计划，不执行 DDL。
     * 仅当 information_schema 与目标字段定义不一致时才生成 MODIFY，避免无变更重复锁表。
     */
    public List<String> planEntityTableStructure(EntityDefinition entityDefinition) {
        String entityCode = entityDefinition.getEntityCode();
        String tableName = tableResolver.resolve(entityDefinition);
        List<EntityField> fields = entityFieldMapper.findByEntityId(entityDefinition.getId());
        if (!tableExists(entityCode)) {
            return List.of(buildCreateTableSql(tableName, fields, entityDefinition.getEntityName()));
        }
        Map<String, ColumnInfo> existing = getTableColumns(entityCode).stream()
                .collect(Collectors.toMap(ColumnInfo::getName, Function.identity(), (left, right) -> left));
        List<String> plan = new ArrayList<>();
        for (EntityField field : fields) {
            if (!isPhysicalDynamicField(field)) {
                continue;
            }
            String columnName = columnName(field);
            ColumnInfo actual = existing.get(columnName);
            String action = actual == null ? " ADD COLUMN " : " MODIFY COLUMN ";
            if (actual == null || (Boolean.TRUE.equals(field.getIsPublished())
                    && !columnMatches(actual, field))) {
                plan.add("ALTER TABLE " + quoteIdentifier(tableName)
                        + action + buildColumnDefinition(field));
            }
        }
        return plan;
    }

    /** 返回目标元数据与实际表之间可供发布拦截和修复预览使用的真实列级差异。 */
    public List<String> inspectSchemaDrift(EntityDefinition entity, List<EntityField> fields) {
        if (!tableExists(entity.getEntityCode())) {
            return List.of("物理表不存在: " + getTableName(entity.getEntityCode()));
        }
        Set<String> expected = expectedColumns(fields);
        Set<String> actual = getTableColumns(entity.getEntityCode()).stream()
                .map(ColumnInfo::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> drift = new ArrayList<>();
        expected.stream().filter(column -> !actual.contains(column)).sorted()
                .forEach(column -> drift.add("缺少列: " + column));
        actual.stream().filter(column -> !expected.contains(column)).sorted()
                .forEach(column -> drift.add("存在元数据未声明列: " + column));
        return drift;
    }

    /** 目标指纹由规范化的目标列集合生成，用于确认发布计划未在执行期间被替换。 */
    public String targetSchemaFingerprint(EntityDefinition entity, List<EntityField> fields) {
        return fingerprint(expectedColumns(fields));
    }

    /** 实际指纹直接读取 information_schema；表不存在时返回稳定的缺失指纹。 */
    public String actualSchemaFingerprint(String entityCode) {
        if (!tableExists(entityCode)) {
            return sha256("TABLE_MISSING:" + getTableName(entityCode));
        }
        Set<String> columns = getTableColumns(entityCode).stream()
                .map(ColumnInfo::getName)
                .collect(Collectors.toSet());
        return fingerprint(columns);
    }

    /** 使用 information_schema 的表统计值评估 DDL 影响量级。 */
    public long estimateRows(String entityCode) {
        if (!tableExists(entityCode)) {
            return 0L;
        }
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COALESCE(TABLE_ROWS, 0) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Long.class,
                getTableName(entityCode));
        return rows == null ? 0L : rows;
    }

    /**
     * 发布唯一约束前扫描存量重复值。运行期并发写入仍由 entity_unique_value 主键串行化。
     */
    public List<String> scanUniqueConflicts(EntityDefinition entity, List<EntityField> fields) {
        if (!tableExists(entity.getEntityCode())) {
            return List.of();
        }
        String tableName = quoteIdentifier(getTableName(entity.getEntityCode()));
        List<String> conflicts = new ArrayList<>();
        for (EntityField field : fields) {
            if (!Boolean.TRUE.equals(field.getIsUnique()) || !isPhysicalDynamicField(field)) {
                continue;
            }
            String column = quoteIdentifier(columnName(field));
            String sql = "SELECT CAST(" + column + " AS CHAR) value_text, COUNT(*) duplicate_count FROM "
                    + tableName + " WHERE `deleted` = 0 AND " + column + " IS NOT NULL "
                    + "GROUP BY " + column + " HAVING COUNT(*) > 1 LIMIT 5";
            jdbcTemplate.query(sql, rs -> {
                conflicts.add(field.getFieldName() + "=" + rs.getString("value_text")
                        + "（" + rs.getLong("duplicate_count") + "条）");
            });
        }
        return conflicts;
    }

    /**
     * 删除实体数据表
     */
    @Transactional(rollbackFor = Exception.class)
    public void dropEntityTable(String entityCode) {
        String tableName = getTableName(entityCode);
        String sql = "DROP TABLE IF EXISTS " + quoteIdentifier(tableName);
        schemaDdlExecutor.execute(sql);
        schemaDdlExecutor.execute("DROP TABLE IF EXISTS "
                + quoteIdentifier(deriveMultiValueTableName(tableName)));
        log.info("实体数据表 {} 已删除", tableName);
    }

    /**
     * 为实体添加字段（实体定义修改后）
     */
    @Transactional(rollbackFor = Exception.class)
    public void addColumn(String entityCode, EntityField field) {
        String tableName = getTableName(entityCode);
        if (!tableExists(entityCode) || isMultiValueField(field)) {
            return;
        }

        String columnDef = buildColumnDefinition(field);
        String sql = "ALTER TABLE " + quoteIdentifier(tableName) + " ADD COLUMN " + columnDef;
        
        schemaDdlExecutor.execute(sql);
        log.info("为表 {} 添加字段: {}", tableName, field.getFieldCode());
    }

    /**
     * 修改字段
     */
    @Transactional(rollbackFor = Exception.class)
    public void modifyColumn(String entityCode, EntityField field) {
        String tableName = getTableName(entityCode);
        if (!tableExists(entityCode) || isMultiValueField(field)) {
            return;
        }

        String columnDef = buildColumnDefinition(field);
        String sql = "ALTER TABLE " + quoteIdentifier(tableName) + " MODIFY COLUMN " + columnDef;
        
        schemaDdlExecutor.execute(sql);
        log.info("修改表 {} 字段: {}", tableName, field.getFieldCode());
    }

    /**
     * 删除字段
     */
    @Transactional(rollbackFor = Exception.class)
    public void dropColumn(String entityCode, String columnName) {
        String tableName = getTableName(entityCode);
        if (!tableExists(entityCode)) {
            return;
        }

        String sql = "ALTER TABLE " + quoteIdentifier(tableName)
                + " DROP COLUMN " + quoteIdentifier(columnName);
        schemaDdlExecutor.execute(sql);
        log.info("删除表 {} 字段: {}", tableName, columnName);
    }

    /**
     * 构建建表SQL
     */
    private String buildCreateTableSql(String tableName, List<EntityField> fields, String entityName) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(quoteIdentifier(tableName)).append(" (\n");
        
        // 基础字段
        sql.append("  `id` VARCHAR(64) NOT NULL COMMENT '主键ID',\n");
        sql.append("  `data_no` VARCHAR(100) DEFAULT NULL COMMENT '业务单号',\n");
        sql.append("  `title` VARCHAR(500) DEFAULT NULL COMMENT '数据标题',\n");
        sql.append("  `name` VARCHAR(200) DEFAULT NULL COMMENT '数据名称',\n");
        sql.append("  `code` VARCHAR(100) DEFAULT NULL COMMENT '数据编码',\n");
        sql.append("  `status` VARCHAR(50) DEFAULT NULL COMMENT '数据状态',\n");
        sql.append("  `process_instance_id` VARCHAR(64) DEFAULT NULL COMMENT '流程实例ID',\n");
        sql.append("  `process_start_time` DATETIME DEFAULT NULL COMMENT '流程开始时间',\n");
        sql.append("  `process_end_time` DATETIME DEFAULT NULL COMMENT '流程结束时间',\n");
        sql.append("  `current_task_id` VARCHAR(64) DEFAULT NULL COMMENT '当前任务ID',\n");
        sql.append("  `current_task_name` VARCHAR(200) DEFAULT NULL COMMENT '当前任务名称',\n");
        sql.append("  `current_task_assignee` VARCHAR(64) DEFAULT NULL COMMENT '当前任务审批人',\n");
        sql.append("  `submitter_id` VARCHAR(64) DEFAULT NULL COMMENT '提交人ID',\n");
        sql.append("  `submitter_name` VARCHAR(100) DEFAULT NULL COMMENT '提交人姓名',\n");
        sql.append("  `submit_time` DATETIME DEFAULT NULL COMMENT '提交时间',\n");
        sql.append("  `dept_id` VARCHAR(64) DEFAULT NULL COMMENT '所属部门ID（数据权限用）',\n");
        sql.append("  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',\n");
        sql.append("  `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',\n");
        sql.append("  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n");
        sql.append("  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n");
        sql.append("  `deleted` TINYINT DEFAULT 0 COMMENT '是否删除（0否/1是）',\n");
        
        // 动态字段（跳过系统字段和子表单字段）
        for (EntityField field : fields) {
            // 跳过子表单字段（子表单有独立表）
            if (isSubFormField(field) || isMultiValueField(field)) {
                continue;
            }
            // 跳过系统字段（已在基础字段中定义）
            if (Boolean.TRUE.equals(field.getIsSystem()) || 
                SYSTEM_FIELD_CODES.contains(field.getFieldCode())) {
                continue;
            }
            sql.append("  ").append(buildColumnDefinition(field)).append(",\n");
        }
        
        // 基线索引随 CREATE TABLE 原子创建，避免建表成功但后续索引步骤遗漏。
        sql.append("  KEY `").append(indexName(tableName, "status")).append("` (`status`),\n");
        sql.append("  KEY `").append(indexName(tableName, "process")).append("` (`process_instance_id`),\n");
        sql.append("  KEY `").append(indexName(tableName, "deleted")).append("` (`deleted`),\n");
        sql.append("  KEY `").append(indexName(tableName, "created")).append("` (`create_time`),\n");
        // 主键
        sql.append("  PRIMARY KEY (`id`)\n");
        String comment = entityName != null && !entityName.isEmpty() ? entityName : tableName;
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='")
                .append(escapeSqlLiteral(comment))
                .append("';");
        
        return sql.toString();
    }

    /**
     * 构建字段定义
     */
    static String buildColumnDefinition(EntityField field) {
        if (field == null || field.getFieldType() == null) {
            throw new IllegalArgumentException("动态字段及字段类型不能为空");
        }
        StringBuilder col = new StringBuilder();
        String columnName = field.getDbColumnName() != null && !field.getDbColumnName().isEmpty() 
            ? field.getDbColumnName() 
            : field.getFieldCode();
        col.append(quoteIdentifier(columnName)).append(" ");
        
        // 根据字段类型确定数据库类型
        String dbType = getDbType(field);
        col.append(dbType);
        
        // 程序级动态控制必填/唯一，数据库列统一可空、不建唯一索引
        col.append(buildDefaultClause(field));
        
        // 注释
        col.append(" COMMENT '");
        String comment = field.getFieldName() != null ? field.getFieldName() : field.getFieldCode();
        // 处理单引号转义
        col.append(escapeSqlLiteral(comment));
        col.append("'");
        
        return col.toString();
    }

    static String buildDefaultClause(EntityField field) {
        String defaultValue = field.getDefaultValue();
        if (defaultValue == null || defaultValue.isBlank()) {
            return " DEFAULT NULL";
        }
        if (defaultValue.length() > MAX_DEFAULT_LENGTH) {
            throw new IllegalArgumentException(
                    "字段 " + field.getFieldCode() + " 的默认值超过长度限制");
        }

        if (field.getFieldType() == EntityField.FieldType.BOOLEAN) {
            String normalized = defaultValue.trim();
            if ("true".equalsIgnoreCase(normalized) || "1".equals(normalized)) {
                return " DEFAULT 1";
            }
            if ("false".equalsIgnoreCase(normalized) || "0".equals(normalized)) {
                return " DEFAULT 0";
            }
            throw new IllegalArgumentException(
                    "布尔字段 " + field.getFieldCode() + " 的默认值必须是 true、false、1 或 0");
        }

        return " DEFAULT '" + escapeSqlLiteral(defaultValue) + "'";
    }

    /**
     * 获取数据库字段类型
     */
    static String getDbType(EntityField field) {
        if (field == null || field.getFieldType() == null) {
            throw new IllegalArgumentException("动态字段及字段类型不能为空");
        }
        // Database types are always derived server-side. Client dbType is metadata only.
        switch (field.getFieldType()) {
            case STRING:
            case SELECT:
            case RADIO:
            case USER:
            case DEPT:
            case REFERENCE:
                int length = bounded(
                        field.getFieldLength(), 200, 1, MAX_VARCHAR_LENGTH, "字段长度");
                return "VARCHAR(" + length + ")";
            case TEXT:
            case RICH_TEXT:
                return "TEXT";
            case INTEGER:
                return "INT";
            case LONG:
                return "BIGINT";
            case DECIMAL:
                int prec = bounded(
                        field.getFieldLength(), 18, 1, MAX_DECIMAL_PRECISION, "DECIMAL 精度");
                int scale = bounded(
                        field.getFieldPrecision(), 2, 0, MAX_DECIMAL_SCALE, "DECIMAL 小数位数");
                if (scale > prec) {
                    throw new IllegalArgumentException("DECIMAL 小数位数不能大于精度");
                }
                return "DECIMAL(" + prec + "," + scale + ")";
            case DATE:
                return "DATE";
            case DATETIME:
                return "DATETIME";
            case BOOLEAN:
                return "TINYINT(1)";
            case MULTI_SELECT:
            case CHECKBOX:
                return "VARCHAR(500)";
            case FILE:
            case IMAGE:
                return "TEXT";
            case MULTI_REFERENCE:
                return "LONGTEXT";
            default:
                return "VARCHAR(255)";
        }
    }

    /**
     * 是否为子表单字段
     */
    private boolean isSubFormField(EntityField field) {
        return field.getFieldType() == EntityField.FieldType.SUB_FORM 
                || field.getFieldType() == EntityField.FieldType.SUB_LIST;
    }

    private boolean isMultiValueField(EntityField field) {
        if (field.getFieldType() == EntityField.FieldType.MULTI_REFERENCE) {
            return field.getRefEntityId() != null && !field.getRefEntityId().isBlank();
        }
        return (field.getFieldType() == EntityField.FieldType.MULTI_SELECT
                || field.getFieldType() == EntityField.FieldType.CHECKBOX)
                && field.getDictType() != null
                && !field.getDictType().isBlank();
    }

    private boolean isPhysicalDynamicField(EntityField field) {
        return !Boolean.TRUE.equals(field.getIsSystem())
                && !SYSTEM_FIELD_CODES.contains(field.getFieldCode())
                && !isSubFormField(field)
                && !isMultiValueField(field);
    }

    private String columnName(EntityField field) {
        return field.getDbColumnName() != null && !field.getDbColumnName().isBlank()
                ? validateIdentifier(field.getDbColumnName())
                : validateIdentifier(field.getFieldCode());
    }

    private boolean columnMatches(ColumnInfo actual, EntityField field) {
        String expectedType = getDbType(field).toLowerCase(Locale.ROOT);
        String expectedBase = expectedType.contains("(")
                ? expectedType.substring(0, expectedType.indexOf('(')) : expectedType;
        if (!expectedBase.equals(actual.getType().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (expectedBase.equals("varchar")) {
            int start = expectedType.indexOf('(') + 1;
            int end = expectedType.indexOf(')');
            long expectedLength = Long.parseLong(expectedType.substring(start, end));
            return actual.getLength() != null && actual.getLength() == expectedLength;
        }
        return true;
    }

    private Set<String> expectedColumns(List<EntityField> fields) {
        Set<String> expected = new LinkedHashSet<>(BASE_COLUMNS);
        for (EntityField field : fields == null ? List.<EntityField>of() : fields) {
            if (isPhysicalDynamicField(field)) {
                expected.add(columnName(field));
            }
        }
        return expected;
    }

    private static String fingerprint(Set<String> columns) {
        String canonical = columns.stream().sorted(Comparator.naturalOrder())
                .collect(Collectors.joining("\n"));
        return sha256(canonical);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    public String getMultiValueTableName(String entityCode) {
        return deriveMultiValueTableName(getTableName(entityCode));
    }

    public void ensureEntityMultiValueTable(String entityCode) {
        ensureMultiValueTable(getTableName(entityCode));
    }

    private String deriveMultiValueTableName(String tableName) {
        String multiTableName = tableName + "_multi";
        if (multiTableName.length() > MYSQL_IDENTIFIER_LIMIT) {
            throw new IllegalArgumentException("实体多值表名超过数据库限制: " + multiTableName);
        }
        return validateIdentifier(multiTableName);
    }

    private void ensureMultiValueTable(String tableName) {
        String multiTableName = deriveMultiValueTableName(tableName);
        schemaDdlExecutor.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                  `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
                  `record_id` VARCHAR(64) NOT NULL COMMENT '主记录ID',
                  `field_code` VARCHAR(100) NOT NULL COMMENT '字段编码',
                  `target_entity_id` VARCHAR(64) NOT NULL COMMENT '目标实体ID',
                  `target_record_id` VARCHAR(64) NOT NULL COMMENT '目标记录ID',
                  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '选择顺序',
                  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
                  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_record_field_target` (`record_id`,`field_code`,`target_entity_id`,`target_record_id`,`deleted`),
                  KEY `idx_record_field` (`record_id`,`field_code`,`deleted`),
                  KEY `idx_field_target` (`field_code`,`target_entity_id`,`target_record_id`,`deleted`),
                  KEY `idx_target` (`target_entity_id`,`target_record_id`,`deleted`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体多值引用表'
                """.formatted(quoteIdentifier(multiTableName)));
    }

    /**
     * 创建索引
     */
    private void createIndexes(String tableName, List<EntityField> fields) {
        // 常用查询字段索引
        String quotedTable = quoteIdentifier(tableName);
        schemaDdlExecutor.execute("CREATE INDEX " + quoteIdentifier(indexName(tableName, "status"))
                + " ON " + quotedTable + " (`status`)");
        schemaDdlExecutor.execute("CREATE INDEX " + quoteIdentifier(indexName(tableName, "process"))
                + " ON " + quotedTable + " (`process_instance_id`)");
        schemaDdlExecutor.execute("CREATE INDEX " + quoteIdentifier(indexName(tableName, "deleted"))
                + " ON " + quotedTable + " (`deleted`)");
        schemaDdlExecutor.execute("CREATE INDEX " + quoteIdentifier(indexName(tableName, "created"))
                + " ON " + quotedTable + " (`create_time`)");
        
        // 唯一性改为程序级动态校验，不再根据 isUnique 创建数据库唯一索引
    }

    /**
     * 为字段创建索引
     */
    private void createIndexForColumn(String tableName, String columnName, boolean unique) {
        try {
            String indexName = indexName(
                    tableName, (unique ? "uniq_" : "idx_") + columnName);
            String sql;
            if (unique) {
                sql = "CREATE UNIQUE INDEX " + quoteIdentifier(indexName)
                        + " ON " + quoteIdentifier(tableName)
                        + " (" + quoteIdentifier(columnName) + ")";
            } else {
                sql = "CREATE INDEX " + quoteIdentifier(indexName)
                        + " ON " + quoteIdentifier(tableName)
                        + " (" + quoteIdentifier(columnName) + ")";
            }
            schemaDdlExecutor.execute(sql);
        } catch (Exception e) {
            log.warn("创建索引失败: {}.{}, 原因: {}", tableName, columnName, e.getMessage());
        }
    }
    
    /**
     * 构建建表SQL预览（不执行）
     */
    public String buildCreateTableSqlPreview(String entityCode, List<EntityField> fields, String entityName) {
        String tableName = getTableName(entityCode);
        return buildCreateTableSql(tableName, fields, entityName);
    }
    
    /**
     * 构建添加字段的SQL预览列表（不执行）
     */
    public List<String> buildAddColumnSqlPreviews(String entityCode, List<EntityField> fields) {
        String tableName = getTableName(entityCode);
        List<String> ddls = new ArrayList<>();
        
        for (EntityField field : fields) {
            if (isSubFormField(field) || isMultiValueField(field)) {
                continue;
            }
            String columnDef = buildColumnDefinition(field);
            String sql = "ALTER TABLE " + quoteIdentifier(tableName)
                    + " ADD COLUMN " + columnDef;
            ddls.add(sql);
        }
        
        return ddls;
    }

    static String quoteIdentifier(String identifier) {
        return "`" + validateIdentifier(identifier) + "`";
    }

    static String validateIdentifier(String identifier) {
        return SqlIdentifierPolicy.validate(identifier);
    }

    private static String escapeSqlLiteral(String value) {
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("SQL 文本不能包含 NUL 字符");
        }
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    private static int bounded(
            Integer configured,
            int defaultValue,
            int minimum,
            int maximum,
            String label) {
        int value = configured == null ? defaultValue : configured;
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    label + "必须在 " + minimum + " 到 " + maximum + " 之间");
        }
        return value;
    }

    private static String indexName(String tableName, String suffix) {
        String candidate = "idx_" + validateIdentifier(tableName) + "_" + suffix;
        if (candidate.length() > MYSQL_IDENTIFIER_LIMIT) {
            candidate = candidate.substring(0, MYSQL_IDENTIFIER_LIMIT);
        }
        return validateIdentifier(candidate);
    }

    /**
     * 列信息内部类
     */
    @lombok.Data
    public static class ColumnInfo {
        private String name;
        private String type;
        private Long length;
        private boolean nullable;
        private String defaultValue;
    }
}
