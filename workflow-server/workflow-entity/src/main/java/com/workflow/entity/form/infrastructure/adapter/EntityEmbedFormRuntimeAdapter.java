package com.workflow.entity.form.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedRuntimeFormPort;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataActionService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.PublishedFormConditionEvaluator;
import com.workflow.entity.form.application.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.list.application.EntityListRuntimeService;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 将已发布 Entity Form 与标准实体读取链适配为 Embed 表单端口。
 *
 * <p>适配器始终使用服务端固定的 Form Release；记录同时经过对象权限、数据范围、
 * 行级 view 能力，以及（存在时）固定 List Release 条件。它只构造稳定的中间模型，
 * 不把 componentProps、数据源、脚本、内部事件或设计态配置带到 Embed 模块。</p>
 */
@Component
public class EntityEmbedFormRuntimeAdapter implements EmbedRuntimeFormPort {

    private static final Pattern SAFE_CODE =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final int INLINE_OPTION_LIMIT = 100;
    private static final Set<String> VALIDATION_KEYS = Set.of(
            "minLength", "maxLength", "minimum", "maximum",
            "pattern", "format");

    private final UiConfigReleaseService releaseService;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityDataActionService dataActionService;
    private final EntityListRuntimeService listRuntimeService;
    private final EntityActionCapabilityService capabilityService;
    private final PublishedFormConditionEvaluator conditionEvaluator;
    private final ObjectMapper objectMapper;

