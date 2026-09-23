package com.workflow.entity.data.application;

import com.workflow.integration.database.api.*;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import java.util.*;

/**
 * 将实体业务规则映射为通用表结构。流程字段、团队事件和多值引用关系只在实体模块定义，
 * 数据库模块不依赖 EntityField，也不决定必填、唯一或系统字段的业务语义。
 */
public final class EntityTableDefinitionFactory {
    private EntityTableDefinitionFactory() {}
    private static final Set<String> SYSTEM_FIELD_CODES = Set.of(
            "name", "code", "status", "processStatus", "process_status", "processInstanceId", "processInstance_id",
            "processStartTime", "process_startTime", "processStart_time", "processEndTime", "process_endTime", "processEnd_time",
            "submitterId", "submitter_id", "submitterName", "submitter_name", "deptId", "dept_id");

    /** 客户端 dbType 仅作元数据展示，实际类型由字段类型及受限尺寸决定。 */
    public static SchemaType fieldType(EntityField field) {
        if (field == null || field.getFieldType() == null) throw new IllegalArgumentException("动态字段及字段类型不能为空");
        return switch (field.getFieldType()) {
            case STRING, SELECT, RADIO, USER, DEPT, REFERENCE -> SchemaType.string(field.getFieldLength() == null ? 200 : field.getFieldLength());
            case TEXT, RICH_TEXT, FILE, IMAGE -> SchemaType.of(SchemaType.Kind.TEXT);
            case INTEGER -> SchemaType.of(SchemaType.Kind.INTEGER);
            case LONG -> SchemaType.of(SchemaType.Kind.LONG);
            case DECIMAL -> SchemaType.decimal(field.getFieldLength() == null ? 18 : field.getFieldLength(),
                    field.getFieldPrecision() == null ? 2 : field.getFieldPrecision());
            case DATE -> SchemaType.of(SchemaType.Kind.DATE);
            case DATETIME -> SchemaType.of(SchemaType.Kind.TIMESTAMP);
            case BOOLEAN -> SchemaType.of(SchemaType.Kind.BOOLEAN);
            case MULTI_SELECT, CHECKBOX -> SchemaType.string(500);
            case MULTI_REFERENCE -> SchemaType.of(SchemaType.Kind.LARGE_TEXT);
            default -> SchemaType.string(255);
        };
    }

    /** 动态字段的必填和唯一性由应用校验，物理列保持可空。 */
    public static SchemaColumn fieldColumn(EntityField field) {
        SchemaType type = fieldType(field);
        String name = field.getDbColumnName() != null && !field.getDbColumnName().isBlank() ? field.getDbColumnName() : field.getFieldCode();
        return new SchemaColumn(SqlIdentifierPolicy.validate(name), type, true, fieldDefault(field),
                field.getFieldName() == null ? field.getFieldCode() : field.getFieldName(), false);
    }

    public static SchemaDefault fieldDefault(EntityField field) {
        String value = field.getDefaultValue();
        if (value == null || value.isBlank()) return SchemaDefault.none();
        if (field.getFieldType() == EntityField.FieldType.BOOLEAN) {
            String normalized = value.trim();
            if ("true".equalsIgnoreCase(normalized) || "1".equals(normalized)) return SchemaDefault.literal(true);
            if ("false".equalsIgnoreCase(normalized) || "0".equals(normalized)) return SchemaDefault.literal(false);
            throw new IllegalArgumentException("布尔字段 " + field.getFieldCode() + " 的默认值必须是 true、false、1 或 0");
        }
        return SchemaDefault.literal(value);
    }

    public static boolean isPhysicalDynamicField(EntityField field) {
        return !Boolean.TRUE.equals(field.getIsSystem()) && !SYSTEM_FIELD_CODES.contains(field.getFieldCode())
                && !isSubFormField(field) && !isMultiValueField(field);
    }
    public static boolean isSubFormField(EntityField field) {
        return field.getFieldType() == EntityField.FieldType.SUB_FORM || field.getFieldType() == EntityField.FieldType.SUB_LIST;
    }
    public static boolean isMultiValueField(EntityField field) {
        if (field.getFieldType() == EntityField.FieldType.MULTI_REFERENCE) return field.getRefEntityId() != null && !field.getRefEntityId().isBlank();
        return (field.getFieldType() == EntityField.FieldType.MULTI_SELECT || field.getFieldType() == EntityField.FieldType.CHECKBOX)
                && field.getDictType() != null && !field.getDictType().isBlank();
    }

