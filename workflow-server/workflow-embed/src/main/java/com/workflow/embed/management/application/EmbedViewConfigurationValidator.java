package com.workflow.embed.management.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.embed.management.domain.EmbedManagementModel.Capability;
import com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.ValidationResult;
import com.workflow.embed.management.domain.EmbedManagementModel.Violation;
import com.workflow.embed.management.port.EmbedManagementRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Embed View 发布校验器。
 *
 * <p>校验器同时生成 canonical 快照和摘要，发布事务必须重新调用本组件，不能复用之前预检的
 * 结果，从而避免校验后草稿或底层 UI Release 变化造成 TOCTOU。</p>
 */
@Component
public class EmbedViewConfigurationValidator {

    private static final int MAX_DRAFT_BYTES = 262_144;
    private static final int MAX_CONTEXT_SCHEMA_BYTES = 16_384;
    private static final int MAX_SCHEMA_DEPTH = 8;
    private static final int MAX_SCHEMA_PROPERTIES = 32;
    private static final int MAX_SCHEMA_ENUM_VALUES = 100;
    private static final int MAX_VIOLATIONS = 100;
    private static final Set<String> SUPPORTED_SCHEMA_TYPES = Set.of(
            "object", "array", "string", "integer", "number", "boolean", "null");
    private static final Set<String> SUPPORTED_SCHEMA_KEYWORDS = Set.of(
            "type", "enum", "properties", "required", "additionalProperties", "items",
            "minItems", "maxItems", "minLength", "maxLength", "pattern",
            "minimum", "maximum", "title", "description", "default", "examples");
    private static final Set<Capability> V1_BLOCKED = Set.of(
            // V1 尚未具备通用 record_version、强类型 EmbedActionRegistry 与流程动作契约。
            // 发布端必须 fail-closed，不能生成运行时无法安全执行的能力快照。
            Capability.RECORD_UPDATE,
            Capability.ACTION_EXECUTE,
            Capability.PROCESS_START,
            Capability.RECORD_DELETE,
            Capability.BATCH_DELETE,
            Capability.EXPORT,
            Capability.FILE_UPLOAD,
            Capability.FILE_DOWNLOAD);

    private final ObjectMapper objectMapper;
    private final EmbedManagementRepository repository;

