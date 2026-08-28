package com.workflow.embed.management.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.FieldRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.FormTargetRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ListTargetRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * 将 UI 配置的不可变发布文档投影为 Embed 发布校验资源。
 *
 * <p>该适配器只读取 {@code ui_config_release.snapshot_document}，草稿表仅用于定位 Release 和
 * 确认实体仍处于已发布状态。这样 UI 草稿在发布后继续编辑，不会改变既有 Embed Release 的
 * 字段、动作或组件安全结论。</p>
 */
final class PublishedUiResourceSnapshotParser {

    private static final List<String> BASE_ACTIONS = List.of();
    private static final Set<String> SAFE_EXTERNAL_ACTIONS = Set.of(
            "view", "create", "edit", "save", "select", "submit", "saveAndStart");
    private static final Set<String> SAFE_LIST_FIELD_SOURCES = Set.of(
            "ENTITY_FIELD", "REFERENCE");
    private static final Set<String> DEFERRED_LOOKUP_FIELD_TYPES = Set.of(
            "REFERENCE", "MULTI_REFERENCE", "LOOKUP", "MULTI_LOOKUP",
            "USER", "DEPT", "ROLE", "GROUP");
    // 与平台 SystemEntityFieldPolicy 使用同一保守命名规则；发布快照位于独立模块，
    // 在此复制常量避免为一个安全谓词引入 entity 实现层依赖。
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i)(^password$|password_hash|token_version|(^|_)(secret|token|private_key|credential|salt|otp|mfa_secret)(_|$))");

    private final ObjectMapper objectMapper;

    PublishedUiResourceSnapshotParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 校验 Release 完整性并生成字段/动作白名单；快照损坏时 fail closed。
     */
    ResolvedResource parse(
            SurfaceType surfaceType,
            String entityCode,
            String listKey,
            String formId,
            ListTargetRow listTarget,
            FormTargetRow formTarget,
            List<FieldRow> currentFields) {
        JsonNode listSnapshot = listTarget == null ? null
                : verifiedSnapshot(listTarget.snapshotDocument(), listTarget.contentHash(), "LIST");
        JsonNode formSnapshot = formTarget == null ? null
                : verifiedSnapshot(formTarget.snapshotDocument(), formTarget.contentHash(), "FORM");
        verifySnapshotOwnership(entityCode, listKey, formId,
                listTarget, listSnapshot, formTarget, formSnapshot);

        Map<String, FieldRow> currentByCode = new LinkedHashMap<>();
        for (FieldRow field : currentFields == null ? List.<FieldRow>of() : currentFields) {
            if (StringUtils.hasText(field.fieldCode())) {
                currentByCode.putIfAbsent(field.fieldCode(), field);
            }
        }

        LinkedHashMap<String, JsonNode> listFields = fields(
                listSnapshot == null ? null : listSnapshot.path("list").path("fields"));
        LinkedHashMap<String, JsonNode> formFields = fields(
                formSnapshot == null ? null : formSnapshot.path("legacyFields"));
        LinkedHashSet<String> publishedFields = new LinkedHashSet<>();
        publishedFields.addAll(listFields.keySet());
        publishedFields.addAll(formFields.keySet());
        publishedFields.retainAll(currentByCode.keySet());

        LinkedHashSet<String> queryable = new LinkedHashSet<>();
        if (surfaceType == SurfaceType.FORM) {
            queryable.addAll(formFields.keySet());
        } else {
            listFields.forEach((code, field) -> {
                if (flag(field, "isQuery", false)) {
                    queryable.add(code);
                }
            });
        }
        queryable.retainAll(currentByCode.keySet());

        LinkedHashSet<String> writable = new LinkedHashSet<>();
        formFields.forEach((code, field) -> {
            FieldRow current = currentByCode.get(code);
            if (current != null && current.editable() && !flag(field, "isReadonly", false)) {
                writable.add(code);
            }
        });

        LinkedHashSet<String> sensitive = new LinkedHashSet<>();
        for (String code : publishedFields) {
            FieldRow current = currentByCode.get(code);
            JsonNode formField = formFields.get(code);
            String snapshotType = text(formField, "fieldType");
            if (sensitive(code, current == null ? null : current.fieldType())
                    || sensitive(code, snapshotType)) {
                sensitive.add(code);
            }
        }
        // 敏感字段不能成为浏览器可控过滤条件；否则即使不直接 return，也可能通过
        // 相等/存在性查询形成凭据探测信道。
        queryable.removeAll(sensitive);

        LinkedHashSet<String> actions = new LinkedHashSet<>(BASE_ACTIONS);
        if (formTarget != null) {
            actions.add("view");
            actions.add("create");
            actions.add("save");
        }
        if (listSnapshot != null) {
            collectActions(listSnapshot.path("list").path("toolbarConfig"), actions);
            collectActions(listSnapshot.path("list").path("rowActionConfig"), actions);
        }
        if (formTarget == null) {
            actions.removeAll(Set.of("create", "edit", "save", "submit", "saveAndStart"));
        }
        boolean trusted = trustedList(listSnapshot) && trustedForm(formSnapshot);
        return new ResolvedResource(
                entityCode,
                listKey,
                formId,
                listTarget == null ? null : listTarget.releaseId(),
                listTarget == null ? null : listTarget.releaseVersion(),
                formTarget == null ? null : formTarget.releaseId(),
                formTarget == null ? null : formTarget.releaseVersion(),
                List.copyOf(publishedFields),
                List.copyOf(queryable),
                List.copyOf(writable),
                List.copyOf(sensitive),
                List.copyOf(actions),
                trusted);
    }

    private JsonNode verifiedSnapshot(String document, String expectedHash, String type) {
        if (!StringUtils.hasText(document) || !StringUtils.hasText(expectedHash)) {
            throw corrupted(type);
        }
        try {
            JsonNode parsed = objectMapper.readTree(document);
            if (parsed == null || !parsed.isObject()
                    || !type.equals(text(parsed, "configType"))) {
                throw corrupted(type);
            }
            String canonical = objectMapper.writeValueAsString(canonicalize(parsed));
            if (!expectedHash.equals(sha256(canonical))) {
                throw corrupted(type);
            }
            return parsed;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(type + " UI 发布快照不是合法 JSON", exception);
        }
    }

    private static void verifySnapshotOwnership(
            String entityCode,
            String listKey,
            String formId,
            ListTargetRow listTarget,
            JsonNode listSnapshot,
            FormTargetRow formTarget,
            JsonNode formSnapshot) {
        if (listSnapshot != null) {
            JsonNode list = listSnapshot.path("list");
            if (!listTarget.configId().equals(text(list, "id"))
                    || !listTarget.entityId().equals(text(list, "entityId"))
                    || !entityCode.equals(text(list, "entityCode"))
                    || !listKey.equals(text(list, "listKey"))) {
                throw corrupted("LIST");
            }
        }
        if (formSnapshot != null) {
            JsonNode form = formSnapshot.path("form");
            if (!formTarget.formId().equals(text(form, "id"))
                    || !formTarget.entityId().equals(text(form, "entityId"))
                    || !formId.equals(text(form, "id"))) {
                throw corrupted("FORM");
            }
        }
    }

    private static LinkedHashMap<String, JsonNode> fields(JsonNode array) {
        LinkedHashMap<String, JsonNode> result = new LinkedHashMap<>();
        if (array == null || !array.isArray()) {
            return result;
        }
        for (JsonNode field : array) {
            String code = text(field, "fieldCode");
            if (StringUtils.hasText(code)) {
                result.putIfAbsent(code, field);
            }
        }
        return result;
    }

    private static void collectActions(JsonNode array, Set<String> target) {
        if (!array.isArray()) {
            return;
        }
        for (JsonNode action : array) {
            String key = text(action, "key");
            String type = text(action, "type");
            if (SAFE_EXTERNAL_ACTIONS.contains(key)
                    && "built-in".equalsIgnoreCase(type)
                    && flag(action, "enabled", true)) {
                target.add(key);
            }
        }
    }

    private static boolean trustedList(JsonNode snapshot) {
        if (snapshot == null) {
            return true;
        }
        JsonNode list = snapshot.path("list");
        if (hasText(list, "customComponent")
                || hasText(list, "queryProviderCode")
                || hasText(list, "queryDataSourceId")
                || hasText(list, "queryOperationCode")
                || nonEmpty(snapshot.path("eventBindings"))
                || nonEmpty(snapshot.path("viewCompositions"))) {
            return false;
        }
        JsonNode fields = list.path("fields");
        if (fields.isArray()) {
            for (JsonNode field : fields) {
                String source = text(field, "dataSourceType");
                if (hasText(field, "renderComponent")
                        || hasText(field, "templateId")
                        || hasText(field, "dataSourceId")
                        || hasText(field, "dataSourceOperationCode")
                        || (StringUtils.hasText(source)
                        && !SAFE_LIST_FIELD_SOURCES.contains(
                                source.toUpperCase(Locale.ROOT)))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean trustedForm(JsonNode snapshot) {
        if (snapshot == null) {
            return true;
        }
        JsonNode form = snapshot.path("form");
        if (hasText(form, "customComponent")
                || unsafeDocument(text(form, "dataSourceBindingsDocument"))
                || nonEmpty(snapshot.path("eventBindings"))
                || nonEmpty(snapshot.path("viewCompositions"))) {
            return false;
        }
        JsonNode nodes = snapshot.path("nodes");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                if (hasText(node, "componentName")
                        || hasText(node, "templateId")
                        || unsafeDocument(text(node, "dataSourceBindingsDocument"))) {
                    return false;
                }
            }
        }
        JsonNode legacyFields = snapshot.path("legacyFields");
        if (legacyFields.isArray()) {
            for (JsonNode field : legacyFields) {
                String fieldType = text(field, "fieldType");
                if (hasUnsupportedPattern(field.get("validationRules"))
                        || StringUtils.hasText(fieldType)
                        && DEFERRED_LOOKUP_FIELD_TYPES.contains(
                                fieldType.toUpperCase(Locale.ROOT))) {
                    // V1 尚未固定候选 List Release 与返回投影，引用字段不能以一个
                    // 看似可用、运行时却只能拒绝的契约发布给外部系统。
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasUnsupportedPattern(JsonNode validationRules) {
        if (validationRules == null || validationRules.isNull()
                || validationRules.isMissingNode()) {
            return false;
        }
        try {
            JsonNode rules = validationRules.isTextual()
                    ? objectMapper.readTree(validationRules.textValue())
                    : validationRules;
            return rules == null || !rules.isObject() || rules.has("pattern");
        } catch (JsonProcessingException error) {
            return true;
        }
    }

    private static boolean nonEmpty(JsonNode value) {
        return value != null && ((value.isArray() || value.isObject()) && !value.isEmpty()
                || (!value.isMissingNode() && !value.isNull()
                && !value.isArray() && !value.isObject()));
    }

    private static boolean unsafeDocument(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim();
        return !Set.of("{}", "[]", "null").contains(normalized);
    }

    private static boolean flag(JsonNode node, String field, boolean defaultValue) {
        if (node == null || !node.isObject() || !node.has(field)) {
            return defaultValue;
        }
        JsonNode value = node.get(field);
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isIntegralNumber()) {
            return value.intValue() != 0;
        }
        return value.isTextual()
                ? Boolean.parseBoolean(value.textValue()) : defaultValue;
    }

    private static boolean hasText(JsonNode node, String field) {
        return StringUtils.hasText(text(node, field));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null || !node.isObject() ? null : node.get(field);
        return value != null && value.isTextual() && StringUtils.hasText(value.textValue())
                ? value.textValue().trim() : null;
    }

    private static boolean sensitive(String code, String type) {
        String normalizedCode = code == null ? "" : code.toLowerCase(Locale.ROOT);
        String normalizedType = type == null ? "" : type.toUpperCase(Locale.ROOT);
        return SENSITIVE_FIELD.matcher(normalizedCode).find()
                || Set.of("PASSWORD", "SECRET", "ENCRYPTED").contains(normalizedType);
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
            node.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        return node.deepCopy();
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

    private static IllegalStateException corrupted(String type) {
        return new IllegalStateException(type + " UI 发布快照完整性或归属校验失败");
    }
}
