package com.workflow.entity.form.application;

import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiDataSourceDefinitionValidator;
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
    private final UiDataSourceDefinitionValidator schemaValidator;
    private final JsonDocumentCodec codec;

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
            processed.add(childFormApplier.apply(
                    childResolved,
                    childDefinition.getEntityCode(),
                    childRecordId,
                    mode,
                    row,
                    safeExecutionContext,
                    resolutionContext,
                    childContext,
                    depth + 1));
        }
        parentRecord.put(
                relation.fieldCode(),
                relationValue instanceof Map<?, ?>
                        ? processed.get(0)
                        : processed);
    }

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

    private FormSubmissionExecutionContext safeExecutionContext(
            FormSubmissionExecutionContext executionContext,
            String mode) {
        return executionContext == null
                ? FormSubmissionExecutionContext.standalone(
                        "FORM_" + normalizeOperation(mode))
                : executionContext;
    }

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

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map
                ? stringMap(map) : Map.of();
    }

    private String nodeOwnerKey(EntityFormNode node) {
        return StringUtils.hasText(node.getId())
                ? "node:" + node.getId()
                : "node:" + String.valueOf(node.getNodeKey());
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        return value == null
                ? "" : value.trim().toUpperCase();
    }

    private static String normalizeOperation(String mode) {
        return StringUtils.hasText(mode)
                ? mode.trim().toUpperCase()
                : "EDIT";
    }

    @FunctionalInterface
    interface ChildFormApplier {
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

    record Context(
            Map<String, Object> parent,
            Map<String, Object> params,
            Map<String, Object> row,
            Map<String, Object> relation,
            String ownerPath) {

        Context {
            parent = parent == null ? Map.of() : parent;
            params = params == null ? Map.of() : params;
            row = row == null ? Map.of() : row;
            relation = relation == null ? Map.of() : relation;
            ownerPath = ownerPath == null ? "" : ownerPath;
        }

        static Context root() {
            return new Context(
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    "");
        }

        Map<String, Object> runtimeValues() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("parent", parent);
            result.put("params", params);
            result.put("row", row);
            result.put("relation", relation);
            return result;
        }

        String ownerKey(String localOwnerKey) {
            return StringUtils.hasText(ownerPath)
                    ? ownerPath + "/" + localOwnerKey
                    : localOwnerKey;
        }

        String childOwnerPath(String segment) {
            return StringUtils.hasText(ownerPath)
                    ? ownerPath + "/" + segment
                    : segment;
        }
    }
}
