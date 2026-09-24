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
 * 子表单通过实体编码/表单键和可移植内容指纹定位固定版本。
 * 源版本号与源 ID 均不参与目标匹配；完整固定内容随包携带，避免改绑目标最新版本。
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

    /** 递归收集固定内容及依赖，检测循环和过深嵌套；不修改源发布历史。 */
    Map<String, Object> exportReferences(Map<String, Object> snapshot) {
        List<Map<String, Object>> dependencies = new ArrayList<>(maps(snapshot.get("dependencies")));
        Map<String, Object> result = exportDocument(snapshot, new LinkedHashSet<>(), dependencies);
        result.put("dependencies", ConfigMigrationAssignmentSupport.mergeDependencies(dependencies));
        return result;
    }

    private Map<String, Object> exportDocument(Map<String, Object> document, Set<String> path,
            List<Map<String, Object>> dependencies) {
        return rewrite(document, (type, value) -> value, config -> {
            if (config.containsKey("childFormReleaseRef")) return config;
            String releaseId = first(config, RELEASE_IDS);
            String formId = first(config, FORM_IDS);
            if (releaseId.isBlank() && formId.isBlank()) return config;
            if (releaseId.isBlank()) throw new IllegalArgumentException("子表单缺少固定发布版本: " + formId);
            if (path.size() >= 8 || !path.add(releaseId)) throw new IllegalArgumentException("子表单固定版本引用循环或超过 8 层");
            try {
                UiConfigRelease release = releaseMapper.selectById(releaseId);
                if (release == null || !"FORM".equals(release.getConfigType())) throw new IllegalArgumentException("源子表单发布版本不存在: " + releaseId);
                if (!formId.isBlank() && !formId.equals(release.getConfigId())) throw new IllegalArgumentException("源子表单与发布版本归属不一致");
                EntityForm form = formMapper.selectById(release.getConfigId());
                if (form == null) throw new IllegalArgumentException("源子表单不存在: " + release.getConfigId());
                Map<String, Object> pinned = assetService.getObject().pinnedFormSnapshot(form, release);
                pinned = references.exportReferences(pinned);
                pinned = exportDocument(pinned, path, dependencies);
                String entityCode = text(pinned.get("entityCode"));
                dependencies.addAll(maps(pinned.remove("dependencies")));
                dependencies.add(Map.of("type", "ENTITY", "key", entityCode,
                        "required", true, "source", "固定子表单所属实体"));
                pinned.put("formRef", "wf-form://" + entityCode + "/" + form.getFormKey());
                pinned.put("fingerprint", fingerprint(object(pinned.get("form"))));
                clearIds(config);
                config.put("childFormReleaseRef", pinned);
                return config;
            } finally {
                path.remove(releaseId);
            }
        });
    }

    /** 分析阶段校验固定快照自身及所属实体，基础目录由同一个身份解析器验证。 */
    void validate(Map<String, Object> snapshot, Set<String> packageForms,
            BiFunction<String, String, String> mapping) {
        validate(snapshot, packageForms, mapping, 0);
    }

    private void validate(Map<String, Object> snapshot, Set<String> packageForms,
            BiFunction<String, String, String> mapping, int depth) {
        if (depth > 8) throw new IllegalArgumentException("子表单固定版本引用超过 8 层");
        rewrite(snapshot, (type, value) -> value, config -> {
            requirePortable(config);
            if (config.get("childFormReleaseRef") instanceof Map<?, ?> raw) {
                Map<String, Object> pinned = object(raw);
                verifyFingerprint(pinned);
                String[] coordinate = coordinate(text(pinned.get("formRef")));
                if (!packageForms.contains(text(pinned.get("formRef"))) && findMatchingRelease(pinned, mapping) == null) {
                    throw new IllegalArgumentException("固定子表单目标版本不存在，包内需包含该表单以恢复当前配置: " + coordinate[0]);
                }
                references.importReferences(pinned, mapping);
                validate(object(pinned.get("form")), packageForms, mapping, depth + 1);
            }
            return config;
        });
    }

    /** 落库前把固定引用解析为目标本地表单与版本 ID，缺失内容由同事务内导入器补齐。 */
    Map<String, Object> materialize(Map<String, Object> document,
            BiFunction<String, String, String> mapping, Function<Map<String, Object>, UiConfigRelease> importer) {
        return rewrite(document, (type, value) -> value, config -> {
            requirePortable(config);
            if (!(config.get("childFormReleaseRef") instanceof Map<?, ?> raw)) return config;
            Map<String, Object> pinned = object(raw);
            verifyFingerprint(pinned);
            UiConfigRelease release = findMatchingRelease(pinned, mapping);
            if (release == null) release = importer.apply(pinned);
            if (release == null) throw new IllegalStateException("子表单固定版本导入后未生成发布记录");
            config.remove("childFormReleaseRef");
            config.put("childFormId", release.getConfigId());
            config.put("childFormReleaseId", release.getId());
            config.put("childFormReleaseVersion", release.getVersion());
            return config;
        });
    }

    /** 内容相同的历史或活跃版本均可复用，版本号不同不影响匹配。 */
    UiConfigRelease findMatchingRelease(Map<String, Object> pinned, BiFunction<String, String, String> mapping) {
        String[] coordinate = coordinate(text(pinned.get("formRef")));
        var entity = entityMapper.findByEntityCode(mapping.apply("ENTITY", coordinate[0])).orElse(null);
        if (entity == null) return null;
        EntityForm form = formMapper.selectByEntityIdAndFormKey(entity.getId(), mapping.apply("FORM", coordinate[1]));
        if (form == null) return null;
        // 先把源身份编码映射成目标编码再比较，禁止用源 ID 或同版本号判定相同内容。
        Map<String, Object> expected = remapPortable(object(pinned.get("form")), mapping);
        String expectedHash = fingerprint(expected);
        for (UiConfigRelease release : releaseMapper.findReleases("FORM", form.getId())) {
            Map<String, Object> candidate = assetService.getObject().pinnedFormSnapshot(form, release);
            candidate = exportReferences(references.exportReferences(candidate));
            if (expectedHash.equals(fingerprint(object(candidate.get("form"))))) return release;
        }
        return null;
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
                        // 哈希与归属 ID 在目标发布时重建；实际接口实现、策略、参数和制品身份必须保持一致。
                        List.of("extensionId", "scopeId", "extensionRevision").forEach(executable::remove);
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
