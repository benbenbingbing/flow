package com.workflow.migration.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.migration.spi.ConfigMigrationParameterReferenceProvider;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.workflow.migration.application.ConfigMigrationReferenceSupport.*;
import static com.workflow.migration.application.ConfigMigrationValueReferences.invalid;

/**
 * SQL/表达式只做固定目录 ID 检查，不执行或改写代码。扩展参数按显式 JSON Pointer 契约转换，
 * 引用声明仅存于迁移副本，不进入源业务配置；目标物化后移除声明。
 */
final class ConfigMigrationOpaqueReferences {
    static final String PARAMETER_TYPES = "_migrationParameterReferences";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> PARAMETERS = Set.of("paramsJson", "extraParams", "implementationConfigDocument");
    private static final Set<String> JSON_DOCUMENTS = Set.of("configJson", "componentProps", "matchConfig", "filterConfig",
            "toolbarConfig", "rowActionConfig", "availabilityRule", "viewConfig", "extensionConfig",
            "assigneeConfig", "multiInstanceConfig", "ccConfig", "nodeOperationPolicy", "callConfig");
    private static final Set<String> CODE_FIELDS = Set.of("sql", "customSql", "script", "scriptBody", "expression",
            "conditionExpression", "skipExpression", "visibleExpression", "hiddenExpression", "disabledExpression");
    private static final Set<String> TYPES = Set.of("USER", "ROLE", "GROUP", "DEPT", "VALUE");

    private ConfigMigrationOpaqueReferences() { }

    /**
     * 导出校验固定目录 ID，导入依赖源端检查协议并重新解析所有显式引用。
     * 未经过检查的旧包一旦包含代码或非空扩展参数，要求源端重新导出，避免目标端碰撞 ID 蒙混通过。
     */
    static Map<String, Object> rewrite(Map<String, Object> snapshot, BiFunction<String, String, String> identity,
            List<ConfigMigrationParameterReferenceProvider> providers, Function<String, String> sourceIdentityType,
            boolean exporting) {
        Object protocol = snapshot.get(ConfigMigrationValueReferences.PROTOCOL);
        boolean checked = protocol != null && String.valueOf(ConfigMigrationValueReferences.PROTOCOL_VERSION).equals(text(protocol));
        if (protocol != null && !checked) throw invalid(ConfigMigrationValueReferences.PROTOCOL,
                "不支持的引用协议版本: " + protocol, "请将目标系统更新到支持该发布包的版本后再导入");
        return object(walk(snapshot, "$[" + text(snapshot.get("businessKey")) + "]", identity,
                providers, sourceIdentityType, exporting, checked));
    }

    private static Object walk(Object value, String path, BiFunction<String, String, String> identity,
            List<ConfigMigrationParameterReferenceProvider> providers, Function<String, String> sourceIdentityType,
            boolean exporting, boolean checked) {
        if (value instanceof Collection<?> values) {
            List<Object> result = new ArrayList<>();
            int index = 0;
            for (Object child : values) result.add(walk(child, path + "[" + index++ + "]", identity,
                    providers, sourceIdentityType, exporting, checked));
            return result;
        }
        if (!(value instanceof Map<?, ?>)) return value;
        Map<String, Object> result = object(value);
        Map<String, Object> declarations = object(result.get(PARAMETER_TYPES));
        for (String name : PARAMETERS) {
            if (!result.containsKey(name) || result.get(name) == null || text(result.get(name)).isBlank()) continue;
            Object original = result.get(name);
            Object parameters = decode(original, path + "." + name);
            if (!nonempty(parameters)) continue;
            if (!exporting && !checked) throw legacy(path + "." + name);
            Map<String, Object> types = object(declarations.get(name));
            if (exporting) {
                for (var provider : providers) {
                    Map<String, String> supplied = provider.referenceTypes(Collections.unmodifiableMap(object(result)), name);
                    if (supplied == null) throw invalid(path, "扩展引用适配器返回了 null", "请修正适配器，使不支持的配置返回空 Map");
                    supplied.forEach((pointer, type) -> {
                        Object existing = types.putIfAbsent(pointer, type);
                        if (existing != null && !existing.equals(type)) throw invalid(path + "." + name + pointer,
                                "多个迁移适配器声明的引用类型不一致", "请为该扩展保留一份一致的引用契约后重新导出");
                    });
                }
            }
            validatePointers(parameters, types, path + "." + name);
            Object rewritten = parameters(parameters, "", path + "." + name, name, types, identity, sourceIdentityType, exporting);
            result.put(name, original instanceof String ? encode(rewritten) : rewritten);
            if (!types.isEmpty()) declarations.put(name, types);
        }
        if (exporting && !declarations.isEmpty()) result.put(PARAMETER_TYPES, declarations);
        if (!exporting) result.remove(PARAMETER_TYPES);
        for (var entry : new ArrayList<>(result.entrySet())) {
            String key = entry.getKey();
            Object child = entry.getValue();
            if (PARAMETERS.contains(key) || Set.of(PARAMETER_TYPES, "dependencies", ConfigMigrationValueReferences.FIELD_TYPES).contains(key)) continue;
            String position = path + "." + key;
            if ("bpmnXml".equals(key) && child instanceof String xml) {
                result.put(key, bpmn(xml, position, identity, providers, sourceIdentityType, exporting, checked));
            } else if (child instanceof String document
                    && (JSON_DOCUMENTS.contains(key) || key.endsWith("Document"))
                    && (document.stripLeading().startsWith("{") || document.stripLeading().startsWith("["))) {
                result.put(key, encode(walk(decode(document, position), position, identity, providers, sourceIdentityType, exporting, checked)));
            } else if (child instanceof String code && (CODE_FIELDS.contains(key) || code.contains("${") || code.contains("#{"))) {
                inspectCode(code, position, sourceIdentityType, exporting, checked, "sql".equalsIgnoreCase(key) || "customSql".equals(key));
            } else {
                result.put(key, walk(child, position, identity, providers, sourceIdentityType, exporting, checked));
            }
        }
        return result;
    }

