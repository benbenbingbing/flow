package com.workflow.entity.form.application;

import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.form.uniqueness.application.FormUniqueMutationContext;
import com.workflow.entity.form.uniqueness.application.TrustedSubFormUniqueReference;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiExtensionDefinitionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按已发布子表单契约递归处理提交数据。
 */
@RequiredArgsConstructor
final class PublishedSubFormSubmissionProcessor {

    private static final int MAX_NESTED_DEPTH = 8;
    private static final Set<String> SUB_FORM_NODE_TYPES =
            Set.of("SUB_FORM", "REPEATER");

    private final EntityDefinitionMapper entityDefinitionMapper;
    private final UiConfigReleaseService releaseService;
    private final UiExtensionDefinitionValidator schemaValidator;
    private final JsonDocumentCodec codec;

    /**
     * 应用已发布子级表单提交，并将结果传给后续步骤。
     *
     * @param node 节点，作为 {@code SubFormParameterContractPolicy.contract} 的输入影响后续处理
     * @param parentForm 父级表单，作为 {@code trustedRuntimeContext} 的输入影响后续处理
     * @param parentEntityCode 父级实体编码，后续用于应用已发布子级表单提交时定位或关联目标
     * @param parentRecordId 父级记录ID，后续用于应用已发布子级表单提交时定位或关联目标
     * @param mode 模式标识，决定后续已发布子级表单提交采用的处理分支
     * @param parentRecord 父级记录，作为 {@code SubFormParameterContractPolicy.runtimeSource} 的输入影响后续处理
     * @param executionContext 执行上下文，向后续已发布子级表单提交步骤传递身份、配置或状态
     * @param resolutionContext 执行上下文，向后续已发布子级表单提交步骤传递身份、配置或状态
     * @param parentContext 执行上下文，向后续已发布子级表单提交步骤传递身份、配置或状态
     * @param depth 深度，供本方法应用已发布子级表单提交时使用
     * @param authoritativeSubmission {@code authoritative}提交，供本方法应用已发布子级表单提交时使用
     * @param childFormApplier 子级表单{@code applier}，供本方法应用已发布子级表单提交时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    void apply(
            EntityFormNode node,
            EntityForm parentForm,
            String parentEntityCode,
            String parentRecordId,
            String mode,
            Map<String, Object> parentRecord,
            FormSubmissionExecutionContext executionContext,
            UiRuntimeResolutionContext resolutionContext,
            Context parentContext,
            int depth,
            boolean authoritativeSubmission,
            ChildFormApplier childFormApplier) {
        if (!SUB_FORM_NODE_TYPES.contains(normalize(node.getNodeType()))) {
            return;
        }
        SubFormParameterContractPolicy.Contract contract =
                SubFormParameterContractPolicy.contract(node, codec);
        if (!contract.present()) {
            return;
        }
        SubFormParameterContractPolicy.requireVersion(contract);
        SubFormParameterContractPolicy.RelationConfig relation =
                SubFormParameterContractPolicy.relationConfig(node, codec);
        if (!StringUtils.hasText(relation.fieldCode())
                || !parentRecord.containsKey(relation.fieldCode())) {
            return;
        }
        Object relationValue = parentRecord.get(relation.fieldCode());
        if (relationValue == null) {
            return;
        }
        List<Map<String, Object>> rows = relationRows(relationValue);
        if (rows.isEmpty()) {
            return;
        }
        if (depth >= MAX_NESTED_DEPTH) {
            throw new IllegalArgumentException(
                    "子表单嵌套层级不能超过 "
                            + MAX_NESTED_DEPTH
                            + " 层");
        }

        ResolvedEntityFormRelease childResolved =
                resolveChildRelease(relation, resolutionContext);
        EntityForm childForm = childResolved.form();
        EntityDefinition childDefinition =
                requireChildDefinition(childForm, relation);
        Map<String, Object> inputSchema =
                SubFormParameterContractPolicy.inputParameterSchema(
                        childForm,
                        codec);
        schemaValidator.validateSchemaDefinition(
                inputSchema,
                "子表单输入参数契约");
        SubFormParameterContractPolicy.validateRuntimeTargets(
                contract,
                inputSchema,
                childForm.getFields(),
                relation.childRefFieldCode());

        FormSubmissionExecutionContext safeExecutionContext =
                safeExecutionContext(executionContext, mode);
        Map<String, Object> trustedContext = trustedRuntimeContext(
                safeExecutionContext,
                parentForm,
                parentEntityCode,
                mode);
        Map<String, Object> parameterSource =
                SubFormParameterContractPolicy.runtimeSource(
                        parentRecordId,
                        parentRecord,
                        trustedContext,
                        Map.of(),
                        Map.of(),
                        relation.asMap());
        Map<String, Object> params =
                SubFormParameterContractPolicy.resolveParameters(
                        contract,
                        inputSchema,
                        parameterSource);
        schemaValidator.validateSchemaValue(
                inputSchema,
                params,
                "子表单输入参数");

        List<Map<String, Object>> processed =
                new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> row =
                    new LinkedHashMap<>(rows.get(index));
            String childRecordId = text(row.get("id"));
            Map<String, Object> rowContext = rowContext(row, index);
            Map<String, Object> source =
                    SubFormParameterContractPolicy.runtimeSource(
                            parentRecordId,
                            parentRecord,
                            trustedContext,
                            params,
                            rowContext,
                            relation.asMap());
            Collection<String> blockedFields =
                    StringUtils.hasText(relation.childRefFieldCode())
                            ? List.of(relation.childRefFieldCode())
                            : List.of();
            SubFormParameterContractPolicy.applyEmptyOnlyInitialization(
                    row,
                    contract,
                    source,
                    blockedFields);
            String rowIdentity = StringUtils.hasText(childRecordId)
                    ? childRecordId : String.valueOf(index);
            Context childContext = new Context(
                    mapValue(source.get("parent")),
                    params,
                    rowContext,
                    relation.asMap(),
                    parentContext.childOwnerPath(
                            nodeOwnerKey(node)
                                    + "/row:"
                                    + rowIdentity));
            Map<String, Object> processedRow = new LinkedHashMap<>(
                    childFormApplier.apply(
                    childResolved,
                    childDefinition.getEntityCode(),
                    childRecordId,
                    mode,
                    row,
                    safeExecutionContext,
                    resolutionContext,
                    childContext,
                    depth + 1));
            if (authoritativeSubmission) {
                // 只有正式提交能激活子行终检；无副作用预览的返回值
                // 会离开写入链路，不能携带内部可信标记。
                TrustedSubFormUniqueReference.attach(
                        processedRow,
                        childDefinition.getEntityCode(),
                        new FormUniqueMutationContext.Reference(
                                childForm.getId(),
                                childResolved.releaseId(),
                                childResolved.releaseVersion(),
                                childResolved.effectiveReleaseId(),
                                childResolved.effectiveContentHash(),
                                childResolved.hotfixTargetId()));
            }
            processed.add(processedRow);
        }
        parentRecord.put(
                relation.fieldCode(),
                relationValue instanceof Map<?, ?>
                        ? processed.get(0)
                        : processed);
    }

    /**
     * 解析子级发布版本；输出作为后续校验或处理的输入。
     *
     * @param relation 关系，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param resolutionContext 执行上下文，向后续子级发布版本步骤传递身份、配置或状态
     * @return 解析后的子级发布版本结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private ResolvedEntityFormRelease resolveChildRelease(
            SubFormParameterContractPolicy.RelationConfig relation,
            UiRuntimeResolutionContext resolutionContext) {
        if (!StringUtils.hasText(relation.childFormId())
                || !StringUtils.hasText(relation.childFormReleaseId())
                || relation.childFormReleaseVersion() == null) {
            throw new IllegalArgumentException(
                    "子表单发布版本引用不完整: "
                            + relation.fieldCode());
        }
        ResolvedEntityFormRelease resolved =
                resolutionContext == null
                        ? releaseService.resolveRuntimeFormRelease(
                                relation.childFormId(),
                                relation.childFormReleaseId(),
                                relation.childFormReleaseVersion())
                        : releaseService.resolveRuntimeFormRelease(
                                relation.childFormId(),
                                relation.childFormReleaseId(),
                                relation.childFormReleaseVersion(),
                                resolutionContext);
        if (resolved.form() == null) {
            throw new IllegalArgumentException(
                    "子表单发布版本不存在: "
                            + relation.childFormId());
        }
        return resolved;
    }

    /**
     * 校验并获取子级定义；不满足约束时阻止后续处理。
     *
     * @param childForm 子级表单，作为 {@code entityDefinitionMapper.selectById} 的输入影响后续处理
     * @param relation 关系，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 校验并获取后的子级定义结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityDefinition requireChildDefinition(
            EntityForm childForm,
            SubFormParameterContractPolicy.RelationConfig relation) {
        EntityDefinition definition =
                entityDefinitionMapper.selectById(
                        childForm.getEntityId());
        if (definition == null
                || !StringUtils.hasText(definition.getEntityCode())) {
            throw new IllegalArgumentException(
                    "子表单所属实体不存在: "
                            + childForm.getEntityId());
        }
        if (StringUtils.hasText(relation.childEntityId())
                && !relation.childEntityId().equals(
                        childForm.getEntityId())) {
            throw new IllegalArgumentException(
                    "子表单发布版本所属实体与关系配置不一致: "
                            + relation.fieldCode());
        }
        return definition;
    }

    /**
     * 处理安全执行上下文，并将结果传给后续步骤。
     *
     * @param executionContext 执行上下文，向后续安全执行上下文步骤传递身份、配置或状态
     * @param mode 模式标识，决定后续安全执行上下文采用的处理分支
     * @return 处理后的安全执行上下文结果，供调用方继续处理
     */
    private FormSubmissionExecutionContext safeExecutionContext(
            FormSubmissionExecutionContext executionContext,
            String mode) {
        return executionContext == null
                ? FormSubmissionExecutionContext.standalone(
                        "FORM_" + normalizeOperation(mode))
                : executionContext;
    }