    public EntityEmbedFormRuntimeAdapter(
            UiConfigReleaseService releaseService,
            EntityDefinitionMapper definitionMapper,
            EntityDataActionService dataActionService,
            EntityListRuntimeService listRuntimeService,
            EntityActionCapabilityService capabilityService,
            PublishedFormConditionEvaluator conditionEvaluator,
            ObjectMapper objectMapper) {
        this.releaseService = releaseService;
        this.definitionMapper = definitionMapper;
        this.dataActionService = dataActionService;
        this.listRuntimeService = listRuntimeService;
        this.capabilityService = capabilityService;
        this.conditionEvaluator = conditionEvaluator;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析固定发布表单，并按模式和已鉴权记录求值字段可见、可编辑及必填状态。
     */
    @Override
    public FormSnapshot resolveForm(ResolveQuery query) {
        requireResolveQuery(query);
        RuntimeForm runtime = resolve(query.target());
        List<EntityFormField> publishedFields = publishedFields(runtime.form());
        Map<String, Object> evaluationValues = new LinkedHashMap<>();
        for (EntityFormField source : publishedFields) {
            Object defaultValue = defaultValue(source.getDefaultValue());
            if (defaultValue != null) {
                evaluationValues.put(requiredCode(source.getFieldCode()), defaultValue);
            }
        }
        // 已鉴权记录和强制 Context 值优先于表单默认值，保持与最终提交语义一致。
        evaluationValues.putAll(query.trustedRecordValues() == null
                ? Map.of() : query.trustedRecordValues());
        Map<String, Object> record = immutableMap(evaluationValues);
        List<Field> fields = new ArrayList<>();
        for (EntityFormField source : publishedFields) {
            fields.add(field(runtime.form(), source, query.mode(), record));
        }
        return new FormSnapshot(
                requiredText(runtime.form().getFormName(), "发布表单缺少名称"),
                requiredText(runtime.form().getLayoutType(), "发布表单缺少布局"),
                List.copyOf(fields),
                createActions(query.mode(), query.target().entityCode()));
    }

    /**
     * CREATE 只发布内建 save，不转发任意 Form Action/Event；点击后仍由专用写适配器重查权限。
     */
    private List<Action> createActions(RuntimeMode mode, String entityCode) {
        if (mode != RuntimeMode.CREATE) {
            return List.of();
        }
        boolean enabled;
        try {
            capabilityService.requireStandardPermission(
                    entityCode, EntityPermissionAction.CREATE);
            enabled = true;
        } catch (ForbiddenException denied) {
            enabled = false;
        }
        return List.of(new Action(
                "save", "保存", true, enabled,
                enabled ? null : "FLOW_CREATE_PERMISSION_REQUIRED"));
    }

    /**
     * 读取记录并把“不存在/无权访问”统一折叠为空；基础设施或快照损坏继续向上抛出。
     */
    @Override
    public Optional<RecordSnapshot> findRecord(RecordQuery query) {
        requireRecordQuery(query);
        RuntimeForm runtime = resolve(query.target());
        try {
            if (!matchesPinnedList(query)) {
                return Optional.empty();
            }
            EntityDataDTO row = dataActionService.getDetailReadOnly(
                    query.target().entityCode(), query.recordId(),
                    emptyToNull(query.target().listKey()));
            if (row == null || !Objects.equals(query.recordId(), row.getId())) {
                throw new IllegalStateException("实体详情读取返回了越界记录");
            }
            return Optional.of(new RecordSnapshot(
                    row.getId(),
                    // 当前动态实体表尚无通用 record_version，审计版本不能冒充并发版本。
                    null,
                    publishedValues(row, runtime.form()),
                    instant(row.getCreatedAt()),
                    instant(row.getUpdatedAt())));
        } catch (ForbiddenException denied) {
            return Optional.empty();
        }
    }

    /**
     * 只分页不可变 Form Release 内的大型静态选项集。
     * 任意 service/operation/provider 绑定仍默认拒绝，绝不回读可变设计态定义。
     */
    @Override
    public OptionPage queryOptions(OptionQuery query) {
        requireOptionQuery(query);
        RuntimeForm runtime = resolve(query.target());
        EntityFormField field = requireField(runtime.form(), query.fieldCode());
        if (hasDynamicBinding(runtime.form(), field)
                || query.clientDependencies() != null
                && !query.clientDependencies().isEmpty()) {
            throw notAllowed("动态选项来源尚未进入 Embed 只读 Registry");
        }
        List<Option> options = options(field);
        if (options.size() <= INLINE_OPTION_LIMIT) {
            throw notAllowed("字段没有运行时分页选项");
        }
        String keyword = StringUtils.hasText(query.keyword())
                ? query.keyword().trim().toLowerCase(Locale.ROOT) : null;
        List<Option> matched = options.stream()
                .filter(option -> keyword == null
                        || option.label().toLowerCase(Locale.ROOT).contains(keyword))
                .toList();
        long offset = Math.multiplyExact(
                (long) query.pageNum() - 1L, query.pageSize());
        if (offset >= matched.size()) {
            return new OptionPage(List.of(), false,
                    query.pageNum(), query.pageSize());
        }
        int from = Math.toIntExact(offset);
        int to = Math.min(matched.size(), from + query.pageSize());
        return new OptionPage(
                List.copyOf(matched.subList(from, to)),
                to < matched.size(), query.pageNum(), query.pageSize());
    }

    /**
     * 普通 Form Release 尚未固定候选列表版本和 External Projection，V1 必须拒绝。
     */
    @Override
    public LookupPage queryLookups(LookupQuery query) {
        throw notAllowed("Lookup 缺少已审核且固定版本的候选列表");
    }

    private Field field(
            EntityForm form,
            EntityFormField source,
            RuntimeMode mode,
            Map<String, Object> record) {
        String code = requiredCode(source.getFieldCode());
        Map<String, Object> extension = readObject(
                source.getExtensionConfig(), "发布字段扩展配置");
        Map<String, Object> access = childMap(
                childMap(extension.get("modes")).get(
                        mode.name().toLowerCase(Locale.ROOT)));
        requireBoolean(access, "visible");
        requireBoolean(access, "editable");

        Map<String, Object> componentProps = readObject(
                source.getComponentProps(), "发布字段组件配置");
        Map<String, Object> linkage = childMap(
                componentProps.get("linkageRules"));
        boolean modeVisible = !Boolean.FALSE.equals(access.get("visible"))
                && conditionEvaluator.evaluate(
                        linkage.get("visibilityConditionConfig"),
                        text(linkage.get("visibilityRule")),
                        record, true);
        boolean required = modeVisible
                && (Integer.valueOf(1).equals(source.getIsRequired())
                || conditionEvaluator.evaluate(
                        linkage.get("requiredConditionConfig"),
                        text(linkage.get("requiredRule")),
                        record, false));
        List<Option> publishedOptions = options(source);
        boolean runtimeOptions = !hasDynamicBinding(form, source)
                && publishedOptions.size() > INLINE_OPTION_LIMIT;

        return new Field(
                code,
                firstText(source.getFieldLabel(), source.getFieldName(), code),
                requiredText(firstText(source.getFieldType(),
                        source.getComponentType()), "发布字段缺少类型"),
                required,
                Integer.valueOf(1).equals(source.getIsReadonly()),
                Integer.valueOf(1).equals(source.getIsHidden()),
                modeVisible,
                !Boolean.FALSE.equals(access.get("editable")),
                defaultValue(source.getDefaultValue()),
                validation(source.getValidationRules()),
                runtimeOptions ? List.of() : publishedOptions,
                runtimeOptions,
                List.of(),
                // 未固定 lookup 列表坐标时只保留字段值展示，不发布查询入口。
                false,
                List.of(),
                List.of(),
                normalizeSpan(source.getGridSpan()));
    }

    private RuntimeForm resolve(Target target) {
        requireTarget(target);
        ResolvedEntityFormRelease resolved =
                releaseService.resolveRuntimeFormRelease(
                        target.formId(), target.formReleaseId(),
                        target.formReleaseVersion(),
                        UiRuntimeResolutionContext.historical(null, null));
        EntityForm form = resolved == null ? null : resolved.form();
        if (form == null
                || !Objects.equals(target.formId(), form.getId())
                || !Objects.equals(target.formReleaseId(), resolved.releaseId())
                || !Objects.equals(target.formReleaseVersion(),
                        resolved.releaseVersion())
                || !resolved.pinned()) {
            throw new IllegalStateException("固定表单发布版本解析结果不一致");
        }
        EntityDefinition definition = definitionMapper
                .findByEntityCode(target.entityCode())
                .orElseThrow(() -> new IllegalStateException("Embed 目标实体不存在"));
        if (!Objects.equals(definition.getId(), form.getEntityId())) {
            throw new IllegalStateException("Embed 表单与目标实体不一致");
        }
        return new RuntimeForm(form, definition);
    }

    /**
     * 固定列表存在时，以 id 作为服务端可信条件执行同一不可变发布版本；随后详情读取
     * 再执行当前对象/数据权限，两套授权取交集，避免任一路径成为旁路。
     */
    private boolean matchesPinnedList(RecordQuery query) {
        Target target = query.target();
        boolean hasReleaseId = StringUtils.hasText(target.listReleaseId());
        boolean hasReleaseVersion = target.listReleaseVersion() != null;
        if (hasReleaseId != hasReleaseVersion
                || hasReleaseVersion && target.listReleaseVersion() < 1
                || (hasReleaseId && !StringUtils.hasText(target.listKey()))) {
            throw new IllegalStateException("Embed 列表固定坐标不完整");
        }
        if (!hasReleaseId) {
            return true;
        }
        Map<String, Object> trusted = new LinkedHashMap<>(
                query.trustedContextFilters() == null
                        ? Map.of() : query.trustedContextFilters());
        Object currentId = trusted.putIfAbsent("id", query.recordId());
        if (currentId != null
                && !Objects.equals(String.valueOf(currentId), query.recordId())) {
            return false;
        }
        Object currentOperator = trusted.putIfAbsent("id_op", "EQ");
        if (currentOperator != null
                && !"EQ".equalsIgnoreCase(String.valueOf(currentOperator))) {
            return false;
        }
        Object result = listRuntimeService.queryPinned(
                target.entityCode(), target.listKey(), target.listReleaseId(),
                target.listReleaseVersion(), 1, 2, Map.of(),
                Collections.unmodifiableMap(trusted));
        if (!(result instanceof PageResult<?> page)) {
            throw new IllegalStateException("固定列表详情校验没有返回分页结果");
        }
        List<?> records = page.getRecords() == null ? List.of() : page.getRecords();
        if (records.isEmpty()) {
            return false;
        }
        if (records.size() != 1
                || !Objects.equals(query.recordId(), recordId(records.get(0)))) {
            // Provider 忽略服务端 id 条件属于安全失败，不能选第一行继续。
            throw new IllegalStateException("固定列表详情校验返回了越界记录");
        }
        return viewAllowed(records.get(0));
    }

    private boolean viewAllowed(Object value) {
        Map<String, EntityActionCapabilityDTO> capabilities;
        if (value instanceof EntityDataDTO row) {
            capabilities = row.getActionCapabilities();
        } else {
            JsonNode node = objectMapper.valueToTree(value);
            JsonNode action = node.path("actionCapabilities").path("view");
            return action.isObject()
                    && action.path("visible").asBoolean(false)
                    && action.path("enabled").asBoolean(false);
        }
        EntityActionCapabilityDTO view = capabilities == null
                ? null : capabilities.get("view");
        return view != null && view.isVisible() && view.isEnabled();
    }

    private String recordId(Object value) {
        if (value instanceof EntityDataDTO row) {
            return row.getId();
        }
        JsonNode node = objectMapper.valueToTree(value);
        return node.path("id").isValueNode()
                ? node.path("id").asText(null) : null;
    }

    private Map<String, Object> publishedValues(
            EntityDataDTO row,
            EntityForm form) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Object> data = row.getData() == null ? Map.of() : row.getData();
        Map<String, Object> ext = row.getExtData() == null ? Map.of() : row.getExtData();
        for (EntityFormField field : publishedFields(form)) {
            String code = requiredCode(field.getFieldCode());
            if (data.containsKey(code)) {
                values.put(code, stableRecordValue(data.get(code)));
            } else if (ext.containsKey(code)) {
                values.put(code, stableRecordValue(ext.get(code)));
            } else {
                standardValue(row, code).ifPresent(value ->
                        values.put(code, stableRecordValue(value)));
            }
        }
        return Collections.unmodifiableMap(values);
    }

