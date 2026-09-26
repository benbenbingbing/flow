package com.workflow.entity.form.application.validation;

import com.workflow.entity.form.application.CrossFieldValueComparator;
import com.workflow.entity.form.application.FormCrossFieldRulePolicy;
import com.workflow.entity.form.application.PublishedFormConditionEvaluator;
import com.workflow.entity.form.application.model.PublishedFormRecordView;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.entity.form.application.error.FormCrossFieldValidationException;
import com.workflow.entity.form.application.error.FormCrossFieldValidationException.FieldError;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 已发布表单跨字段终检：仅对当前可见、可编辑字段执行，始终按最终数据重新计算状态。 */
@Component
@RequiredArgsConstructor
public class PublishedFormCrossFieldValidator {
    private final EntityDataDynamicService dataService;
    private final ObjectMapper objectMapper;
    private final JsonDocumentCodec codec;
    private final PublishedFormConditionEvaluator conditionEvaluator;

    /**
     * 没有跨字段配置时跳过新链路，保持历史表单和既有必填校验行为。
     *
     * @param form 表单，供本方法判断是否具有规则集合时使用
     * @return 规则集合条件成立时为 true，否则为 false
     */
    public boolean hasRules(EntityForm form) {
        if (form == null || form.getFields() == null) return false;
        return form.getFields().stream().anyMatch(field -> {
            Object value = crossFieldConfig(field);
            return value != null && !FormCrossFieldRulePolicy.parse(value, field.getFieldType()).isEmpty();
        });
    }

    /**
     * 一次读取原记录并构造提交前检查视图；实际写入前还需在持有记录锁时重检。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param patch 补丁，作为 {@code PublishedFormRecordView.merge} 的输入影响后续处理
     * @return {@code final}记录键值结果，供调用方继续处理
     */
    public Map<String, Object> finalRecord(String entityCode, String recordId, Map<String, Object> patch) {
        Map<String, Object> existing = StringUtils.hasText(recordId)
                ? objectMapper.convertValue(dataService.findById(entityCode, recordId), new TypeReference<>() {})
                : Map.of();
        return PublishedFormRecordView.merge(existing, patch);
    }

    /**
     * 校验可信发布表单与最终记录。mode 由服务端入口确定，客户端字段状态不能覆盖它。
     *
     * @param form 表单，作为 {@code FormCrossFieldRulePolicy.boundFields} 的输入影响后续处理
     * @param mode 模式标识，决定后续记录采用的处理分支
     * @param record 记录，供本方法校验记录时使用
     * @throws FormCrossFieldValidationException 比较失败或非空值无法按声明类型解释
     */
    public void validateRecord(EntityForm form, String mode, Map<String, Object> record) {
        if (!hasRules(form)) return;
        Map<String, String> types = new LinkedHashMap<>();
        Map<String, EntityFormField> fields = new LinkedHashMap<>();
        FormCrossFieldRulePolicy.boundFields(form).forEach(field -> {
            if (field.getFieldType() != null) types.put(field.getFieldCode(), field.getFieldType());
            fields.put(field.getFieldCode(), field);
        });
        List<FieldError> errors = new ArrayList<>();
        for (EntityFormField field : form.getFields()) {
            Object config = crossFieldConfig(field);
            FormCrossFieldRulePolicy.validateReferences(config, field.getFieldCode(), types);
            if (config == null || !editableAndVisible(form, field, mode, record)) continue;
            for (FormCrossFieldRulePolicy.Rule rule : FormCrossFieldRulePolicy.parse(config, types.get(field.getFieldCode()))) {
                Object left = record.get(field.getFieldCode());
                Object right = record.get(rule.targetFieldCode());
                if (blank(left) || blank(right)) continue;
                EntityFormField target = fields.get(rule.targetFieldCode());
                String message = null;
                try {
                    int compared = CrossFieldValueComparator.compare(left, right, types.get(field.getFieldCode()), types.get(rule.targetFieldCode()));
                    if (!FormCrossFieldRulePolicy.passes(rule.operator(), compared)) {
                        message = StringUtils.hasText(rule.message()) ? rule.message().trim()
                                : label(field) + "必须" + FormCrossFieldRulePolicy.operatorLabel(rule.operator()) + label(target);
                    }
                } catch (IllegalArgumentException exception) {
                    message = label(field) + "与" + label(target) + "无法比较：" + exception.getMessage();
                }
                if (message != null) {
                    errors.add(new FieldError(field.getFieldCode(), rule.id(), rule.targetFieldCode(), message));
                    break;
                }
            }
        }
        if (!errors.isEmpty()) throw new FormCrossFieldValidationException(errors);
    }

