package com.workflow.entity.form.application;

import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 校验已发布子表单参数契约及其固定发布版本。
 */
@RequiredArgsConstructor
final class SubFormParameterContractReleaseValidator {

    private static final Set<String> SUB_FORM_NODE_TYPES =
            Set.of("SUB_FORM", "REPEATER");

    private final EntityFormMapper formMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityRelationMapper relationMapper;
    private final UiConfigReleaseMapper releaseMapper;
    private final JsonDocumentCodec codec;

    void validateNode(
            EntityForm parentForm,
            EntityFormNode node,
            UiConfigRelease release) {
        SubFormParameterContractPolicy.Contract contract =
                SubFormParameterContractPolicy.contract(node, codec);
        if (!contract.present()) {
            return;
        }
        Map<String, Object> snapshot = codec.readObject(
                release.getSnapshotDocument(),
                "子表单发布快照");
        Map<String, Object> formDocument = objectMap(
                snapshot.get("form"),
                "子表单发布快照表单");
        Map<String, Object> viewConfig = objectMap(
                formDocument.get("viewConfig"),
                "子表单发布视图配置");
        Map<String, Object> inputSchema = objectMap(
                viewConfig.get("inputParameterSchema"),
                "子表单输入参数契约");
        List<EntityField> parentFields =
                fieldMapper.findByEntityId(parentForm.getEntityId());
        SubFormParameterContractPolicy.validateContract(
                contract,
                inputSchema,
                parentFields,
                releasedFields(snapshot),
                childRefFieldCode(parentForm, node));
    }

    void validateSnapshot(EntityForm parentForm) {
        if (parentForm == null) {
            throw new IllegalArgumentException(
                    "发布快照表单不能为空");
        }
        for (EntityFormNode node
                : parentForm.getNodes() == null
                        ? List.<EntityFormNode>of()
                        : parentForm.getNodes()) {
            if (!SUB_FORM_NODE_TYPES.contains(
                    normalize(node.getNodeType()))) {
                continue;
            }
            SubFormParameterContractPolicy.RelationConfig reference =
                    SubFormParameterContractPolicy.relationConfig(
                            node,
                            codec);
            if (!hasReleaseReference(reference)) {
                continue;
            }
            UiConfigRelease release = requireRelease(reference);
            validateRelationReleaseEntity(parentForm, node, release);
            validateNode(parentForm, node, release);
        }
    }

    private String childRefFieldCode(
            EntityForm parentForm,
            EntityFormNode node) {
        if ("RELATION".equals(normalize(node.getBindingType()))) {
            return requireBoundRelation(parentForm, node)
                    .getChildRefFieldCode();
        }
        return SubFormParameterContractPolicy
                .relationConfig(node, codec)
                .childRefFieldCode();
    }

    private void validateRelationReleaseEntity(
            EntityForm parentForm,
            EntityFormNode node,
            UiConfigRelease release) {
        if (!"RELATION".equals(normalize(node.getBindingType()))) {
            return;
        }
        EntityRelation relation =
                requireBoundRelation(parentForm, node);
        EntityForm childForm =
                formMapper.selectById(release.getConfigId());
        if (childForm == null
                || !Objects.equals(
                        relation.getChildEntityId(),
                        childForm.getEntityId())) {
            throw new IllegalArgumentException(
                    "子表单发布版本所属实体与绑定关系不一致: "
                            + release.getConfigId());
        }
    }

    private EntityRelation requireBoundRelation(
            EntityForm parentForm,
            EntityFormNode node) {
        EntityRelation relation =
                relationMapper.selectActiveByBindingRef(
                        parentForm.getEntityId(),
                        node.getBindingRef());
        if (relation == null) {
            throw new IllegalArgumentException(
                    "表单节点绑定的实体关系不存在或已禁用: "
                            + node.getBindingRef());
        }
        if (relation.getRelationType() == null
                || !StringUtils.hasText(relation.getChildEntityId())
                || !StringUtils.hasText(
                        relation.getChildRefFieldCode())) {
            throw new IllegalArgumentException(
                    "实体关系配置不完整: "
                            + relation.getRelationCode());
        }
        return relation;
    }

    private UiConfigRelease requireRelease(
            SubFormParameterContractPolicy.RelationConfig reference) {
        if (!StringUtils.hasText(reference.childFormId())
                || !StringUtils.hasText(
                        reference.childFormReleaseId())
                || reference.childFormReleaseVersion() == null) {
            throw new IllegalArgumentException(
                    "子表单节点必须固定 childFormId、"
                            + "childFormReleaseId 和 "
                            + "childFormReleaseVersion: "
                            + reference.fieldCode());
        }
        UiConfigRelease release =
                releaseMapper.selectById(
                        reference.childFormReleaseId());
        if (release == null
                || !"FORM".equalsIgnoreCase(
                        release.getConfigType())
                || !Objects.equals(
                        reference.childFormId(),
                        release.getConfigId())) {
            throw new IllegalArgumentException(
                    "子表单发布版本与表单不匹配: "
                            + reference.childFormId()
                            + "@"
                            + reference.childFormReleaseId());
        }
        if (!Objects.equals(
                reference.childFormReleaseVersion(),
                release.getVersion())) {
            throw new IllegalArgumentException(
                    "子表单发布版本号不匹配: "
                            + reference.childFormId()
                            + " 期望 v"
                            + reference.childFormReleaseVersion()
                            + "，实际 v"
                            + release.getVersion());
        }
        if (!StringUtils.hasText(release.getSnapshotDocument())) {
            throw new IllegalArgumentException(
                    "子表单发布快照为空: "
                            + reference.childFormId()
                            + "@v"
                            + reference.childFormReleaseVersion());
        }
        return release;
    }

    private boolean hasReleaseReference(
            SubFormParameterContractPolicy.RelationConfig reference) {
        return StringUtils.hasText(reference.childFormId())
                || StringUtils.hasText(
                        reference.childFormReleaseId())
                || reference.childFormReleaseVersion() != null;
    }

    private List<EntityFormField> releasedFields(
            Map<String, Object> snapshot) {
        Object configured = snapshot.get("legacyFields");
        if (!(configured instanceof List<?> values)) {
            return List.of();
        }
        List<EntityFormField> result = new ArrayList<>();
        for (Object value : values) {
            Map<String, Object> field =
                    objectMap(value, "子表单发布字段");
            EntityFormField item = new EntityFormField();
            item.setId(text(field.get("id")));
            item.setFieldId(text(field.get("fieldId")));
            item.setFieldCode(firstText(
                    field.get("fieldCode"),
                    field.get("fieldKey")));
            item.setFieldName(firstText(
                    field.get("fieldName"),
                    field.get("fieldLabel")));
            item.setFieldType(firstText(
                    field.get("fieldType"),
                    field.get("componentType")));
            item.setIsReadonly(booleanFlag(
                    field.get("isReadonly")));
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> objectMap(
            Object value,
            String label) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) ->
                    result.put(String.valueOf(key), item));
            return result;
        }
        if (value instanceof String document
                && StringUtils.hasText(document)) {
            return codec.readObject(document, label);
        }
        return new LinkedHashMap<>();
    }

    private Integer booleanFlag(Object value) {
        if (value instanceof Boolean flag) {
            return flag ? 1 : 0;
        }
        if (value instanceof Number number) {
            return number.intValue() == 0 ? 0 : 1;
        }
        return "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value))
                ? 1 : 0;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String current = text(value);
            if (StringUtils.hasText(current)) {
                return current.trim();
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        return value == null
                ? "" : value.trim().toUpperCase();
    }
}
