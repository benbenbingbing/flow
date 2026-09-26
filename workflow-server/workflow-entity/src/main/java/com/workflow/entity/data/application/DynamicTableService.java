package com.workflow.entity.data.application;

import com.workflow.entity.data.application.port.SchemaDdlExecutor;

import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import lombok.extern.slf4j.Slf4j;
import com.workflow.integration.database.api.schema.SchemaDdlDialect;
import com.workflow.core.database.port.SchemaMetadataPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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

    private static final int IDENTIFIER_LIMIT = SqlIdentifierPolicy.MAX_LENGTH;
    private static final Set<String> BASE_COLUMNS = Set.of(
            "id", "name", "code", "status", "process_status",
            "process_instance_id", "process_start_time", "process_end_time",
            "current_task_id", "current_task_name", "current_task_assignee",
            "submitter_id", "submitter_name", "submit_time", "dept_id",
            "create_by", "update_by", "create_time", "update_time", "deleted");
    private final JdbcTemplate jdbcTemplate;
    private final EntityFieldMapper entityFieldMapper;
    private final EntityPhysicalTableResolver tableResolver;
    private final SchemaDdlExecutor schemaDdlExecutor;
    private final SchemaDdlDialect dialect;
    private final SchemaMetadataPort metadata;
    private final DatabaseQueryDialect queryDialect;

    /**
     * 初始化动态表服务，保存构造参数供后续方法使用。
     *
     * @param jdbcTemplate JDBC模板依赖，保存到当前对象供后续业务方法调用
     * @param entityFieldMapper 实体字段映射器依赖，保存到当前对象供后续业务方法调用
     * @param tableResolver 表解析器依赖，保存到当前对象供后续业务方法调用
     * @param schemaDdlExecutor 结构DDL执行器依赖，保存到当前对象供后续业务方法调用
     * @param dialect 方言依赖，保存到当前对象供后续业务方法调用
     * @param metadata 元数据依赖，保存到当前对象供后续业务方法调用
     * @param queryDialect 查询方言依赖，保存到当前对象供后续业务方法调用
     */
    @Autowired
    public DynamicTableService(
            JdbcTemplate jdbcTemplate,
            EntityFieldMapper entityFieldMapper,
            EntityPhysicalTableResolver tableResolver,
            SchemaDdlExecutor schemaDdlExecutor, SchemaDdlDialect dialect, SchemaMetadataPort metadata, DatabaseQueryDialect queryDialect) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityFieldMapper = entityFieldMapper;
        this.tableResolver = tableResolver;
        this.schemaDdlExecutor = schemaDdlExecutor;
        this.dialect = dialect;
        this.metadata = metadata;
        this.queryDialect = queryDialect;
    }

    /**
     * 获取当前数据库中表的列信息
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 列{@code info}集合，供调用方遍历或展示
     */
    public List<ColumnInfo> getTableColumns(String entityCode) {
        return getTableColumnsByName(getTableName(entityCode));
    }

    /**
     * 只接收经 tableResolver 校验的物理表名，查询结构时不再依赖实体元数据的事务可见性。
     *
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     * @return 列{@code info}集合，供调用方遍历或展示
     */
    private List<ColumnInfo> getTableColumnsByName(String tableName) {
        return metadata.columns(tableName).stream().map(actual -> {
            ColumnInfo col = new ColumnInfo();
            col.setName(actual.name());
            col.setType(actual.typeName());
            col.setLength(actual.length());
            col.setPrecision(actual.precision());
            col.setScale(actual.scale());
            col.setNullable(actual.nullable());
            col.setDefaultValue(actual.defaultValue());
            return col;
        }).toList();
    }

    /**
     * 获取实体数据表名
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 读取后的表名称文本，供调用方比较或展示
     */
    public String getTableName(String entityCode) {
        return tableResolver.resolve(entityCode);
    }

    /**
     * 检查表是否存在
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 表存在条件成立时为 true，否则为 false
     */
    public boolean tableExists(String entityCode) {
        return tableExistsByName(getTableName(entityCode));
    }

    /**
     * 物理表不存在是首次发布的正常状态；调用方负责先通过 tableResolver 校验表名。
     *
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     * @return 表存在名称条件成立时为 true，否则为 false
     */
    private boolean tableExistsByName(String tableName) {
        return metadata.tableExists(tableName);
    }

    /**
     * 创建实体数据表
     *
     * @param entityDefinition 实体定义，作为 {@code tableResolver.resolve} 的输入影响后续处理
     * @return 返回创建表的DDL语句
     */
    @Transactional(rollbackFor = Exception.class)
    public String createEntityTable(EntityDefinition entityDefinition) {
        String tableName = tableResolver.resolve(entityDefinition);

        if (tableExistsByName(tableName)) {
            ensureMultiValueTable(tableName);
            log.info("表 {} 已存在，跳过创建", tableName);
            return null;
        }

        // 获取实体字段定义
        List<EntityField> fields = entityFieldMapper.findByEntityId(entityDefinition.getId());

        // 构建建表SQL
        List<String> createPlan = buildCreateTablePlan(tableName, fields, entityDefinition.getEntityName());
        
        log.info("创建实体数据表: {}", tableName);
        executePlan(createPlan);
        ensureMultiValueTable(tableName);
        
        log.info("实体数据表 {} 创建成功", tableName);
        return String.join(";\n", createPlan);
    }
    
    /**
     * 同步实体字段变更到数据库表
     * 用于发布时同步新增或修改的字段
     *
     * @param entityDefinition 实体定义，作为 {@code tableResolver.resolve} 的输入影响后续处理
     * @return 返回执行的DDL语句列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> syncEntityTableStructure(EntityDefinition entityDefinition) {
        String tableName = tableResolver.resolve(entityDefinition);
        List<String> plan = planEntityTableStructure(entityDefinition);
        // 计划先完整固化，再按顺序执行；任何一步失败都向上抛出并保留失败状态，禁止“告警后继续发布”。
        executePlan(plan);
        ensureMultiValueTable(tableName);
        return plan;
    }

    /**
     * 生成一次不可变的结构变更计划，不执行 DDL。
     * 仅当实际结构与目标字段定义不一致时才生成改列语句，避免无变更重复锁表。
     *
     * @param entityDefinition 实体定义，作为 {@code tableResolver.resolve} 的输入影响后续处理
     * @return 动态表集合，供调用方遍历或展示
     */
    public List<String> planEntityTableStructure(EntityDefinition entityDefinition) {
        String tableName = tableResolver.resolve(entityDefinition);
        List<EntityField> fields = entityFieldMapper.findByEntityId(entityDefinition.getId());
        if (!tableExistsByName(tableName)) {
            return buildCreateTablePlan(tableName, fields, entityDefinition.getEntityName());
        }
        Map<String, ColumnInfo> existing = getTableColumnsByName(tableName).stream()
                .collect(Collectors.toMap(ColumnInfo::getName, Function.identity(), (left, right) -> left));
        List<String> plan = new ArrayList<>();
        for (EntityField field : fields) {
            if (!isPhysicalDynamicField(field)) {
                continue;
            }
            String columnName = columnName(field);
            ColumnInfo actual = existing.get(columnName);
            if (actual == null || (Boolean.TRUE.equals(field.getIsPublished())
                    && !columnMatches(actual, field))) {
                plan.addAll(actual == null ? dialect.addColumn(tableName, EntityTableDefinitionFactory.fieldColumn(field))
                        : dialect.modifyColumn(tableName, EntityTableDefinitionFactory.fieldColumn(field)));
            }
        }
        return plan;
    }

    /**
     * 返回目标元数据与实际表的列级差异。使用调用方已加载的定义，允许在独立事务中
     * 检查同一迁移尚未提交的新实体；表名合法性和系统实体限制仍由 tableResolver 校验。
     *
     * @param entity 实体，作为 {@code tableResolver.resolve} 的输入影响后续处理
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @return 动态表集合，供调用方遍历或展示
     */
    public List<String> inspectSchemaDrift(EntityDefinition entity, List<EntityField> fields) {
        String tableName = tableResolver.resolve(entity);
        if (!tableExistsByName(tableName)) {
            return List.of("物理表不存在: " + tableName);
        }
        Set<String> expected = expectedColumns(fields);
        Set<String> actual = getTableColumnsByName(tableName).stream()
                .map(ColumnInfo::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> drift = new ArrayList<>();
        expected.stream().filter(column -> !actual.contains(column)).sorted()
                .forEach(column -> drift.add("缺少列: " + column));
        actual.stream().filter(column -> !expected.contains(column)).sorted()
                .forEach(column -> drift.add("存在元数据未声明列: " + column));
        return drift;
    }

    /**
     * 目标指纹由规范化的目标列集合生成，用于确认发布计划未在执行期间被替换。
     *
     * @param entity 实体，供本方法处理目标结构指纹时使用
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @return 处理后的目标结构指纹文本，供调用方比较或展示
     */
    public String targetSchemaFingerprint(EntityDefinition entity, List<EntityField> fields) {
        return fingerprint(expectedColumns(fields));
    }

    /**
     * 实际指纹直接读取物理结构；表不存在时返回稳定的缺失指纹。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 处理后的实际结构指纹文本，供调用方比较或展示
     */
    public String actualSchemaFingerprint(String entityCode) {
        return actualSchemaFingerprintByName(getTableName(entityCode));
    }

    /**
     * 根据已加载的实体读取实际结构指纹，供新实体尚未提交时的独立结构校验使用。
     *
     * @param entity 实体，作为 {@code actualSchemaFingerprintByName} 的输入影响后续处理
     * @return 处理后的实际结构指纹文本，供调用方比较或展示
     */
    public String actualSchemaFingerprint(EntityDefinition entity) {
        return actualSchemaFingerprintByName(tableResolver.resolve(entity));
    }

    /**
     * 生成实际结构指纹名称文本，供后续匹配或展示。
     *
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     * @return 处理后的实际结构指纹名称文本，供调用方比较或展示
     */
    private String actualSchemaFingerprintByName(String tableName) {
        if (!tableExistsByName(tableName)) {
            return sha256("TABLE_MISSING:" + tableName);
        }
        Set<String> columns = getTableColumnsByName(tableName).stream()
                .map(ColumnInfo::getName)
                .collect(Collectors.toSet());
        return fingerprint(columns);
    }

    /**
     * 使用数据库表统计值评估 DDL 影响量级。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 处理后的{@code estimate}行结果，供调用方继续处理
     */
    public long estimateRows(String entityCode) {
        return estimateRowsByName(getTableName(entityCode));
    }

    /**
     * 根据已加载的实体评估数据量；首次发布时物理表尚不存在，返回零。
     *
     * @param entity 实体，作为 {@code estimateRowsByName} 的输入影响后续处理
     * @return 处理后的{@code estimate}行结果，供调用方继续处理
     */
    public long estimateRows(EntityDefinition entity) {
        return estimateRowsByName(tableResolver.resolve(entity));
    }

    /**
     * 处理{@code estimate}行名称，并将结果传给后续步骤。
     *
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     * @return 处理后的{@code estimate}行名称结果，供调用方继续处理
     */
    private long estimateRowsByName(String tableName) {
        return metadata.estimateRows(tableName);
    }

    /**
     * 发布唯一约束前扫描存量重复值。运行期并发写入仍由 entity_unique_value 主键串行化。
     * 使用已加载实体解析物理表名，使独立事务也能检查新实体的建表前后状态。
     *
     * @param entity 实体，作为 {@code tableResolver.resolve} 的输入影响后续处理
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @return 动态表集合，供调用方遍历或展示
     */
    public List<String> scanUniqueConflicts(EntityDefinition entity, List<EntityField> fields) {
        String physicalTableName = tableResolver.resolve(entity);
        if (!tableExistsByName(physicalTableName)) {
            return List.of();
        }
        String tableName = quoteIdentifier(physicalTableName);
        Set<String> physicalColumns = getTableColumnsByName(physicalTableName).stream()
                .map(ColumnInfo::getName)
                .collect(Collectors.toSet());
        List<String> conflicts = new ArrayList<>();
        for (EntityField field : fields) {
            if (!Boolean.TRUE.equals(field.getIsUnique()) || !isPhysicalDynamicField(field)) {
                continue;
            }
            // 发布预检在 ADD COLUMN 执行前运行；新唯一字段尚无存量值可冲突，
            // 直接查询草稿列会让预览因 Unknown column 失败。
            if (!physicalColumns.contains(columnName(field))) {
                continue;
            }
            String column = quoteIdentifier(columnName(field));
            var kind = EntityTableDefinitionFactory.fieldColumn(field).type().kind();
            boolean largeText = kind == com.workflow.integration.database.api.schema.SchemaType.Kind.TEXT
                    || kind == com.workflow.integration.database.api.schema.SchemaType.Kind.LARGE_TEXT;
            // 大文本不能截取前缀后分组：Oracle 系不支持 LOB GROUP BY，MySQL 的文本排序
            // 长度限制也可能把尾部不同的长文本合并。完整比较并按最小记录 ID 选代表行。
            // 此扫描只用于发布预检；普通标量仍使用数据库原有分组，避免改变其比较规则。
            String sql = largeText ? largeTextUniqueConflictsSql(physicalTableName, columnName(field), kind)
                    : "SELECT " + column + " value_text, COUNT(*) duplicate_count FROM "
                    + tableName + " WHERE " + quoteIdentifier("deleted") + " = 0 AND " + column + " IS NOT NULL "
                    + "GROUP BY " + column + " HAVING COUNT(*) > 1 ORDER BY " + column
                    + queryDialect.paginationClause("0", "5");
            jdbcTemplate.query(sql, rs -> {
                conflicts.add(field.getFieldName() + "=" + rs.getString("value_text")
                        + "（" + rs.getLong("duplicate_count") + "条）");
            });
        }
        return conflicts;
    }

    /**
     * 按完整大文本比较重复值；每组只返回 ID 最小的有效记录，并保留真实重复数量。
     * 不对 LOB 排序、分组或转短字符串，最多读取五组样本；比较规则由产品方言负责。
     *
     * @param physicalTable 物理表，作为 {@code quoteIdentifier} 的输入影响后续处理
     * @param physicalColumn 物理列，作为 {@code quoteIdentifier} 的输入影响后续处理
     * @param kind 类型，作为 {@code queryDialect.columnComparisonPredicate} 的输入影响后续处理
     * @return 处理后的{@code large}文本唯一{@code conflicts}SQL文本，供调用方比较或展示
     */
    private String largeTextUniqueConflictsSql(String physicalTable, String physicalColumn,
                                               com.workflow.integration.database.api.schema.SchemaType.Kind kind) {
        String table = quoteIdentifier(physicalTable);
        String candidate = quoteIdentifier("candidate");
        String duplicate = quoteIdentifier("duplicate_row");
        String earlier = quoteIdentifier("earlier_row");
        String id = quoteIdentifier("id");
        String deleted = quoteIdentifier("deleted");
        String column = quoteIdentifier(physicalColumn);
        String duplicates = " FROM " + table + " " + duplicate + " WHERE " + duplicate + "." + deleted + " = 0 AND "
                + queryDialect.columnComparisonPredicate("duplicate_row." + physicalColumn, kind, "=", "candidate." + physicalColumn);
        String sql = "SELECT " + candidate + "." + column + " value_text, (SELECT COUNT(*)" + duplicates + ") duplicate_count"
                + " FROM " + table + " " + candidate
                + " WHERE " + candidate + "." + deleted + " = 0 AND " + candidate + "." + column + " IS NOT NULL"
                + " AND EXISTS (SELECT 1" + duplicates + " AND " + duplicate + "." + id + " <> " + candidate + "." + id + ")"
                + " AND NOT EXISTS (SELECT 1 FROM " + table + " " + earlier
                + " WHERE " + earlier + "." + deleted + " = 0 AND " + earlier + "." + id + " < " + candidate + "." + id
                + " AND " + queryDialect.columnComparisonPredicate("earlier_row." + physicalColumn, kind, "=", "candidate." + physicalColumn) + ")"
                + " ORDER BY " + candidate + "." + id;
        return queryDialect.paginate(sql, "0", "5");
    }

    /**
     * 删除实体数据表
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     */
    @Transactional(rollbackFor = Exception.class)
    public void dropEntityTable(String entityCode) {
        String tableName = getTableName(entityCode);
        for (var table : List.of(EntityTableDefinitionFactory.mainTable(tableName, List.of(), tableName),
                EntityTableDefinitionFactory.multiTable(deriveMultiValueTableName(tableName)))) {
            if (tableExistsByName(table.name())) executePlan(dialect.dropTable(table));
        }
        log.info("实体数据表 {} 已删除", tableName);
    }

    /**
     * 为实体添加字段（实体定义修改后）
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param field 字段，作为 {@code executePlan} 的输入影响后续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public void addColumn(String entityCode, EntityField field) {
        String tableName = getTableName(entityCode);
        if (!tableExists(entityCode) || isMultiValueField(field)) {
            return;
        }

        executePlan(dialect.addColumn(tableName, EntityTableDefinitionFactory.fieldColumn(field)));
        log.info("为表 {} 添加字段: {}", tableName, field.getFieldCode());
    }

    /**
     * 修改字段
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param field 字段，作为 {@code executePlan} 的输入影响后续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public void modifyColumn(String entityCode, EntityField field) {
        String tableName = getTableName(entityCode);
        if (!tableExists(entityCode) || isMultiValueField(field)) {
            return;
        }

        executePlan(dialect.modifyColumn(tableName, EntityTableDefinitionFactory.fieldColumn(field)));
        log.info("修改表 {} 字段: {}", tableName, field.getFieldCode());
    }

    /**
     * 删除字段
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param columnName 列名称，后续用于处理{@code drop}列时匹配或展示
     */
    @Transactional(rollbackFor = Exception.class)
    public void dropColumn(String entityCode, String columnName) {
        String tableName = getTableName(entityCode);
        if (!tableExists(entityCode)) {
            return;
        }

        executePlan(dialect.dropColumn(tableName, validateIdentifier(columnName)));
        log.info("删除表 {} 字段: {}", tableName, columnName);
    }

    /**
     * 方言返回有序的单次执行语句，预览与发布共用该计划。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param entityName 实体名称，后续用于构建创建表方案时匹配或展示
     * @return 动态表集合，供调用方遍历或展示
     */
    private List<String> buildCreateTablePlan(String table, List<EntityField> fields, String entityName) {
        return dialect.createTable(EntityTableDefinitionFactory.mainTable(table, fields, entityName));
    }

    /**
     * 执行方案，并将结果传给后续步骤。
     *
     * @param statements {@code statements}，供本方法执行方案时使用
     */
    private void executePlan(List<String> statements) {
        // 先校验完整计划再执行，避免后续语句不安全导致前面的 DDL 已经提交。
        statements.forEach(dialect::validateStatement);
        statements.forEach(schemaDdlExecutor::execute);
    }

    /**
     * 判断是否子级表单字段；判断结果决定调用方的后续分支。
     *
     * @param field 字段，供本方法判断是否子级表单字段时使用
     * @return 子级表单字段条件成立时为 true，否则为 false
     */
    private boolean isSubFormField(EntityField field) { return EntityTableDefinitionFactory.isSubFormField(field); }
    /**
     * 判断是否多实例值字段；判断结果决定调用方的后续分支。
     *
     * @param field 字段，供本方法判断是否多实例值字段时使用
     * @return 多实例值字段条件成立时为 true，否则为 false
     */
    private boolean isMultiValueField(EntityField field) { return EntityTableDefinitionFactory.isMultiValueField(field); }
    /**
     * 判断是否物理动态字段；判断结果决定调用方的后续分支。
     *
     * @param field 字段，供本方法判断是否物理动态字段时使用
     * @return 物理动态字段条件成立时为 true，否则为 false
     */
    private boolean isPhysicalDynamicField(EntityField field) { return EntityTableDefinitionFactory.isPhysicalDynamicField(field); }

    /**
     * 生成列名称文本，供后续匹配或展示。
     *
     * @param field 字段，供本方法处理列名称时使用
     * @return 处理后的列名称文本，供调用方比较或展示
     */
    private String columnName(EntityField field) {
        return field.getDbColumnName() != null && !field.getDbColumnName().isBlank()
                ? validateIdentifier(field.getDbColumnName())
                : validateIdentifier(field.getFieldCode());
    }

    /**
     * 判断列匹配条件是否成立，供调用方选择后续分支。
     *
     * @param actual 实际，供本方法处理列匹配时使用
     * @param field 字段，作为 {@code dialect.columnTypeMatches} 的输入影响后续处理
     * @return 列匹配条件成立时为 true，否则为 false
     */
    private boolean columnMatches(ColumnInfo actual, EntityField field) {
        return dialect.columnTypeMatches(EntityTableDefinitionFactory.fieldType(field), actual.getType(),
                actual.getLength(), actual.getPrecision(), actual.getScale());
    }

    /**
     * 整理预期列集合数据，供调用方遍历或继续处理。
     *
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @return 动态表集合，供调用方遍历或展示
     */
    private Set<String> expectedColumns(List<EntityField> fields) {
        Set<String> expected = new LinkedHashSet<>(BASE_COLUMNS);
        for (EntityField field : fields == null ? List.<EntityField>of() : fields) {
            if (isPhysicalDynamicField(field)) {
                expected.add(columnName(field));
            }
        }
        return expected;
    }

    /**
     * 生成指纹文本，供后续匹配或展示。
     *
     * @param columns 列集合，供本方法处理指纹时使用
     * @return 处理后的指纹文本，供调用方比较或展示
     */
    private static String fingerprint(Set<String> columns) {
        String canonical = columns.stream().sorted(Comparator.naturalOrder())
                .collect(Collectors.joining("\n"));
        return sha256(canonical);
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
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    /**
     * 读取多实例值表名称；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 读取后的多实例值表名称文本，供调用方比较或展示
     */
    public String getMultiValueTableName(String entityCode) {
        return deriveMultiValueTableName(getTableName(entityCode));
    }

    /**
     * 确保实体多实例值表；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     */
    public void ensureEntityMultiValueTable(String entityCode) {
        ensureMultiValueTable(getTableName(entityCode));
    }

    /**
     * 生成{@code derive}多实例值表名称文本，供后续匹配或展示。
     *
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     * @return 处理后的{@code derive}多实例值表名称文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String deriveMultiValueTableName(String tableName) {
        String multiTableName = tableName + "_multi";
        if (multiTableName.length() > IDENTIFIER_LIMIT) {
            throw new IllegalArgumentException("实体多值表名超过数据库限制: " + multiTableName);
        }
        return validateIdentifier(multiTableName);
    }

    /**
     * 确保多实例值表；不满足约束时阻止后续处理。
     *
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     */
    private void ensureMultiValueTable(String tableName) {
        String multiTableName = deriveMultiValueTableName(tableName);
        if (!dialect.supportsCreateIfNotExists() && tableExistsByName(multiTableName)) return;
        executePlan(dialect.createTable(EntityTableDefinitionFactory.multiTable(multiTableName)));
    }

    /**
     * 构建建表SQL预览（不执行）
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param entityName 实体名称，后续用于构建创建表SQL预览时匹配或展示
     * @return 构建后的创建表SQL预览文本，供调用方比较或展示
     */
    public String buildCreateTableSqlPreview(String entityCode, List<EntityField> fields, String entityName) {
        String tableName = getTableName(entityCode);
        return String.join(";\n", buildCreateTablePlan(tableName, fields, entityName));
    }
    
    /**
     * 构建添加字段的SQL预览列表（不执行）
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @return 动态表集合，供调用方遍历或展示
     */
    public List<String> buildAddColumnSqlPreviews(String entityCode, List<EntityField> fields) {
        String tableName = getTableName(entityCode);
        List<String> ddls = new ArrayList<>();
        
        for (EntityField field : fields) {
            if (isSubFormField(field) || isMultiValueField(field)) {
                continue;
            }
            ddls.addAll(dialect.addColumn(tableName, EntityTableDefinitionFactory.fieldColumn(field)));
        }
        
        return ddls;
    }

    /**
     * 生成引用标识符文本，供后续匹配或展示。
     *
     * @param identifier 标识符，供本方法处理引用标识符时使用
     * @return 处理后的引用标识符文本，供调用方比较或展示
     */
    private String quoteIdentifier(String identifier) {
        return dialect.quoteIdentifier(validateIdentifier(identifier));
    }

    /**
     * 校验标识符；不满足约束时阻止后续处理。
     *
     * @param identifier 标识符，作为 {@code SqlIdentifierPolicy.validate} 的输入影响后续处理
     * @return 校验后的标识符文本，供调用方比较或展示
     */
    static String validateIdentifier(String identifier) {
        return SqlIdentifierPolicy.validate(identifier);
    }

    /**
     * 列信息内部类
     */
    @lombok.Data
    public static class ColumnInfo {
        private String name;
        private String type;
        private Long length;
        private Integer precision;
        private Integer scale;
        private boolean nullable;
        private String defaultValue;
    }
}
