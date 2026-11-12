package com.workflow.process.action.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.contracts.entity.mutation.model.*;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.port.EntityCodeCatalogPort;
import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.spi.FlowActionProvider;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 连线实际命中后给当前实体的普通字段赋固定值。配置按流程版本发布，写入走统一变更管道；
 * 不提供目标实体、记录 ID 或表达式参数，避免简单赋值扩大为任意写入或脚本执行。
 */
@Component(EntityFieldAssignmentFlowActionHandler.BEAN_NAME)
@RequiredArgsConstructor
public class EntityFieldAssignmentFlowActionHandler implements FlowActionProvider {
    public static final String BEAN_NAME = "entityFieldAssignmentFlowActionHandler";
    private static final Set<EntityField.FieldType> TYPES = EnumSet.of(
            EntityField.FieldType.STRING, EntityField.FieldType.TEXT, EntityField.FieldType.INTEGER,
            EntityField.FieldType.LONG, EntityField.FieldType.DECIMAL, EntityField.FieldType.BOOLEAN,
            EntityField.FieldType.DATE, EntityField.FieldType.DATETIME,
            EntityField.FieldType.SELECT, EntityField.FieldType.RADIO);
    private static final Set<String> RESERVED = Set.of("id", "code", "name", "status", "processstatus",
            "processinstanceid", "currenttaskid", "currenttaskname", "currenttaskassignee",
            "submitterid", "submittername", "submittime", "createdby", "updatedby",
            "createtime", "updatetime", "createdat", "updatedat", "deleted");
    private final EntityCodeCatalogPort entityCatalog;
    private final EntityPublishedSnapshotService snapshots;
    private final EntityFieldValidationRuleService fieldRules;
    private final SysDictItemService dictionaries;
    private final EntityMutationPort mutations;
    private final ObjectMapper objectMapper;

    @Override public ExtensionImplementationOrigin implementationOrigin() { return ExtensionImplementationOrigin.PLATFORM; }
    @Override public Set<String> supportedTriggerTimings() { return Set.of("TRANSITION_TAKEN"); }
    @Override public Set<String> supportedExecutionModes() { return Set.of("IN_TRANSACTION"); }
    @Override public String recommendedExecutionMode() { return "IN_TRANSACTION"; }
    @Override public Map<String, Object> extraParamSchema() {
        return Map.of("type", "object", "properties", Map.of("assignments", Map.of("type", "array")),
                "required", List.of("assignments"));
    }

    /** 只返回已发布、允许赋值的字段及枚举候选；不读取实体业务记录。 */
    public List<FieldOption> fieldsForProcess(String processConfigId) {
        return fields(entityForProcess(processConfigId)).stream().filter(this::assignable)
                .map(field -> new FieldOption(field.getFieldCode(), field.getFieldName(), field.getFieldType().name(),
                        field.getFieldLength(), field.getFieldPrecision(), Boolean.TRUE.equals(field.getIsRequired()),
                        options(field)))
                .toList();
    }

    /** 保存和发布共用权威校验，返回规范化补丁供并发分支校验使用；不写业务数据。 */
    public Map<String, Object> validateConfiguration(FlowAction action) {
        requirePolicy(action.getScopeType(), action.getTriggerTiming(), action.getExecutionMode(), action.getFailurePolicy());
        try {
            JsonNode root = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(action.getParamsJson() == null ? "" : action.getParamsJson());
            return patch(entityForProcess(action.getProcessConfigId()), root);
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("实体字段赋值参数必须是合法 JSON", error);
        }
    }

