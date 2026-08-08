package com.workflow.entity.form.application;

import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiDataSourceDefinitionValidator;
import com.workflow.entity.ui.application.UiDataSourceService;

import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 已发布表单提交处理服务，在提交前应用表单默认值与 BEFORE_SUBMIT 数据源绑定。
 *
 * <p>解析实体默认表单或指定发布版本，执行字段默认值、计算和前置数据源绑定，
 * 确保提交数据符合发布版本约束，并通过执行上下文保证绑定幂等。</p>
 */
@Service
@RequiredArgsConstructor
public class PublishedFormSubmissionService {

    private static final String BEFORE_SUBMIT = "BEFORE_SUBMIT";
    private final EntityDefinitionMapper entityDefinitionMapper;
    private final EntityFormMapper formMapper;
    private final EntityRelationMapper entityRelationMapper;
    private final UiConfigReleaseService releaseService;
    private final UiDataSourceService dataSourceService;
    private final JsonDocumentCodec codec;
    private final UiDataSourceDefinitionValidator schemaValidator;

    /**
     * 应用实体默认表单的默认值与前置数据源（使用独立执行上下文）。
     *
     * @param entityCode    实体编码
     * @param recordId      数据记录ID，新增时为 null
     * @param mode          提交模式，如 create、edit
     * @param submittedData 提交的表单数据
     * @return 处理后的表单数据
     */
    public Map<String, Object> applyDefaultForm(
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData) {
        return applyDefaultForm(
                entityCode,
                recordId,
                mode,
                submittedData,
                FormSubmissionExecutionContext.standalone(
                        "ENTITY_" + normalizeOperation(mode)));
    }

    /**
     * 应用实体默认表单的默认值与前置数据源（使用指定执行上下文）。
     *
     * @param entityCode       实体编码
     * @param recordId         数据记录ID，新增时为 null
     * @param mode             提交模式
     * @param submittedData    提交的表单数据
     * @param executionContext 提交执行上下文，用于幂等键生成
     * @return 处理后的表单数据
     * @throws IllegalArgumentException 实体不存在时抛出
     */
    public Map<String, Object> applyDefaultForm(
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext) {
        EntityDefinition definition =
                entityDefinitionMapper.findByEntityCode(entityCode)
                        .orElse(null);
        if (definition == null) {
            throw new IllegalArgumentException(
                    "实体不存在: " + entityCode);
        }
        EntityForm form =
                formMapper.selectDefaultByEntityId(definition.getId());
        return form == null
                ? mutable(submittedData)
                : applyForm(
                        form.getId(),
                        entityCode,
                        recordId,
                        mode,
                        submittedData,
                        executionContext);
    }

    /**
     * 应用指定表单的默认值与前置数据源（使用独立执行上下文，取当前激活发布版本）。
     *
     * @param formId       表单ID
     * @param entityCode   实体编码
     * @param recordId     数据记录ID
     * @param mode         提交模式
     * @param submittedData 提交的表单数据
     * @return 处理后的表单数据
     */
    public Map<String, Object> applyForm(
            String formId,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData) {
        return applyForm(
                formId,
                entityCode,
                recordId,
                mode,
                submittedData,
                FormSubmissionExecutionContext.standalone(
                        "FORM_" + normalizeOperation(mode)));
    }

    /**
     * 应用指定表单的默认值与前置数据源（使用指定执行上下文，取当前激活发布版本）。
     *
     * @param formId           表单ID
     * @param entityCode       实体编码
     * @param recordId         数据记录ID
     * @param mode             提交模式
     * @param submittedData    提交的表单数据
     * @param executionContext 提交执行上下文
     * @return 处理后的表单数据
     */
    public Map<String, Object> applyForm(
            String formId,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext) {
        return applyForm(
                formId,
                null,
                null,
                entityCode,
                recordId,
                mode,
                submittedData,
                executionContext);
    }

    /**
     * 应用指定发布版本表单的默认值与前置数据源（支持版本号一致性校验）。
     *
     * @param formId           表单ID
     * @param releaseId       发布记录ID，为空取当前激活版本
     * @param releaseVersion  发布版本号，为空跳过校验
     * @param entityCode      实体编码
     * @param recordId        数据记录ID
     * @param mode            提交模式
     * @param submittedData   提交的表单数据
     * @param executionContext 提交执行上下文
     * @return 处理后的表单数据
     * @throws IllegalArgumentException 表单或发布版本不存在时抛出
     */
    public Map<String, Object> applyForm(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext) {
        return applyForm(
                formId,
                releaseId,
                releaseVersion,
                entityCode,
                recordId,
                mode,
                submittedData,
                executionContext,
                null);
    }

