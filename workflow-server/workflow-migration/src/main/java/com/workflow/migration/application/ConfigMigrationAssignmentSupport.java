package com.workflow.migration.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.flowable.common.engine.impl.de.odysseus.el.tree.impl.Builder;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 人员规则的迁移文档转换。只访问声明字段，不执行表达式、查询成员或调用人员接口。
 * 导出、导入共用遍历规则，确保 BPMN 与节点 JSON 不会只转换其中一份。
 */
final class ConfigMigrationAssignmentSupport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final Set<String> EXTENSIONS = Set.of(
            "http://flowable.org/bpmn", "http://camunda.org/schema/1.0/bpmn");
    static final Set<String> TARGET_TYPES = Set.of("USER", "GROUP", "ROLE", "DEPT",
            "PERSON_RESOLVER", "POSITION", "ORG_BUSINESS_LEVEL", "ENTITY_USER_FIELD");

    private ConfigMigrationAssignmentSupport() { }

    /** 返回稳定编码或目标编码；上下文用于记录具体节点、用途和配置参数。 */
    @FunctionalInterface
    interface ReferenceMapper {
        String map(String type, String key, Map<String, Object> context);
    }

    /**
     * 定点转换 UserTask 的人员属性和已知扩展属性；其他文本、脚本和表达式原样保留。
     * XML 禁止外部实体；没有实际变更时返回原文，避免无意义改变发布内容。
     */
    static String rewriteBpmn(String xml, String processKey, ReferenceMapper mapper) {
        if (!StringUtils.hasText(xml)) return xml;
        try {
            Document document = parseXml(xml);
            boolean changed = false;
            NodeList tasks = document.getElementsByTagNameNS(BPMN, "userTask");
            for (int i = 0; i < tasks.getLength(); i++) {
                Element task = (Element) tasks.item(i);
                Map<String, Object> context = new LinkedHashMap<>();
                context.put("processKey", processKey == null ? "" : processKey);
                context.put("nodeId", task.getAttribute("id"));
                context.put("nodeName", task.getAttribute("name"));
                context.put("section", "bpmnXml");
                context.put("multiInstance", task.getElementsByTagNameNS(
                        BPMN, "multiInstanceLoopCharacteristics").getLength() > 0);
                for (String namespace : EXTENSIONS) {
                    for (String field : List.of("assignee", "candidateUsers", "candidateGroups")) {
                        Attr attribute = task.getAttributeNodeNS(namespace, field);
                        if (attribute == null) continue;
                        String value = values(attribute.getValue(),
                                "candidateGroups".equals(field) ? "GROUP_OR_ROLE" : "USER",
                                at(context, field), mapper);
                        if (!value.equals(attribute.getValue())) {
                            attribute.setValue(value);
                            changed = true;
                        }
                    }
                    NodeList properties = task.getElementsByTagNameNS(namespace, "property");
                    for (int j = 0; j < properties.getLength(); j++) {
                        Element property = (Element) properties.item(j);
                        String name = property.getAttribute("name");
                        if (!Set.of("assigneeConfig", "multiInstanceConfig").contains(name)) continue;
                        String original = property.getAttribute("value");
                        Map<String, Object> config = read(original);
                        Map<String, Object> before = read(original);
                        rewriteAssignment(config, at(context, name), mapper);
                        if (!config.equals(before)) {
                            property.setAttribute("value", write(config));
                            changed = true;
                        }
                    }
                }
            }
            if (!changed) return xml;
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toString();
        } catch (Exception exception) {
            throw new IllegalArgumentException("流程人员迁移文档转换失败: " + exception.getMessage(), exception);
        }
    }

    /** 转换节点配置中的完整人员规则；未知业务参数保持原样。 */
    static String rewriteNodeConfig(String json, Map<String, Object> context, ReferenceMapper mapper) {
        if (!StringUtils.hasText(json)) return json;
        Map<String, Object> config = read(json);
        for (String field : List.of("assigneeConfig", "multiInstanceConfig")) {
            if (config.get(field) instanceof Map<?, ?> nested) {
                Map<String, Object> assignment = object(nested);
                rewriteAssignment(assignment, at(context, field), mapper);
                config.put(field, assignment);
            }
        }
        return write(config);
    }

    /** 新发布规则按原始指定类型提取，组成员、岗位任职人等动态结果不属于配置快照。 */
    private static void rewriteAssignment(Map<String, Object> config,
            Map<String, Object> context, ReferenceMapper mapper) {
        String type = text(config.get("assigneeType")).toLowerCase(Locale.ROOT);
        switch (type) {
            case "user", "candidate" -> field(config, "assigneeValue", "USER", context, mapper);
            case "group" -> field(config, "assigneeValue", "GROUP_OR_ROLE", context, mapper);
            case "role" -> field(config, "assigneeValue", "ROLE_VALUES", context, mapper);
            case "dept" -> field(config, "assigneeValue", "DEPT", context, mapper);
            default -> { /* 表达式、节点引用及动态接口不提前计算人员。 */ }
        }
        if (Set.of("user", "candidate", "group", "role", "").contains(type)) {
            field(config, "candidateUsers", "USER", context, mapper);
            field(config, "candidateGroups", "GROUP_OR_ROLE", context, mapper);
        }
        if ("interface".equals(type)) rewriteResolver(config, context, mapper, false);
        Object selection = config.get("nextApproverSelection");
        if (selection instanceof Map<?, ?> rawSelection) {
            Map<String, Object> selected = object(rawSelection);
            Map<String, Object> source = object(selected.get("source"));
            // 隐藏的配置不会提供人员范围，不应制造目标环境的无效硬依赖。
            if (Boolean.TRUE.equals(selected.get("visible"))) {
                Map<String, Object> selectionContext = at(context, "nextApproverSelection.source");
                if ("RESOLVER".equals(source.get("type"))) {
                    rewriteResolver(source, selectionContext, mapper, true);
                } else if ("SCOPE".equals(source.get("type"))) {
                    List<Map<String, Object>> rules = new ArrayList<>();
                    for (Map<String, Object> rule : maps(source.get("rules"))) {
                        String scopeType = text(rule.get("type"));
                        if ("ORGANIZATION".equals(scopeType)) scopeType = "DEPT";
                        if (Set.of("USER", "GROUP", "ROLE", "DEPT").contains(scopeType)) {
                            field(rule, "values", scopeType, selectionContext, mapper);
                        }
                        rules.add(rule);
                    }
                    source.put("rules", rules);
                }
            }
            selected.put("source", source);
            config.put("nextApproverSelection", selected);
        }
    }

    /** 内置解析器的参数是已知契约；自定义解析器参数不按字段名称猜测引用。 */
    private static void rewriteResolver(Map<String, Object> config,
            Map<String, Object> context, ReferenceMapper mapper, boolean selection) {
        String code = text(config.get("resolverCode"));
        if (code.isEmpty()) code = text(config.get("interfaceName"));
        Map<String, Object> params = object(config.get("extraParams"));
        if ("entityUserReferenceField".equals(code)) {
            String entity = text(params.get("entityCode"));
            String field = text(params.get("fieldCode"));
            String original = entity + "/" + field;
            String coordinate = mapper.map("ENTITY_USER_FIELD", original, context);
            String[] parts = coordinate.split("/", 2);
            if (parts.length == 2) {
                // 显式字段映射已给出完整目标坐标，不能再把目标实体当作来源重复映射。
                params.put("entityCode", coordinate.equals(original) ? mapper.map("ENTITY", parts[0], context) : parts[0]);
                params.put("fieldCode", parts[1]);
            }
        } else if ("relativeOrgPosition".equals(code)) {
            field(params, "positionCode", "POSITION", context, mapper);
            Map<String, Object> hierarchy = object(params.get("hierarchy"));
            if ("BUSINESS_LEVEL".equals(hierarchy.get("mode"))) {
                field(hierarchy, "businessLevelCode", "ORG_BUSINESS_LEVEL", context, mapper);
            }
            params.put("hierarchy", hierarchy);
        }
        config.put("extraParams", params);
        Map<String, Object> resolverContext = new LinkedHashMap<>(context);
        boolean multi = Boolean.TRUE.equals(context.get("multiInstance"));
        String mode = text(config.get("assignmentMode"));
        resolverContext.put("assignmentMode", mode.isEmpty() ? "DIRECT" : mode);
        // 普通节点的 DIRECT/CANDIDATE 都使用 ASSIGNEE 契约；改选候选范围才是 CANDIDATE 用途。
        resolverContext.put("usage", selection ? "CANDIDATE" : multi ? "MULTI_INSTANCE" : "ASSIGNEE");
        resolverContext.put("extraParams", params);
        if (!code.isEmpty()) {
            String mapped = mapper.map("PERSON_RESOLVER", code, resolverContext);
            config.put("resolverCode", mapped);
            if (config.containsKey("interfaceName")) config.put("interfaceName", mapped);
        }
    }

    private static void field(Map<String, Object> config, String field, String type,
            Map<String, Object> context, ReferenceMapper mapper) {
        Object value = config.get(field);
        if (value instanceof Collection<?> collection) {
            config.put(field, collection.stream().map(item ->
                    values(text(item), type, at(context, field), mapper)).toList());
        } else if (value instanceof String string) {
            config.put(field, values(string, type, at(context, field), mapper));
        }
    }

    private static String values(String value, String type,
            Map<String, Object> context, ReferenceMapper mapper) {
        if (!StringUtils.hasText(value) || value.contains("${") || value.contains("#{")) return value;
        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String key = part.trim();
            if (key.isEmpty()) continue;
            if ("ROLE_VALUES".equals(type)) {
                result.add("ROLE_" + mapper.map("ROLE", key.startsWith("ROLE_") ? key.substring(5) : key, context));
            } else if ("GROUP_OR_ROLE".equals(type)) {
                result.add(key.startsWith("ROLE_")
                        ? "ROLE_" + mapper.map("ROLE", key.substring(5), context)
                        : mapper.map("GROUP", key, context));
            } else {
                result.add(mapper.map(type, key, context));
            }
        }
        return String.join(",", result);
    }

    /**
     * 导入时检查同流程节点引用图及表达式语法，不读取任何运行时变量或执行表达式。
     * 固定编码的存在性由依赖清单校验；节点 ID 是包内坐标，不做跨环境映射。
     */
    static void validateBpmn(String xml) {
        if (!StringUtils.hasText(xml)) return;
        try {
            Document document = parseXml(xml);
            Set<String> nodes = new LinkedHashSet<>();
            Map<String, String> references = new LinkedHashMap<>();
            NodeList tasks = document.getElementsByTagNameNS(BPMN, "userTask");
            for (int i = 0; i < tasks.getLength(); i++) {
                Element task = (Element) tasks.item(i);
                String nodeId = task.getAttribute("id");
                nodes.add(nodeId);
                for (String namespace : EXTENSIONS) {
                    for (String name : List.of("assignee", "candidateUsers", "candidateGroups")) {
                        validateExpression(task.getAttributeNS(namespace, name));
                    }
                    NodeList properties = task.getElementsByTagNameNS(namespace, "property");
                    for (int j = 0; j < properties.getLength(); j++) {
                        Element property = (Element) properties.item(j);
                        if (!"assigneeConfig".equals(property.getAttribute("name"))) continue;
                        Map<String, Object> config = read(property.getAttribute("value"));
                        if ("node_reference".equals(config.get("assigneeType"))) {
                            references.put(nodeId, text(config.get("referencedNodeId")));
                        }
                        for (String name : List.of("assigneeValue", "candidateUsers", "candidateGroups")) {
                            validateExpression(text(config.get(name)));
                        }
                    }
                }
            }
            for (String start : references.keySet()) {
                Set<String> visited = new LinkedHashSet<>();
                String current = start;
                while (references.containsKey(current)) {
                    if (!visited.add(current)) throw new IllegalArgumentException("节点 " + start + " 的审批人引用形成循环");
                    if (visited.size() > 16) throw new IllegalArgumentException("节点 " + start + " 的审批人引用超过 16 层");
                    current = references.get(current);
                    if (!nodes.contains(current)) throw new IllegalArgumentException("节点 " + start + " 引用的审批节点不存在: " + current);
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("人员规则结构或表达式无效: " + exception.getMessage(), exception);
        }
    }

    private static void validateExpression(String expression) {
        if (expression.contains("${") || expression.contains("#{")) {
            // 只建语法树，不绑定函数/Bean；运行期提供的函数不能因分析上下文为空而被误报。
            new Builder(Builder.Feature.METHOD_INVOCATIONS, Builder.Feature.VARARGS).build(expression);
        }
    }

    /** 合并同编码的全部引用位置，避免同一解析器不同参数只校验最后一个节点。 */
    static List<Map<String, Object>> mergeDependencies(List<Map<String, Object>> dependencies) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> dependency : dependencies) {
            String id = dependency.get("type") + ":" + dependency.get("key");
            Map<String, Object> existing = result.get(id);
            if (existing == null) {
                result.put(id, new LinkedHashMap<>(dependency));
                continue;
            }
            List<Map<String, Object>> references = new ArrayList<>(maps(existing.get("references")));
            for (Map<String, Object> reference : maps(dependency.get("references"))) {
                if (!references.contains(reference)) references.add(reference);
            }
            // 保留原有的后写覆盖规则：细粒度选择会在末尾添加 targetOnly 所属资产依赖。
            Map<String, Object> merged = new LinkedHashMap<>(dependency);
            if (!references.isEmpty()) merged.put("references", references);
            if (Boolean.TRUE.equals(existing.get("required")) || Boolean.TRUE.equals(dependency.get("required"))) {
                merged.put("required", true);
            }
            result.put(id, merged);
        }
        return new ArrayList<>(result.values());
    }

    static Map<String, Object> object(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) map.forEach((key, child) -> result.put(String.valueOf(key), child));
        return result;
    }

    static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().filter(Map.class::isInstance).map(ConfigMigrationAssignmentSupport::object).toList();
    }

    static Map<String, Object> read(String json) {
        if (!StringUtils.hasText(json)) return new LinkedHashMap<>();
        try { return JSON.readValue(json, new TypeReference<>() { }); }
        catch (Exception exception) { throw new IllegalArgumentException("人员规则 JSON 无效", exception); }
    }

    static String write(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalArgumentException("人员规则 JSON 序列化失败", exception); }
    }

    private static Map<String, Object> at(Map<String, Object> context, String field) {
        Map<String, Object> result = new LinkedHashMap<>(context);
        String prefix = text(context.get("location"));
        result.put("location", prefix.isEmpty() ? field : prefix + "." + field);
        return result;
    }

    static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }
}
