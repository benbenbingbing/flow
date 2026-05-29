package com.workflow.service;

import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.mapper.EntityFieldMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.jdbc.SQL;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 动态表管理服务
 * 负责创建、修改实体数据表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicTableService {

    private final JdbcTemplate jdbcTemplate;
    private final EntityFieldMapper entityFieldMapper;
    
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
            col.setLength(rs.getInt("CHARACTER_MAXIMUM_LENGTH"));
            col.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
            col.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
            return col;
        }, tableName);
    }

    /**
     * 获取实体数据表名
     */
    public String getTableName(String entityCode) {
        return "entity_data_" + entityCode.toLowerCase();
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
        String tableName = getTableName(entityCode);

        if (tableExists(entityCode)) {
            log.info("表 {} 已存在，跳过创建", tableName);
            return null;
        }

        // 获取实体字段定义
        List<EntityField> fields = entityFieldMapper.findByEntityId(entityDefinition.getId());

        // 构建建表SQL
        String createTableSql = buildCreateTableSql(tableName, fields, entityDefinition.getEntityName());
        
        log.info("创建实体数据表: {}", tableName);
        jdbcTemplate.execute(createTableSql);
        
        // 创建索引
        createIndexes(tableName, fields);
        
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
        String entityCode = entityDefinition.getEntityCode();
        String tableName = getTableName(entityCode);
        List<String> executedDdls = new java.util.ArrayList<>();
        
        // 获取实体字段定义
        List<EntityField> fields = entityFieldMapper.findByEntityId(entityDefinition.getId());
        
        if (!tableExists(entityCode)) {
            // 表不存在，创建新表（包含所有非子表单字段）
            String ddl = buildCreateTableSql(tableName, fields, entityDefinition.getEntityName());
            jdbcTemplate.execute(ddl);
            createIndexes(tableName, fields);
            executedDdls.add(ddl);
            log.info("创建实体数据表: {}", tableName);
        } else {
            // 表已存在，同步未发布的字段到数据库表
            List<ColumnInfo> existingColumns = getTableColumns(entityCode);
            java.util.Set<String> existingColumnNames = existingColumns.stream()
                    .map(ColumnInfo::getName)
                    .collect(java.util.stream.Collectors.toSet());
            
            for (EntityField field : fields) {
                // 跳过系统字段和子表单字段
                if (Boolean.TRUE.equals(field.getIsSystem()) || isSubFormField(field)) {
                    continue;
                }
                
                String dbColumnName = field.getDbColumnName() != null && !field.getDbColumnName().isEmpty() 
                    ? field.getDbColumnName() 
                    : field.getFieldCode();
                
                if (!existingColumnNames.contains(dbColumnName)) {
                    // 字段在数据库中不存在，添加字段（新字段或未发布的字段）
                    String columnDef = buildColumnDefinition(field);
                    String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnDef;
                    jdbcTemplate.execute(sql);
                    executedDdls.add(sql);
                    
                    // 创建索引（仅对唯一字段创建唯一索引）
                    if (Boolean.TRUE.equals(field.getIsUnique())) {
                        createIndexForColumn(tableName, dbColumnName, true);
                    }
                    
                    log.info("为表 {} 添加字段: {}", tableName, dbColumnName);
                } else if (Boolean.TRUE.equals(field.getIsPublished())) {
                    // 已发布的字段，检查是否需要修改列定义（长度、精度、必填等变更）
                    try {
                        String columnDef = buildColumnDefinition(field);
                        String sql = "ALTER TABLE " + tableName + " MODIFY COLUMN " + columnDef;
                        jdbcTemplate.execute(sql);
                        executedDdls.add(sql);
                        log.info("修改表 {} 字段定义: {}", tableName, dbColumnName);
                    } catch (Exception e) {
                        log.warn("修改表 {} 字段 {} 定义失败: {}", tableName, dbColumnName, e.getMessage());
                    }
                }
            }
        }
        
        return executedDdls;
    }

    /**
     * 删除实体数据表
     */
    @Transactional(rollbackFor = Exception.class)
    public void dropEntityTable(String entityCode) {
        String tableName = getTableName(entityCode);
        String sql = "DROP TABLE IF EXISTS " + tableName;
        jdbcTemplate.execute(sql);
        log.info("实体数据表 {} 已删除", tableName);
    }

    /**
     * 为实体添加字段（实体定义修改后）
     */
    @Transactional(rollbackFor = Exception.class)
    public void addColumn(String entityCode, EntityField field) {
        String tableName = getTableName(entityCode);
        if (!tableExists(entityCode)) {
            return;
        }

        String columnDef = buildColumnDefinition(field);
        String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnDef;
        
        jdbcTemplate.execute(sql);
        
        // 如果字段需要索引，创建索引
        if (Boolean.TRUE.equals(field.getIsUnique())) {
            createIndexForColumn(tableName, field.getFieldCode(), true);
        }
        
        log.info("为表 {} 添加字段: {}", tableName, field.getFieldCode());
    }

    /**
     * 修改字段
     */
    @Transactional(rollbackFor = Exception.class)
    public void modifyColumn(String entityCode, EntityField field) {
        String tableName = getTableName(entityCode);
        if (!tableExists(entityCode)) {
            return;
        }

        String columnDef = buildColumnDefinition(field);
        String sql = "ALTER TABLE " + tableName + " MODIFY COLUMN " + columnDef;
        
        jdbcTemplate.execute(sql);
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

        String sql = "ALTER TABLE " + tableName + " DROP COLUMN " + columnName;
        jdbcTemplate.execute(sql);
        log.info("删除表 {} 字段: {}", tableName, columnName);
    }

    /**
     * 构建建表SQL
     */
    private String buildCreateTableSql(String tableName, List<EntityField> fields, String entityName) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(tableName).append(" (\n");
        
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
        sql.append("  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',\n");
        sql.append("  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',\n");
        sql.append("  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n");
        sql.append("  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n");
        sql.append("  `deleted` TINYINT DEFAULT 0 COMMENT '是否删除（0否/1是）',\n");
        
        // 动态字段（跳过系统字段和子表单字段）
        Set<String> systemFieldCodes = new HashSet<>(Arrays.asList(
                "name", "code", "status", "processInstanceId", "processInstance_id",
                "processStartTime", "process_startTime", "processStart_time",
                "processEndTime", "process_endTime", "processEnd_time",
                "submitterId", "submitter_id", "submitterName", "submitter_name",
                "deptId", "dept_id"
        ));
        
        for (EntityField field : fields) {
            // 跳过子表单字段（子表单有独立表）
            if (isSubFormField(field)) {
                continue;
            }
            // 跳过系统字段（已在基础字段中定义）
            if (Boolean.TRUE.equals(field.getIsSystem()) || 
                systemFieldCodes.contains(field.getFieldCode())) {
                continue;
            }
            sql.append("  ").append(buildColumnDefinition(field)).append(",\n");
        }
        
        // 主键
        sql.append("  PRIMARY KEY (`id`)\n");
        String comment = entityName != null && !entityName.isEmpty() ? entityName : tableName;
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='").append(comment).append("';");
        
        return sql.toString();
    }

    /**
     * 构建字段定义
     */
    private String buildColumnDefinition(EntityField field) {
        StringBuilder col = new StringBuilder();
        String columnName = field.getDbColumnName() != null && !field.getDbColumnName().isEmpty() 
            ? field.getDbColumnName() 
            : field.getFieldCode();
        col.append("`").append(columnName).append("` ");
        
        // 根据字段类型确定数据库类型
        String dbType = getDbType(field);
        col.append(dbType);
        
        // 是否必填
        if (Boolean.TRUE.equals(field.getIsRequired())) {
            col.append(" NOT NULL");
        } else {
            col.append(" DEFAULT NULL");
        }
        
        // 默认值
        if (field.getDefaultValue() != null && !field.getDefaultValue().isEmpty()) {
            col.append(" DEFAULT '");
            // 处理单引号转义
            String escaped = field.getDefaultValue().replace("'", "''");
            col.append(escaped);
            col.append("'");
        }
        
        // 注释
        col.append(" COMMENT '");
        String comment = field.getFieldName() != null ? field.getFieldName() : field.getFieldCode();
        // 处理单引号转义
        comment = comment.replace("'", "''");
        col.append(comment);
        col.append("'");
        
        return col.toString();
    }

    /**
     * 获取数据库字段类型
     */
    private String getDbType(EntityField field) {
        // 优先使用用户指定的db_type
        if (field.getDbType() != null && !field.getDbType().isEmpty()) {
            return field.getDbType();
        }
        
        // 根据字段类型推断
        switch (field.getFieldType()) {
            case STRING:
            case SELECT:
            case RADIO:
            case USER:
            case DEPT:
            case REFERENCE:
                int length = field.getFieldLength() != null ? field.getFieldLength() : 200;
                return "VARCHAR(" + length + ")";
            case TEXT:
            case RICH_TEXT:
                return "TEXT";
            case INTEGER:
                return "INT";
            case LONG:
                return "BIGINT";
            case DECIMAL:
                int prec = field.getFieldLength() != null ? field.getFieldLength() : 18;
                int scale = field.getFieldPrecision() != null ? field.getFieldPrecision() : 2;
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
                // 多选实体，使用JSON数组存储多个实体ID
                return "JSON";
            default:
                return "VARCHAR(255)";
        }
    }

    /**
     * 是否为子表单字段
     */
    private boolean isSubFormField(EntityField field) {
        return field.getFieldType() == EntityField.FieldType.SUB_FORM 
                || field.getFieldType() == EntityField.FieldType.SUB_FORM_LIST;
    }

    /**
     * 创建索引
     */
    private void createIndexes(String tableName, List<EntityField> fields) {
        // 常用查询字段索引
        jdbcTemplate.execute("CREATE INDEX idx_" + tableName + "_status ON " + tableName + " (`status`)");
        jdbcTemplate.execute("CREATE INDEX idx_" + tableName + "_process ON " + tableName + " (`process_instance_id`)");
        jdbcTemplate.execute("CREATE INDEX idx_" + tableName + "_deleted ON " + tableName + " (`deleted`)");
        jdbcTemplate.execute("CREATE INDEX idx_" + tableName + "_created ON " + tableName + " (`created_at`)");
        
        // 字段索引
        for (EntityField field : fields) {
            if (Boolean.TRUE.equals(field.getIsUnique())) {
                createIndexForColumn(tableName, field.getFieldCode(), true);
            }
        }
    }

    /**
     * 为字段创建索引
     */
    private void createIndexForColumn(String tableName, String columnName, boolean unique) {
        try {
            String indexName = (unique ? "uniq_" : "idx_") + tableName + "_" + columnName;
            String sql;
            if (unique) {
                sql = "CREATE UNIQUE INDEX " + indexName + " ON " + tableName + " (`" + columnName + "`)";
            } else {
                sql = "CREATE INDEX " + indexName + " ON " + tableName + " (`" + columnName + "`)";
            }
            jdbcTemplate.execute(sql);
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
            String columnDef = buildColumnDefinition(field);
            String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnDef;
            ddls.add(sql);
        }
        
        return ddls;
    }

    /**
     * 列信息内部类
     */
    @lombok.Data
    public static class ColumnInfo {
        private String name;
        private String type;
        private Integer length;
        private boolean nullable;
        private String defaultValue;
    }
}