    /**
     * 按服务端可信流程上下文应用指定发布版本表单的提交处理。
     */
    public Map<String, Object> applyForm(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext,
            UiRuntimeResolutionContext resolutionContext) {
        ResolvedEntityFormRelease resolved = resolutionContext == null
                ? releaseService.resolveRuntimeFormRelease(
                        formId,
                        releaseId,
                        releaseVersion)
                : releaseService.resolveRuntimeFormRelease(
                        formId,
                        releaseId,
                        releaseVersion,
                        resolutionContext);
        return applyResolvedForm(
                resolved,
                entityCode,
                recordId,
                mode,
                submittedData,
                executionContext,
                resolutionContext,
                PublishedSubFormSubmissionProcessor.Context.root(),
                0);
    }

    private Map<String, Object> applyResolvedForm(
            ResolvedEntityFormRelease resolved,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext,
            UiRuntimeResolutionContext resolutionContext,
            PublishedSubFormSubmissionProcessor.Context nestedContext,
            int depth) {
        EntityForm form = resolved.form();
        if (form == null) {
            throw new IllegalArgumentException(
                    "已发布表单不存在");
        }
        Map<String, Object> result =
                filterUndeclaredRelationData(
                        form,
                        submittedData);
        Map<String, Object> formBindings =
                StringUtils.hasText(
                        form.getDataSourceBindingsDocument())
                        ? codec.readObject(
                        form.getDataSourceBindingsDocument(),
                        "已发布表单级数据源绑定")
                        : Map.of();
        executeBindings(
                formBindings,
                form,
                "form:" + form.getId(),
                entityCode,
                recordId,
                mode,
                result,
                executionContext,
                resolved,
                nestedContext);
        List<EntityFormNode> nodes =
                form.getNodes() == null
                        ? List.of() : form.getNodes();
        if (!nodes.isEmpty()) {
            for (EntityFormNode node : nodes) {
                Map<String, Object> bindings =
                        StringUtils.hasText(
                                node.getDataSourceBindingsDocument())
                                ? codec.readObject(
                                        node.getDataSourceBindingsDocument(),
                                        "已发布表单节点数据源绑定")
                                : Map.of();
                executeBindings(
                        bindings,
                        form,
                        nodeOwnerKey(node),
                        entityCode,
                        recordId,
                        mode,
                        result,
                        executionContext,
                        resolved,
                        nestedContext);
                subFormSubmissionProcessor().apply(
                        node,
                        form,
                        entityCode,
                        recordId,
                        mode,
                        result,
                        executionContext,
                        resolutionContext,
                        nestedContext,
                        depth,
                        this::applyResolvedForm);
            }
        } else {
            for (EntityFormField field :
                    form.getFields() == null
                            ? List.<EntityFormField>of()
                            : form.getFields()) {
                executeBindings(
                        field.getDataSourceBindings(),
                        form,
                        fieldOwnerKey(field),
                        entityCode,
                        recordId,
                        mode,
                        result,
                        executionContext,
                        resolved,
                        nestedContext);
            }
        }
        return result;
    }

    private Map<String, Object> filterUndeclaredRelationData(
            EntityForm form,
            Map<String, Object> submittedData) {
        Map<String, Object> result = mutable(submittedData);
        if (form == null
                || !StringUtils.hasText(form.getEntityId())) {
            return result;
        }
        List<EntityRelation> relations =
                entityRelationMapper.selectByParentEntityId(
                        form.getEntityId());
        if (relations == null || relations.isEmpty()) {
            return result;
        }
        Set<String> declaredRelationFields =
                declaredRelationFields(
                        form,
                        relations);
        for (EntityRelation relation : relations) {
            String fieldCode =
                    relation.getParentFieldCode();
            if (StringUtils.hasText(fieldCode)
                    && !declaredRelationFields.contains(
                            fieldCode)) {
                result.remove(fieldCode);
            }
        }
        return result;
    }