    /** 主表基线与动态字段；索引属于结构定义，不能在发布后“尽力补建”。 */
    public static SchemaTable mainTable(String tableName, List<EntityField> fields, String entityName) {
        var columns = new ArrayList<SchemaColumn>();
        columns.add(new SchemaColumn("id", SchemaType.string(64), false, SchemaDefault.none(), "主键ID", false));
        columns.add(new SchemaColumn("name", SchemaType.string(200), true, SchemaDefault.none(), "数据名称", false));
        columns.add(new SchemaColumn("code", SchemaType.string(100), true, SchemaDefault.none(), "数据编码", false));
        columns.add(new SchemaColumn("status", SchemaType.string(50), true, SchemaDefault.none(), "数据状态", false));
        columns.add(new SchemaColumn("process_status", SchemaType.string(20), false, SchemaDefault.literal("NOT_STARTED"), "流程生命周期", false));
        columns.add(new SchemaColumn("process_instance_id", SchemaType.string(64), true, SchemaDefault.none(), "流程实例ID", false));
        columns.add(new SchemaColumn("process_start_time", SchemaType.of(SchemaType.Kind.TIMESTAMP), true, SchemaDefault.none(), "流程开始时间", false));
        columns.add(new SchemaColumn("process_end_time", SchemaType.of(SchemaType.Kind.TIMESTAMP), true, SchemaDefault.none(), "流程结束时间", false));
        columns.add(new SchemaColumn("current_task_id", SchemaType.string(64), true, SchemaDefault.none(), "当前任务ID", false));
        columns.add(new SchemaColumn("current_task_name", SchemaType.string(200), true, SchemaDefault.none(), "当前任务名称", false));
        columns.add(new SchemaColumn("current_task_assignee", SchemaType.string(64), true, SchemaDefault.none(), "当前任务审批人", false));
        columns.add(new SchemaColumn("submitter_id", SchemaType.string(64), true, SchemaDefault.none(), "提交人ID", false));
        columns.add(new SchemaColumn("submitter_name", SchemaType.string(100), true, SchemaDefault.none(), "提交人姓名", false));
        columns.add(new SchemaColumn("submit_time", SchemaType.of(SchemaType.Kind.TIMESTAMP), true, SchemaDefault.none(), "提交时间", false));
        columns.add(new SchemaColumn("dept_id", SchemaType.string(64), true, SchemaDefault.none(), "所属部门ID（数据权限用）", false));
        columns.add(new SchemaColumn("create_by", SchemaType.string(64), true, SchemaDefault.none(), "创建人", false));
        columns.add(new SchemaColumn("update_by", SchemaType.string(64), true, SchemaDefault.none(), "更新人", false));
        columns.add(new SchemaColumn("create_time", SchemaType.of(SchemaType.Kind.TIMESTAMP), true, SchemaDefault.currentTimestamp(), "创建时间", false));
        columns.add(new SchemaColumn("update_time", SchemaType.of(SchemaType.Kind.TIMESTAMP), true, SchemaDefault.currentTimestamp(), "更新时间", true));
        columns.add(new SchemaColumn("deleted", SchemaType.of(SchemaType.Kind.BYTE), true, SchemaDefault.literal(0), "是否删除（0否/1是）", false));
        for (var field : fields == null ? List.<EntityField>of() : fields) {
            if (isPhysicalDynamicField(field)) columns.add(fieldColumn(field));
        }
        var indexes = List.of(index(indexName(tableName, "status"), false, "status"),
                index(indexName(tableName, "process"), false, "process_instance_id"),
                index(indexName(tableName, "deleted"), false, "deleted"),
                index(indexName(tableName, "created"), false, "create_time"));
        return new SchemaTable(tableName, columns, List.of("id"), indexes,
                entityName == null || entityName.isEmpty() ? tableName : entityName, false);
    }

    /** 多值引用采用独立关联表，删除标记参与唯一约束。 */
    public static SchemaTable multiTable(String tableName) {
        return new SchemaTable(tableName, List.of(
                text("id",64,false,"主键ID"), text("record_id",64,false,"主记录ID"),
                text("field_code",100,false,"字段编码"), text("target_entity_id",64,false,"目标实体ID"),
                text("target_record_id",64,false,"目标记录ID"),
                new SchemaColumn("sort_order",SchemaType.of(SchemaType.Kind.INTEGER),false,SchemaDefault.literal(0),"选择顺序",false),
                new SchemaColumn("deleted",SchemaType.of(SchemaType.Kind.BYTE),false,SchemaDefault.literal(0),"逻辑删除",false),
                text("create_by",64,true,"创建人"), time("create_time",false,"创建时间",false), time("update_time",false,"更新时间",true)),
                List.of("id"), List.of(
                    index("uk_record_field_target",true,"record_id","field_code","target_entity_id","target_record_id","deleted"),
                    index("idx_record_field",false,"record_id","field_code","deleted"),
                    index("idx_field_target",false,"field_code","target_entity_id","target_record_id","deleted"),
                    index("idx_target",false,"target_entity_id","target_record_id","deleted")),"实体多值引用表",true);
    }

    /** 团队事件表的参与者与流程字段属于业务定义，数据库方言只负责渲染。 */
    public static SchemaTable teamTable(String tableName) {
        return new SchemaTable(tableName, List.of(
                text("id",64,false,"参与事件ID"), text("record_id",64,false,"业务记录ID"), text("user_id",64,false,"参与用户ID"),
                text("action_type",50,false,"参与动作类型"), text("action_description",500,true,"参与动作说明"),
                text("process_instance_id",64,true,"流程实例ID"), text("process_task_id",64,true,"流程任务ID"),
                time("create_time",false,"参与事件入库时间",false)), List.of("id"), List.of(
                    index("idx_team_user_record",false,"user_id","record_id"),
                    index("idx_team_record_time",false,"record_id","create_time"),
                    index("idx_team_process_task",false,"process_instance_id","process_task_id")),"业务数据参与团队事件",true);
    }

    private static SchemaColumn text(String name,int length,boolean nullable,String comment) {
        return new SchemaColumn(name,SchemaType.string(length),nullable,SchemaDefault.none(),comment,false);
    }
    private static SchemaColumn time(String name,boolean nullable,String comment,boolean refresh) {
        return new SchemaColumn(name,SchemaType.of(SchemaType.Kind.TIMESTAMP),nullable,SchemaDefault.currentTimestamp(),comment,refresh);
    }
    private static SchemaIndex index(String name,boolean unique,String... columns) { return new SchemaIndex(name,List.of(columns),unique); }
    private static String indexName(String table,String suffix) {
        String name = "idx_" + SqlIdentifierPolicy.validate(table) + "_" + suffix;
        // 表名接近上限时保留后缀，避免 status/process 等索引被截断为同一名称。
        return name.length() <= 63 ? name : name.substring(0,63-suffix.length()-1) + "_" + suffix;
    }
}