    /**
     * 整理可信运行时上下文数据，供调用方遍历或继续处理。
     *
     * @param executionContext 执行上下文，向后续可信运行时上下文步骤传递身份、配置或状态
     * @param form 表单，作为 {@code context.put} 的输入影响后续处理
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param mode 模式标识，决定后续可信运行时上下文采用的处理分支
     * @return 可信运行时上下文键值结果，供调用方继续处理
     */
    private Map<String, Object> trustedRuntimeContext(
            FormSubmissionExecutionContext executionContext,
            EntityForm form,
            String entityCode,
            String mode) {
        Map<String, Object> context =
                executionContext.runtimeContext();
        context.put(
                "mode",
                StringUtils.hasText(mode) ? mode : "edit");
        context.put("formId", form.getId());
        context.put("entityId", form.getEntityId());
        if (StringUtils.hasText(entityCode)) {
            context.put("entityCode", entityCode);
        }
        return context;
    }

    /**
     * 整理关系行数据，供调用方遍历或继续处理。
     *
     * @param relationValue 关系值，供本方法处理关系行时使用
     * @return 已发布子级表单提交集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private List<Map<String, Object>> relationRows(Object relationValue) {
        if (relationValue instanceof Map<?, ?> map) {
            return List.of(stringMap(map));
        }
        if (!(relationValue instanceof List<?> values)) {
            throw new IllegalArgumentException(
                    "子表单提交数据必须为对象或对象数组");
        }
        List<Map<String, Object>> result =
                new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (!(value instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(
                        "子表单第 "
                                + (index + 1)
                                + " 行必须为对象");
            }
            result.add(stringMap(map));
        }
        return result;
    }

    /**
     * 整理行上下文数据，供调用方遍历或继续处理。
     *
     * @param row 行，作为 {@code context.put} 的输入影响后续处理
     * @param index 索引，作为 {@code context.put} 的输入影响后续处理
     * @return 行上下文键值结果，供调用方继续处理
     */
    private Map<String, Object> rowContext(
            Map<String, Object> row,
            int index) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("index", index);
        context.put("id", row.get("id"));
        context.put(
                "isNew",
                !StringUtils.hasText(text(row.get("id"))));
        context.put("data", row);
        return context;
    }

    /**
     * 将输入映射的键规范为字符串，供后续序列化和字段读取。
     *
     * @param source 待处理字符串映射的原始输入，结果供调用方继续使用
     * @return 字符串映射键值结果，供调用方继续处理
     */
    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param value 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map
                ? stringMap(map) : Map.of();
    }

    /**
     * 生成节点归属方键文本，供后续匹配或展示。
     *
     * @param node 节点，供本方法处理节点归属方键时使用
     * @return 处理后的节点归属方键文本，供调用方比较或展示
     */
    private String nodeOwnerKey(EntityFormNode node) {
        return StringUtils.hasText(node.getId())
                ? "node:" + node.getId()
                : "node:" + String.valueOf(node.getNodeKey());
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
     * @param value 待规范化已发布子级表单提交的原始输入，结果供调用方继续使用
     * @return 规范化后的已发布子级表单提交文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return value == null
                ? "" : value.trim().toUpperCase();
    }

    /**
     * 规范化操作；输出作为后续校验或处理的输入。
     *
     * @param mode 模式标识，决定后续操作采用的处理分支
     * @return 规范化后的操作文本，供调用方比较或展示
     */
    private static String normalizeOperation(String mode) {
        return StringUtils.hasText(mode)
                ? mode.trim().toUpperCase()
                : "EDIT";
    }

    /**
     * 定义子级表单{@code applier}的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
     */
    @FunctionalInterface
    interface ChildFormApplier {
        /**
         * 应用子级表单{@code applier}，并将结果传给后续步骤。
         *
         * @param resolved 已解析，供本方法应用子级表单{@code applier}时使用
         * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
         * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
         * @param mode 模式标识，决定后续子级表单{@code applier}采用的处理分支
         * @param submittedData 已提交数据，供本方法应用子级表单{@code applier}时使用
         * @param executionContext 执行上下文，向后续子级表单{@code applier}步骤传递身份、配置或状态
         * @param resolutionContext 执行上下文，向后续子级表单{@code applier}步骤传递身份、配置或状态
         * @param context 执行上下文，向后续子级表单{@code applier}步骤传递身份、配置或状态
         * @param depth 深度，供本方法应用子级表单{@code applier}时使用
         * @return 子级表单{@code applier}键值结果，供调用方继续处理
         */
        Map<String, Object> apply(
                ResolvedEntityFormRelease resolved,
                String entityCode,
                String recordId,
                String mode,
                Map<String, Object> submittedData,
                FormSubmissionExecutionContext executionContext,
                UiRuntimeResolutionContext resolutionContext,
                Context context,
                int depth);
    }

    /**
     * 封装上下文的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param parent 父级，保存在对象中供后续校验、查询或展示
     * @param params 参数，保存在对象中供后续校验、查询或展示
     * @param row 行，保存在对象中供后续校验、查询或展示
     * @param relation 关系，保存在对象中供后续校验、查询或展示
     * @param ownerPath 归属方路径，保存在对象中供后续校验、查询或展示
     */
    record Context(
            Map<String, Object> parent,
            Map<String, Object> params,
            Map<String, Object> row,
            Map<String, Object> relation,
            String ownerPath) {

        /**
         * 初始化上下文，保存构造参数供后续方法使用。
         *
         * @param parent 父级，保存在对象中供后续校验、查询或展示
         * @param params 参数，保存在对象中供后续校验、查询或展示
         * @param row 行，保存在对象中供后续校验、查询或展示
         * @param relation 关系，保存在对象中供后续校验、查询或展示
         * @param ownerPath 归属方路径，保存在对象中供后续校验、查询或展示
         */
        Context {
            parent = parent == null ? Map.of() : parent;
            params = params == null ? Map.of() : params;
            row = row == null ? Map.of() : row;
            relation = relation == null ? Map.of() : relation;
            ownerPath = ownerPath == null ? "" : ownerPath;
        }

        /**
         * 处理根，并将结果传给后续步骤。
         *
         * @return 处理后的根结果，供调用方继续处理
         */
        static Context root() {
            return new Context(
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    "");
        }

        /**
         * 整理运行时值集合数据，供调用方遍历或继续处理。
         *
         * @return 运行时值集合键值结果，供调用方继续处理
         */
        Map<String, Object> runtimeValues() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("parent", parent);
            result.put("params", params);
            result.put("row", row);
            result.put("relation", relation);
            return result;
        }

        /**
         * 生成归属方键文本，供后续匹配或展示。
         *
         * @param localOwnerKey 本地归属方键，后续用于授权校验、关联或幂等去重
         * @return 处理后的归属方键文本，供调用方比较或展示
         */
        String ownerKey(String localOwnerKey) {
            return StringUtils.hasText(ownerPath)
                    ? ownerPath + "/" + localOwnerKey
                    : localOwnerKey;
        }

        /**
         * 生成子级归属方路径文本，供后续匹配或展示。
         *
         * @param segment 片段，供本方法处理子级归属方路径时使用
         * @return 处理后的子级归属方路径文本，供调用方比较或展示
         */
        String childOwnerPath(String segment) {
            return StringUtils.hasText(ownerPath)
                    ? ownerPath + "/" + segment
                    : segment;
        }
    }
}
