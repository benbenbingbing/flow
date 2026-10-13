package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.workflow.migration.application.ConfigMigrationReferenceSupport.*;
import static com.workflow.migration.application.ConfigMigrationReferenceService.maps;

/**
 * 子表单通过实体编码/表单键定位稳定身份，运行时统一使用当前配置。
 */
@Service
@RequiredArgsConstructor
public class ConfigMigrationSubFormReferences {
    private static final List<String> FORM_IDS = List.of("childFormId", "refFormId", "publishedFormId");
    private static final List<String> RELEASE_IDS = List.of("childFormReleaseId", "refFormReleaseId", "publishedFormReleaseId");
    private static final Set<String> METADATA = Set.of("id", "entityId", "formId", "fieldId", "parentId",
            "createdAt", "updatedAt", "createdBy", "updatedBy", "deleted", "revision", "publishedVersion",
            "activeReleaseId", "draftRevision", "draftHash", "publishedHash", "publishStatus",
            "childFormReleaseVersion", "refFormReleaseVersion", "publishedFormReleaseVersion");
    private final EntityDefinitionMapper entityMapper;
    private final EntityFormMapper formMapper;
    private final UiConfigReleaseMapper releaseMapper;
    private final ConfigMigrationReferenceService references;
    // 延迟获取仅用于复用纯快照导出方法，避免发布资产服务与引用解析的构造循环。
    private final ObjectProvider<ConfigMigrationAssetService> assetService;
    private final ObjectMapper json;

    /** 导出稳定的实体编码/表单键引用；子表单内容由包内自己的表单资产携带。 */
    Map<String, Object> exportReferences(Map<String, Object> snapshot) {
        List<Map<String, Object>> dependencies = new ArrayList<>(maps(snapshot.get("dependencies")));
        Map<String, Object> result = rewrite(snapshot, (type, value) -> value, config -> {
            if (config.containsKey("childFormReleaseRef")) throw new IllegalArgumentException("旧固定子表单包必须重新导出");
            String formId = first(config, FORM_IDS);
            if (formId.isBlank()) return config;
            EntityForm form = formMapper.selectById(formId);
            if (form == null) throw new IllegalArgumentException("源子表单不存在: " + formId);
            var entity = entityMapper.selectById(form.getEntityId());
            if (entity == null) throw new IllegalArgumentException("源子表单所属实体不存在: " + formId);
            clearIds(config);
            config.put("childFormRef", "wf-form://" + entity.getEntityCode() + "/" + form.getFormKey());
            dependencies.add(Map.of("type", "ENTITY", "key", entity.getEntityCode(), "required", true,
                    "source", "子表单当前配置依赖"));
            return config;
        });
        result.put("dependencies", ConfigMigrationAssignmentSupport.mergeDependencies(dependencies));
        return result;
    }

    /** 新包只接受稳定身份；目标未包含该表单时必须已存在，不能暗中恢复历史版本。 */
    void validate(Map<String, Object> snapshot, Set<String> packageForms, BiFunction<String, String, String> mapping) {
        rewrite(snapshot, (type, value) -> value, config -> {
            requirePortable(config);
            String ref = text(config.get("childFormRef"));
            if (ref.isBlank()) return config;
            coordinate(ref);
            if (!packageForms.contains(ref) && targetForm(ref, mapping) == null)
                throw new IllegalArgumentException("子表单依赖不存在，请把对应实体表单加入迁移包: " + ref);
            return config;
        });
    }

    /** 所有 UI 宿主已按业务键建立后，把身份引用换成目标本地 formId，无发布版本坐标。 */
    Map<String, Object> materialize(Map<String, Object> document,
            BiFunction<String, String, String> mapping, Function<Map<String, Object>, UiConfigRelease> unusedImporter) {
        return rewrite(document, (type, value) -> value, config -> {
            requirePortable(config);
            String ref = text(config.get("childFormRef"));
            if (ref.isBlank()) return config;
            EntityForm form = targetForm(ref, mapping);
            if (form == null) throw new IllegalArgumentException("目标子表单不存在: " + ref);
            config.remove("childFormRef");
            clearIds(config);
            config.put("childFormId", form.getId());
            return config;
        });
    }

