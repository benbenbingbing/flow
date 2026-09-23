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
import java.util.UUID;
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
            "name",
            "code",
            "status",
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

    /**
     * 列出实体关系定义；查询结果供调用方展示或继续处理。
     *
     * @param parentEntityId 父级实体ID，后续用于列出实体关系定义时定位或关联目标
     * @return 实体关系集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<EntityRelationDTO> list(String parentEntityId) {
        requireParent(parentEntityId);
        List<EntityRelation> relations =
                relationMapper.selectAllByParentEntityId(parentEntityId);
        return relations == null
                ? List.of()
                : relations.stream().map(this::toDto).toList();
    }

    /**
     * 读取实体关系；结果供调用方展示或继续处理。
     *
     * @param parentEntityId 父级实体ID，后续用于读取实体关系定义时定位或关联目标
     * @param relationId 关系ID，后续用于读取实体关系定义时定位或关联目标
     * @return 符合条件的实体关系结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public EntityRelationDTO get(
            String parentEntityId,
            String relationId) {
        return toDto(requireOwned(parentEntityId, relationId));
    }

    /**
     * 返回当前实体可引用的正向和反向关系；只返回目录，运行时仍按发布快照鉴权。
     *
     * @param entityId 实体ID，后续用于处理可用时定位或关联目标
     * @return 实体关系集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<EntityRelationDTO> available(String entityId) {
        var result = new java.util.ArrayList<>(list(entityId));
        var incoming = relationMapper.selectAllByChildEntityId(entityId);
        for (var relation : incoming == null ? List.<EntityRelation>of() : incoming) {
            var dto = toDto(relation);
            dto.setDirection("REVERSE");
            result.add(dto);
        }
        return result;
    }

    /**
     * 创建实体关系定义；结果供后续流程传递或持久化。
     *
     * @param parentEntityId 父级实体ID，后续用于创建实体关系定义时定位或关联目标
     * @param request 本次请求，后续经校验后用于创建实体关系定义
     * @return 创建后的实体关系定义结果，供调用方继续处理
     */
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

    /**
     * 更新实体关系定义；后续读取或执行将使用更新后的状态。
     *
     * @param parentEntityId 父级实体ID，后续用于更新实体关系定义时定位或关联目标
     * @param relationId 关系ID，后续用于更新实体关系定义时定位或关联目标
     * @param request 本次请求，后续经校验后用于更新实体关系定义
     * @return 更新后的实体关系定义结果，供调用方继续处理
     */
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

    /**
     * 删除实体关系定义；后续读取或执行将使用更新后的状态。
     *
     * @param parentEntityId 父级实体ID，后续用于删除实体关系定义时定位或关联目标
     * @param relationId 关系ID，后续用于删除实体关系定义时定位或关联目标
     */
    @Transactional
    public void delete(String parentEntityId, String relationId) {
        EntityRelation relation = requireOwned(parentEntityId, relationId);
        relationMapper.deleteById(relation.getId());
    }

    /**
     * 发布前校验全部启用关系，防止冻结不完整的数据图。
     *
     * @param parentEntityId 父级实体ID，后续用于校验发布时定位或关联目标
     */
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

    /**
     * 校验实体关系定义；不满足约束时阻止后续处理。
     *
     * @param parent 父级，作为 {@code newRelationKey} 的输入影响后续处理
     * @param existing 已有，供本方法校验实体关系定义时使用
     * @param request 本次请求，后续经校验后用于校验实体关系定义
     * @return 校验后的实体关系定义结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private Validated validate(
            EntityDefinition parent,
            EntityRelation existing,
            EntityRelationSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("实体关系配置不能为空");
        }
        String relationCode = stableCode(
                StringUtils.hasText(request.getRelationCode())
                        ? request.getRelationCode()
                        : existing == null ? newRelationKey(parent.getId())
                        : existing.getRelationCode(),
                "关系编码",
                existing == null);
        String dataKey = stableCode(
                StringUtils.hasText(request.getDataKey())
                        ? request.getDataKey()
                        : existing == null ? availableDataKey(parent.getId(), relationCode)
                        : effectiveDataKey(existing),
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
        validateChildReference(parent, child, childRef);
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

    /**
     * 校验关系承载字段可存放父记录 ID，并保留已有引用目标约束。
     *
     * <p>组成关系和普通关联在所有权、删除语义上不同，但二者都依赖子记录上的
     * 同一个权威父 ID 字段；普通字段的目标由关系定义声明，不需要再配置
     * 实体引用，但不能覆盖字段已经声明的其他引用目标。</p>
     *
     * @param parent 父级，作为 {@code EntityRelationFieldPolicy.violation} 的输入影响后续处理
     * @param child 子级，供本方法校验子级引用时使用
     * @param childRef 子级引用，作为 {@code EntityRelationFieldPolicy.violation} 的输入影响后续处理
     */
    private void validateChildReference(
            EntityDefinition parent,
            EntityDefinition child,
            EntityField childRef) {
        var violation = EntityRelationFieldPolicy.violation(childRef, parent.getId());
        if (violation != null) {
            throw new BusinessConflictException(
                    violation.code(), violation.message() + ": "
                            + child.getEntityCode() + "."
                            + childRef.getFieldCode());
        }
    }

    /**
     * 校验父级字段命名空间；不满足约束时阻止后续处理。
     *
     * @param parent 父级，作为 {@code fieldMapper.findByEntityIdAndFieldCode} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于校验父级字段命名空间
     * @param dataKey 数据键，后续用于授权校验、关联或幂等去重
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
                    "关系数据键与当前实体的字段同名: " + dataKey
                            + "。数据键是关系结果的内部标识，不是关联字段；新建时可留空自动生成，实际关联字段请在关联实体中选择。");
        }
        if (StringUtils.hasText(request.getParentFieldId())
                && !Objects.equals(
                collision.getId(), request.getParentFieldId().trim())) {
            throw new IllegalArgumentException(
                    "旧版关系绑定字段 ID 与字段编码不一致");
        }
    }

    /**
     * 应用实体关系定义，并将结果传给后续步骤。
     *
     * @param relation 关系，供本方法应用实体关系定义时使用
     * @param parent 父级，作为 {@code relation.setParentEntityId} 的输入影响后续处理
     * @param validated 已校验，作为 {@code relation.setRelationCode} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于应用实体关系定义
     */
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

    /**
     * 校验并获取父级；不满足约束时阻止后续处理。
     *
     * @param parentEntityId 父级实体ID，后续用于校验并获取父级时定位或关联目标
     * @return 校验并获取后的父级结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
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

    /**
     * 校验并获取{@code owned}；不满足约束时阻止后续处理。
     *
     * @param parentEntityId 父级实体ID，后续用于校验并获取{@code owned}时定位或关联目标
     * @param relationId 关系ID，后续用于校验并获取{@code owned}时定位或关联目标
     * @return 校验并获取后的{@code owned}结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 转换为DTO；输出作为后续校验或处理的输入。
     *
     * @param relation 关系，作为 {@code dto.setId} 的输入影响后续处理
     * @return 转换为后的DTO结果，供调用方继续处理
     */
    private EntityRelationDTO toDto(EntityRelation relation) {
        EntityRelationDTO dto = new EntityRelationDTO();
        dto.setId(relation.getId());
        dto.setParentEntityId(relation.getParentEntityId());
        dto.setParentEntityCode(relation.getParentEntityCode());
        EntityDefinition parent = entityMapper.selectById(relation.getParentEntityId());
        dto.setParentEntityName(parent == null ? relation.getParentEntityCode() : parent.getEntityName());
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

    /**
     * 转换为请求；输出作为后续校验或处理的输入。
     *
     * @param relation 关系，作为 {@code request.setRelationCode} 的输入影响后续处理
     * @return 转换为后的请求结果，供调用方继续处理
     */
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

    /**
     * 生成有效数据键文本，供后续匹配或展示。
     *
     * @param relation 关系，供本方法处理有效数据键时使用
     * @return 处理后的有效数据键文本，供调用方比较或展示
     */
    private String effectiveDataKey(EntityRelation relation) {
        if (StringUtils.hasText(relation.getDataKey())) {
            return relation.getDataKey();
        }
        if (StringUtils.hasText(relation.getParentFieldCode())) {
            return relation.getParentFieldCode();
        }
        return relation.getRelationCode();
    }

    /**
     * 为新关系生成内部标识；不要求业务人员创建同名实体字段。
     * 查询包含退役关系，避免误用历史表单或快照仍引用的键。
     *
     * @param parentId 父级ID，后续用于处理新关系键时定位或关联目标
     * @return 处理后的新关系键文本，供调用方比较或展示
     */
    private String newRelationKey(String parentId) {
        String key;
        do {
            key = "rel_" + UUID.randomUUID().toString().replace("-", "");
        } while (relationMapper.selectByRelationCode(parentId, key) != null
                || !isDataKeyAvailable(parentId, key));
        return key;
    }

    /**
     * 自定义关系编码可以与业务字段同名，承载结果的数据键必须独立分配。
     *
     * @param parentId 父级ID，后续用于处理可用数据键时定位或关联目标
     * @param relationCode 关系编码，后续用于处理可用数据键时定位或关联目标
     * @return 处理后的可用数据键文本，供调用方比较或展示
     */
    private String availableDataKey(String parentId, String relationCode) {
        return isDataKeyAvailable(parentId, relationCode)
                ? relationCode : newRelationKey(parentId);
    }

    /**
     * 判断是否数据键可用；判断结果决定调用方的后续分支。
     *
     * @param parentId 父级ID，后续用于判断是否数据键可用时定位或关联目标
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 数据键可用条件成立时为 true，否则为 false
     */
    private boolean isDataKeyAvailable(String parentId, String key) {
        return !RESERVED_DATA_KEYS.contains(key.toLowerCase(Locale.ROOT))
                && fieldMapper.findByEntityIdAndFieldCode(parentId, key) == null
                && relationMapper.selectByDataKey(parentId, key) == null;
    }

    /**
     * 生成稳定编码文本，供后续匹配或展示。
     *
     * @param value 待处理稳定编码的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于处理稳定编码时匹配或展示
     * @param enforceNewDefinitionPattern {@code enforce}新定义{@code pattern}，供本方法处理稳定编码时使用
     * @return 处理后的稳定编码文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 生成必填文本文本，供后续匹配或展示。
     *
     * @param value 待处理必填文本的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于处理必填文本时匹配或展示
     * @param maxLength 最大长度，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 处理后的必填文本文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 把空白文本转为 null，避免后续把空字符串当作有效配置。
     *
     * @param value 待处理空白截止空值的原始输入，结果供调用方继续使用
     * @return 处理后的空白截止空值文本，供调用方比较或展示
     */
    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 封装已校验的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param relationCode 关系编码，后续用于处理已校验时定位或关联目标
     * @param relationName 关系名称，后续用于处理已校验时匹配或展示
     * @param dataKey 数据键，后续用于授权校验、关联或幂等去重
     * @param child 子级，保存在对象中供后续校验、查询或展示
     * @param childRefFieldCode 子级引用字段编码，后续用于处理已校验时定位或关联目标
     * @param relationType 关系类型标识，决定后续已校验采用的处理分支
     * @param ownershipType {@code ownership}类型标识，决定后续已校验采用的处理分支
     */
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
