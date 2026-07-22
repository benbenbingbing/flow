package com.workflow.service;

import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.UiDataSourceExecuteRequest;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.EntityFormNode;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFormMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PublishedFormSubmissionService {

    private static final String BEFORE_SUBMIT = "BEFORE_SUBMIT";

    private final EntityDefinitionMapper entityDefinitionMapper;
    private final EntityFormMapper formMapper;
    private final UiConfigReleaseService releaseService;
    private final UiDataSourceService dataSourceService;
    private final JsonDocumentCodec codec;

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

    public Map<String, Object> applyForm(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext) {
        ResolvedEntityFormRelease resolved =
                releaseService.resolveRuntimeFormRelease(
                        formId,
                        releaseId,
                        releaseVersion);
        EntityForm form = resolved.form();
        if (form == null) {
            throw new IllegalArgumentException(
                    "已发布表单不存在: " + formId);
        }
        Map<String, Object> result = mutable(submittedData);
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
                resolved);
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
                        resolved);
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
                        resolved);
            }
        }
        return result;
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
            ResolvedEntityFormRelease resolved) {
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
                    executionContext == null
                            ? FormSubmissionExecutionContext.standalone(
                                    "FORM_" + normalizeOperation(mode))
                            : executionContext;
            String idempotencyKey =
                    safeExecutionContext.bindingIdempotencyKey(
                            form.getId(),
                            resolved.releaseId(),
                            ownerKey,
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
            context.put("bindingOwner", ownerKey);
            context.put("bindingIndex", bindingIndex);
            context.put("sourceId", sourceId);
            context.put("idempotencyKey", idempotencyKey);
            Object mappedInput = applyMapping(
                    mapping(value, "inputMapping"),
                    Map.of(
                            "data", record,
                            "context", context,
                            "input", rawInput),
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
