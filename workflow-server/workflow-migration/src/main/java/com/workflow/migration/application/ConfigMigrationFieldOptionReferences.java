package com.workflow.migration.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.workflow.migration.application.ConfigMigrationAssignmentSupport.maps;
import static com.workflow.migration.application.ConfigMigrationReferenceSupport.object;
import static com.workflow.migration.application.ConfigMigrationReferenceSupport.text;
import static com.workflow.migration.application.ConfigMigrationValueReferences.invalid;

/** 实体字段候选接口按接口编码和实现版本迁移，两者共同定位字段实际选中的接口。 */
final class ConfigMigrationFieldOptionReferences {
    static final String KEY = "optionsInterfaceRef";
    static final String LOCAL_ID = "optionsInterfaceId";
    static final String FORM_BINDING_VERSION = "_migrationFieldOptionsVersion";
    private static final String SUGGESTION = "请在更新后的源端确认字段绑定的候选接口，重新发布实体并导出";
    private static final ObjectMapper JSON = new ObjectMapper();

    private ConfigMigrationFieldOptionReferences() {}

    /**
     * 校验实际选中分区内的字段引用。旧包若仍携带源 ID，在任何目标接口查找、字段写入前拦截。
     * 接口定义随字段迁移，预检只能使用同一快照中与引用版本一致的定义。
     */
    static void validate(Map<String, Object> snapshot) {
        if (!ConfigMigrationAssetService.ENTITY.equals(snapshot.get("assetType"))) return;
        List<Map<String, Object>> interfaces = maps(snapshot.get("interfaceExtensions"));
        for (Map<String, Object> field : maps(snapshot.get("fields"))) {
            String path = "$[" + text(snapshot.get("businessKey")) + "].fields[" + text(field.get("fieldCode")) + "]";
            if (!text(field.get(LOCAL_ID)).isBlank()) {
                throw invalid(path + "." + LOCAL_ID, "旧快照包含源环境候选接口 ID，无法安全迁移", SUGGESTION);
            }
            if (!field.containsKey(KEY)) continue;
            Ref reference = read(field);
            if (reference == null) {
                throw invalid(path + "." + KEY, "候选接口引用缺少有效的接口编码或版本", SUGGESTION);
            }
            List<Map<String, Object>> matches = interfaces.stream().filter(reference::matches).toList();
            if (matches.size() != 1) {
                throw invalid(path + "." + KEY, "包内必须包含唯一的候选接口定义 " + reference.code() + "@" + reference.version(), SUGGESTION);
            }
            validateDefinition(matches.get(0), text(snapshot.get("businessKey")), path + "." + KEY);
        }
    }

    /** 校验随包定义的选项契约，避免分析通过后才在目标字段发布时发现接口类型或作用范围无效。 */
    private static void validateDefinition(Map<String, Object> definition, String entityCode, String path) {
        Object outputSchema = definition.get("outputSchemaDocument");
        Map<String, Object> schema;
        try {
            schema = object(outputSchema instanceof String json ? JSON.readValue(json, Object.class) : outputSchema);
        } catch (JsonProcessingException exception) {
            throw invalid(path, "候选接口输出 Schema 无效", SUGGESTION);
        }
        boolean coversEntity = "GLOBAL".equals(definition.get("scopeType"))
                || "ENTITY".equals(definition.get("scopeType"))
                        && definition.get("scopeEntityRefs") instanceof List<?> codes && codes.contains(entityCode);
        if (!"ACTIVE".equals(definition.get("status")) || !"READ".equals(definition.get("interfaceKind"))
                || !"FORM".equals(definition.get("interfaceContextType"))
                || !"REGISTERED_PROVIDER".equals(definition.get("implementationType"))
                || text(definition.get("providerCode")).isBlank() || !"array".equalsIgnoreCase(text(schema.get("type")))
                || !coversEntity) {
            throw invalid(path, "候选接口必须是覆盖该实体的已启用 FORM 只读 Provider，且输出为数组", SUGGESTION);
        }
    }

    /** 读取字段的可移植坐标。裁剪快照时忽略无效坐标，由随后正文校验报告具体字段。 */
    static Ref read(Map<String, Object> field) {
        Map<String, Object> value = object(field.get(KEY));
        if (!(value.get("extensionKey") instanceof String code) || code.isBlank() || !code.equals(code.strip())) {
            return null;
        }
        try {
            return new Ref(code, ConfigMigrationPackageValidation.positiveVersion(value.get("version"), KEY + ".version"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    record Ref(String code, int version) {
        boolean matches(Map<String, Object> definition) {
            return Objects.equals(code, definition.get("extensionKey"))
                    && definition.get("version") instanceof Number number
                    && Integer.toString(version).equals(number.toString());
        }
    }
}
