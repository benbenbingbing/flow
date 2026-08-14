package com.workflow.entity.definition.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.api.request.EntityRelationSaveRequest;
import com.workflow.entity.definition.api.response.EntityRelationDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 独立实体关系定义服务。
 *
 * <p>关系拥有独立生命周期；实体字段仅可保留一个可选的旧版展示绑定，
 * 不再负责创建、更新或删除关系。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityRelationDefinitionService {

    private static final Pattern STABLE_CODE =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final Set<String> RESERVED_DATA_KEYS = Set.of(
            "id",
            "data",
            "title",
            "name",
            "code",
            "status",
            "datano",
            "processinstanceid",
            "processstarttime",
            "processendtime",
            "currenttaskid",
            "currenttaskname",
            "currenttaskassignee",
            "submitterid",
            "submittername",
            "deptid",
            "deptname",
            "submittime",
            "createdat",
            "updatedat",
            "createdby",
            "updatedby",
            "entitycode",
            "entityname",
            "extdata",
            "actioncapabilities",
            "processvariables",
            "startprocess",
            "listkey",
            "listreleaseid",
            "listreleaseversion",
            "listreleaseresolutiontoken",
            "formid",
            "formreleaseid",
            "formreleaseversion",
            "formreleaseresolutiontoken");

    private final EntityDefinitionMapper entityMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityRelationMapper relationMapper;

    @Transactional(readOnly = true)
    public List<EntityRelationDTO> list(String parentEntityId) {
        requireParent(parentEntityId);
        List<EntityRelation> relations =
                relationMapper.selectAllByParentEntityId(parentEntityId);
        return relations == null
                ? List.of()
                : relations.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public EntityRelationDTO get(
            String parentEntityId,
            String relationId) {
        return toDto(requireOwned(parentEntityId, relationId));
    }

    @Transactional
    public EntityRelationDTO create(
            String parentEntityId,
            EntityRelationSaveRequest request) {
        EntityDefinition parent = requireParent(parentEntityId);
        Validated validated = validate(parent, null, request);
        EntityRelation relation = new EntityRelation();
        apply(relation, parent, validated, request);
        relation.setDeleted(0);
        relationMapper.insert(relation);
        return toDto(relation);
    }

    @Transactional
    public EntityRelationDTO update(
            String parentEntityId,
            String relationId,
            EntityRelationSaveRequest request) {
        EntityDefinition parent = requireParent(parentEntityId);
        EntityRelation relation = requireOwned(parentEntityId, relationId);
        Validated validated = validate(parent, relation, request);
        apply(relation, parent, validated, request);
        relationMapper.updateById(relation);
        return toDto(relation);
    }

    @Transactional
    public void delete(String parentEntityId, String relationId) {
        EntityRelation relation = requireOwned(parentEntityId, relationId);
        relationMapper.deleteById(relation.getId());
    }

    /** 发布前校验全部启用关系，防止冻结不完整的数据图。 */
    @Transactional(readOnly = true)
    public void validateForPublish(String parentEntityId) {
        EntityDefinition parent = requireParent(parentEntityId);
        List<EntityRelation> relations =
                relationMapper.selectByParentEntityId(parentEntityId);
        for (EntityRelation relation
                : relations == null ? List.<EntityRelation>of() : relations) {
            EntityRelationSaveRequest request = toRequest(relation);
            validate(parent, relation, request);
            EntityDefinition child = entityMapper.selectById(
                    relation.getChildEntityId());
            if (child == null
                    || child.getStatus()
                    != EntityDefinition.Status.PUBLISHED) {
                throw new BusinessConflictException(
                        "ENTITY_RELATION_CHILD_NOT_PUBLISHED",
                        "实体关系的子实体尚未发布: "
                                + relation.getChildEntityCode());
            }
        }
    }

    private Validated validate(
            EntityDefinition parent,
            EntityRelation existing,
            EntityRelationSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("实体关系配置不能为空");
        }
        String relationCode = stableCode(
                request.getRelationCode(),
                "关系编码",
                existing == null);
        String dataKey = stableCode(
                request.getDataKey(),
                "关系数据键",
                existing == null);
        if (existing != null
                && !Objects.equals(existing.getRelationCode(), relationCode)) {
            throw new BusinessConflictException(
                    "ENTITY_RELATION_CODE_LOCKED",
                    "关系编码创建后不能修改");
        }
        if (existing != null
                && !Objects.equals(effectiveDataKey(existing), dataKey)) {
            throw new BusinessConflictException(
                    "ENTITY_RELATION_DATA_KEY_LOCKED",
                    "关系数据键创建后不能修改");
        }
        if (RESERVED_DATA_KEYS.contains(
                dataKey.toLowerCase(Locale.ROOT))) {
            throw new BusinessConflictException(
                    "ENTITY_RELATION_DATA_KEY_RESERVED",
                    "关系数据键与实体运行时标准属性冲突: " + dataKey);
        }
        validateParentFieldNamespace(parent, request, dataKey);
        EntityRelation duplicateCode = relationMapper.selectByRelationCode(
                parent.getId(), relationCode);
        if (duplicateCode != null
                && (existing == null
                || !Objects.equals(duplicateCode.getId(), existing.getId()))) {
            if (Integer.valueOf(1).equals(duplicateCode.getDeleted())) {
                throw new BusinessConflictException(
                        "ENTITY_RELATION_CODE_RETIRED",
                        "关系编码已退役，不能复用: " + relationCode);
            }
            throw new BusinessConflictException(
                    "ENTITY_RELATION_CODE_DUPLICATE",
                    "同一父实体内关系编码不能重复: " + relationCode);
        }
        EntityRelation duplicateDataKey = relationMapper.selectByDataKey(
                parent.getId(), dataKey);
        if (duplicateDataKey != null
                && (existing == null
                || !Objects.equals(duplicateDataKey.getId(), existing.getId()))) {
            if (Integer.valueOf(1).equals(duplicateDataKey.getDeleted())) {
                throw new BusinessConflictException(
                        "ENTITY_RELATION_DATA_KEY_RETIRED",
                        "关系数据键已退役，不能复用: " + dataKey);
            }
            throw new BusinessConflictException(
                    "ENTITY_RELATION_DATA_KEY_DUPLICATE",
                    "同一父实体内关系数据键不能重复: " + dataKey);
        }

        String relationName = requiredText(
                request.getRelationName(), "关系名称", 200);
        String childEntityId = requiredText(
                request.getChildEntityId(), "子实体", 64);
        EntityDefinition child = entityMapper.selectById(childEntityId);
        if (child == null) {
            throw new IllegalArgumentException(
                    "子实体不存在: " + childEntityId);
        }
        if (child.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            throw new BusinessConflictException(
                    "ENTITY_RELATION_SYSTEM_CHILD_UNSUPPORTED",
                    "实体关系的子实体必须使用动态存储: "
                            + child.getEntityCode());
        }
        String childRefFieldCode = stableCode(
                request.getChildRefFieldCode(),
                "子实体外键字段",
                existing == null);
        EntityField childRef = fieldMapper.findByEntityIdAndFieldCode(
                child.getId(), childRefFieldCode);
        if (childRef == null) {
            throw new IllegalArgumentException(
                    "子实体外键字段不存在: "
                            + child.getEntityCode() + "."
                            + childRefFieldCode);
        }
        EntityRelation.RelationType relationType =
                request.getRelationType() == null
                        ? EntityRelation.RelationType.ONE_TO_MANY
                        : request.getRelationType();
        EntityRelation.OwnershipType ownershipType =
                request.getOwnershipType() == null
                        ? EntityRelation.OwnershipType.COMPOSITION
                        : request.getOwnershipType();
        if (ownershipType == EntityRelation.OwnershipType.ASSOCIATION
                && Boolean.TRUE.equals(request.getCascadeDelete())) {
            throw new IllegalArgumentException(
                    "普通关联关系不能开启级联删除");
        }
        return new Validated(
                relationCode,
                relationName,
                dataKey,
                child,
                childRefFieldCode,
                relationType,
                ownershipType);
    }

    private void validateParentFieldNamespace(
            EntityDefinition parent,
            EntityRelationSaveRequest request,
            String dataKey) {
        EntityField collision = fieldMapper.findByEntityIdAndFieldCode(
                parent.getId(), dataKey);
        if (collision == null) {
            return;
        }
        boolean explicitLegacyBinding = Objects.equals(
                dataKey, blankToNull(request.getParentFieldCode()));
        if (!explicitLegacyBinding) {
            throw new BusinessConflictException(
                    "ENTITY_RELATION_DATA_KEY_FIELD_CONFLICT",
                    "关系数据键与父实体字段编码冲突: " + dataKey);
        }
        if (StringUtils.hasText(request.getParentFieldId())
                && !Objects.equals(
                collision.getId(), request.getParentFieldId().trim())) {
            throw new IllegalArgumentException(
                    "旧版关系绑定字段 ID 与字段编码不一致");
        }
    }

    private void apply(
            EntityRelation relation,
            EntityDefinition parent,
            Validated validated,
            EntityRelationSaveRequest request) {
        relation.setParentEntityId(parent.getId());
        relation.setParentEntityCode(parent.getEntityCode());
        relation.setRelationCode(validated.relationCode());
        relation.setRelationName(validated.relationName());
        relation.setDataKey(validated.dataKey());
        relation.setChildEntityId(validated.child().getId());
        relation.setChildEntityCode(validated.child().getEntityCode());
        relation.setChildRefFieldCode(validated.childRefFieldCode());
        relation.setRelationType(validated.relationType());
        relation.setOwnershipType(validated.ownershipType());
        relation.setCascadeDelete(
                validated.ownershipType()
                        == EntityRelation.OwnershipType.COMPOSITION
                        && (request.getCascadeDelete() == null
                        || request.getCascadeDelete()));
        relation.setRequired(Boolean.TRUE.equals(request.getRequired()));
        relation.setSortOrder(
                request.getSortOrder() == null ? 0 : request.getSortOrder());
        relation.setEnabled(request.getEnabled() == null
                || request.getEnabled());
        relation.setParentFieldId(blankToNull(request.getParentFieldId()));
        relation.setParentFieldCode(
                blankToNull(request.getParentFieldCode()));
    }

    private EntityDefinition requireParent(String parentEntityId) {
        EntityDefinition parent = entityMapper.selectById(parentEntityId);
        if (parent == null) {
            throw new IllegalArgumentException(
                    "父实体不存在: " + parentEntityId);
        }
        if (parent.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            throw new BusinessConflictException(
                    "ENTITY_SYSTEM_DEFINITION_PROTECTED",
                    "平台系统实体不能配置聚合关系");
        }
        return parent;
    }

    private EntityRelation requireOwned(
            String parentEntityId,
            String relationId) {
        EntityRelation relation = relationMapper.selectById(relationId);
        if (relation == null
                || !Objects.equals(
                parentEntityId, relation.getParentEntityId())) {
            throw new IllegalArgumentException(
                    "实体关系不存在: " + relationId);
        }
        return relation;
    }

    private EntityRelationDTO toDto(EntityRelation relation) {
        EntityRelationDTO dto = new EntityRelationDTO();
        dto.setId(relation.getId());
        dto.setParentEntityId(relation.getParentEntityId());
        dto.setParentEntityCode(relation.getParentEntityCode());
        dto.setRelationCode(relation.getRelationCode());
        dto.setRelationName(relation.getRelationName());
        dto.setDataKey(effectiveDataKey(relation));
        dto.setChildEntityId(relation.getChildEntityId());
        dto.setChildEntityCode(relation.getChildEntityCode());
        EntityDefinition child = entityMapper.selectById(
                relation.getChildEntityId());
        dto.setChildEntityName(child == null
                ? null : child.getEntityName());
        dto.setChildRefFieldCode(relation.getChildRefFieldCode());
        dto.setRelationType(relation.getRelationType());
        dto.setOwnershipType(relation.getOwnershipType() == null
                ? EntityRelation.OwnershipType.COMPOSITION
                : relation.getOwnershipType());
        dto.setCascadeDelete(relation.getCascadeDelete());
        dto.setRequired(relation.getRequired());
        dto.setSortOrder(relation.getSortOrder());
        dto.setEnabled(relation.getEnabled());
        dto.setParentFieldId(relation.getParentFieldId());
        dto.setParentFieldCode(relation.getParentFieldCode());
        return dto;
    }

    private EntityRelationSaveRequest toRequest(EntityRelation relation) {
        EntityRelationSaveRequest request = new EntityRelationSaveRequest();
        request.setRelationCode(relation.getRelationCode());
        request.setRelationName(relation.getRelationName());
        request.setDataKey(effectiveDataKey(relation));
        request.setChildEntityId(relation.getChildEntityId());
        request.setChildRefFieldCode(relation.getChildRefFieldCode());
        request.setRelationType(relation.getRelationType());
        request.setOwnershipType(relation.getOwnershipType());
        request.setCascadeDelete(relation.getCascadeDelete());
        request.setRequired(relation.getRequired());
        request.setSortOrder(relation.getSortOrder());
        request.setEnabled(relation.getEnabled());
        request.setParentFieldId(relation.getParentFieldId());
        request.setParentFieldCode(relation.getParentFieldCode());
        return request;
    }

    private String effectiveDataKey(EntityRelation relation) {
        if (StringUtils.hasText(relation.getDataKey())) {
            return relation.getDataKey();
        }
        if (StringUtils.hasText(relation.getParentFieldCode())) {
            return relation.getParentFieldCode();
        }
        return relation.getRelationCode();
    }

    private String stableCode(
            String value,
            String label,
            boolean enforceNewDefinitionPattern) {
        String normalized = requiredText(value, label, 100);
        if (enforceNewDefinitionPattern
                && !STABLE_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    label + "仅允许字母开头及字母、数字、下划线");
        }
        return normalized;
    }

    private String requiredText(
            String value, String label, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    label + "长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record Validated(
            String relationCode,
            String relationName,
            String dataKey,
            EntityDefinition child,
            String childRefFieldCode,
            EntityRelation.RelationType relationType,
            EntityRelation.OwnershipType ownershipType) {
    }
}