    private static Object parameters(Object value, String pointer, String path, String parameterName,
            Map<String, Object> types, BiFunction<String, String, String> identity,
            Function<String, String> sourceIdentityType, boolean exporting) {
        if (types.containsKey(pointer)) {
            String type = text(types.get(pointer));
            if ("VALUE".equals(type)) return value;
            return ConfigMigrationValueReferences.rewriteValue(value, type, path + pointer, identity, true);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = object(map);
            for (var entry : new ArrayList<>(result.entrySet())) result.put(entry.getKey(), parameters(entry.getValue(),
                    pointer + "/" + escape(entry.getKey()), path, parameterName, types, identity, sourceIdentityType, exporting));
            return result;
        }
        if (value instanceof Collection<?> values) {
            List<Object> result = new ArrayList<>();
            int index = 0;
            for (Object child : values) result.add(parameters(child, pointer + "/" + index++, path, parameterName,
                    types, identity, sourceIdentityType, exporting));
            return result;
        }
        if (value instanceof String text && (text.contains("${") || text.contains("#{"))) {
            inspectCode(text, path + pointer, sourceIdentityType, exporting, true, false);
        } else if (exporting && value != null) {
            String type = sourceIdentityType.apply(text(value));
            if (!type.isBlank()) throw invalid(path + pointer, "扩展参数含未声明的固定 " + type + " ID",
                    "请为该扩展实现 ConfigMigrationParameterReferenceProvider，声明 " + parameterName + " 中的 JSON Pointer "
                            + pointer + " 为 " + type + "；若该值确为普通业务文本，请明确声明为 VALUE；也可改为传递稳定编码并由扩展按编码查询");
        }
        return value;
    }

    /** 校验所有声明都能定位实际参数，禁止拼错路径或覆盖父子位置导致部分引用未物化。 */
    private static void validatePointers(Object value, Map<String, Object> types, String path) {
        var tree = JSON.valueToTree(value);
        for (var entry : types.entrySet()) {
            String pointer = entry.getKey();
            String type = text(entry.getValue());
            if (!TYPES.contains(type)) throw invalid(path + pointer, "不支持的扩展引用类型: " + type,
                    "请声明 USER、ROLE、GROUP、DEPT 或 VALUE；业务记录应改用稳定业务编码");
            try {
                if (tree.at(pointer).isMissingNode()) throw new IllegalArgumentException("missing");
            } catch (IllegalArgumentException exception) {
                throw invalid(path + pointer, "迁移适配器声明的参数路径不存在或不是合法 JSON Pointer",
                        "请根据当前参数结构修正路径，数组使用具体下标，特殊字符按 JSON Pointer 转义");
            }
            for (String other : types.keySet()) if (!pointer.equals(other) && other.startsWith(pointer + "/"))
                throw invalid(path + pointer, "引用声明存在父子路径重叠", "请只声明实际存放 ID 的叶子或 ID 数组位置");
        }
    }