    /** 只开放实体表单可合理引用的标准列，流程令牌/变量等内部属性永不进入投影。 */
    private Optional<Object> standardValue(EntityDataDTO row, String code) {
        Object value = switch (code) {
            case "dataNo" -> row.getDataNo();
            case "title" -> row.getTitle();
            case "name" -> row.getName();
            case "code" -> row.getCode();
            case "status" -> row.getStatus();
            case "submitterId" -> row.getSubmitterId();
            case "submitterName" -> row.getSubmitterName();
            case "deptId" -> row.getDeptId();
            case "deptName" -> row.getDeptName();
            case "submitTime" -> row.getSubmitTime();
            case "createdAt" -> row.getCreatedAt();
            case "updatedAt" -> row.getUpdatedAt();
            case "createdBy" -> row.getCreatedBy();
            case "updatedBy" -> row.getUpdatedBy();
            default -> null;
        };
        return Optional.ofNullable(value);
    }

    /** 将 JDBC/Java 时间值固定成无实现类型泄漏的 ISO 文本；复杂对象留给外层白名单拒绝。 */
    private Object stableRecordValue(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toInstant(ZoneOffset.UTC).toString();
        }
        if (value instanceof java.time.OffsetDateTime dateTime) {
            return dateTime.toInstant().toString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof java.time.LocalDate date) {
            return date.toString();
        }
        if (value instanceof java.time.LocalTime time) {
            return time.toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().toString();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::stableRecordValue).toList();
        }
        return value;
    }

    private List<EntityFormField> publishedFields(EntityForm form) {
        if (form.getFields() == null) {
            throw new IllegalStateException("发布表单缺少字段快照");
        }
        return form.getFields();
    }

    private EntityFormField requireField(EntityForm form, String fieldCode) {
        return publishedFields(form).stream()
                .filter(field -> field != null
                        && Objects.equals(fieldCode, field.getFieldCode()))
                .findFirst()
                .orElseThrow(() -> notAllowed("字段不属于固定表单发布版本"));
    }

    private boolean hasDynamicBinding(EntityForm form, EntityFormField field) {
        if (field.getDataSourceBindings() != null
                && !field.getDataSourceBindings().isEmpty()) {
            return true;
        }
        if (form == null || !StringUtils.hasText(form.getDataSourceBindingsDocument())) {
            return false;
        }
        return !readObject(form.getDataSourceBindingsDocument(),
                "发布表单数据源绑定").isEmpty();
    }

    private List<Option> options(EntityFormField field) {
        if (!StringUtils.hasText(field.getOptionsJson())) {
            return List.of();
        }
        JsonNode array;
        try {
            array = objectMapper.readTree(field.getOptionsJson());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("发布字段选项快照无效", error);
        }
        if (array == null || !array.isArray()) {
            throw new IllegalStateException("发布字段选项必须为数组");
        }
        List<Option> result = new ArrayList<>();
        for (JsonNode item : array) {
            if (!item.isObject() || !item.has("value")
                    || !item.get("value").isValueNode()
                    || item.get("value").isContainerNode()) {
                throw new IllegalStateException("发布字段选项结构无效");
            }
            Object value = objectMapper.convertValue(item.get("value"), Object.class);
            String label = item.path("label").isTextual()
                    ? item.path("label").asText() : String.valueOf(value);
            if (!StringUtils.hasText(label) || label.length() > 200) {
                throw new IllegalStateException("发布字段选项标签无效");
            }
            if (item.has("disabled") && !item.get("disabled").isBoolean()) {
                throw new IllegalStateException("发布字段选项禁用状态无效");
            }
            result.add(new Option(label, value,
                    item.path("disabled").asBoolean(false)));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> validation(String document) {
        Map<String, Object> source = readObject(document, "发布字段校验规则");
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String externalKey = switch (key) {
                case "min" -> "minimum";
                case "max" -> "maximum";
                default -> key;
            };
            if (VALIDATION_KEYS.contains(externalKey)) {
                if (!scalar(value)) {
                    throw new IllegalStateException("发布字段校验规则类型无效");
                }
                result.put(externalKey, value);
            }
        });
        return Collections.unmodifiableMap(result);
    }

    private Object defaultValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node == null ? null : objectMapper.convertValue(node, Object.class);
        } catch (JsonProcessingException ignored) {
            // 历史表单允许直接存纯文本默认值，只有 JSON 解析失败时才按文本处理。
            return value;
        }
    }

    private Map<String, Object> readObject(String document, String label) {
        if (!StringUtils.hasText(document)) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(document);
            if (node == null || !node.isObject()) {
                throw new IllegalStateException(label + "必须为对象");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(
                    entry.getKey(), objectMapper.convertValue(
                            entry.getValue(), Object.class)));
            return Collections.unmodifiableMap(result);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(label + "无效", error);
        }
    }

    private void requireResolveQuery(ResolveQuery query) {
        if (query == null || query.mode() == null) {
            throw new IllegalArgumentException("Embed 表单解析参数不完整");
        }
        requireTarget(query.target());
        if ((query.mode() == RuntimeMode.CREATE
                && StringUtils.hasText(query.recordId()))
                || (query.mode() != RuntimeMode.CREATE
                && !StringUtils.hasText(query.recordId()))) {
            throw new IllegalArgumentException("Embed 表单记录坐标与模式不一致");
        }
    }

    private void requireRecordQuery(RecordQuery query) {
        if (query == null || !StringUtils.hasText(query.recordId())) {
            throw new IllegalArgumentException("Embed 记录查询参数不完整");
        }
        requireTarget(query.target());
    }

    private void requireOptionQuery(OptionQuery query) {
        if (query == null || query.mode() == null
                || !StringUtils.hasText(query.fieldCode())
                || query.pageNum() < 1 || query.pageSize() < 1
                || query.pageSize() > 50) {
            throw new IllegalArgumentException("Embed 选项查询参数不完整");
        }
        requireTarget(query.target());
    }

    private void requireTarget(Target target) {
        if (target == null
                || !safeCode(target.entityCode())
                || !StringUtils.hasText(target.formId())
                || !StringUtils.hasText(target.formReleaseId())
                || target.formReleaseVersion() < 1) {
            throw new IllegalArgumentException("Embed 表单目标坐标不完整");
        }
    }

    private String requiredCode(String value) {
        if (!safeCode(value)) {
            throw new IllegalStateException("发布字段编码无效");
        }
        return value;
    }

    private static int normalizeSpan(Integer value) {
        if (value == null || value == 0) {
            return 24;
        }
        if (value < 1 || value > 24) {
            throw new IllegalStateException("发布字段栅格宽度无效");
        }
        return value;
    }

    private static void requireBoolean(Map<String, Object> source, String key) {
        if (source.containsKey(key) && source.get(key) != null
                && !(source.get(key) instanceof Boolean)) {
            throw new IllegalStateException("发布字段模式权限无效");
        }
    }

    private static Map<String, Object> childMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        return value == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private static boolean safeCode(String value) {
        return value != null && SAFE_CODE.matcher(value).matches();
    }

    private static boolean scalar(Object value) {
        return value == null || value instanceof String
                || value instanceof Number || value instanceof Boolean;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static OperationNotAllowedException notAllowed(String message) {
        return new OperationNotAllowedException(message);
    }

    private record RuntimeForm(
            EntityForm form,
            EntityDefinition definition) {
    }
}
