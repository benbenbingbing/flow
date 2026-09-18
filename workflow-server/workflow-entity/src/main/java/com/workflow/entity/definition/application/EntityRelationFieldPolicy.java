package com.workflow.entity.definition.application;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Set;

/** 实体关系承载字段的统一规则，供定义、发布快照和运行时写入共同使用。 */
public final class EntityRelationFieldPolicy {

    private static final Set<EntityField.FieldType> VARCHAR_TYPES = Set.of(
            EntityField.FieldType.STRING, EntityField.FieldType.SELECT,
            EntityField.FieldType.RADIO, EntityField.FieldType.REFERENCE,
            EntityField.FieldType.USER, EntityField.FieldType.DEPT);

    private EntityRelationFieldPolicy() {
    }

    /**
     * 检查字段能否承载动态实体的 VARCHAR(64) 主键；兼容时返回 null。
     *
     * <p>普通字段的关联目标由关系定义明确指定，不能依赖其 refEntityId。
     * 类型按服务端建表规则推导，不信任可由客户端填写的 dbType；多值存储、
     * 数字隐式转换以及已有引用目标冲突均不允许。查询、权限和生命周期仍由关系定义控制。</p>
     *
     * @param field 子实体中的真实字段或钉定发布字段
     * @param parentEntityId 关系声明的父实体 ID
     * @return 不兼容的原因与稳定错误码，兼容时为 null
     */
    public static Violation violation(EntityField field, String parentEntityId) {
        if (field == null || field.getFieldType() == null
                || !VARCHAR_TYPES.contains(field.getFieldType())
                || "MULTI_TABLE".equalsIgnoreCase(field.getValueStorage())
                || "id".equalsIgnoreCase(field.getFieldCode())
                || "id".equalsIgnoreCase(field.getDbColumnName())) {
            return new Violation("ENTITY_RELATION_CHILD_REF_TYPE_INVALID",
                    "关联字段必须是与记录 ID 类型兼容的单值字符串字段，不能使用主键或多值字段");
        }
        if (field.getFieldType() == EntityField.FieldType.USER
                || field.getFieldType() == EntityField.FieldType.DEPT
                || (field.getRefEntityType() != null
                && field.getRefEntityType() != EntityField.RefEntityType.CUSTOM)
                || (field.getFieldType() == EntityField.FieldType.REFERENCE
                && !Objects.equals(parentEntityId, field.getRefEntityId()))
                || (StringUtils.hasText(field.getRefEntityId())
                && !Objects.equals(parentEntityId, field.getRefEntityId()))) {
            return new Violation("ENTITY_RELATION_CHILD_REF_TARGET_INVALID",
                    "关联字段已声明的引用目标必须是当前实体");
        }
        // 与 DynamicTableService 的 VARCHAR 默认长度及上限一致，确保完整保留 ID。
        int length = field.getFieldLength() == null ? 200 : field.getFieldLength();
        if (length < 64 || length > 4096) {
            return new Violation("ENTITY_RELATION_CHILD_REF_LENGTH_INVALID",
                    "关联字段长度须为 64～4096，才能完整保存记录 ID");
        }
        return null;
    }

    public record Violation(String code, String message) {
    }
}
