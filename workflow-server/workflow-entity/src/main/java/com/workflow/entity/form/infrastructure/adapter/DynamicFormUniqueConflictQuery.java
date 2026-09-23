package com.workflow.entity.form.infrastructure.adapter;

import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.application.port.FormUniqueConflictQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于动态实体表的表单唯一冲突查询适配器。
 *
 * <p>条件唯一允许引用多个字段，因此适配器返回字段编码视角的完整候选记录，
 * 由共享规则策略统一完成条件和规范化比较。后续如需按字段建立专用查询索引，
 * 可以替换此端口实现而无需改动表单规则语义。</p>
 */
@Component
@RequiredArgsConstructor
public class DynamicFormUniqueConflictQuery
        implements FormUniqueConflictQuery {

    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityDataDynamicMapper dynamicMapper;
    private final DynamicTableService dynamicTableService;
    private final EntityRuntimeRecordMapper recordMapper;

    /**
     * 查询记录；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 记录键值结果，供调用方继续处理
     */
    @Override
    public Map<String, Object> findRecord(
            String entityCode,
            String recordId) {
        if (!StringUtils.hasText(recordId)) {
            return Map.of();
        }
        RuntimeEntity runtime = requireRuntimeEntity(entityCode);
        Map<String, Object> row = dynamicMapper.selectById(
                dynamicTableService.getTableName(entityCode),
                recordId);
        return row == null
                ? Map.of()
                : toFieldRecord(row, runtime.fields());
    }

    /**
     * 查询候选集合；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param fieldCode 字段编码，后续用于查询候选集合时定位或关联目标
     * @param normalizedValue 规范化值，供本方法查询候选集合时使用
     * @param excludeRecordId 排除记录ID，后续用于查询候选集合时定位或关联目标
     * @return 动态表单唯一冲突查询集合，供调用方遍历或展示
     */
    @Override
    public List<Map<String, Object>> findCandidates(
            String entityCode,
            String fieldCode,
            String normalizedValue,
            String excludeRecordId) {
        return findCandidates(
                entityCode,
                fieldCode,
                normalizedValue,
                excludeRecordId,
                false);
    }

    /**
     * 查询候选集合{@code authoritative}检查；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param fieldCode 字段编码，后续用于查询候选集合{@code authoritative}检查时定位或关联目标
     * @param normalizedValue 规范化值，作为 {@code findCandidates} 的输入影响后续处理
     * @param excludeRecordId 排除记录ID，后续用于查询候选集合{@code authoritative}检查时定位或关联目标
     * @return 动态表单唯一冲突查询集合，供调用方遍历或展示
     */
    @Override
    public List<Map<String, Object>>
            findCandidatesForAuthoritativeCheck(
                    String entityCode,
                    String fieldCode,
                    String normalizedValue,
                    String excludeRecordId) {
        return findCandidates(
                entityCode,
                fieldCode,
                normalizedValue,
                excludeRecordId,
                true);
    }

    /**
     * 查询候选集合；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param fieldCode 字段编码，后续用于查询候选集合时定位或关联目标
     * @param normalizedValue 规范化值，供本方法查询候选集合时使用
     * @param excludeRecordId 排除记录ID，后续用于查询候选集合时定位或关联目标
     * @param authoritativeCheck {@code authoritative}检查，供本方法查询候选集合时使用
     * @return 动态表单唯一冲突查询集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private List<Map<String, Object>> findCandidates(
            String entityCode,
            String fieldCode,
            String normalizedValue,
            String excludeRecordId,
            boolean authoritativeCheck) {
        RuntimeEntity runtime = requireRuntimeEntity(entityCode);
        EntityField targetField = runtime.fields().stream()
                .filter(field -> field != null
                        && fieldCode.equals(field.getFieldCode()))
                .findFirst()
                .orElse(null);
        if (targetField == null) {
            // 新发布在配置边界已禁止虚拟/失效字段；这里仍对历史
            // 快照 fail closed，不能把不可信 fieldCode 拼成 SQL 列名。
            throw new IllegalArgumentException(
                    "表单唯一规则字段不存在: "
                            + fieldCode);
        }
        String targetColumn = StringUtils.hasText(
                targetField.getDbColumnName())
                ? targetField.getDbColumnName()
                : recordMapper.toColumnName(fieldCode);
        List<Map<String, Object>> rows;
        if (requiresFullScan(targetField)) {
            // 数字、布尔和日期必须按 Java 的类型规则比较，不能依赖数据库转文本。
            // TEXT 在 Oracle 等产品是 CLOB；转换成 VARCHAR 会丢失尾部或超过上限，
            // 因此读取完整大字段后复用规则策略。普通 VARCHAR 仍使用不会漏报的预筛。
            rows = authoritativeCheck
                    ? dynamicMapper.selectListForUpdate(
                            dynamicTableService.getTableName(
                                    entityCode))
                    : dynamicMapper.selectList(
                            dynamicTableService.getTableName(
                                    entityCode));
        } else {
            rows = authoritativeCheck
                    ? dynamicMapper
                            .selectFormUniqueCandidatesForUpdate(
                                    dynamicTableService.getTableName(
                                            entityCode),
                                    targetColumn,
                                    normalizedValue,
                                    excludeRecordId)
                    : dynamicMapper.selectFormUniqueCandidates(
                            dynamicTableService.getTableName(
                                    entityCode),
                            targetColumn,
                            normalizedValue,
                            excludeRecordId);
        }
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (StringUtils.hasText(excludeRecordId)
                    && excludeRecordId.equals(
                            String.valueOf(row.get("id")))) {
                continue;
            }
            result.add(toFieldRecord(row, runtime.fields()));
        }
        return List.copyOf(result);
    }

    /**
     * 历史元数据也必须具有可验证的物理类型，不能把虚拟、多值或未知类型送入字符 SQL。
     *
     * @param field 字段，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 需要{@code full}{@code scan}条件成立时为 true，否则为 false
     */
    private boolean requiresFullScan(EntityField field) {
        if (field.getFieldType() == null) {
            throw new IllegalArgumentException("表单唯一规则字段缺少存储类型: " + field.getFieldCode());
        }
        return switch (field.getFieldType()) {
            case TEXT, INTEGER, LONG, DECIMAL, BOOLEAN, DATE, DATETIME -> true;
            case STRING, SELECT, RADIO, USER, DEPT, REFERENCE -> false;
            default -> throw new IllegalArgumentException("表单唯一规则不支持字段类型: " + field.getFieldType());
        };
    }

    /**
     * 校验并获取运行时实体；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 校验并获取后的运行时实体结果，供调用方继续处理
     */
    private RuntimeEntity requireRuntimeEntity(String entityCode) {
        EntityDefinition definition = definitionMapper
                .findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "实体不存在: " + entityCode));
        List<EntityField> fields = fieldMapper.findByEntityId(
                definition.getId());
        return new RuntimeEntity(
                definition,
                fields == null ? List.of() : fields);
    }

    /**
     * 转换为字段记录；输出作为后续校验或处理的输入。
     *
     * @param row 行，供本方法转换为字段记录时使用
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @return 字段记录键值结果，供调用方继续处理
     */
    private Map<String, Object> toFieldRecord(
            Map<String, Object> row,
            List<EntityField> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((column, value) -> {
            result.put(column, value);
            result.putIfAbsent(underscoreToCamel(column), value);
        });
        for (EntityField field : fields) {
            if (field == null || !StringUtils.hasText(
                    field.getFieldCode())) {
                continue;
            }
            String column = StringUtils.hasText(
                    field.getDbColumnName())
                    ? field.getDbColumnName()
                    : recordMapper.toColumnName(
                            field.getFieldCode());
            if (row.containsKey(column)) {
                result.put(field.getFieldCode(), row.get(column));
            }
        }
        return result;
    }

    /**
     * 生成{@code underscore}截止{@code camel}文本，供后续匹配或展示。
     *
     * @param source 待处理{@code underscore}截止{@code camel}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code underscore}截止{@code camel}文本，供调用方比较或展示
     */
    private String underscoreToCamel(String source) {
        if (!StringUtils.hasText(source)
                || !source.contains("_")) {
            return source;
        }
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char value : source.toCharArray()) {
            if (value == '_') {
                upper = true;
            } else if (upper) {
                result.append(Character.toUpperCase(value));
                upper = false;
            } else {
                result.append(value);
            }
        }
        return result.toString();
    }

    /**
     * 封装运行时实体的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param definition 定义，保存在对象中供后续校验、查询或展示
     * @param fields 字段集合，后续逐项校验、转换或持久化
     */
    private record RuntimeEntity(
            EntityDefinition definition,
            List<EntityField> fields) {
    }
}
