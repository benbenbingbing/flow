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
     *
     * @param path 路径，作为 {@code requireSteps} 的输入影响后续处理
     * @return 校验后的已发布关系路径解析器结果，供调用方继续处理
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

    /**
     * 编译跳；结果供调用方的后续步骤使用。
     *
     * @param source 待编译跳的原始输入，结果供调用方继续使用
     * @param sourceHash 来源哈希，作为 {@code compileRelation} 的输入影响后续处理
     * @param step 步骤，作为 {@code required} 的输入影响后续处理
     * @return 编译后的跳结果，供调用方继续处理
     */
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

    /**
     * 编译关系；结果供调用方的后续步骤使用。
     *
     * @param source 待编译关系的原始输入，结果供调用方继续使用
     * @param sourceHash 来源哈希，作为 {@code CompiledHop} 的输入影响后续处理
     * @param relationCode 关系编码，后续用于编译关系时定位或关联目标
     * @return 编译后的关系结果，供调用方继续处理
     */
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
                relationLinkField(childReference, source.getEntityId()));
    }

    /**
     * 编译引用；结果供调用方的后续步骤使用。
     *
     * @param source 待编译引用的原始输入，结果供调用方继续使用
     * @param sourceHash 来源哈希，作为 {@code CompiledHop} 的输入影响后续处理
     * @param fieldCode 字段编码，后续用于编译引用时定位或关联目标
     * @return 编译后的引用结果，供调用方继续处理
     */
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

    /**
     * 编译{@code reverse}；结果供调用方的后续步骤使用。
     *
     * @param source 待编译{@code reverse}的原始输入，结果供调用方继续使用
     * @param sourceHash 来源哈希，作为 {@code CompiledHop} 的输入影响后续处理
     * @param fieldCode 字段编码，后续用于编译{@code reverse}时定位或关联目标
     * @param targetEntityId 目标实体ID，后续用于编译{@code reverse}时定位或关联目标
     * @return 编译后的{@code reverse}结果，供调用方继续处理
     */
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

    /**
     * 校验跳身份；不满足约束时阻止后续处理。
     *
     * @param source 待校验跳身份的原始输入，结果供调用方继续使用
     * @param expected 预期，作为 {@code validateRelationHop} 的输入影响后续处理
     * @param target 目标，作为 {@code validateRelationHop} 的输入影响后续处理
     */
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

    /**
     * 校验关系跳；不满足约束时阻止后续处理。
     *
     * @param source 待校验关系跳的原始输入，结果供调用方继续使用
     * @param expected 预期，作为 {@code hasText} 的输入影响后续处理
     * @param target 目标，作为 {@code requireRelationChildField} 的输入影响后续处理
     */
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
                        relationLinkField(childReference, source.getEntityId()))) {
            throw invalid("关联路径第 " + expected.index()
                    + " 步关系定义与发布快照不一致");
        }
    }

    /**
     * 校验引用跳；不满足约束时阻止后续处理。
     *
     * @param source 待校验引用跳的原始输入，结果供调用方继续使用
     * @param expected 预期，作为 {@code requireReferenceField} 的输入影响后续处理
     * @param target 目标，供本方法校验引用跳时使用
     */
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

    /**
     * 校验{@code reverse}跳；不满足约束时阻止后续处理。
     *
     * @param source 待校验{@code reverse}跳的原始输入，结果供调用方继续使用
     * @param expected 预期，作为 {@code requireReferenceField} 的输入影响后续处理
     * @param target 目标，作为 {@code requireReferenceField} 的输入影响后续处理
     */
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

    /**
     * 校验并获取固定；不满足约束时阻止后续处理。
     *
     * @param historyId 历史ID，后续用于校验并获取固定时定位或关联目标
     * @param schemaHash 结构哈希，供本方法校验并获取固定时使用
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param label 标签，后续用于校验并获取固定时匹配或展示
     * @return 校验并获取后的固定结果，供调用方继续处理
     */
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

    /**
     * 校验并获取引用字段；不满足约束时阻止后续处理。
     *
     * @param snapshot 快照，作为 {@code safe} 的输入影响后续处理
     * @param fieldCode 字段编码，后续用于校验并获取引用字段时定位或关联目标
     * @param label 标签，后续用于校验并获取引用字段时匹配或展示
     * @return 校验并获取后的引用字段结果，供调用方继续处理
     */
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
     * <p>普通单值字符串也可承载关系，使用同一兼容规则校验精确发布快照，
     * 不能放开引用字段自身的目标校验，也不能退回当前草稿字段。</p>
     *
     * @param target 目标，作为 {@code safe} 的输入影响后续处理
     * @param fieldCode 字段编码，后续用于校验并获取关系子级字段时定位或关联目标
     * @param expectedParentEntityId 预期父级实体ID，后续用于校验并获取关系子级字段时定位或关联目标
     * @param label 标签，后续用于校验并获取关系子级字段时匹配或展示
     * @return 校验并获取后的关系子级字段结果，供调用方继续处理
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
        var violation = EntityRelationFieldPolicy.violation(field, expectedParentEntityId);
        if (violation != null) {
            throw invalid(label + violation.message() + ": " + requiredFieldCode);
        }
        return field;
    }

    /**
     * 普通字段没有引用元数据，链接投影的目标必须取自已校验的关系来源实体。
     *
     * @param field 字段，作为 {@code LinkField} 的输入影响后续处理
     * @param parentEntityId 父级实体ID，后续用于处理关系链接字段时定位或关联目标
     * @return 处理后的关系链接字段结果，供调用方继续处理
     */
    private LinkField relationLinkField(EntityField field, String parentEntityId) {
        return new LinkField(field.getFieldCode(), LinkValueType.SCALAR_REFERENCE,
                storageColumn(field), parentEntityId);
    }

    /**
     * 从发布字段生成运行时唯一允许使用的最小投影描述。
     *
     * @param field 字段，作为 {@code LinkField} 的输入影响后续处理
     * @return 处理后的链接字段结果，供调用方继续处理
     */
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

    /**
     * 生成存储列文本，供后续匹配或展示。
     *
     * @param field 字段，作为 {@code required} 的输入影响后续处理
     * @return 处理后的存储列文本，供调用方比较或展示
     */
    private String storageColumn(EntityField field) {
        String configured = field.getDbColumnName();
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        String code = required(field.getFieldCode(), "引用字段编码不能为空");
        return code.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 判断相同链接条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，供本方法处理相同链接时使用
     * @param right 右侧，作为 {@code left.equals} 的输入影响后续处理
     * @return 相同链接条件成立时为 true，否则为 false
     */
    private boolean sameLink(LinkField left, LinkField right) {
        return left == null ? right == null : left.equals(right);
    }

    /**
     * 校验并获取关系快照；不满足约束时阻止后续处理。
     *
     * @param snapshot 快照，供本方法校验并获取关系快照时使用
     */
    private void requireRelationSnapshot(EntityPublishedSnapshot snapshot) {
        if (!snapshot.isRelationsSnapshotAvailable()) {
            throw invalid("实体发布版本未冻结关系定义，请重新发布实体后再配置关联路径");
        }
    }

    /**
     * 校验并获取实体；不满足约束时阻止后续处理。
     *
     * @param snapshot 快照，供本方法校验并获取实体时使用
     * @param expectedCode 预期编码，后续用于校验并获取实体时定位或关联目标
     * @param message 消息，作为 {@code invalid} 的输入影响后续处理
     */
    private void requireEntity(
            EntityPublishedSnapshot snapshot,
            String expectedCode,
            String message) {
        if (snapshot == null || !same(snapshot.getEntityCode(), expectedCode)) {
            throw invalid(message);
        }
    }

    /**
     * 校验并获取步骤集合；不满足约束时阻止后续处理。
     *
     * @param steps 步骤集合，供本方法校验并获取步骤集合时使用
     */
    private void requireSteps(List<?> steps) {
        int size = steps == null ? 0 : steps.size();
        if (size < 1 || size > MAX_PATH_DEPTH) {
            throw invalid("关联路径必须包含 1 至 "
                    + MAX_PATH_DEPTH + " 步");
        }
    }

    /**
     * 生成必填文本，供后续匹配或展示。
     *
     * @param value 待处理必填的原始输入，结果供调用方继续使用
     * @param message 消息，作为 {@code invalid} 的输入影响后续处理
     * @return 处理后的必填文本，供调用方比较或展示
     */
    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw invalid(message);
        }
        return value.trim();
    }

    /**
     * 判断相同条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，供本方法处理相同时使用
     * @param right 右侧，作为 {@code left.equals} 的输入影响后续处理
     * @return 相同条件成立时为 true，否则为 false
     */
    private boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    /**
     * 整理安全数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 已发布关系路径解析器集合，供调用方遍历或展示
     */
    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @param message 消息，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 处理后的无效结果，供调用方继续处理
     */
    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    /**
     * 封装{@code compiled}跳的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param source 待处理{@code compiled}跳的原始输入，结果供调用方继续使用
     * @param sourceHash 来源哈希，保存在对象中供后续校验、查询或展示
     * @param target 目标，保存在对象中供后续校验、查询或展示
     * @param type 类型标识，决定后续{@code compiled}跳采用的处理分支
     * @param code 业务编码，供后续匹配和引用
     * @param sourceFieldCode 来源字段编码，后续用于处理{@code compiled}跳时定位或关联目标
     * @param targetFieldCode 目标字段编码，后续用于处理{@code compiled}跳时定位或关联目标
     * @param relationCode 关系编码，后续用于处理{@code compiled}跳时定位或关联目标
     * @param ownershipType {@code ownership}类型标识，决定后续{@code compiled}跳采用的处理分支
     * @param multiple {@code multiple}，保存在对象中供后续校验、查询或展示
     * @param sourceLinkField 来源链接字段，保存在对象中供后续校验、查询或展示
     * @param targetLinkField 目标链接字段，保存在对象中供后续校验、查询或展示
     */
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

        /**
         * 处理跳，并将结果传给后续步骤。
         *
         * @param index 索引，作为 {@code Hop} 的输入影响后续处理
         * @return 处理后的跳结果，供调用方继续处理
         */
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
