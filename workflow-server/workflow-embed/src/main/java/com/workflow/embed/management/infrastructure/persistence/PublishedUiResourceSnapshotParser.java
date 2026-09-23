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

    /**
     * 初始化已发布界面资源快照解析器，保存构造参数供后续方法使用。
     *
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    PublishedUiResourceSnapshotParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 校验固定 Release 完整性与归属，并生成管理/列表边界需要的最小资源快照。
     *
     * @param surfaceType 界面类型标识，决定后续已发布界面资源快照解析器采用的处理分支
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param formId 表单ID，后续用于解析已发布界面资源快照解析器时定位或关联目标
     * @param listTarget 列表目标，作为 {@code verifiedSnapshot} 的输入影响后续处理
     * @param formTarget 表单目标，作为 {@code verifiedSnapshot} 的输入影响后续处理
     * @param currentFields 当前字段，供本方法解析已发布界面资源快照解析器时使用
     * @return 解析后的已发布界面资源快照解析器结果，供调用方继续处理
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

    /**
     * 处理已验证快照，并将结果传给后续步骤。
     *
     * @param document 文档，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @param expectedHash 预期哈希，供本方法处理已验证快照时使用
     * @param type 类型标识，决定后续已验证快照采用的处理分支
     * @return 处理后的已验证快照结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 验证快照{@code ownership}；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param formId 表单ID，后续用于验证快照{@code ownership}时定位或关联目标
     * @param listTarget 列表目标，供本方法验证快照{@code ownership}时使用
     * @param listSnapshot 列表快照，供本方法验证快照{@code ownership}时使用
     * @param formTarget 表单目标，供本方法验证快照{@code ownership}时使用
     * @param formSnapshot 表单快照，供本方法验证快照{@code ownership}时使用
     */
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

    /**
     * 处理字段，并将结果传给后续步骤。
     *
     * @param array 数组，供本方法处理字段时使用
     * @return 处理后的字段结果，供调用方继续处理
     */
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

    /**
     * 收集动作集合；结果供调用方的后续步骤使用。
     *
     * @param array 数组，供本方法收集动作集合时使用
     * @param target 目标，供本方法收集动作集合时使用
     */
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

    /**
     * 标记已发布界面资源快照解析器；后续读取或执行将使用更新后的状态。
     *
     * @param node 节点，供本方法标记已发布界面资源快照解析器时使用
     * @param field 字段，作为 {@code node.get} 的输入影响后续处理
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @return 已发布界面资源快照解析器条件成立时为 true，否则为 false
     */
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

    /**
     * 判断是否具有文本；判断结果决定调用方的后续分支。
     *
     * @param node 节点，供本方法判断是否具有文本时使用
     * @param field 字段，供本方法判断是否具有文本时使用
     * @return 文本条件成立时为 true，否则为 false
     */
    private static boolean hasText(JsonNode node, String field) {
        return StringUtils.hasText(text(node, field));
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param node 节点，供本方法处理文本时使用
     * @param field 字段，供本方法处理文本时使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node == null || !node.isObject()
                ? null : node.get(field);
        return value != null && value.isTextual()
                && StringUtils.hasText(value.textValue())
                ? value.textValue().trim() : null;
    }

    /**
     * 判断{@code sensitive}条件是否成立，供调用方选择后续分支。
     *
     * @param code 编码，后续用于处理{@code sensitive}时定位或关联目标
     * @param type 类型标识，决定后续{@code sensitive}采用的处理分支
     * @return {@code sensitive}条件成立时为 true，否则为 false
     */
    private static boolean sensitive(String code, String type) {
        String normalizedCode = code == null
                ? "" : code.toLowerCase(Locale.ROOT);
        String normalizedType = type == null
                ? "" : type.toUpperCase(Locale.ROOT);
        return SENSITIVE_FIELD.matcher(normalizedCode).find()
                || Set.of("PASSWORD", "SECRET", "ENCRYPTED")
                .contains(normalizedType);
    }

    /**
     * 规范化已发布界面资源快照解析器；输出作为后续校验或处理的输入。
     *
     * @param node 节点，供本方法规范化已发布界面资源快照解析器时使用
     * @return 规范化后的已发布界面资源快照解析器结果，供调用方继续处理
     */
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

    /**
     * 计算输入内容的 SHA-256 摘要，供后续签名或幂等键使用。
     *
     * @param value 待处理{@code sha256}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code sha256}文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 构造{@code corrupted}异常，供调用方区分失败原因。
     *
     * @param type 类型标识，决定后续{@code corrupted}采用的处理分支
     * @return 处理后的{@code corrupted}结果，供调用方继续处理
     */
    private static IllegalStateException corrupted(String type) {
        return new IllegalStateException(
                type + " UI 发布快照完整性或归属校验失败");
    }
}