    private Set<String> declaredRelationFields(
            EntityForm form,
            List<EntityRelation> relations) {
        Set<String> declared = new LinkedHashSet<>();
        for (EntityFormField field :
                form.getFields() == null
                        ? List.<EntityFormField>of()
                        : form.getFields()) {
            if (StringUtils.hasText(field.getFieldCode())) {
                declared.add(field.getFieldCode());
            }
        }
        for (EntityFormNode node :
                form.getNodes() == null
                        ? List.<EntityFormNode>of()
                        : form.getNodes()) {
            Map<String, Object> props =
                    nodeProperties(node);
            Object fieldCodeValue =
                    props.get("fieldCode");
            String fieldCode =
                    fieldCodeValue == null
                            ? null
                            : String.valueOf(fieldCodeValue);
            if (StringUtils.hasText(fieldCode)) {
                declared.add(fieldCode);
            }
            String bindingRef = node.getBindingRef();
            if (!StringUtils.hasText(bindingRef)) {
                continue;
            }
            for (EntityRelation relation : relations) {
                if (bindingRef.equals(
                                relation.getRelationCode())
                        || bindingRef.equals(
                                relation.getParentFieldCode())) {
                    declared.add(
                            relation.getParentFieldCode());
                }
            }
        }
        return declared;
    }

    private Map<String, Object> nodeProperties(
            EntityFormNode node) {
        if (node == null) {
            return Map.of();
        }
        String document =
                StringUtils.hasText(node.getPropsDocument())
                        ? node.getPropsDocument()
                        : node.getLegacyPropsDocument();
        return StringUtils.hasText(document)
                ? codec.readObject(
                        document,
                        "已发布表单节点属性")
                : Map.of();
    }

    private void executeBindings(
            Map<String, Object> bindings,
            EntityForm form,
            String ownerKey,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> record,
            FormSubmissionExecutionContext executionContext,
            ResolvedEntityFormRelease resolved,
            PublishedSubFormSubmissionProcessor.Context nestedContext) {
        if (bindings == null) {
            return;
        }
        Object configured = bindings.get(BEFORE_SUBMIT);
        if (configured == null) {
            return;
        }
        List<?> values = configured instanceof List<?> list
                ? list : List.of(configured);
        int bindingIndex = 0;
        for (Object value : values) {
            String sourceId = sourceId(value);
            if (!StringUtils.hasText(sourceId)) {
                throw new IllegalArgumentException(
                        "BEFORE_SUBMIT 数据源绑定缺少 sourceId");
            }
            FormSubmissionExecutionContext safeExecutionContext =
                    safeExecutionContext(
                            executionContext,
                            mode);
            String effectiveOwnerKey =
                    nestedContext.ownerKey(ownerKey);
            String idempotencyKey =
                    safeExecutionContext.bindingIdempotencyKey(
                            form.getId(),
                            resolved.releaseId(),
                            effectiveOwnerKey,
                            sourceId,
                            bindingIndex);
            UiDataSourceExecuteRequest request =
                    new UiDataSourceExecuteRequest();
            request.setUsage(BEFORE_SUBMIT);
            request.setConfigType("FORM");
            request.setConfigId(form.getId());
            request.setReleaseId(resolved.releaseId());
            request.setReleaseVersion(
                    resolved.releaseVersion());
            request.setServerPinnedRelease(
                    resolved.pinned());
            request.setEntityCode(entityCode);
            request.setServerIdempotencyKey(idempotencyKey);
            Map<String, Object> rawInput =
                    new LinkedHashMap<>();
            rawInput.put(
                    "recordId",
                    recordId == null ? "" : recordId);
            rawInput.put(
                    "data",
                    new LinkedHashMap<>(record));
            rawInput.put(
                    "params",
                    nestedContext.params());
            rawInput.put(
                    "businessTraceKey",
                    safeExecutionContext.businessTraceKey());
            rawInput.put(
                    "idempotencyKey",
                    idempotencyKey);
            Map<String, Object> context =
                    safeExecutionContext.runtimeContext();
            context.put(
                    "mode",
                    mode == null ? "edit" : mode);
            context.put("formId", form.getId());
            context.put("entityId", form.getEntityId());
            context.put(
                    "bindingOwner",
                    effectiveOwnerKey);
            context.put("bindingIndex", bindingIndex);
            context.put("sourceId", sourceId);
            context.put("idempotencyKey", idempotencyKey);
            context.putAll(
                    nestedContext.runtimeValues());
            Map<String, Object> mappingSource =
                    new LinkedHashMap<>();
            mappingSource.put("data", record);
            mappingSource.put("context", context);
            mappingSource.put("input", rawInput);
            mappingSource.put(
                    "parent",
                    nestedContext.parent());
            mappingSource.put(
                    "params",
                    nestedContext.params());
            mappingSource.put(
                    "row",
                    nestedContext.row());
            mappingSource.put(
                    "relation",
                    nestedContext.relation());
            Object mappedInput = applyMapping(
                    mapping(value, "inputMapping"),
                    mappingSource,
                    rawInput);
            if (!(mappedInput instanceof Map<?, ?> inputMap)) {
                throw new IllegalArgumentException(
                        "BEFORE_SUBMIT 输入映射结果必须为对象");
            }
            Map<String, Object> trustedInput =
                    stringMap(inputMap);
            trustedInput.put(
                    "businessTraceKey",
                    safeExecutionContext.businessTraceKey());
            trustedInput.put(
                    "idempotencyKey",
                    idempotencyKey);
            request.setInput(trustedInput);
            request.setContext(context);
            Object response = dataSourceService.execute(
                    sourceId, request);
            response = applyMapping(
                    mapping(value, "outputMapping"),
                    Map.of(
                            "data",
                            response == null ? Map.of() : response,
                            "response",
                            response == null ? Map.of() : response),
                    response);
            if (response instanceof Map<?, ?> map) {
                map.forEach((key, child) ->
                        record.put(String.valueOf(key), child));
            }
            bindingIndex++;
        }
    }

