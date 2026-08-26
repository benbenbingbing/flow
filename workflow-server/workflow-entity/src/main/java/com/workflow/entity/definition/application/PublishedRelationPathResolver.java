package com.workflow.entity.definition.application;

import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService.PinnedEntitySnapshot;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.application.model.PublishedRelationPath;
import com.workflow.entity.definition.application.model.PublishedRelationPath.Hop;
import com.workflow.entity.definition.application.model.PublishedRelationPath.LinkField;
import com.workflow.entity.definition.application.model.PublishedRelationPath.LinkValueType;
import com.workflow.entity.definition.application.model.PublishedRelationPath.StepSpec;
import com.workflow.entity.definition.application.model.PublishedRelationPath.StepType;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 把用户选择的关系/引用方式编译为精确钉定的已发布关系路径。
 *
 * <p>该服务是关联内容、多层版本和流程统计共同复用的定义层原语。它不读取
 * 业务记录，也不接受 SQL 或表达式；运行时通过 {@link #validate} 再次校验
 * 所有不可变实体快照及关系身份。</p>
 */
@Service
@RequiredArgsConstructor
public class PublishedRelationPathResolver {

    public static final int MAX_PATH_DEPTH = 8;

    private final EntityPublishedSnapshotService snapshotService;

    /**
     * 基于来源实体的精确发布历史编译路径。
     *
     * @param sourceHistoryId 来源实体发布历史 ID
     * @param steps           设计端按业务名称选择的路径步骤
     * @return 包含每一跳目标发布版本和指纹的冻结路径
     */
    @Transactional(readOnly = true)
    public PublishedRelationPath compile(
            String sourceHistoryId,
            List<StepSpec> steps) {
        requireSteps(steps);
        PinnedEntitySnapshot source = snapshotService
                .getPinnedByHistoryId(required(sourceHistoryId,
                        "来源实体发布历史ID不能为空"));
        EntityPublishedSnapshot current = source.snapshot();
        String currentHash = source.schemaHash();
        List<Hop> hops = new ArrayList<>();
        for (int index = 0; index < steps.size(); index++) {
            StepSpec step = steps.get(index);
            if (step == null || step.type() == null) {
                throw invalid("第 " + (index + 1) + " 步缺少关联方式");
            }
            CompiledHop compiled = compileHop(current, currentHash, step);
            hops.add(compiled.hop(index + 1));
            current = compiled.target().snapshot();
            currentHash = compiled.target().schemaHash();
        }
        return new PublishedRelationPath(
                source.snapshot().getEntityCode(),
                source.snapshot().getHistoryId(),
                source.schemaHash(),
                hops);
    }

    /**
     * 运行时按精确历史重新验证路径。任何历史缺失、指纹漂移或关系身份变化
     * 都会失败，禁止静默改用最新定义。
     */
    @Transactional(readOnly = true)
    public PublishedRelationPath validate(PublishedRelationPath path) {
        if (path == null) {
            throw invalid("关联路径不能为空");
        }
        requireSteps(path.hops());
        PinnedEntitySnapshot source = requirePinned(
                path.sourceHistoryId(), path.sourceSchemaHash(),
                path.sourceEntityCode(), "来源实体");
        EntityPublishedSnapshot current = source.snapshot();
        String currentHash = source.schemaHash();
        for (int index = 0; index < path.hops().size(); index++) {
            Hop expected = path.hops().get(index);
            if (expected == null || expected.index() != index + 1) {
                throw invalid("关联路径步骤序号不连续");
            }
            if (expected.type() == null) {
                throw invalid("关联路径第 " + (index + 1)
                        + " 步缺少关联方式");
            }
            if (!same(current.getEntityCode(), expected.sourceEntityCode())
                    || !same(current.getHistoryId(), expected.sourceHistoryId())
                    || !same(currentHash, expected.sourceSchemaHash())) {
                throw invalid("关联路径第 " + (index + 1)
                        + " 步来源实体版本不一致");
            }
            PinnedEntitySnapshot target = requirePinned(
                    expected.targetHistoryId(),
                    expected.targetSchemaHash(),
                    expected.targetEntityCode(),
                    "第 " + (index + 1) + " 步目标实体");
            validateHopIdentity(current, expected, target.snapshot());
            current = target.snapshot();
            currentHash = target.schemaHash();
        }
        return path;
    }

    private CompiledHop compileHop(
            EntityPublishedSnapshot source,
            String sourceHash,
            StepSpec step) {
        String code = required(step.code(), "关联字段或关系不能为空");
        return switch (step.type()) {
            case RELATION -> compileRelation(source, sourceHash, code);
            case REFERENCE_FIELD -> compileReference(source, sourceHash, code);
            case REVERSE_REFERENCE -> compileReverse(
                    source, sourceHash, code, step.targetEntityId());
        };
    }

    private CompiledHop compileRelation(
            EntityPublishedSnapshot source,
            String sourceHash,
            String relationCode) {
        requireRelationSnapshot(source);
        EntityRelation relation = safe(source.getRelations()).stream()
                .filter(item -> item != null
                        && !Boolean.FALSE.equals(item.getEnabled())
                        && relationCode.equals(item.getRelationCode()))
                .findFirst()
                .orElseThrow(() -> invalid("来源实体未发布关系: " + relationCode));
        PinnedEntitySnapshot target = snapshotService
                .getLatestPinnedByEntityId(required(
                        relation.getChildEntityId(),
                        "关系缺少目标实体: " + relationCode));
        requireEntity(target.snapshot(), relation.getChildEntityCode(),
                "关系目标实体已变化: " + relationCode);
        EntityField childReference = requireRelationChildField(
                target.snapshot(),
                required(relation.getChildRefFieldCode(),
                        "关系缺少子实体引用字段: " + relationCode),
                source.getEntityId(),
                "关系子实体引用字段");
        return new CompiledHop(
                source,
                sourceHash,
                target,
                StepType.RELATION,
                relationCode,
                null,
                childReference.getFieldCode(),
                relationCode,
                relation.getOwnershipType() == null
                        ? null : relation.getOwnershipType().name(),
                relation.getRelationType()
                        != EntityRelation.RelationType.ONE_TO_ONE,
                null,
                linkField(childReference));
    }

    private CompiledHop compileReference(
            EntityPublishedSnapshot source,
            String sourceHash,
            String fieldCode) {
        EntityField field = requireReferenceField(source, fieldCode,
                "来源实体引用字段");
        PinnedEntitySnapshot target = snapshotService
                .getLatestPinnedByEntityId(required(field.getRefEntityId(),
                        "引用字段缺少目标实体: " + fieldCode));
        return new CompiledHop(
                source,
                sourceHash,
                target,
                StepType.REFERENCE_FIELD,
                fieldCode,
                fieldCode,
                null,
                null,
                null,
                field.getFieldType() == EntityField.FieldType.MULTI_REFERENCE,
                linkField(field),
                null);
    }

    private CompiledHop compileReverse(
            EntityPublishedSnapshot source,
            String sourceHash,
            String fieldCode,
            String targetEntityId) {
        PinnedEntitySnapshot target = snapshotService
                .getLatestPinnedByEntityId(required(targetEntityId,
                        "反向引用必须选择目标实体"));
        EntityField field = requireReferenceField(
                target.snapshot(), fieldCode, "目标实体反向引用字段");
        if (!same(source.getEntityId(), field.getRefEntityId())) {
            throw invalid("反向引用字段未指向当前来源实体: " + fieldCode);
        }
        return new CompiledHop(
                source,
                sourceHash,
                target,
                StepType.REVERSE_REFERENCE,
                fieldCode,
                null,
                fieldCode,
                null,
                null,
                true,
                null,
                linkField(field));
    }

    private void validateHopIdentity(
            EntityPublishedSnapshot source,
            Hop expected,
            EntityPublishedSnapshot target) {
        // 运行时只能核对当前 hop 已钉定的两个历史快照。这里不能复用
        // compileHop，因为 compileHop 会读取目标实体 latest，导致目标后来重新
        // 发布后旧宿主路径被错误判定为漂移。
        switch (expected.type()) {
            case RELATION -> validateRelationHop(source, expected, target);
            case REFERENCE_FIELD ->
                    validateReferenceHop(source, expected, target);
            case REVERSE_REFERENCE ->
                    validateReverseHop(source, expected, target);
        }
    }

    private void validateRelationHop(
            EntityPublishedSnapshot source,
            Hop expected,
            EntityPublishedSnapshot target) {
        requireRelationSnapshot(source);
        EntityRelation relation = safe(source.getRelations()).stream()
                .filter(item -> item != null
                        && !Boolean.FALSE.equals(item.getEnabled())
                        && expected.code().equals(item.getRelationCode()))
                .findFirst()
                .orElseThrow(() -> invalid("关联路径第 " + expected.index()
                        + " 步关系不存在"));
        EntityField childReference = requireRelationChildField(
                target,
                relation.getChildRefFieldCode(),
                source.getEntityId(),
                "关系路径第 " + expected.index() + " 步子实体引用字段");
        boolean multiple = relation.getRelationType()
                != EntityRelation.RelationType.ONE_TO_ONE;
        if (!same(relation.getChildEntityId(), target.getEntityId())
                || !same(relation.getChildEntityCode(), target.getEntityCode())
                || !same(relation.getChildRefFieldCode(),
                        expected.targetFieldCode())
                || !same(relation.getRelationCode(), expected.relationCode())
                || !same(relation.getOwnershipType() == null
                                ? null : relation.getOwnershipType().name(),
                        expected.ownershipType())
                || StringUtils.hasText(expected.sourceFieldCode())
                || multiple != expected.multiple()
                || expected.sourceLinkField() != null
                || !sameLink(expected.targetLinkField(),
                        linkField(childReference))) {
            throw invalid("关联路径第 " + expected.index()
                    + " 步关系定义与发布快照不一致");
        }
    }

    private void validateReferenceHop(
            EntityPublishedSnapshot source,
            Hop expected,
            EntityPublishedSnapshot target) {
        EntityField field = requireReferenceField(
                source, expected.code(), "来源实体引用字段");
        boolean multiple = field.getFieldType()
                == EntityField.FieldType.MULTI_REFERENCE;
        if (!same(field.getRefEntityId(), target.getEntityId())
                || !same(expected.sourceFieldCode(), field.getFieldCode())
                || StringUtils.hasText(expected.targetFieldCode())
                || StringUtils.hasText(expected.relationCode())
                || StringUtils.hasText(expected.ownershipType())
                || multiple != expected.multiple()
                || !sameLink(expected.sourceLinkField(), linkField(field))
                || expected.targetLinkField() != null) {
            throw invalid("关联路径第 " + expected.index()
                    + " 步引用定义与发布快照不一致");
        }
    }

    private void validateReverseHop(
            EntityPublishedSnapshot source,
            Hop expected,
            EntityPublishedSnapshot target) {
        EntityField field = requireReferenceField(
                target, expected.code(), "目标实体反向引用字段");
        if (!same(field.getRefEntityId(), source.getEntityId())
                || StringUtils.hasText(expected.sourceFieldCode())
                || !same(expected.targetFieldCode(), field.getFieldCode())
                || StringUtils.hasText(expected.relationCode())
                || StringUtils.hasText(expected.ownershipType())
                || !expected.multiple()
                || expected.sourceLinkField() != null
                || !sameLink(expected.targetLinkField(), linkField(field))) {
            throw invalid("关联路径第 " + expected.index()
                    + " 步反向引用定义与发布快照不一致");
        }
    }

    private PinnedEntitySnapshot requirePinned(
            String historyId,
            String schemaHash,
            String entityCode,
            String label) {
        PinnedEntitySnapshot pinned = snapshotService
                .getPinnedByHistoryId(required(historyId,
                        label + "发布历史ID不能为空"));
        if (!same(schemaHash, pinned.schemaHash())) {
            throw invalid(label + "发布定义指纹不一致");
        }
        requireEntity(pinned.snapshot(), entityCode,
                label + "身份不一致");
        return pinned;
    }

    private EntityField requireReferenceField(
            EntityPublishedSnapshot snapshot,
            String fieldCode,
            String label) {
        EntityField field = safe(snapshot.getFields()).stream()
                .filter(item -> item != null
                        && fieldCode.equals(item.getFieldCode()))
                .findFirst()
                .orElseThrow(() -> invalid(label + "不存在: " + fieldCode));
        if (field.getFieldType() != EntityField.FieldType.REFERENCE
                && field.getFieldType()
                        != EntityField.FieldType.MULTI_REFERENCE) {
            throw invalid(label + "不是实体引用字段: " + fieldCode);
        }
        if (field.getRefEntityType() != null
                && field.getRefEntityType()
                        != EntityField.RefEntityType.CUSTOM) {
            throw invalid(label + "不是自定义实体引用: " + fieldCode);
        }
        if (!StringUtils.hasText(field.getRefEntityId())) {
            throw invalid(label + "缺少目标实体: " + fieldCode);
        }
        return field;
    }

    /**
     * 校验关系定义在目标钉定快照中的实际外键。
     *
     * <p>实体关系只能由子实体的单值自定义引用承载。兼容旧发布中为空的
     * {@code refEntityType}，但拒绝系统引用和多值引用，避免关系元数据与
     * 实际存储形态不一致。</p>
     */
    private EntityField requireRelationChildField(
            EntityPublishedSnapshot target,
            String fieldCode,
            String expectedParentEntityId,
            String label) {
        String requiredFieldCode = required(fieldCode,
                label + "不能为空");
        EntityField field = safe(target.getFields()).stream()
                .filter(item -> item != null
                        && requiredFieldCode.equals(item.getFieldCode()))
                .findFirst()
                .orElseThrow(() -> invalid(label + "不存在: "
                        + requiredFieldCode));
        if (field.getFieldType() != EntityField.FieldType.REFERENCE) {
            throw invalid(label + "必须是单值实体引用: "
                    + requiredFieldCode);
        }
        if (field.getRefEntityType() != null
                && field.getRefEntityType()
                        != EntityField.RefEntityType.CUSTOM) {
            throw invalid(label + "必须是自定义实体引用: "
                    + requiredFieldCode);
        }
        if (!same(expectedParentEntityId, field.getRefEntityId())) {
            throw invalid(label + "未指向关系来源实体: "
                    + requiredFieldCode);
        }
        return field;
    }

    /** 从发布字段生成运行时唯一允许使用的最小投影描述。 */
    private LinkField linkField(EntityField field) {
        boolean multiple = field.getFieldType()
                == EntityField.FieldType.MULTI_REFERENCE;
        return new LinkField(
                field.getFieldCode(),
                multiple
                        ? LinkValueType.MULTI_REFERENCE
                        : LinkValueType.SCALAR_REFERENCE,
                multiple ? null : storageColumn(field),
                field.getRefEntityId());
    }

    private String storageColumn(EntityField field) {
        String configured = field.getDbColumnName();
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        String code = required(field.getFieldCode(), "引用字段编码不能为空");
        return code.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private boolean sameLink(LinkField left, LinkField right) {
        return left == null ? right == null : left.equals(right);
    }

    private void requireRelationSnapshot(EntityPublishedSnapshot snapshot) {
        if (!snapshot.isRelationsSnapshotAvailable()) {
            throw invalid("实体发布版本未冻结关系定义，请重新发布实体后再配置关联路径");
        }
    }

    private void requireEntity(
            EntityPublishedSnapshot snapshot,
            String expectedCode,
            String message) {
        if (snapshot == null || !same(snapshot.getEntityCode(), expectedCode)) {
            throw invalid(message);
        }
    }

    private void requireSteps(List<?> steps) {
        int size = steps == null ? 0 : steps.size();
        if (size < 1 || size > MAX_PATH_DEPTH) {
            throw invalid("关联路径必须包含 1 至 "
                    + MAX_PATH_DEPTH + " 步");
        }
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw invalid(message);
        }
        return value.trim();
    }

    private boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private record CompiledHop(
            EntityPublishedSnapshot source,
            String sourceHash,
            PinnedEntitySnapshot target,
            StepType type,
            String code,
            String sourceFieldCode,
            String targetFieldCode,
            String relationCode,
            String ownershipType,
            boolean multiple,
            LinkField sourceLinkField,
            LinkField targetLinkField) {

        private Hop hop(int index) {
            return new Hop(
                    index,
                    type,
                    code,
                    source.getEntityCode(),
                    source.getHistoryId(),
                    sourceHash,
                    target.snapshot().getEntityCode(),
                    target.snapshot().getHistoryId(),
                    target.schemaHash(),
                    sourceFieldCode,
                    targetFieldCode,
                    relationCode,
                    ownershipType,
                    multiple,
                    sourceLinkField,
                    targetLinkField);
        }
    }
}