    /**
     * 当前字段按默认状态、模式权限、条件联动及祖先容器计算；布局折叠不改变可编辑性。
     *
     * @param form 表单，供本方法处理可编辑与可见时使用
     * @param field 字段，作为 {@code codec.readObject} 的输入影响后续处理
     * @param mode 模式标识，决定后续可编辑与可见采用的处理分支
     * @param record 记录，供本方法处理可编辑与可见时使用
     * @return 可编辑与可见条件成立时为 true，否则为 false
     */
    private boolean editableAndVisible(EntityForm form, EntityFormField field, String mode, Map<String, Object> record) {
        if ("view".equals(mode) || flag(field.getIsHidden()) || flag(field.getIsReadonly())) return false;
        Map<?, ?> extension = codec.readObject(field.getExtensionConfig(), "已发布字段扩展配置");
        Map<?, ?> access = map(map(extension.get("modes")).get(mode));
        if (Boolean.FALSE.equals(access.get("visible")) || Boolean.FALSE.equals(access.get("editable"))) return false;
        Map<?, ?> props = codec.readObject(field.getComponentProps(), "已发布字段组件配置");
        Map<?, ?> linkage = map(props.get("linkageRules"));
        if (!conditionEvaluator.evaluate(linkage.get("visibilityConditionConfig"), text(linkage.get("visibilityRule")), record, true)
                || conditionEvaluator.evaluate(linkage.get("disabledConditionConfig"), text(linkage.get("disabledRule")), record, false)) return false;
        Map<String, EntityFormNode> nodes = new LinkedHashMap<>();
        if (form.getNodes() != null) form.getNodes().forEach(node -> nodes.put(node.getId(), node));
        EntityFormNode node = nodes.get(field.getId());
        Set<String> visited = new HashSet<>();
        while (node != null) {
            if (!visited.add(node.getId())) throw new IllegalArgumentException("表单节点存在循环引用");
            Map<?, ?> nodeProps = codec.readObject(node.getPropsDocument(), "已发布节点属性");
            String modeAccess = text(map(nodeProps.get("modeAccess")).get(mode));
            if (flag(nodeProps.get("hidden")) || flag(nodeProps.get("readonly")) || flag(nodeProps.get("disabled"))
                    || "HIDDEN".equalsIgnoreCase(modeAccess) || "READONLY".equalsIgnoreCase(modeAccess)) return false;
            String permission = text(nodeProps.get("permissionCode"));
            if (StringUtils.hasText(permission) && !PermissionUtil.hasPermission(permission)) return false;
            node = nodes.get(node.getParentId());
        }
        return true;
    }

    /**
     * 处理跨字段配置，并将结果传给后续步骤。
     *
     * @param field 字段，作为 {@code codec.read} 的输入影响后续处理
     * @return 处理后的跨字段配置结果，供调用方继续处理
     */
    private Object crossFieldConfig(EntityFormField field) {
        if (field == null || !StringUtils.hasText(field.getValidationRules())) return null;
        // 历史发布快照允许数组形式的单字段校验；其中没有跨字段配置，不改变其旧行为。
        Object validation = codec.read(field.getValidationRules(), "已发布字段校验规则");
        return validation instanceof Map<?, ?> map ? map.get("crossField") : null;
    }
    /**
     * 整理映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     */
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    /**
     * 标记已发布表单跨字段；后续读取或执行将使用更新后的状态。
     *
     * @param value 待标记已发布表单跨字段的原始输入，结果供调用方继续使用
     * @return 已发布表单跨字段条件成立时为 true，否则为 false
     */
    private static boolean flag(Object value) { return Boolean.TRUE.equals(value) || Integer.valueOf(1).equals(value) || "1".equals(value); }
    /**
     * 判断空白条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理空白的原始输入，结果供调用方继续使用
     * @return 空白条件成立时为 true，否则为 false
     */
    private static boolean blank(Object value) { return value == null || "".equals(value); }
    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(Object value) { return value == null ? "" : value.toString(); }
    /**
     * 生成标签文本，供后续匹配或展示。
     *
     * @param field 字段，供本方法处理标签时使用
     * @return 处理后的标签文本，供调用方比较或展示
     */
    private static String label(EntityFormField field) {
        return StringUtils.hasText(field.getFieldLabel()) ? field.getFieldLabel()
                : StringUtils.hasText(field.getFieldName()) ? field.getFieldName() : field.getFieldCode();
    }
}