    public EmbedViewConfigurationValidator(
            ObjectMapper objectMapper,
            EmbedManagementRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    /** 校验草稿并返回发布可直接持久化的解析结果。 */
    public ValidationResult validate(SurfaceType surfaceType, JsonNode draft) {
        List<Violation> violations = new ArrayList<>();
        if (draft == null || !draft.isObject()) {
            add(violations, "$", "INVALID_DOCUMENT", "draft 必须是 JSON Object");
            return invalid(violations);
        }
        if (serializedSize(draft) > MAX_DRAFT_BYTES) {
            add(violations, "$", "DOCUMENT_TOO_LARGE", "draft 最大为 256 KiB");
        }

        JsonNode target = draft.path("target");
        JsonNode releasePolicy = draft.path("releasePolicy");
        requiredText(target, "entityCode", "target.entityCode", violations);
        if (surfaceType == SurfaceType.LIST) {
            requiredText(target, "listKey", "target.listKey", violations);
        }
        requiredText(releasePolicy, "strategy", "releasePolicy.strategy", violations);

        List<Capability> capabilities = enumArray(
                draft.path("capabilities"), Capability.class, "capabilities", violations);
        for (Capability capability : capabilities) {
            if (V1_BLOCKED.contains(capability)) {
                add(violations, "capabilities", "CAPABILITY_NOT_SUPPORTED",
                        capability + " 在 Embed V1 中强制关闭");
            }
        }
        List<String> entryModes = validateEntryModes(
                surfaceType, draft.path("entryModes"), violations);
        validateEntryCapabilities(entryModes, capabilities, violations);
        validateContextSchema(draft.path("contextSchema"), violations);

        ResolvedResource resolved = null;
        // 只要目标定位字段完整，就继续解析资源并聚合字段/动作违规；这样一次预检可返回尽量完整的
        // 修复清单，而不会因一个独立的 Schema 或 Capability 错误遮蔽后续问题。
        boolean targetResolvable = StringUtils.hasText(text(target, "entityCode"))
                && StringUtils.hasText(text(releasePolicy, "strategy"))
                && (surfaceType != SurfaceType.LIST
                    || StringUtils.hasText(text(target, "listKey")));
        if (targetResolvable) {
            resolved = repository.resolvePublishedResource(surfaceType, target, releasePolicy);
            if (resolved == null) {
                add(violations, "target", "PUBLISHED_RESOURCE_NOT_FOUND",
                        "目标实体及 List/Form 必须存在匹配的已发布 Release");
            }
        }
        if (resolved != null) {
            validateResolvedResource(draft, capabilities, resolved, violations);
        }
        if (!violations.isEmpty()) {
            return invalid(violations);
        }

        ObjectNode snapshot = ((ObjectNode) draft).deepCopy();
        snapshot.set("resolved", resolvedNode(resolved));
        JsonNode canonicalNode = canonicalize(snapshot);
        try {
            String canonical = objectMapper.writeValueAsString(canonicalNode);
            // resolved 是发布器注入的不可变定位信息，不在原草稿大小中。必须对最终完整快照
            // 再做一次 UTF-8 字节检查，否则 validate=true 后会在 Release INSERT 撞数据库 CHECK。
            if (canonical.getBytes(StandardCharsets.UTF_8).length > MAX_DRAFT_BYTES) {
                add(violations, "$", "DOCUMENT_TOO_LARGE",
                        "包含 resolved 信息的发布快照最大为 256 KiB");
                return invalid(violations);
            }
            return new ValidationResult(
                    true,
                    resolved,
                    List.of(),
                    List.of(),
                    canonical,
                    sha256(canonical));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 Embed View 发布快照", exception);
        }
    }

    private void validateResolvedResource(
            JsonNode draft,
            List<Capability> capabilities,
            ResolvedResource resource,
            List<Violation> violations) {
        if (!resource.trustedComponentsOnly()) {
            add(violations, "target", "UNTRUSTED_COMPONENT",
                    "Embed V1 只允许平台可信内建组件");
        }
        Set<String> allFields = Set.copyOf(resource.fields());
        Set<String> queryableFields = Set.copyOf(resource.queryableFields());
        Set<String> writableFields = Set.copyOf(resource.writableFields());
        Set<String> sensitiveFields = Set.copyOf(resource.sensitiveFields());
        JsonNode fieldPolicy = draft.path("fieldPolicy");
        List<String> visible = stringArray(fieldPolicy.path("visible"),
                "fieldPolicy.visible", violations);
        List<String> queryable = stringArray(fieldPolicy.path("queryable"),
                "fieldPolicy.queryable", violations);
        List<String> writable = stringArray(fieldPolicy.path("writable"),
                "fieldPolicy.writable", violations);
        List<String> returnable = stringArray(fieldPolicy.path("returnable"),
                "fieldPolicy.returnable", violations);
        subset(visible, allFields, "fieldPolicy.visible", "FIELD_NOT_FOUND", violations);
        subset(queryable, queryableFields, "fieldPolicy.queryable", "FIELD_NOT_QUERYABLE", violations);
        subset(writable, writableFields, "fieldPolicy.writable", "FIELD_NOT_WRITABLE", violations);
        subset(returnable, new HashSet<>(visible), "fieldPolicy.returnable",
                "RETURNABLE_NOT_VISIBLE", violations);
        for (int index = 0; index < returnable.size(); index++) {
            if (sensitiveFields.contains(returnable.get(index))) {
                add(violations, "fieldPolicy.returnable[" + index + "]",
                        "SENSITIVE_FIELD_NOT_RETURNABLE", "敏感字段不能回传给宿主页面");
            }
        }

        Set<String> actions = Set.copyOf(resource.actionKeys());
        List<String> allowedActions = stringArray(
                draft.path("actionPolicy").path("allowed"),
                "actionPolicy.allowed", violations);
        subset(allowedActions, actions, "actionPolicy.allowed",
                "ACTION_NOT_PUBLISHED", violations);
        validateContextBindings(draft.path("contextBindings"), draft.path("contextSchema"),
                allFields, queryableFields, writableFields, violations);

        if (capabilities.contains(Capability.LIST_QUERY)
                && resource.listReleaseId() == null) {
            add(violations, "capabilities", "LIST_RELEASE_REQUIRED",
                    "LIST_QUERY 必须绑定已发布列表快照");
        }
        if ((capabilities.contains(Capability.RECORD_VIEW)
                || capabilities.contains(Capability.RECORD_CREATE)
                || capabilities.contains(Capability.RECORD_UPDATE))
                && resource.formReleaseId() == null) {
            add(violations, "capabilities", "FORM_RELEASE_REQUIRED",
                    "记录查看或写入能力必须绑定已发布表单快照");
        }
    }

    private static void validateEntryCapabilities(
            List<String> entryModes,
            List<Capability> capabilities,
            List<Violation> violations) {
        Map<String, Capability> required = Map.of(
                "LIST", Capability.LIST_QUERY,
                "CREATE", Capability.RECORD_CREATE,
                "VIEW", Capability.RECORD_VIEW);
        for (int index = 0; index < entryModes.size(); index++) {
            Capability capability = required.get(entryModes.get(index));
            if (capability != null && !capabilities.contains(capability)) {
                add(violations, "entryModes[" + index + "]",
                        "ENTRY_CAPABILITY_REQUIRED",
                        entryModes.get(index) + " 入口必须同时发布 " + capability + " 能力");
            }
        }
        if (capabilities.contains(Capability.SELECTION_RETURN)
                && !capabilities.contains(Capability.LIST_QUERY)) {
            add(violations, "capabilities", "LIST_QUERY_REQUIRED",
                    "SELECTION_RETURN 必须同时发布 LIST_QUERY");
        }
    }

    private void validateContextSchema(JsonNode schema, List<Violation> violations) {
        if (schema.isMissingNode() || schema.isNull()) {
            // Runtime 会无条件解析 context_schema_json；缺失值会落成 null，导致所有 Launch
            // 在 Context 校验阶段失败。因此即使不接收上下文，也必须显式发布空 Object Schema。
            add(violations, "contextSchema", "INVALID_CONTEXT_SCHEMA",
                    "contextSchema 必须显式配置为 JSON Object");
            return;
        }
        if (!schema.isObject()) {
            add(violations, "contextSchema", "INVALID_CONTEXT_SCHEMA",
                    "contextSchema 必须是 JSON Object");
            return;
        }
        if (serializedSize(schema) > MAX_CONTEXT_SCHEMA_BYTES) {
            add(violations, "contextSchema", "CONTEXT_SCHEMA_TOO_LARGE",
                    "contextSchema 最大为 16 KiB");
        }
        SchemaStats stats = new SchemaStats();
        inspectSchema(schema, "contextSchema", 0, stats, violations);
        validateSchemaNode(schema, "contextSchema", true, violations);
        if (stats.properties > MAX_SCHEMA_PROPERTIES) {
            add(violations, "contextSchema", "TOO_MANY_CONTEXT_PROPERTIES",
                    "Embed V1 contextSchema 属性总数不能超过 32");
        }
    }

    /**
     * 校验 Launch 运行端实际实现的 JSON Schema 子集。
     *
     * <p>不能让管理端接受运行端会忽略的关键字，否则管理员以为已经发布的约束实际不会生效；
     * 同样也不能接受运行时必然报错的类型、边界或正则。</p>
     */
    private void validateSchemaNode(
            JsonNode schema,
            String path,
            boolean root,
            List<Violation> violations) {
        if (!schema.isObject()) {
            add(violations, path, "INVALID_CONTEXT_SCHEMA_NODE",
                    "Schema 节点必须是 JSON Object");
            return;
        }
        schema.fieldNames().forEachRemaining(keyword -> {
            if (!SUPPORTED_SCHEMA_KEYWORDS.contains(keyword) && !"$ref".equals(keyword)) {
                add(violations, path + "." + keyword, "UNSUPPORTED_SCHEMA_KEYWORD",
                        "Embed V1 运行端不支持该 JSON Schema 关键字");
            }
        });

        JsonNode type = schema.get("type");
        if (type != null && (!type.isTextual()
                || !SUPPORTED_SCHEMA_TYPES.contains(type.textValue()))) {
            add(violations, path + ".type", "UNSUPPORTED_SCHEMA_TYPE",
                    "type 不是 Embed V1 支持的单一 JSON 类型");
        } else if (root && type != null && !"object".equals(type.textValue())) {
            // Launch 的 context 契约固定为 Map；发布 array/string 等顶层 Schema 会使每次 Launch
            // 都失败，而不是把 Context 改造成相应类型。
            add(violations, path + ".type", "CONTEXT_OBJECT_REQUIRED",
                    "contextSchema 顶层 type 只能是 object");
        }

        JsonNode properties = schema.get("properties");
        if (properties != null && !properties.isObject()) {
            add(violations, path + ".properties", "SCHEMA_OBJECT_REQUIRED",
                    "properties 必须是 JSON Object");
        } else if (properties != null) {
            properties.fields().forEachRemaining(property -> validateSchemaNode(
                    property.getValue(), path + ".properties." + property.getKey(),
                    false, violations));
        }

        validateRequired(schema, properties, path, violations);
        JsonNode additional = schema.get("additionalProperties");
        if (additional != null && !additional.isBoolean()) {
            add(violations, path + ".additionalProperties", "SCHEMA_BOOLEAN_REQUIRED",
                    "additionalProperties 在 Embed V1 中只能是 boolean");
        }

        JsonNode items = schema.get("items");
        if (items != null) {
            validateSchemaNode(items, path + ".items", false, violations);
        }
        JsonNode enumValues = schema.get("enum");
        if (enumValues != null && (!enumValues.isArray() || enumValues.isEmpty()
                || enumValues.size() > MAX_SCHEMA_ENUM_VALUES)) {
            add(violations, path + ".enum", "INVALID_SCHEMA_ENUM",
                    "enum 必须包含 1 到 100 个值");
        }

        validateNonNegativeRange(schema, path, "minLength", "maxLength", violations);
        validateNonNegativeRange(schema, path, "minItems", "maxItems", violations);
        validateDecimalRange(schema, path, violations);
        validatePattern(schema.get("pattern"), path + ".pattern", violations);
    }

    private static void validateRequired(
            JsonNode schema,
            JsonNode properties,
            String path,
            List<Violation> violations) {
        JsonNode required = schema.get("required");
        if (required == null) {
            return;
        }
        if (!required.isArray()) {
            add(violations, path + ".required", "SCHEMA_ARRAY_REQUIRED",
                    "required 必须是字符串数组");
            return;
        }
        Set<String> names = new HashSet<>();
        for (int index = 0; index < required.size(); index++) {
            JsonNode value = required.get(index);
            String itemPath = path + ".required[" + index + "]";
            if (!value.isTextual() || !StringUtils.hasText(value.textValue())) {
                add(violations, itemPath, "STRING_REQUIRED", "required 元素必须是非空字符串");
            } else if (!names.add(value.textValue())) {
                add(violations, itemPath, "DUPLICATE_VALUE", "required 不能包含重复字段");
            } else if (properties == null || !properties.isObject()
                    || !properties.has(value.textValue())) {
                add(violations, itemPath, "REQUIRED_PROPERTY_NOT_DEFINED",
                        "required 字段必须在 properties 中定义");
            }
        }
    }

    private static void validateNonNegativeRange(
            JsonNode schema,
            String path,
            String minimumName,
            String maximumName,
            List<Violation> violations) {
        JsonNode minimum = schema.get(minimumName);
        JsonNode maximum = schema.get(maximumName);
        if (minimum != null && (!minimum.isIntegralNumber() || minimum.longValue() < 0)) {
            add(violations, path + "." + minimumName, "NON_NEGATIVE_INTEGER_REQUIRED",
                    minimumName + " 必须是非负整数");
        }
        if (maximum != null && (!maximum.isIntegralNumber() || maximum.longValue() < 0)) {
            add(violations, path + "." + maximumName, "NON_NEGATIVE_INTEGER_REQUIRED",
                    maximumName + " 必须是非负整数");
        }
        if (minimum != null && maximum != null
                && minimum.isIntegralNumber() && maximum.isIntegralNumber()
                && minimum.longValue() > maximum.longValue()) {
            add(violations, path, "INVALID_SCHEMA_RANGE",
                    minimumName + " 不能大于 " + maximumName);
        }
    }

    private static void validateDecimalRange(
            JsonNode schema, String path, List<Violation> violations) {
        JsonNode minimum = schema.get("minimum");
        JsonNode maximum = schema.get("maximum");
        if (minimum != null && !minimum.isNumber()) {
            add(violations, path + ".minimum", "SCHEMA_NUMBER_REQUIRED",
                    "minimum 必须是数字");
        }
        if (maximum != null && !maximum.isNumber()) {
            add(violations, path + ".maximum", "SCHEMA_NUMBER_REQUIRED",
                    "maximum 必须是数字");
        }
        if (minimum != null && maximum != null && minimum.isNumber() && maximum.isNumber()
                && minimum.decimalValue().compareTo(maximum.decimalValue()) > 0) {
            add(violations, path, "INVALID_SCHEMA_RANGE", "minimum 不能大于 maximum");
        }
    }

    private static void validatePattern(
            JsonNode pattern, String path, List<Violation> violations) {
        if (pattern == null) {
            return;
        }
        // Java 回溯正则没有可靠的单次匹配超时。Context 值由第三方应用控制，若允许管理员
        // 发布任意 pattern，灾难性回溯可被反复触发为 Launch 线程耗尽。V1 因此整体禁用该
        // 关键字；后续只能在引入线性时间正则引擎后同时放开发布端和运行端。
        add(violations, path, "SCHEMA_PATTERN_NOT_SUPPORTED",
                "Embed V1 不支持 pattern；请使用 enum 或长度约束");
    }

    private void inspectSchema(JsonNode node, String path, int depth,
                               SchemaStats stats, List<Violation> violations) {
        if (depth > MAX_SCHEMA_DEPTH) {
            add(violations, path, "CONTEXT_SCHEMA_TOO_DEEP",
                    "Embed V1 contextSchema 最大嵌套深度为 8");
            return;
        }
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null) {
                // Launch V1 不带 Schema resolver；发布端若放行本地 $ref，配置虽然能发布，
                // 但所有 Launch 都会失败。待运行态支持安全的纯本地 resolver 后再同时放开。
                add(violations, path + ".$ref", "REMOTE_REF_FORBIDDEN",
                        "Embed V1 contextSchema 不支持任何 $ref");
            }
            JsonNode properties = node.get("properties");
            if (properties != null && properties.isObject()) {
                stats.properties += properties.size();
            }
            node.fields().forEachRemaining(entry -> inspectSchema(
                    entry.getValue(), path + "." + entry.getKey(), depth + 1,
                    stats, violations));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                inspectSchema(node.get(index), path + "[" + index + "]",
                        depth + 1, stats, violations);
            }
        }
    }

    private void validateContextBindings(
            JsonNode bindings,
            JsonNode contextSchema,
            Set<String> fields,
            Set<String> queryable,
            Set<String> writable,
            List<Violation> violations) {
        if (bindings.isMissingNode() || bindings.isNull()) {
            return;
        }
        if (!bindings.isArray()) {
            add(violations, "contextBindings", "INVALID_BINDINGS",
                    "contextBindings 必须是数组");
            return;
        }
        JsonNode schemaProperties = contextSchema.path("properties");
        Set<String> requiredSources = new HashSet<>();
        contextSchema.path("required").forEach(value -> {
            if (value.isTextual()) {
                requiredSources.add(value.textValue());
            }
        });
        Set<String> uniqueBindings = new HashSet<>();
        Set<String> uniqueUsageTargets = new HashSet<>();
        Set<String> encodedFixedFilterKeys = new HashSet<>();
        for (int index = 0; index < bindings.size(); index++) {
            JsonNode binding = bindings.get(index);
            String target = text(binding, "target");
            String source = text(binding, "source");
            String usage = text(binding, "usage");
            String path = "contextBindings[" + index + "]";
            if (!binding.isObject() || !StringUtils.hasText(source)
                    || !StringUtils.hasText(target)) {
                add(violations, path, "INVALID_BINDING", "source 和 target 均为必填");
                continue;
            }
            if (!uniqueBindings.add(source + "\0" + target + "\0" + usage)) {
                add(violations, path, "DUPLICATE_BINDING", "Context Binding 不能重复");
            }
            if (StringUtils.hasText(usage)
                    && !uniqueUsageTargets.add(usage + "\0" + target)) {
                add(violations, path + ".target", "DUPLICATE_BINDING_TARGET",
                        "同一用途不能重复绑定目标字段");
            }
            if ("FIXED_FILTER".equals(usage)) {
                // 实体列表用 field/field_op 两个键表达固定条件；发布时必须同时预留，
                // 否则 foo 与 foo_op 的顺序会让后者覆盖前者并改变租户过滤语义。
                boolean valueKeyAdded = encodedFixedFilterKeys.add(target);
                boolean operatorKeyAdded = encodedFixedFilterKeys.add(target + "_op");
                if (!valueKeyAdded || !operatorKeyAdded) {
                    add(violations, path + ".target", "FILTER_KEY_COLLISION",
                            "固定过滤字段编码键发生冲突");
                }
            }
            JsonNode sourceSchema = schemaProperties.isObject()
                    ? schemaProperties.get(source) : null;
            if (sourceSchema == null) {
                add(violations, path + ".source", "CONTEXT_SOURCE_NOT_DEFINED",
                        "绑定来源必须是 contextSchema 顶层 properties 中的字段");
            } else {
                String sourceType = text(sourceSchema, "type");
                if (sourceType == null || !Set.of("string", "integer", "number", "boolean")
                        .contains(sourceType)) {
                    add(violations, path + ".source", "CONTEXT_SOURCE_NOT_SCALAR",
                            "Context Binding 来源必须显式声明为 string/integer/number/boolean");
                }
                // Fixed Filter/Forced Value 不能依赖可选 Context；否则调用方省略字段即可绕开
                // 管理员期望的租户或归属约束。
                if (!requiredSources.contains(source)) {
                    add(violations, path + ".source", "CONTEXT_SOURCE_MUST_BE_REQUIRED",
                            "Context Binding 来源必须在 contextSchema.required 中声明");
                }
            }
            if (!fields.contains(target)) {
                add(violations, path + ".target", "FIELD_NOT_FOUND", "绑定目标字段不存在");
            } else if ("FIXED_FILTER".equals(usage) && !queryable.contains(target)) {
                add(violations, path + ".target", "FIELD_NOT_QUERYABLE",
                        "固定过滤目标必须是可查询字段");
            } else if ("FORCED_FORM_VALUE".equals(usage) && !writable.contains(target)) {
                add(violations, path + ".target", "FIELD_NOT_WRITABLE",
                        "表单强制值目标必须是可写字段");
            }
            if (!Set.of("FIXED_FILTER", "FORCED_FORM_VALUE").contains(usage)) {
                add(violations, path + ".usage", "INVALID_BINDING_USAGE",
                        "usage 仅允许 FIXED_FILTER 或 FORCED_FORM_VALUE");
            }
        }
    }

    private List<String> validateEntryModes(
            SurfaceType surfaceType, JsonNode node, List<Violation> violations) {
        List<String> modes = stringArray(node, "entryModes", violations);
        Set<String> allowed = surfaceType == SurfaceType.LIST
                ? Set.of("LIST", "CREATE", "VIEW")
                : Set.of("CREATE", "VIEW");
        if (modes.isEmpty()) {
            add(violations, "entryModes", "ENTRY_MODE_REQUIRED", "至少需要一个入口模式");
        }
        subset(modes, allowed, "entryModes", "ENTRY_MODE_NOT_SUPPORTED", violations);
        return modes;
    }

    private ObjectNode resolvedNode(ResolvedResource resource) {
        ObjectNode node = objectMapper.createObjectNode();
        put(node, "entityCode", resource.entityCode());
        put(node, "listKey", resource.listKey());
        put(node, "defaultFormId", resource.defaultFormId());
        put(node, "listReleaseId", resource.listReleaseId());
        put(node, "listReleaseVersion", resource.listReleaseVersion());
        put(node, "formReleaseId", resource.formReleaseId());
        put(node, "formReleaseVersion", resource.formReleaseVersion());
        return node;
    }

    private static void put(ObjectNode node, String name, Object value) {
        if (value == null) {
            node.putNull(name);
        } else if (value instanceof Long number) {
            node.put(name, number);
        } else {
            node.put(name, String.valueOf(value));
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            node.fields().forEachRemaining(fields::add);
            fields.sort(Comparator.comparing(Map.Entry::getKey));
            fields.forEach(field -> result.set(field.getKey(), canonicalize(field.getValue())));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        return node.deepCopy();
    }

    private static <E extends Enum<E>> List<E> enumArray(
            JsonNode node, Class<E> type, String path, List<Violation> violations) {
        List<String> strings = stringArray(node, path, violations);
        List<E> values = new ArrayList<>();
        for (int index = 0; index < strings.size(); index++) {
            try {
                values.add(Enum.valueOf(type, strings.get(index)));
            } catch (IllegalArgumentException exception) {
                add(violations, path + "[" + index + "]", "UNKNOWN_VALUE",
                        "存在不支持的枚举值");
            }
        }
        return List.copyOf(values);
    }

    private static List<String> stringArray(
            JsonNode node, String path, List<Violation> violations) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            add(violations, path, "ARRAY_REQUIRED", path + " 必须是数组");
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode value = node.get(index);
            if (!value.isTextual() || !StringUtils.hasText(value.textValue())) {
                add(violations, path + "[" + index + "]", "STRING_REQUIRED",
                        "数组元素必须是非空字符串");
            } else if (!values.add(value.textValue())) {
                add(violations, path + "[" + index + "]", "DUPLICATE_VALUE",
                        "数组不能包含重复值");
            }
        }
        return List.copyOf(values);
    }

    private static void subset(List<String> values, Set<String> allowed, String path,
                               String code, List<Violation> violations) {
        for (int index = 0; index < values.size(); index++) {
            if (!allowed.contains(values.get(index))) {
                add(violations, path + "[" + index + "]", code,
                        "值未包含在已发布资源允许范围内");
            }
        }
    }

    private static void requiredText(JsonNode parent, String name, String path,
                                     List<Violation> violations) {
        if (!StringUtils.hasText(text(parent, name))) {
            add(violations, path, "REQUIRED", path + " 为必填");
        }
    }

    private static String text(JsonNode parent, String name) {
        if (parent == null || !parent.isObject()) {
            return null;
        }
        JsonNode value = parent.get(name);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private int serializedSize(JsonNode node) {
        try {
            return objectMapper.writeValueAsBytes(node).length;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON 文档无法序列化", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private static ValidationResult invalid(List<Violation> violations) {
        return new ValidationResult(false, null,
                List.copyOf(violations.subList(0, Math.min(violations.size(), MAX_VIOLATIONS))),
                List.of(), null, null);
    }

    private static void add(List<Violation> violations, String path,
                            String code, String message) {
        if (violations.size() < MAX_VIOLATIONS) {
            violations.add(new Violation(path, code, message));
        }
    }

    private static final class SchemaStats {
        private int properties;
    }
}
