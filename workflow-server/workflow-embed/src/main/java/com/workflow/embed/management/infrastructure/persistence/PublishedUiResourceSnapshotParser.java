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
 * 从不可变 UI Release 恢复 Embed 发布所需的资源坐标和策略字段。
 *
 * <p>FORM 只校验快照哈希、发布归属、字段绑定和敏感回传边界，不解析或限制
 * componentType、componentProps、布局节点、选项、弹层或组件注册名。原生 iframe
 * 直接消费 Flow Published Form，因此未来新增组件不会要求修改本解析器。</p>
 */
final class PublishedUiResourceSnapshotParser {

    private static final Set<String> SAFE_EXTERNAL_ACTIONS = Set.of(
            "view", "create", "edit", "save", "select", "submit",
            "saveAndStart");
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i)(^password$|password_hash|token_version|(^|_)(secret|token|private_key|credential|salt|otp|mfa_secret)(_|$))");

    private final ObjectMapper objectMapper;

    PublishedUiResourceSnapshotParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 校验固定 Release 完整性与归属，并生成管理/列表边界需要的最小资源快照。 */
    ResolvedResource parse(
            SurfaceType surfaceType,
            String entityCode,
            String listKey,
            String formId,
            ListTargetRow listTarget,
            FormTargetRow formTarget,
            List<FieldRow> currentFields) {
        JsonNode listSnapshot = listTarget == null ? null
                : verifiedSnapshot(listTarget.snapshotDocument(),
                listTarget.contentHash(), "LIST");
        JsonNode formSnapshot = formTarget == null ? null
                : verifiedSnapshot(formTarget.snapshotDocument(),
                formTarget.contentHash(), "FORM");
        verifySnapshotOwnership(entityCode, listKey, formId,
                listTarget, listSnapshot, formTarget, formSnapshot);

        Map<String, FieldRow> currentByCode = new LinkedHashMap<>();
        for (FieldRow field : currentFields == null
                ? List.<FieldRow>of() : currentFields) {
            if (StringUtils.hasText(field.fieldCode())) {
                currentByCode.putIfAbsent(field.fieldCode(), field);
            }
        }
        LinkedHashMap<String, JsonNode> listFields = fields(
                listSnapshot == null ? null
                        : listSnapshot.path("list").path("fields"));
        LinkedHashMap<String, JsonNode> formFields = fields(
                formSnapshot == null ? null
                        : formSnapshot.path("legacyFields"));

        LinkedHashSet<String> publishedFields = new LinkedHashSet<>();
        if (surfaceType == SurfaceType.FORM) {
            // 字段集合只服务 Context/returnable 校验；渲染仍读取完整 Form Release。
            publishedFields.addAll(formFields.keySet());
        } else {
            publishedFields.addAll(listFields.keySet());
            publishedFields.addAll(formFields.keySet());
            publishedFields.retainAll(currentByCode.keySet());
        }

        LinkedHashSet<String> queryable = new LinkedHashSet<>();
        if (surfaceType == SurfaceType.FORM) {
            queryable.addAll(formFields.keySet());
        } else {
            listFields.forEach((code, field) -> {
                if (flag(field, "isQuery", false)) {
                    queryable.add(code);
                }
            });
            queryable.retainAll(currentByCode.keySet());
        }

        LinkedHashSet<String> writable = new LinkedHashSet<>();
        formFields.forEach((code, field) -> {
            FieldRow current = currentByCode.get(code);
            boolean editable = surfaceType == SurfaceType.FORM
                    ? !flag(field, "isReadonly", false)
                    : current != null && current.editable()
                    && !flag(field, "isReadonly", false);
            if (editable) {
                writable.add(code);
            }
        });

        LinkedHashSet<String> sensitive = new LinkedHashSet<>();
        for (String code : publishedFields) {
            FieldRow current = currentByCode.get(code);
            JsonNode formField = formFields.get(code);
            if (sensitive(code,
                    current == null ? null : current.fieldType())
                    || sensitive(code, text(formField, "fieldType"))) {
                sensitive.add(code);
            }
        }
        queryable.removeAll(sensitive);

        LinkedHashSet<String> actions = new LinkedHashSet<>();
        if (formTarget != null) {
            actions.add("view");
            actions.add("create");
            actions.add("save");
        }
        if (listSnapshot != null) {
            collectActions(listSnapshot.path("list")
                    .path("toolbarConfig"), actions);
            collectActions(listSnapshot.path("list")
                    .path("rowActionConfig"), actions);
        }
        if (formTarget == null) {
            actions.removeAll(Set.of(
                    "create", "edit", "save", "submit", "saveAndStart"));
        }

        // LIST 与 FORM 都由 iframe 内 Flow 原生运行时消费完整发布快照。
        // 本解析器只恢复坐标及 Context/returnable 策略元数据，不检查
        // renderer、provider、event binding 或 view composition，因此后续新组件无需修改 Embed。
        return new ResolvedResource(
                entityCode, listKey, formId,
                listTarget == null ? null : listTarget.releaseId(),
                listTarget == null ? null : listTarget.releaseVersion(),
                formTarget == null ? null : formTarget.releaseId(),
                formTarget == null ? null : formTarget.releaseVersion(),
                List.copyOf(publishedFields),
                List.copyOf(queryable),
                List.copyOf(writable),
                List.copyOf(sensitive),
                List.copyOf(actions),
                true);
    }

    private JsonNode verifiedSnapshot(
            String document,
            String expectedHash,
            String type) {
        if (!StringUtils.hasText(document)
                || !StringUtils.hasText(expectedHash)) {
            throw corrupted(type);
        }
        try {
            JsonNode parsed = objectMapper.readTree(document);
            if (parsed == null || !parsed.isObject()
                    || !type.equals(text(parsed, "configType"))) {
                throw corrupted(type);
            }
            String canonical = objectMapper.writeValueAsString(
                    canonicalize(parsed));
            if (!expectedHash.equals(sha256(canonical))) {
                throw corrupted(type);
            }
            return parsed;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    type + " UI 发布快照不是合法 JSON", exception);
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

    private static void collectActions(
            JsonNode array,
            Set<String> target) {
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

    private static boolean flag(
            JsonNode node,
            String field,
            boolean defaultValue) {
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
        JsonNode value = node == null || !node.isObject()
                ? null : node.get(field);
        return value != null && value.isTextual()
                && StringUtils.hasText(value.textValue())
                ? value.textValue().trim() : null;
    }

    private static boolean sensitive(String code, String type) {
        String normalizedCode = code == null
                ? "" : code.toLowerCase(Locale.ROOT);
        String normalizedType = type == null
                ? "" : type.toUpperCase(Locale.ROOT);
        return SENSITIVE_FIELD.matcher(normalizedCode).find()
                || Set.of("PASSWORD", "SECRET", "ENCRYPTED")
                .contains(normalizedType);
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            node.fields().forEachRemaining(fields::add);
            fields.sort(Comparator.comparing(Map.Entry::getKey));
            fields.forEach(field -> result.set(
                    field.getKey(), canonicalize(field.getValue())));
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
            throw new IllegalStateException(
                    "JVM 不支持 SHA-256", exception);
        }
    }

    private static IllegalStateException corrupted(String type) {
        return new IllegalStateException(
                type + " UI 发布快照完整性或归属校验失败");
    }
}