    private EntityForm targetForm(String ref, BiFunction<String, String, String> mapping) {
        String[] coordinate = coordinate(ref);
        var entity = entityMapper.findByEntityCode(mapping.apply("ENTITY", coordinate[0])).orElse(null);
        return entity == null ? null : formMapper.selectByEntityIdAndFormKey(entity.getId(), mapping.apply("FORM", coordinate[1]));
    }

    /** 导入发布先处理子依赖；只遍历平台配置，不依赖包内实体排列顺序。 */
    Set<String> formReferences(Map<String, Object> document) {
        Set<String> result = new LinkedHashSet<>();
        rewrite(document, (type, value) -> value, config -> {
            if (config.containsKey("childFormRef")) result.add(text(config.get("childFormRef")));
            return config;
        });
        return result;
    }

    /** 仅供旧内部调用检查当前配置，永不搜索历史。 */
    UiConfigRelease findMatchingRelease(Map<String, Object> source, BiFunction<String, String, String> mapping) {
        EntityForm form = targetForm(text(source.get("formRef")), mapping);
        return form == null ? null : releaseMapper.findActive("FORM", form.getId());
    }

    /** 忽略本地技术元数据，保留业务内容并规范 JSON 键序；指纹只用于内容复用。 */
    String fingerprint(Map<String, Object> form) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsBytes(canonical(normalizeForm(form)))));
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法计算子表单内容指纹", exception);
        }
    }

    /** 仅在持久化行的已知位置清理技术字段；业务默认值/参数内的 id 和 null 必须参与内容比较。 */
    private Map<String, Object> normalizeForm(Map<String, Object> form) {
        Map<String, Object> normalized = withoutMetadata(form);
        for (String section : List.of("nodes", "fields", "eventBindings", "_inheritedEventBindings", "viewCompositions")) {
            if (!form.containsKey(section)) continue;
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> row : maps(form.get(section))) {
                Map<String, Object> clean = withoutMetadata(row);
                if ("nodes".equals(section) && clean.get("propsDocument") instanceof String document) {
                    try {
                        Map<String, Object> props = object(json.readValue(document, Map.class));
                        // 节点的字段 ID 是由 fieldCode/bindingRef 重建的本地缓存，不是默认值里的业务 ID。
                        if (props.containsKey("fieldCode") || row.containsKey("bindingRef")) props.remove("fieldId");
                        clean.put("propsDocument", props);
                    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                        throw new IllegalArgumentException("子表单节点属性 JSON 无效", exception);
                    }
                }
                rows.add(clean);
            }
            if ("nodes".equals(section)) rows.sort(Comparator.comparing(row -> text(row.get("nodeKey"))));
            normalized.put(section, rows);
        }
        return normalized;
    }

    private Map<String, Object> withoutMetadata(Map<String, Object> value) {
        Map<String, Object> result = object(value);
        METADATA.forEach(result::remove);
        return result;
    }

    private Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            boolean executableStep = map.containsKey("operationSnapshotVersion") && map.containsKey("executableSnapshot");
            map.forEach((key, child) -> {
                String name = text(key);
                if (executableStep && Set.of("extensionRevision", "serviceRevision", "definitionHash", "bindingOwnerId").contains(name)) return;
                if (executableStep && "executableSnapshot".equals(name) && child instanceof String text) {
                    try {
                        Map<String, Object> executable = object(json.readValue(text, Map.class));
                        // 范围由独立接口定义负责校验，目标发布会重建本地 ID；可移植编码和本地 ID
                        // 都不参与表单内容指纹，避免同一配置仅因跨环境表示不同而无法复用。
                        List.of("extensionId", "scopeId", "scopeEntityIds", "scopeEntityIdsDocument", "scopeEntityRefs", "extensionRevision")
                                .forEach(executable::remove);
                        result.put(name, canonical(executable));
                        return;
                    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                        throw new IllegalArgumentException("固定接口执行快照 JSON 无效", exception);
                    }
                }
                if ("childFormReleaseRef".equals(name) && child instanceof Map<?, ?> raw) {
                    Map<String, Object> pinned = object(raw);
                    result.put(name, Map.of("formRef", text(pinned.get("formRef")),
                            "form", canonical(normalizeForm(object(pinned.get("form"))))));
                    return;
                }
                // 只解析配置文档字符串；业务值中恰好形似 JSON 的字符串仍然是字符串。
                if (child instanceof String text && (name.endsWith("Document")
                        || Set.of("componentProps", "viewConfig", "matchConfig", "filterConfig", "availabilityRule").contains(name))
                        && (text.trim().startsWith("{") || text.trim().startsWith("["))) {
                    try {
                        result.put(name, canonical(json.readValue(text, Object.class)));
                        return;
                    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                        throw new IllegalArgumentException("子表单配置 JSON 无效: " + name, exception);
                    }
                }
                result.put(name, canonical(child));
            });
            return result;
        }
        if (value instanceof Collection<?> values) return values.stream().map(this::canonical).toList();
        return value;
    }

    private Map<String, Object> remapPortable(Map<String, Object> form, BiFunction<String, String, String> mapping) {
        return rewrite(form, (type, value) -> reference(type, mapping.apply(type, code(type, value))), config -> {
            if (config.get("formRef") instanceof String ref && ref.startsWith("wf-form://")) {
                String[] parts = coordinate(ref);
                config.put("formRef", "wf-form://" + mapping.apply("ENTITY", parts[0]) + "/" + mapping.apply("FORM", parts[1]));
            }
            if (config.containsKey("formKey")) config.put("formKey", mapping.apply("FORM", text(config.get("formKey"))));
            if (config.containsKey("entityCode")) config.put("entityCode", mapping.apply("ENTITY", text(config.get("entityCode"))));
            return config;
        });
    }

    private void verifyFingerprint(Map<String, Object> pinned) {
        String[] parts = coordinate(text(pinned.get("formRef")));
        if (!parts[0].equals(text(pinned.get("entityCode"))) || !parts[1].equals(text(object(pinned.get("form")).get("formKey")))
                || !text(pinned.get("fingerprint")).equals(fingerprint(object(pinned.get("form"))))) {
            throw new IllegalArgumentException("子表单固定内容或引用坐标不一致: " + pinned.get("formRef"));
        }
    }

    private static void requirePortable(Map<String, Object> config) {
        if (config.containsKey("childFormReleaseRef")) throw new IllegalArgumentException("旧固定子表单包必须重新导出");
        if (!first(config, FORM_IDS).isBlank() || !first(config, RELEASE_IDS).isBlank()) {
            throw new IllegalArgumentException("新包不能包含源子表单 ID，请重新导出");
        }
    }

    private static String first(Map<String, Object> value, List<String> keys) {
        return keys.stream().map(key -> text(value.get(key))).filter(text -> !text.isBlank()).findFirst().orElse("");
    }

    private static void clearIds(Map<String, Object> value) {
        FORM_IDS.forEach(value::remove);
        RELEASE_IDS.forEach(value::remove);
        List.of("childFormReleaseVersion", "refFormReleaseVersion", "publishedFormReleaseVersion").forEach(value::remove);
    }

    static String[] coordinate(String ref) {
        if (!ref.startsWith("wf-form://")) throw new IllegalArgumentException("非法子表单引用: " + ref);
        String[] parts = ref.substring(10).split("/", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) throw new IllegalArgumentException("非法子表单引用: " + ref);
        return parts;
    }
}
