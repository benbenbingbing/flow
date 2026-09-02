package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.contracts.entity.EntityUserReferencePort;
import com.workflow.contracts.entity.EntityUserReferencePort.EntityUserReferenceException;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 以实体定义和实体记录为权威来源读取用户关系字段。
 */
@Component
@RequiredArgsConstructor
public class EntityUserReferenceAdapter
        implements EntityUserReferencePort {

    private static final String USER_ENTITY_CODE = "sys_user";
    private static final int MAX_RESOLVED_USERS = 200;
    private static final int MAX_USER_KEY_LENGTH = 200;

    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityDataDynamicMapper dataMapper;
    private final EntityPhysicalTableResolver tableResolver;
    private final DynamicTableService dynamicTableService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 只接受已发布的用户选择、用户单选关系或用户多选关系字段。
     * 目标实体既兼容历史 refEntityType=USER，也支持当前指向 sys_user 的实体引用。
     */
    @Override
    @Transactional(readOnly = true)
    public UserReferenceField requireUserReferenceField(
            String entityCode,
            String fieldCode) {
        EntityDefinition definition = requireDefinition(entityCode);
        EntityField field = fieldMapper.findByEntityIdAndFieldCode(
                definition.getId(), requireText(fieldCode, "字段编码"));
        if (field == null) {
            throw failure(
                    "ENTITY_USER_REFERENCE_FIELD_MISSING",
                    "实体字段不存在: " + entityCode + "." + fieldCode);
        }
        if (!Boolean.TRUE.equals(field.getIsPublished())) {
            throw failure(
                    "ENTITY_USER_REFERENCE_FIELD_UNPUBLISHED",
                    "审批人来源字段尚未发布: " + entityCode + "." + fieldCode);
        }
        boolean multiple = field.getFieldType()
                == EntityField.FieldType.MULTI_REFERENCE;
        boolean supportedType = field.getFieldType()
                == EntityField.FieldType.USER
                || field.getFieldType() == EntityField.FieldType.REFERENCE
                || multiple;
        if (!supportedType || !targetsSystemUser(field)) {
            throw failure(
                    "ENTITY_USER_REFERENCE_FIELD_UNSUPPORTED",
                    "字段不是用户单选或多选关系: "
                            + entityCode + "." + fieldCode);
        }
        return new UserReferenceField(
                definition.getEntityCode(), field.getFieldCode(), multiple);
    }

    /**
     * 单值字段读取实体主表列；多值字段读取该实体专属多值表。
     * 因此解析不依赖字段属于哪个流程表单，也不会使用可能过期的流程变量副本。
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> readUserKeys(
            String entityCode,
            String recordId,
            String fieldCode) {
        UserReferenceField metadata = requireUserReferenceField(
                entityCode, fieldCode);
        String normalizedRecordId = requireText(recordId, "实体记录 ID");
        EntityDefinition definition = requireDefinition(metadata.entityCode());
        EntityField field = fieldMapper.findByEntityIdAndFieldCode(
                definition.getId(), metadata.fieldCode());
        String tableName = tableResolver.resolve(definition);
        Map<String, Object> record = dataMapper.selectById(
                tableName, normalizedRecordId);
        if (record == null) {
            throw failure(
                    "ENTITY_USER_REFERENCE_RECORD_MISSING",
                    "实体记录不存在: " + entityCode + "/" + recordId);
        }

        if (metadata.multiple() && StringUtils.hasText(field.getRefEntityId())) {
            String multiTable = dynamicTableService.getMultiValueTableName(
                    definition.getEntityCode());
            List<String> values = jdbcTemplate.queryForList(
                    "SELECT target_record_id FROM " + multiTable
                            + " WHERE record_id = ? AND field_code = ?"
                            + " AND target_entity_id = ? AND deleted = 0"
                            + " ORDER BY sort_order, id LIMIT "
                            + (MAX_RESOLVED_USERS + 1),
                    String.class,
                    normalizedRecordId,
                    field.getFieldCode(),
                    field.getRefEntityId());
            return resolveUserKeys(
                    field,
                    requireWithinLimit(distinct(values)));
        }

        Object raw = record.get(columnName(field));
        if (raw == null) {
            raw = record.get(field.getFieldCode());
        }
        List<String> values = requireWithinLimit(normalizeValues(raw));
        if (!metadata.multiple() && values.size() > 1) {
            throw failure(
                    "ENTITY_USER_REFERENCE_CARDINALITY_INVALID",
                    "用户单选关系字段包含多个用户值: "
                            + entityCode + "." + fieldCode);
        }
        return resolveUserKeys(field, values);
    }

    /**
     * 现代实体关系保存的是 sys_user 主键，必须按 ID 精确转换成 username。
     * 不能交给下游做“username 优先、ID 兜底”的模糊解释，否则当某用户名
     * 恰好等于另一用户 ID 时会把审批误派给错误用户。
     */
    private List<String> resolveUserKeys(
            EntityField field,
            List<String> values) {
        if (values.isEmpty()) {
            return values;
        }
        String placeholders = String.join(",",
                java.util.Collections.nCopies(values.size(), "?"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, username, deleted FROM sys_user"
                        + " WHERE id IN (" + placeholders + ")",
                values.toArray());
        LinkedHashSet<String> inputsMatchingUserIds =
                new LinkedHashSet<>();
        Map<String, String> usernamesByInput = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            Object username = row.get("username");
            if (id == null) {
                continue;
            }
            String canonicalId = String.valueOf(id);
            for (String value : values) {
                if (!canonicalId.equalsIgnoreCase(value)) {
                    continue;
                }
                // 即使用户已软删除，保存过的 ID 仍具有身份语义，不能再被
                // 同名 username 接管；只有未删除用户才输出 canonical username。
                inputsMatchingUserIds.add(value);
                if (isNotDeleted(row.get("deleted"))
                        && username != null
                        && StringUtils.hasText(String.valueOf(username))) {
                    usernamesByInput.put(
                            value,
                            String.valueOf(username).trim());
                }
            }
        }
        Map<String, String> legacyUsernamesByInput =
                new LinkedHashMap<>();
        if (!StringUtils.hasText(field.getRefEntityId())) {
            // USER 与历史 refEntityType=USER 字段可能曾直接保存 username。
            // 仅对“从未作为用户 ID 存在”的剩余值做 username 校验，从而
            // 在 ID/username 同值冲突及用户软删除后始终保持 ID 语义优先。
            List<String> unresolved = values.stream()
                    .filter(value -> !inputsMatchingUserIds.contains(value))
                    .toList();
            if (!unresolved.isEmpty()) {
                String usernamePlaceholders = String.join(",",
                        java.util.Collections.nCopies(
                                unresolved.size(), "?"));
                List<String> canonicalUsernames = jdbcTemplate.queryForList(
                        "SELECT username FROM sys_user"
                                + " WHERE username IN ("
                                + usernamePlaceholders + ")"
                                + " AND deleted = 0",
                        String.class,
                        unresolved.toArray());
                // 数据库 username 使用大小写不敏感排序规则；按相同语义把
                // 原输入关联到数据库返回值，并始终输出 canonical username。
                for (String value : unresolved) {
                    for (String canonicalUsername : canonicalUsernames) {
                        if (StringUtils.hasText(canonicalUsername)
                                && canonicalUsername.trim()
                                .equalsIgnoreCase(value)) {
                            legacyUsernamesByInput.put(
                                    value, canonicalUsername.trim());
                            break;
                        }
                    }
                }
            }
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String username = usernamesByInput.get(value);
            if (StringUtils.hasText(username)) {
                result.add(username);
            } else {
                String legacyUsername = legacyUsernamesByInput.get(value);
                if (StringUtils.hasText(legacyUsername)) {
                    result.add(legacyUsername);
                }
            }
        }
        return List.copyOf(result);
    }

    /** 兼容 JDBC 驱动将 TINYINT 返回为布尔、数字或字符串的差异。 */
    private boolean isNotDeleted(Object deleted) {
        if (deleted instanceof Boolean booleanValue) {
            return !booleanValue;
        }
        if (deleted instanceof Number number) {
            return number.intValue() == 0;
        }
        return deleted != null
                && "0".equals(String.valueOf(deleted).trim());
    }

    private EntityDefinition requireDefinition(String entityCode) {
        String normalized = requireText(entityCode, "实体编码");
        return definitionMapper.findByEntityCode(normalized)
                .orElseThrow(() -> failure(
                        "ENTITY_USER_REFERENCE_ENTITY_MISSING",
                        "实体不存在: " + normalized));
    }

    private boolean targetsSystemUser(EntityField field) {
        if (field.getFieldType() == EntityField.FieldType.USER) {
            return true;
        }
        if (StringUtils.hasText(field.getRefEntityId())) {
            EntityDefinition target = definitionMapper.selectById(
                    field.getRefEntityId());
            return target != null
                    && USER_ENTITY_CODE.equalsIgnoreCase(
                            target.getEntityCode());
        }
        return field.getRefEntityType()
                == EntityField.RefEntityType.USER;
    }

    private String columnName(EntityField field) {
        return StringUtils.hasText(field.getDbColumnName())
                ? field.getDbColumnName()
                : toSnakeCase(field.getFieldCode());
    }

    private List<String> normalizeValues(Object raw) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Collection<?> collection) {
            collection.forEach(value -> addValue(values, value));
        } else if (raw.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(raw); index++) {
                addValue(values, Array.get(raw, index));
            }
        } else {
            String text = String.valueOf(raw).trim();
            if (text.startsWith("[") && text.endsWith("]")) {
                text = text.substring(1, text.length() - 1);
            }
            for (String value : text.split(",")) {
                addValue(values, value.replace("\"", ""));
            }
        }
        return List.copyOf(values);
    }

    private List<String> distinct(Collection<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            values.forEach(value -> addValue(result, value));
        }
        return List.copyOf(result);
    }

    private void addValue(LinkedHashSet<String> values, Object raw) {
        if (raw == null) {
            return;
        }
        String value = String.valueOf(raw).trim();
        if (!value.isEmpty()) {
            if (value.length() > MAX_USER_KEY_LENGTH) {
                throw failure(
                        "ENTITY_USER_REFERENCE_VALUE_INVALID",
                        "实体用户关系字段包含超长用户标识");
            }
            values.add(value);
        }
    }

    private List<String> requireWithinLimit(List<String> values) {
        if (values.size() > MAX_RESOLVED_USERS) {
            throw failure(
                    "ENTITY_USER_REFERENCE_LIMIT_EXCEEDED",
                    "实体用户关系字段人数超过上限: " + MAX_RESOLVED_USERS);
        }
        return values;
    }

    private String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw failure(
                    "ENTITY_USER_REFERENCE_CONTEXT_INVALID",
                    label + "不能为空");
        }
        return value.trim();
    }

    private EntityUserReferenceException failure(
            String reasonCode,
            String message) {
        return new EntityUserReferenceException(reasonCode, message);
    }

    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z])([A-Z]+)", "$1_$2")
                .toLowerCase(java.util.Locale.ROOT);
    }
}