    private FormSubmissionExecutionContext safeExecutionContext(
            FormSubmissionExecutionContext executionContext,
            String mode) {
        return executionContext == null
                ? FormSubmissionExecutionContext.standalone(
                        "FORM_" + normalizeOperation(mode))
                : executionContext;
    }

    private String nodeOwnerKey(EntityFormNode node) {
        if (StringUtils.hasText(node.getId())) {
            return "node:" + node.getId();
        }
        return "node:" + String.valueOf(node.getNodeKey());
    }

    private String fieldOwnerKey(EntityFormField field) {
        if (StringUtils.hasText(field.getId())) {
            return "field:" + field.getId();
        }
        return "field:" + String.valueOf(field.getFieldCode());
    }

    private static String normalizeOperation(String mode) {
        return StringUtils.hasText(mode)
                ? mode.trim().toUpperCase()
                : "EDIT";
    }

    private PublishedSubFormSubmissionProcessor
            subFormSubmissionProcessor() {
        return new PublishedSubFormSubmissionProcessor(
                entityDefinitionMapper,
                releaseService,
                schemaValidator,
                codec);
    }

    private String sourceId(Object value) {
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Map<?, ?> map) {
            Object sourceId = map.get("sourceId");
            if (sourceId == null) {
                sourceId = map.get("id");
            }
            return sourceId == null
                    ? null : String.valueOf(sourceId);
        }
        return null;
    }

    private Map<String, Object> mapping(
            Object binding,
            String key) {
        if (!(binding instanceof Map<?, ?> map)
                || !(map.get(key) instanceof Map<?, ?> value)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((childKey, childValue) ->
                result.put(String.valueOf(childKey), childValue));
        return result;
    }

    private Map<String, Object> stringMap(
            Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    private Object applyMapping(
            Map<String, Object> mapping,
            Map<String, Object> source,
            Object fallback) {
        if (mapping == null || mapping.isEmpty()) {
            return fallback;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        mapping.forEach((targetPath, selector) -> {
            Object value;
            if (selector instanceof Map<?, ?> literal
                    && literal.containsKey("literal")) {
                value = literal.get("literal");
            } else {
                value = resolvePath(
                        source,
                        selector == null
                                ? "" : String.valueOf(selector));
            }
            setPath(result, targetPath, value);
        });
        return result;
    }

    private Object resolvePath(
            Map<String, Object> source,
            String path) {
        Object current = source;
        for (String part : path.split("\\.")) {
            if (part.isBlank()) {
                continue;
            }
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private void setPath(
            Map<String, Object> target,
            String path,
            Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int index = 0; index < parts.length - 1; index++) {
            if (parts[index].isBlank()) {
                continue;
            }
            Object child = current.get(parts[index]);
            if (!(child instanceof Map<?, ?>)) {
                child = new LinkedHashMap<String, Object>();
                current.put(parts[index], child);
            }
            current = (Map<String, Object>) child;
        }
        if (parts.length > 0 && !parts[parts.length - 1].isBlank()) {
            current.put(parts[parts.length - 1], value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutable(
            Map<String, Object> submittedData) {
        Map<String, Object> source =
                submittedData == null
                        ? Map.of() : submittedData;
        Object nested = source.get("data");
        if (nested instanceof Map<?, ?> nestedMap) {
            Map<String, Object> result =
                    new LinkedHashMap<>(
                            (Map<String, Object>) nestedMap);
            source.forEach((key, value) -> {
                if (!"data".equals(key)) {
                    result.putIfAbsent(key, value);
                }
            });
            return result;
        }
        return new LinkedHashMap<>(source);
    }

}