    private static String bpmn(String xml, String path, BiFunction<String, String, String> identity,
            List<ConfigMigrationParameterReferenceProvider> providers, Function<String, String> sourceIdentityType,
            boolean exporting, boolean checked) {
        return ConfigMigrationBpmnReferences.transform(xml, element -> {
            String position = path + "[" + ownerId(element) + "]." + element.getLocalName();
            if (ConfigMigrationBpmnReferences.BPMN.equals(element.getNamespaceURI())
                    && Set.of("conditionExpression", "script").contains(element.getLocalName())) {
                inspectCode(element.getTextContent(), position, sourceIdentityType, exporting, checked, false);
            }
            boolean changed = false;
            if (ConfigMigrationBpmnReferences.EXTENSIONS.contains(element.getNamespaceURI()) && "property".equals(element.getLocalName())) {
                String name = element.getAttribute("name");
                String before = element.getAttribute("value");
                if (Set.of("assigneeConfig", "multiInstanceConfig", "ccConfig", "nodeOperationPolicy", "callConfig").contains(name)
                        && before.stripLeading().startsWith("{")) {
                    String after = encode(walk(decode(before, position), position + "[" + name + "]",
                            identity, providers, sourceIdentityType, exporting, checked));
                    if (!after.equals(before)) { element.setAttribute("value", after); changed = true; }
                } else if (CODE_FIELDS.contains(name) || before.contains("${") || before.contains("#{")) {
                    inspectCode(before, position + "[" + name + "]", sourceIdentityType, exporting, checked, false);
                }
            }
            for (int index = 0; index < element.getAttributes().getLength(); index++) {
                var attribute = element.getAttributes().item(index);
                if ("value".equals(attribute.getLocalName())) continue;
                String code = attribute.getNodeValue();
                if (code.contains("${") || code.contains("#{")) inspectCode(code, position + "@" + attribute.getNodeName(),
                        sourceIdentityType, exporting, checked, false);
            }
            return changed;
        });
    }

    /** 仅提取字面量进行目录存在性检查；不会用正则替换代码，也不会在目标端按裸 ID 查询。 */
    private static void inspectCode(String code, String path, Function<String, String> sourceIdentityType,
            boolean exporting, boolean checked, boolean sql) {
        if (code.isBlank()) return;
        if (!exporting && !checked) throw legacy(path);
        if (!exporting) return;
        var literals = Pattern.compile("'((?:''|\\\\.|[^'\\\\])*)'|\"((?:\"\"|\\\\.|[^\"\\\\])*)\"|(?<![A-Za-z0-9_])([0-9]{1,32})(?![A-Za-z0-9_])").matcher(code);
        while (literals.find()) {
            String literal = literals.group(1) != null ? literals.group(1).replace("''", "'")
                    : literals.group(2) != null ? literals.group(2).replace("\"\"", "\"") : literals.group(3);
            String type = sourceIdentityType.apply(literal);
            if (type.isBlank()) continue;
            String suggestion = sql
                    ? "当前用户/部门条件请改用 #{userId}、#{deptId} 或 #{orgId} 参数；固定对象请按 username、role_code、group_code、org_code 查询目录 ID，避免写死 ID"
                    : "请把固定 ID 提取为声明了引用类型的扩展参数，或改用按用户登录名/部门等稳定编码查询的解析器；表达式仅引用运行时参数";
            throw invalid(path, "代码含固定 " + type + " ID，不能安全自动替换", suggestion);
        }
    }

    private static String ownerId(Element element) {
        for (org.w3c.dom.Node node = element; node instanceof Element owner; node = node.getParentNode())
            if (owner.hasAttribute("id")) return owner.getAttribute("id");
        return "process";
    }

    private static IllegalArgumentException legacy(String path) {
        return invalid(path, "旧包的代码或扩展参数未经过跨系统引用检查",
                "请在更新后的源系统重新导出；固定 ID 应改为编码查询或由迁移适配器声明参数引用类型，再导入目标系统");
    }

    private static boolean nonempty(Object value) {
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        if (value instanceof Collection<?> values) return !values.isEmpty();
        return value != null && !text(value).isBlank();
    }

    private static Object decode(Object value, String path) {
        if (!(value instanceof String text)) return value;
        try { return JSON.readValue(text, Object.class); }
        catch (JsonProcessingException exception) {
            throw invalid(path, "参数或配置 JSON 无效", "请在源系统修正 JSON 后重新导出");
        }
    }

    private static String encode(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("迁移参数 JSON 序列化失败", exception); }
    }

    private static String escape(String key) { return key.replace("~", "~0").replace("/", "~1"); }
}
