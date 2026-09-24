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

    /**
     * 初始化配置迁移分配支持，保存构造参数供后续方法使用。
     */
    private ConfigMigrationAssignmentSupport() { }

    /** 返回稳定编码或目标编码；上下文用于记录具体节点、用途和配置参数。 */
    @FunctionalInterface
    interface ReferenceMapper {
        /**
         * 生成映射文本，供后续匹配或展示。
         *
         * @param type 类型标识，决定后续映射采用的处理分支
         * @param key 键，后续用于授权校验、关联或幂等去重
         * @param context 执行上下文，向后续映射步骤传递身份、配置或状态
         * @return 处理后的映射文本，供调用方比较或展示
         */
        String map(String type, String key, Map<String, Object> context);
    }

    /**
     * 定点转换 UserTask 的人员属性和已知扩展属性；其他文本、脚本和表达式原样保留。
     * XML 禁止外部实体；没有实际变更时返回原文，避免无意义改变发布内容。
     *
     * @param xml XML，作为 {@code parseXml} 的输入影响后续处理
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @param mapper 持久层映射器，后续用于读取或写入对应业务数据
     * @return 处理后的重写BPMN文本，供调用方比较或展示
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
                        rewriteExtension(name, config, at(context, name), mapper);
                        if (!config.equals(before)) {
                            property.setAttribute("value", write(config));
                            changed = true;
                        }
                    }
                }
            }
            // 知会允许挂在流程或非 UserTask 节点，不能只遍历办理人节点。
            for (String namespace : EXTENSIONS) {
                NodeList properties = document.getElementsByTagNameNS(namespace, "property");
                for (int i = 0; i < properties.getLength(); i++) {
                    Element property = (Element) properties.item(i);
                    String name = property.getAttribute("name");
                    if (!Set.of("ccConfig", "nodeOperationPolicy").contains(name)) continue;
                    org.w3c.dom.Node owner = property.getParentNode();
                    while (owner instanceof Element element && (!BPMN.equals(element.getNamespaceURI())
                            || "extensionElements".equals(element.getLocalName()))) owner = owner.getParentNode();
                    Map<String, Object> context = new LinkedHashMap<>();
                    context.put("processKey", processKey == null ? "" : processKey);
                    context.put("section", "bpmnXml");
                    if (owner instanceof Element element) {
                        context.put("nodeId", element.getAttribute("id"));
                        context.put("nodeName", element.getAttribute("name"));
                    }
                    String original = property.getAttribute("value");
                    Map<String, Object> config = read(original);
                    Map<String, Object> before = read(original);
                    rewriteExtension(name, config, at(context, name), mapper);
                    if (!config.equals(before)) {
                        property.setAttribute("value", write(config));
                        changed = true;
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

    /**
     * 转换节点配置中的完整人员规则；未知业务参数保持原样。
     *
     * @param json JSON，作为 {@code read} 的输入影响后续处理
     * @param context 执行上下文，向后续重写节点配置步骤传递身份、配置或状态
     * @param mapper 持久层映射器，后续用于读取或写入对应业务数据
     * @return 处理后的重写节点配置文本，供调用方比较或展示
     */
    static String rewriteNodeConfig(String json, Map<String, Object> context, ReferenceMapper mapper) {
        if (!StringUtils.hasText(json)) return json;
        Map<String, Object> config = read(json);
        for (String field : List.of("assigneeConfig", "multiInstanceConfig", "ccConfig", "nodeOperationPolicy")) {
            Object nested = config.get(field);
            if (nested instanceof Map<?, ?> || nested instanceof String) {
                Map<String, Object> assignment = nested instanceof String text ? read(text) : object(nested);
                rewriteExtension(field, assignment, at(context, field), mapper);
                config.put(field, nested instanceof String ? write(assignment) : assignment);
            }
        }
        return write(config);
    }

    /**
     * 新发布规则按原始指定类型提取，组成员、岗位任职人等动态结果不属于配置快照。
     *
     * @param config 配置内容，决定后续重写分配的处理规则
     * @param context 执行上下文，向后续重写分配步骤传递身份、配置或状态
     * @param mapper 持久层映射器，后续用于读取或写入对应业务数据
     */
    private static void rewriteAssignment(Map<String, Object> config,
            Map<String, Object> context, ReferenceMapper mapper) {
        // 历史静态人员字段仍会被运行时读取，必须与新候选人字段一起转换。
        for (String key : List.of("multiInstanceUserIds", "multiInstanceUsernames")) field(config, key, "USER_ID", context, mapper);
        for (String key : List.of("multiInstanceGroupIds", "multiInstanceGroupCodes")) field(config, key, "GROUP_ID", context, mapper);
        for (String key : List.of("multiInstanceRoleIds", "multiInstanceRoleCodes")) field(config, key, "ROLE_ID", context, mapper);
        field(config, "multiInstanceUsers", "LEGACY_MIXED", context, mapper);
        for (String name : List.of("ccConfig", "nodeOperationPolicy")) {
            Object nested = config.get(name);
            if (nested instanceof Map<?, ?> || nested instanceof String) {
                Map<String, Object> document = nested instanceof String text ? read(text) : object(nested);
                rewriteExtension(name, document, at(context, name), mapper);
                config.put(name, nested instanceof String ? write(document) : document);
            }
        }

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

    /** 知会与节点固定操作范围同样使用身份引用，包内一律显式声明 ID 语义。 */
    private static void rewriteExtension(String name, Map<String, Object> config,
            Map<String, Object> context, ReferenceMapper mapper) {
        if ("ccConfig".equals(name)) {
            List<Map<String, Object>> rules = new ArrayList<>();
            for (Map<String, Object> rule : maps(config.get("recipientRules"))) {
                String type = text(rule.get("type")).toUpperCase(Locale.ROOT);
                if (Set.of("DEPARTMENT", "DEPT", "ORGANIZATION", "ORG").contains(type)) type = "DEPT";
                if (Set.of("USER", "ROLE", "GROUP", "DEPT").contains(type)) field(rule, "values", type + "_ID", context, mapper);
                rules.add(rule);
            }
            if (config.containsKey("recipientRules")) config.put("recipientRules", rules);
        } else if ("nodeOperationPolicy".equals(name)) {
            Map<String, Object> operations = object(config.get("operations"));
            operations.replaceAll((key, value) -> {
                Map<String, Object> operation = object(value);
                if ("FIXED".equals(operation.get("targetScope"))) field(operation, "targetIds", "USER_ID", at(context, key), mapper);
                return operation;
            });
            if (config.containsKey("operations")) config.put("operations", operations);
        } else {
            rewriteAssignment(config, context, mapper);
        }
    }

    /**
     * 内置解析器的参数是已知契约；自定义解析器参数不按字段名称猜测引用。
     *
     * @param config 配置内容，决定后续重写解析器的处理规则
     * @param context 执行上下文，向后续重写解析器步骤传递身份、配置或状态
     * @param mapper 持久层映射器，后续用于读取或写入对应业务数据
     * @param selection 选择，作为 {@code resolverContext.put} 的输入影响后续处理
     */
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

    /**
     * 处理字段，并将结果传给后续步骤。
     *
     * @param config 配置内容，决定后续字段的处理规则
     * @param field 字段，作为 {@code config.get} 的输入影响后续处理
     * @param type 类型标识，决定后续字段采用的处理分支
     * @param context 执行上下文，向后续字段步骤传递身份、配置或状态
     * @param mapper 持久层映射器，后续用于读取或写入对应业务数据
     */
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

    /**
     * 生成值集合文本，供后续匹配或展示。
     *
     * @param value 待处理值集合的原始输入，结果供调用方继续使用
     * @param type 类型标识，决定后续值集合采用的处理分支
     * @param context 执行上下文，向后续值集合步骤传递身份、配置或状态
     * @param mapper 持久层映射器，后续用于读取或写入对应业务数据
     * @return 处理后的值集合文本，供调用方比较或展示
     */
    private static String values(String value, String type,
            Map<String, Object> context, ReferenceMapper mapper) {
        if (!StringUtils.hasText(value) || value.contains("${") || value.contains("#{")) return value;
        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String key = part.trim();
            if (key.isEmpty()) continue;
            if ("LEGACY_MIXED".equals(type)) {
                result.add(key.startsWith("ROLE_")
                        ? "ROLE_" + mapper.map("ROLE_ID", key.substring(5), context)
                        : mapper.map("USER_ID", key, context));
            } else if ("ROLE_ID".equals(type)) {
                result.add(mapper.map(type, key.startsWith("ROLE_") ? key.substring(5) : key, context));
            } else if ("ROLE_VALUES".equals(type)) {
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
     *
     * @param xml XML，作为 {@code parseXml} 的输入影响后续处理
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

    /**
     * 校验表达式；不满足约束时阻止后续处理。
     *
     * @param expression 表达式，供本方法校验表达式时使用
     */
    private static void validateExpression(String expression) {
        if (expression.contains("${") || expression.contains("#{")) {
            // 只建语法树，不绑定函数/Bean；运行期提供的函数不能因分析上下文为空而被误报。
            new Builder(Builder.Feature.METHOD_INVOCATIONS, Builder.Feature.VARARGS).build(expression);
        }
    }

    /**
     * 按依赖类型和编码合并为一项，与依赖表唯一约束保持一致。
     * 来源说明不同不代表不同依赖；保留所有引用位置，避免只校验最后一个节点。
     *
     * @param dependencies 原始依赖，可为空；缺少类型或编码的项按持久层约定忽略
     * @return 独立的合并结果，供快照、计数和入库共同使用；不修改输入
     */
    static List<Map<String, Object>> mergeDependencies(List<Map<String, Object>> dependencies) {
        Map<List<String>, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> source : dependencies == null ? List.<Map<String, Object>>of() : dependencies) {
            if (source == null) continue;
            String type = text(source.get("type"));
            String key = text(source.get("key"));
            if (type.isBlank() || key.isBlank()) continue;
            Map<String, Object> dependency = new LinkedHashMap<>(source);
            dependency.put("type", type);
            dependency.put("key", key);
            // 用二元键避免编码中含冒号时与其他类型/编码组合碰撞。
            List<String> id = List.of(type, key);
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
            Map<String, Object> merged = new LinkedHashMap<>(existing);
            merged.putAll(dependency);
            if (!references.isEmpty()) merged.put("references", references);
            mergeDescriptions(merged, existing, dependency, "source", "sources");
            mergeDescriptions(merged, existing, dependency, "location", "locations");
            // required 缺省也是硬依赖，不能被另一个位置上的可选引用降级。
            if (!Boolean.FALSE.equals(existing.get("required")) || !Boolean.FALSE.equals(dependency.get("required"))) {
                merged.put("required", true);
            }
            result.put(id, merged);
        }
        return new ArrayList<>(result.values());
    }

    /** 多个来源保存在文档数组中；展示字段仍取最后一项，避免拼接超出数据库列长度。 */
    private static void mergeDescriptions(Map<String, Object> merged, Map<String, Object> previous,
            Map<String, Object> incoming, String scalar, String plural) {
        Set<String> values = new LinkedHashSet<>();
        for (Map<String, Object> source : List.of(previous, incoming)) {
            if (source.get(plural) instanceof Collection<?> descriptions) {
                descriptions.stream().map(ConfigMigrationAssignmentSupport::text)
                        .filter(value -> !value.isBlank()).forEach(values::add);
            }
            String value = text(source.get(scalar));
            if (!value.isBlank()) values.add(value);
        }
        if (values.size() > 1 || previous.containsKey(plural) || incoming.containsKey(plural)) {
            merged.put(plural, new ArrayList<>(values));
        }
    }

    /**
     * 整理对象数据，供调用方遍历或继续处理。
     *
     * @param value 待处理对象的原始输入，结果供调用方继续使用
     * @return 对象键值结果，供调用方继续处理
     */
    static Map<String, Object> object(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) map.forEach((key, child) -> result.put(String.valueOf(key), child));
        return result;
    }

    /**
     * 整理{@code maps}数据，供调用方遍历或继续处理。
     *
     * @param value 待处理{@code maps}的原始输入，结果供调用方继续使用
     * @return 配置迁移分配支持集合，供调用方遍历或展示
     */
    static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().filter(Map.class::isInstance).map(ConfigMigrationAssignmentSupport::object).toList();
    }

    /**
     * 读取配置迁移分配支持；查询结果供调用方展示或继续处理。
     *
     * @param json JSON，作为 {@code JSON.readValue} 的输入影响后续处理
     * @return 配置迁移分配支持键值结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    static Map<String, Object> read(String json) {
        if (!StringUtils.hasText(json)) return new LinkedHashMap<>();
        try { return JSON.readValue(json, new TypeReference<>() { }); }
        catch (Exception exception) { throw new IllegalArgumentException("人员规则 JSON 无效", exception); }
    }

    /**
     * 写入配置迁移分配支持；后续读取或执行将使用更新后的状态。
     *
     * @param value 待写入配置迁移分配支持的原始输入，结果供调用方继续使用
     * @return 写入后的配置迁移分配支持文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    static String write(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalArgumentException("人员规则 JSON 序列化失败", exception); }
    }

    /**
     * 整理时间数据，供调用方遍历或继续处理。
     *
     * @param context 执行上下文，向后续时间步骤传递身份、配置或状态
     * @param field 字段，供本方法处理时间时使用
     * @return 时间键值结果，供调用方继续处理
     */
    private static Map<String, Object> at(Map<String, Object> context, String field) {
        Map<String, Object> result = new LinkedHashMap<>(context);
        String prefix = text(context.get("location"));
        result.put("location", prefix.isEmpty() ? field : prefix + "." + field);
        return result;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    /**
     * 解析XML；输出作为后续校验或处理的输入。
     *
     * @param xml XML，作为 {@code factory.setFeature} 的输入影响后续处理
     * @return 解析后的XML结果，供调用方继续处理
     * @throws Exception 下游操作失败时向调用方传递
     */
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
