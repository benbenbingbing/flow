package com.workflow.entity.form.application;

import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.port.entity.context.ui.UiRuntimeResolutionContext;
import com.workflow.port.system.CurrentActorPort;
import com.workflow.port.system.SysOrganizationPort;
import com.workflow.port.system.SysUserPort;
import com.workflow.port.system.model.identity.IdentityDirectoryModels.Organization;
import com.workflow.port.system.model.identity.IdentityUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 新增表单读取和提交共用的上下文默认值，参数键由发布配置选定。 */
@Component
public class FormContextDefaults {
    private static final Pattern REFERENCE = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*+\"|'(?:\\\\.|[^'\\\\])*+'|\\$\\{ctx\\.([A-Za-z][\\w.]*)}");
    private static final String USER = "currentUser.";
    private static final String DATE = "currentDate";
    private static final String TIME = "currentTime";
    private static final String MODE = "form.currentMode";
    private static final String STARTER = "currentProcessInstance.starterUserId";
    private static final String STARTER_NAME = "currentProcessInstance.starterName";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private static final Set<String> CURRENT_USER_KEYS = Set.of(
            USER + "userId", USER + "nickname", USER + "departmentId", USER + "departmentName",
            USER + "organizationId", USER + "organizationName");
    private static final Set<String> TASK_KEYS = Set.of(
            "currentProcessInstance.id", STARTER, STARTER_NAME,
            "currentTask.id", "currentTask.nodeKey", "currentTask.nodeName", "currentTask.assigneeUserId");
    private static final Set<String> GENERAL_KEYS = Set.of(DATE, TIME, MODE, "processDefinition.key",
            "processDefinition.name", "currentProcessNode.key", "currentProcessNode.code", "currentProcessNode.name");

    private final CurrentActorPort currentActor;
    private final SysUserPort users;
    private final SysOrganizationPort organizations;
    private final JsonDocumentCodec codec;
    private final Clock clock;

    @Autowired
    public FormContextDefaults(CurrentActorPort currentActor, SysUserPort users, SysOrganizationPort organizations,
            JsonDocumentCodec codec, @Value("${workflow.form.context-zone:Asia/Shanghai}") String contextZone) {
        this(currentActor, users, organizations, codec, Clock.system(ZoneId.of(contextZone)));
    }

    FormContextDefaults(CurrentActorPort currentActor, SysUserPort users, SysOrganizationPort organizations,
            JsonDocumentCodec codec, Clock clock) {
        this.currentActor = currentActor;
        this.users = users;
        this.organizations = organizations;
        this.codec = codec;
        this.clock = clock;
    }

    /** 新增默认值校验参数是否属于创建入口。目标字段的值由常规数据校验处理。 */
    public static void validate(String source) {
        if (!hasReference(source)) return;
        for (String key : compile(source).contextKeys()) {
            if (TASK_KEYS.contains(key)) throw new IllegalArgumentException("新增时无法读取待办参数 " + key);
        }
    }

    public static boolean hasReference(String source) {
        return source != null && REFERENCE.matcher(source).results().anyMatch(match -> match.group(1) != null);
    }

    public static boolean known(String key) {
        return key != null && (CURRENT_USER_KEYS.contains(key) || GENERAL_KEYS.contains(key) || TASK_KEYS.contains(key));
    }

    public static boolean taskOnly(String key) { return TASK_KEYS.contains(key); }

    public boolean hasRules(EntityForm form) {
        return mentionsContext(form.getDataSourceBindingsDocument()) || mentionsContext(form.getViewConfig())
                || form.getViewCompositions() != null && form.getViewCompositions().stream().anyMatch(composition ->
                        com.workflow.entity.ui.application.PageParameterPolicy.map(composition.get("config")).get("parameterMappings") instanceof List<?> mappings
                                && mappings.stream().anyMatch(mapping -> mapping instanceof Map<?, ?> row && "CONTEXT".equals(row.get("sourceType"))))
                || form.getNodes() != null && form.getNodes().stream()
                .anyMatch(node -> mentionsContext(node.getPropsDocument()) || mentionsContext(node.getDataSourceBindingsDocument()));
    }

    private static boolean mentionsContext(String document) {
        return document != null && (document.contains("ctx.") || document.contains("\"CONTEXT\""));
    }

    public static FormContextFormula compile(String source) {
        FormContextFormula formula = FormContextFormula.compile(source);
        for (String key : formula.contextKeys()) {
            if (!known(key)) throw new IllegalArgumentException("未知上下文参数 " + key);
        }
        return formula;
    }

    /** 提取配置实际引用的参数，避免为未参与规则的字段查询或返回目录值。 */
    public Map<String, Object> valuesFor(EntityForm form) {
        return valuesFor(form, null, "create");
    }

    public Map<String, Object> valuesFor(EntityForm form, UiRuntimeResolutionContext entry, String mode) {
        return runtime(form, entry, mode).values();
    }

    /** 事件链的配置独立发布，只按本次已解析链引用的键读取运行值。 */
    public Map<String, Object> valuesForConfiguration(Object configuration, UiRuntimeResolutionContext entry, String mode) {
        Set<String> keys = new LinkedHashSet<>();
        collect(configuration, keys);
        return resolve(keys, entry, mode, LocalDateTime.now(clock));
    }

    record RuntimeValues(Map<String, Object> values, LocalDate today) { }

    RuntimeValues runtime(EntityForm form, UiRuntimeResolutionContext entry, String mode) {
        Set<String> keys = new LinkedHashSet<>();
        if (form != null) {
            for (var node : form.getNodes() == null ? List.<EntityFormNode>of() : form.getNodes()) {
                collect(read(node.getPropsDocument()), keys);
                collect(read(node.getDataSourceBindingsDocument()), keys);
            }
            collect(read(form.getDataSourceBindingsDocument()), keys);
            collect(read(form.getViewConfig()), keys);
            collect(form.getViewCompositions(), keys);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return new RuntimeValues(resolve(keys, entry, mode, now), now.toLocalDate());
    }

    private Map<String, Object> read(String document) {
        return StringUtils.hasText(document) ? codec.readObject(document, "已发布表单节点属性") : Map.of();
    }

    private static void collect(Object configuration, Set<String> keys) {
        if (configuration instanceof Map<?, ?> children) {
            children.values().forEach(value -> collect(value, keys));
        } else if (configuration instanceof List<?> children) {
            children.forEach(value -> collect(value, keys));
        } else if (configuration instanceof String text) {
            REFERENCE.matcher(text).results().map(match -> match.group(1)).filter(FormContextDefaults::known).forEach(keys::add);
            if (text.startsWith("ctx.") && known(text.substring(4))) keys.add(text.substring(4));
            if (known(text)) keys.add(text);
        }
    }

    /** 补齐省略的字段，保留显式清空和用户实际提交的数据。 */
    public void apply(EntityForm form, Map<String, Object> data) {
        apply(form, data, runtime(form, null, "create"), Map.of());
    }

    void apply(EntityForm form, Map<String, Object> data, RuntimeValues variables, Map<String, Object> params) {
        Map<String, FormContextFormula> defaults = new LinkedHashMap<>();
        if (form.getNodes() == null) return;
        for (EntityFormNode node : form.getNodes()) {
            if (!"FIELD".equalsIgnoreCase(node.getNodeType()) || !"ENTITY_FIELD".equalsIgnoreCase(node.getBindingType())) continue;
            Map<String, Object> props = read(node.getPropsDocument());
            String field = props.get("fieldCode") instanceof String code ? code : node.getBindingRef();
            if (!StringUtils.hasText(field) || data.containsKey(field) || !"formula".equals(props.get("defaultValueMode"))) continue;
            if (props.get("defaultFormula") instanceof String source && hasReference(source)) defaults.put(field, compile(source));
        }
        // 表单布局顺序不等同于依赖顺序。按依赖处理默认值，再让前置接口读取结果。
        for (String field : new ArrayList<>(defaults.keySet())) {
            applyDefault(field, defaults, new LinkedHashSet<>(), data, variables, params);
        }
    }

    private void applyDefault(String field, Map<String, FormContextFormula> defaults, Set<String> visiting,
            Map<String, Object> data, RuntimeValues variables, Map<String, Object> params) {
        FormContextFormula formula = defaults.get(field);
        if (formula == null) return;
        if (!visiting.add(field)) throw new IllegalArgumentException("默认值公式循环引用 " + field);
        for (String dependency : formula.fieldKeys()) applyDefault(dependency, defaults, visiting, data, variables, params);
        try {
            Object value = formula.evaluate(data, params, variables.values(), variables.today());
            if (value != null && !"".equals(value)) data.put(field, value);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("字段 " + field + " 的上下文默认值无效", invalid);
        }
        defaults.remove(field);
        visiting.remove(field);
    }

    private Map<String, Object> resolve(Set<String> keys, UiRuntimeResolutionContext entry, String mode, LocalDateTime now) {
        if (keys.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        if (keys.contains(DATE)) result.put(DATE, now.toLocalDate().toString());
        if (keys.contains(TIME)) result.put(TIME, now.format(DATE_TIME_FORMAT));
        if (keys.contains(MODE) && StringUtils.hasText(mode)) result.put(MODE, mode);
        if (entry != null) {
            entry.formValues().forEach((key, value) -> {
                if (keys.contains(key) && !STARTER.equals(key) && !STARTER_NAME.equals(key)) result.put(key, value);
            });
            resolveStarter(keys, entry.formValues().get(STARTER), result);
        }
        List<String> userKeys = keys.stream().filter(CURRENT_USER_KEYS::contains).toList();
        if (userKeys.isEmpty()) return result;
        var actor = currentActor.current();
        if (actor == null || !StringUtils.hasText(actor.userId())) return result;
        String userId = actor.userId();
        IdentityUser profile = userKeys.stream().allMatch((USER + "userId")::equals) ? null
                : users.findUsersByIds(List.of(userId)).stream()
                        .filter(user -> userId.equals(user.id())).findFirst().orElse(null);
        for (String key : userKeys) {
            String value = switch (key.substring(USER.length())) {
                case "userId" -> userId;
                case "nickname" -> profile == null ? null : profile.nickname();
                case "departmentId" -> profile == null ? null : profile.departmentId();
                case "organizationId" -> profile == null ? null : profile.organizationId();
                case "departmentName" -> profile == null ? null : organizationName(profile.departmentId());
                case "organizationName" -> profile == null ? null : organizationName(profile.organizationId());
                default -> null;
            };
            if (StringUtils.hasText(value)) result.put(key, value);
        }
        return result;
    }

    private void resolveStarter(Set<String> keys, String starter, Map<String, Object> result) {
        if (!StringUtils.hasText(starter) || !keys.contains(STARTER) && !keys.contains(STARTER_NAME)) return;
        // 历史实例可能记录的是用户名。只有能唯一映射到目录身份时才返回 ID。
        Map<String, IdentityUser> matches = new LinkedHashMap<>();
        users.findUsersByIds(List.of(starter)).forEach(user -> matches.put(user.id(), user));
        users.findUsersByUsernames(List.of(starter)).forEach(user -> matches.put(user.id(), user));
        if (matches.size() != 1) return;
        IdentityUser user = matches.values().iterator().next();
        if (keys.contains(STARTER)) result.put(STARTER, user.id());
        if (keys.contains(STARTER_NAME)) {
            result.put(STARTER_NAME, StringUtils.hasText(user.nickname()) ? user.nickname() : user.username());
        }
    }

    private String organizationName(String id) {
        return StringUtils.hasText(id)
                ? organizations.findOrganization(id).map(Organization::name).orElse(null)
                : null;
    }
}
