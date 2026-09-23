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

    /**
     * 校验节点；不满足约束时阻止后续处理。
     *
     * @param parentForm 父级表单，作为 {@code fieldMapper.findByEntityId} 的输入影响后续处理
     * @param node 节点，作为 {@code SubFormParameterContractPolicy.contract} 的输入影响后续处理
     * @param release 发布版本，作为 {@code codec.readObject} 的输入影响后续处理
     */
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

    /**
     * 校验快照；不满足约束时阻止后续处理。
     *
     * @param parentForm 父级表单，作为 {@code validateRelationReleaseEntity} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 生成子级引用字段编码文本，供后续匹配或展示。
     *
     * @param parentForm 父级表单，作为 {@code requireBoundRelation} 的输入影响后续处理
     * @param node 节点，作为 {@code requireBoundRelation} 的输入影响后续处理
     * @return 处理后的子级引用字段编码文本，供调用方比较或展示
     */
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

    /**
     * 校验关系发布版本实体；不满足约束时阻止后续处理。
     *
     * @param parentForm 父级表单，作为 {@code requireBoundRelation} 的输入影响后续处理
     * @param node 节点，作为 {@code requireBoundRelation} 的输入影响后续处理
     * @param release 发布版本，作为 {@code formMapper.selectById} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 校验并获取绑定关系；不满足约束时阻止后续处理。
     *
     * @param parentForm 父级表单，作为 {@code relationMapper.selectActiveByBindingRef} 的输入影响后续处理
     * @param node 节点，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 校验并获取后的绑定关系结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 校验并获取发布版本；不满足约束时阻止后续处理。
     *
     * @param reference 引用，作为 {@code releaseMapper.selectById} 的输入影响后续处理
     * @return 校验并获取后的发布版本结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 判断是否具有发布版本引用；判断结果决定调用方的后续分支。
     *
     * @param reference 引用，供本方法判断是否具有发布版本引用时使用
     * @return 发布版本引用条件成立时为 true，否则为 false
     */
    private boolean hasReleaseReference(
            SubFormParameterContractPolicy.RelationConfig reference) {
        return StringUtils.hasText(reference.childFormId())
                || StringUtils.hasText(
                        reference.childFormReleaseId())
                || reference.childFormReleaseVersion() != null;
    }

    /**
     * 整理{@code released}字段数据，供调用方遍历或继续处理。
     *
     * @param snapshot 快照，供本方法处理{@code released}字段时使用
     * @return 实体表单字段集合，供调用方遍历或展示
     */
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

    /**
     * 整理对象映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理对象映射的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于处理对象映射时匹配或展示
     * @return 对象映射键值结果，供调用方继续处理
     */
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

    /**
     * 处理布尔值{@code flag}，并将结果传给后续步骤。
     *
     * @param value 待处理布尔值{@code flag}的原始输入，结果供调用方继续使用
     * @return 处理后的布尔值{@code flag}结果，供调用方继续处理
     */
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

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            String current = text(value);
            if (StringUtils.hasText(current)) {
                return current.trim();
            }
        }
        return null;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化子级表单参数契约发布版本的原始输入，结果供调用方继续使用
     * @return 规范化后的子级表单参数契约发布版本文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return value == null
                ? "" : value.trim().toUpperCase();
    }
}
