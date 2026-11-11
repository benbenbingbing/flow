package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

/** BPMN 迁移只修改有明确契约的位置，节点坐标、脚本及说明文字不能参与全文替换。 */
final class ConfigMigrationBpmnReferences {
    private static final ObjectMapper JSON = new ObjectMapper();
    static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    static final Set<String> EXTENSIONS = Set.of("http://flowable.org/bpmn", "http://camunda.org/schema/1.0/bpmn");

    private ConfigMigrationBpmnReferences() { }

    /**
     * 转换平台实体表单绑定（单值、逗号列表、JSON 数组以及历史 formKey）。
     * mapper 必须按完整 ID/编码引用解析，不允许替换子串；没有实际变更时保留原始 XML。
     */
    static String rewriteForms(String xml, UnaryOperator<String> mapper) {
        return transform(xml, element -> {
            boolean changed = false;
            if (EXTENSIONS.contains(element.getNamespaceURI())) {
                if ("property".equals(element.getLocalName())
                        && Set.of("entityFormId", "entityFormIds").contains(element.getAttribute("name"))) {
                    String before = element.getAttribute("value");
                    String after = rewriteFormValue(before, mapper);
                    if (!before.equals(after)) { element.setAttribute("value", after); changed = true; }
                } else if (Set.of("entityFormId", "entityFormIds").contains(element.getLocalName())) {
                    String before = element.getTextContent();
                    String after = rewriteFormValue(before, mapper);
                    if (!before.equals(after)) { element.setTextContent(after); changed = true; }
                }
            }
            if (BPMN.equals(element.getNamespaceURI()) && "userTask".equals(element.getLocalName())) {
                for (String namespace : EXTENSIONS) {
                    var attribute = element.getAttributeNodeNS(namespace, "formKey");
                    if (attribute == null) continue;
                    String after = rewriteFormValue(attribute.getValue(), mapper);
                    if (!attribute.getValue().equals(after)) { attribute.setValue(after); changed = true; }
                }
                if (element.hasAttribute("formKey")) {
                    String before = element.getAttribute("formKey");
                    String after = rewriteFormValue(before, mapper);
                    if (!before.equals(after)) { element.setAttribute("formKey", after); changed = true; }
                }
            }
            return changed;
        });
    }

    /** 从正文重新收集所有表单引用，避免主表单清单遗漏多表单绑定或旧包夹带裸 ID。 */
    static Set<String> formReferences(String xml) {
        Set<String> references = new LinkedHashSet<>();
        rewriteForms(xml, value -> {
            if (!value.matches("wf-form://[^/]+/[^/]+") || value.startsWith("wf-form://missing/"))
                throw ConfigMigrationValueReferences.invalid("bpmnXml.entityFormId/formKey",
                        "流程表单绑定缺少稳定的实体编码和表单键: " + value,
                        "请在更新后的源流程设计器重新选择实体表单，发布流程后重新导出；不要在目标系统按源表单 ID 绑定");
            references.add(value);
            return value;
        });
        return references;
    }

    private static String rewriteFormValue(String value, UnaryOperator<String> mapper) {
        if (value == null || value.isBlank()) return value;
        try {
            if (value.stripLeading().startsWith("[")) {
                Object parsed = JSON.readValue(value, Object.class);
                if (!(parsed instanceof List<?> values) || values.stream().anyMatch(item -> !(item instanceof String)))
                    throw new IllegalArgumentException("BPMN 表单绑定必须是字符串数组");
                List<String> result = values.stream().map(String.class::cast).map(mapper).toList();
                return values.equals(result) ? value : JSON.writeValueAsString(result);
            }
            String[] values = value.split(",", -1);
            List<String> result = new ArrayList<>();
            boolean changed = false;
            for (String item : values) {
                String mapped = item.isBlank() ? item : mapper.apply(item.trim());
                changed |= !item.trim().equals(mapped);
                result.add(mapped);
            }
            return changed ? String.join(",", result) : value;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("BPMN 表单绑定 JSON 无效", exception);
        }
    }

    @FunctionalInterface
    interface ElementEditor { boolean edit(Element element); }

    /** 安全解析并访问每个 XML 元素；回调返回是否修改，禁止 DTD、外部实体及外部样式表。 */
    static String transform(String xml, ElementEditor editor) {
        if (xml == null || xml.isBlank()) return xml;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            // 使用稳定的元素列表，回调允许改写属性或文本，但不会导致遍历遗漏兄弟元素。
            List<Element> elements = new ArrayList<>();
            collect(document.getDocumentElement(), elements);
            boolean changed = false;
            for (Element element : elements) changed |= editor.edit(element);
            if (!changed) return xml;
            TransformerFactory transformers = TransformerFactory.newInstance();
            transformers.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = transformers.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter result = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(result));
            return result.toString();
        } catch (IllegalArgumentException exception) {
            if (exception instanceof ConfigMigrationValueReferences.ReferenceException) throw exception;
            throw ConfigMigrationValueReferences.invalid("bpmnXml", exception.getMessage(),
                    "请在源流程设计器修正表单绑定或 XML 配置，重新发布后导出");
        } catch (Exception exception) {
            throw ConfigMigrationValueReferences.invalid("bpmnXml", "BPMN 引用转换失败: " + exception.getMessage(),
                    "请在源流程设计器修复 XML，禁止使用 DTD 或外部实体，然后重新发布导出");
        }
    }

    private static void collect(Element element, List<Element> result) {
        result.add(element);
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling())
            if (child instanceof Element nested) collect(nested, result);
    }
}