    /**
     * 使用动作本次触发的幂等键提交一次多字段补丁。循环再次经过连线会获得新键；
     * 同一次执行重放沿用原键。拒绝提交后执行，确保写入异常会回滚任务及连线状态。
     */
    @Override public void execute(FlowActionContext context) {
        requirePolicy(context.getScopeType(), context.getTriggerTiming(), context.getExecutionMode(), context.getFailurePolicy());
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("实体字段赋值必须在流程事务中执行");
        }
        if (!StringUtils.hasText(context.getEntityCode()) || !StringUtils.hasText(context.getEntityDataId())
                || !StringUtils.hasText(context.getIdempotencyKey())) {
            throw new IllegalArgumentException("实体字段赋值缺少当前实体或动作执行标识");
        }
        Map<String, Object> values = patch(context.getEntityCode(), objectMapper.valueToTree(context.getExtraParams()));
        String key = context.getIdempotencyKey() + ":entity-field-assignment";
        EntityMutationContext mutationContext = EntityMutationContext.builder(EntityMutationSourceType.FLOW_ACTION,
                        "PROCESS_FIELD_ASSIGNMENT", "流程连线字段赋值")
                .sourceId(context.getActionId()).sourceRecord(context.getEntityCode(), context.getEntityDataId())
                .process(context.getProcessDefinitionId(), context.getProcessInstanceId(), context.getTaskId())
                .operator(context.getOperatorId(), context.getOperatorId())
                .trace(context.getProcessInstanceId(), key).build();
        mutations.execute(new EntityMutationCommand(key, context.getEntityCode(), context.getEntityDataId(),
                EntityMutationOperationType.UPDATE, Map.of("data", values), mutationContext));
        // 本能力只写实体，流程变量由独立接口维护，不能把实体字段名当作任意变量写入口。
        context.setExecutionResult(Map.of("entityCode", context.getEntityCode(), "recordId", context.getEntityDataId(),
                "fields", List.copyOf(values.keySet())));
        context.addExecutionTrace("ENTITY_FIELDS_ASSIGNED", "已更新当前实体字段", context.getExecutionResult());
    }

    private void requirePolicy(String scope, String timing, String mode, String failure) {
        if (!"SEQUENCE_FLOW".equals(scope) || !"TRANSITION_TAKEN".equals(timing)
                || !"IN_TRANSACTION".equals(mode) || !"ROLLBACK".equals(failure)) {
            throw new IllegalArgumentException("实体字段赋值仅支持连线经过时、事务内执行且失败回滚");
        }
    }

    private String entityForProcess(String processId) {
        String code = entityCatalog.findEntityCodeByProcessDefinitionId(processId);
        if (!StringUtils.hasText(code)) throw new IllegalArgumentException("流程尚未绑定实体，不能配置字段赋值");
        return code;
    }

    private List<EntityField> fields(String code) {
        var fields = snapshots.getLatestByEntityCode(code).getFields();
        return fields == null ? List.of() : fields;
    }

    /** 元数据误标也不能开放系统字段；关联、附件、多值和子表需要各自的业务写入契约。 */
    private boolean assignable(EntityField field) {
        return field != null && StringUtils.hasText(field.getFieldCode())
                && !RESERVED.contains(field.getFieldCode().replace("_", "").toLowerCase(Locale.ROOT))
                && !Boolean.TRUE.equals(field.getIsSystem()) && !Boolean.FALSE.equals(field.getEditable())
                && field.getFieldType() != null && TYPES.contains(field.getFieldType());
    }

    private Map<String, Object> patch(String entityCode, JsonNode root) {
        if (root == null || !root.isObject() || root.size() != 1 || !root.path("assignments").isArray()
                || root.path("assignments").isEmpty() || root.path("assignments").size() > 50) {
            throw new IllegalArgumentException("实体字段赋值需配置 1 至 50 个字段，仅允许 assignments 参数");
        }
        Map<String, EntityField> allowed = new HashMap<>();
        for (EntityField field : fields(entityCode)) if (assignable(field)) allowed.put(field.getFieldCode(), field);
        Map<String, Object> result = new LinkedHashMap<>();
        for (JsonNode row : root.path("assignments")) {
            if (!row.isObject()) throw new IllegalArgumentException("每条字段赋值必须是对象");
            row.fieldNames().forEachRemaining(key -> {
                if (!Set.of("fieldCode", "operation", "value").contains(key)) {
                    throw new IllegalArgumentException("不支持的字段赋值属性: " + key);
                }
            });
            String code = row.path("fieldCode").asText("");
            EntityField field = allowed.get(code);
            if (field == null) throw new IllegalArgumentException("字段不存在、未发布或不允许赋值: " + code);
            if (result.containsKey(code)) throw invalid(field, "同一动作不能重复配置字段");
            String operation = row.path("operation").asText("");
            Object value;
            if ("CLEAR".equals(operation)) {
                if (Boolean.TRUE.equals(field.getIsRequired())) throw invalid(field, "必填字段不能清空");
                if (row.hasNonNull("value")) throw invalid(field, "清空操作不能同时设置固定值");
                value = null;
            } else if ("SET".equals(operation) && row.hasNonNull("value")) {
                value = constant(field, row.get("value"));
                fieldRules.validateValue(field, value);
            } else {
                throw invalid(field, "请选择固定值或显式清空，固定值不能为 null");
            }
            result.put(code, value);
        }
        return result;
    }

    /** 严格检查 JSON 类型，LONG/DECIMAL 使用字符串保存，避免浏览器浮点数损失精度。 */
    private Object constant(EntityField field, JsonNode value) {
        try {
            return switch (field.getFieldType()) {
                case BOOLEAN -> { if (!value.isBoolean()) throw invalid(field, "值必须为布尔值"); yield value.booleanValue(); }
                case INTEGER -> { if (!value.isIntegralNumber() || !value.canConvertToInt()) throw invalid(field, "值必须为32位整数"); yield value.intValue(); }
                case LONG -> {
                    if (!value.isTextual() || !value.textValue().matches("-?\\d+")) throw invalid(field, "长整数必须用十进制字符串保存");
                    yield new BigInteger(value.textValue()).longValueExact();
                }
                case DECIMAL -> {
                    if (!value.isTextual() || !value.textValue().matches("-?\\d+(\\.\\d+)?")) throw invalid(field, "小数必须用十进制字符串保存");
                    BigDecimal number = new BigDecimal(value.textValue());
                    int scale = field.getFieldPrecision() == null ? 2 : field.getFieldPrecision();
                    int precision = field.getFieldLength() == null ? 18 : field.getFieldLength();
                    if (number.scale() > scale || Math.max(0, number.precision() - number.scale()) > precision - scale) {
                        throw invalid(field, "值超出字段的小数位数或精度");
                    }
                    yield number;
                }
                case DATE -> { requireText(field, value); yield LocalDate.parse(value.textValue()).toString(); }
                case DATETIME -> {
                    requireText(field, value);
                    if (!value.textValue().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) throw invalid(field, "日期时间格式应为 yyyy-MM-dd HH:mm:ss");
                    yield LocalDateTime.parse(value.textValue().replace(' ', 'T')).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }
                default -> {
                    requireText(field, value);
                    String text = value.textValue();
                    if (Boolean.TRUE.equals(field.getIsRequired()) && text.isBlank()) throw invalid(field, "必填字段不能为空");
                    if (field.getFieldLength() != null && field.getFieldLength() > 0
                            && text.codePointCount(0, text.length()) > field.getFieldLength()) throw invalid(field, "值超过字段长度");
                    if ((field.getFieldType() == EntityField.FieldType.SELECT || field.getFieldType() == EntityField.FieldType.RADIO)
                            && options(field).stream().noneMatch(option -> !option.disabled() && option.value().equals(text))) {
                        throw invalid(field, "值不是当前可用选项");
                    }
                    yield text;
                }
            };
        } catch (java.time.DateTimeException | ArithmeticException error) {
            throw invalid(field, "日期或数值超出有效范围");
        }
    }

    private void requireText(EntityField field, JsonNode value) {
        if (!value.isTextual()) throw invalid(field, "值必须是字符串");
    }

    private IllegalArgumentException invalid(EntityField field, String message) {
        return new IllegalArgumentException("字段「" + field.getFieldName() + "」: " + message);
    }

    private List<ValueOption> options(EntityField field) {
        if (field.getFieldType() != EntityField.FieldType.SELECT && field.getFieldType() != EntityField.FieldType.RADIO) return List.of();
        if (StringUtils.hasText(field.getDictType())) {
            return dictionaries.listDictionaryValues(field.getDictType()).stream()
                    .map(value -> new ValueOption(value.value(), value.label(), !value.enabled())).toList();
        }
        if (!StringUtils.hasText(field.getOptionsJson())) return List.of();
        try {
            JsonNode options = objectMapper.readTree(field.getOptionsJson());
            if (!options.isArray()) throw invalid(field, "选项配置不合法");
            List<ValueOption> result = new ArrayList<>();
            for (JsonNode option : options) result.add(new ValueOption(option.path("value").asText(),
                    option.path("label").asText(), option.path("disabled").asBoolean(false)));
            return result;
        } catch (java.io.IOException error) {
            throw invalid(field, "选项配置不合法");
        }
    }

    /** 面向设计器的发布字段投影，不暴露持久化字段对象。 */
    public record FieldOption(String fieldCode, String fieldName, String fieldType, Integer fieldLength,
                              Integer fieldPrecision, boolean required, List<ValueOption> options) { }
    public record ValueOption(String value, String label, boolean disabled) { }
}
