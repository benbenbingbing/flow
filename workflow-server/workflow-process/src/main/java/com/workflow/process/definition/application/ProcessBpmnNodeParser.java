package com.workflow.process.definition.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parses the DOM-level node metadata stored in BPMN documents.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessBpmnNodeParser {

    private final ObjectMapper objectMapper;

    /**
     * 解析文档；输出作为后续校验或处理的输入。
     *
     * @param bpmnXml BPMNXML，供本方法解析文档时使用
     * @return 解析后的文档结果，供调用方继续处理
     * @throws Exception 下游操作失败时向调用方传递
     */
    Document parseDocument(String bpmnXml) throws Exception {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(
                        bpmnXml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 读取扩展属性集合；查询结果供调用方展示或继续处理。
     *
     * @param userTask 用户任务，供本方法读取扩展属性集合时使用
     * @return 扩展属性集合键值结果，供调用方继续处理
     */
    Map<String, String> readExtensionProperties(
            Element userTask) {
        Map<String, String> values = new HashMap<>();
        NodeList extElements = userTask.getElementsByTagNameNS(
                "*",
                "extensionElements");
        for (int j = 0; j < extElements.getLength(); j++) {
            Element extElement = (Element) extElements.item(j);
            NodeList properties = extElement.getElementsByTagNameNS(
                    "*",
                    "properties");
            for (int k = 0; k < properties.getLength(); k++) {
                Element props = (Element) properties.item(k);
                NodeList propList = props.getElementsByTagNameNS(
                        "*",
                        "property");
                for (int m = 0; m < propList.getLength(); m++) {
                    Element property = (Element) propList.item(m);
                    String name = property.getAttribute("name");
                    String value = property.getAttribute("value");
                    if (name != null
                            && !name.isEmpty()
                            && value != null) {
                        values.put(
                                name,
                                decodeXmlAttributeValue(value));
                    }
                }
            }
        }
        return values;
    }

    /**
     * 解析实体表单ID 集合；输出作为后续校验或处理的输入。
     *
     * @param properties 属性集合，作为 {@code parseFormIdList} 的输入影响后续处理
     * @return 流程BPMN节点解析器集合，供调用方遍历或展示
     */
    List<String> resolveEntityFormIds(
            Map<String, String> properties) {
        List<String> formIds =
                parseFormIdList(properties.get("entityFormIds"));
        return formIds.isEmpty()
                ? parseFormIdList(properties.get("entityFormId"))
                : formIds;
    }

    /**
     * 解析表单键；输出作为后续校验或处理的输入。
     *
     * @param userTask 用户任务，供本方法解析表单键时使用
     * @return 解析后的表单键文本，供调用方比较或展示
     */
    String resolveFormKey(Element userTask) {
        String formKey = userTask.getAttributeNS(
                "http://flowable.org/bpmn",
                "formKey");
        if (formKey == null || formKey.isBlank()) {
            formKey = userTask.getAttribute("formKey");
        }
        if (formKey == null || formKey.isBlank()) {
            formKey = userTask.getAttribute("flowable:formKey");
        }
        return decodeXmlAttributeValue(formKey);
    }

    /**
     * 处理已有{@code readonly}，并将结果传给后续步骤。
     *
     * @param bindings 绑定集合，供本方法处理已有{@code readonly}时使用
     * @param formId 表单ID，后续用于处理已有{@code readonly}时定位或关联目标
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的已有{@code readonly}结果，供调用方继续处理
     */
    Integer existingReadonly(
            List<ProcessNodeForm> bindings,
            String formId,
            Integer fallback) {
        if (bindings == null || bindings.isEmpty()) {
            return fallback;
        }
        return bindings.stream()
                .filter(binding ->
                        formId.equals(binding.getFormId()))
                .map(ProcessNodeForm::getIsReadonly)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(fallback);
    }

    /**
     * 解析表单ID列表；输出作为后续校验或处理的输入。
     *
     * @param value 待解析表单ID列表的原始输入，结果供调用方继续使用
     * @return 流程BPMN节点解析器集合，供调用方遍历或展示
     */
    List<String> parseFormIdList(String value) {
        LinkedHashSet<String> formIds = new LinkedHashSet<>();
        String normalized = decodeXmlAttributeValue(value);
        if (normalized == null || normalized.isBlank()) {
            return new ArrayList<>();
        }
        if (normalized.startsWith("[")
                && normalized.endsWith("]")) {
            try {
                JsonNode node = objectMapper.readTree(normalized);
                if (node.isArray()) {
                    node.forEach(item -> {
                        if (item.isTextual()
                                && !item.asText().isBlank()) {
                            formIds.add(item.asText().trim());
                        }
                    });
                }
            } catch (Exception exception) {
                log.warn(
                        "解析 entityFormIds 失败，按列表处理: {}",
                        exception.getMessage());
            }
        }
        if (formIds.isEmpty()) {
            for (String part : normalized.split(",")) {
                String formId = part.trim();
                if (!formId.isEmpty()) {
                    formIds.add(formId);
                }
            }
        }
        return new ArrayList<>(formIds);
    }

    /**
     * 判断是否{@code truthy}；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否{@code truthy}的原始输入，结果供调用方继续使用
     * @return {@code truthy}条件成立时为 true，否则为 false
     */
    boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized);
    }

    /**
     * 解码XML属性值；输出作为后续校验或处理的输入。
     *
     * @param value 待解码XML属性值的原始输入，结果供调用方继续使用
     * @return 解码后的XML属性值文本，供调用方比较或展示
     */
    String decodeXmlAttributeValue(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&amp;", "&")
                .replace("&#38;", "&")
                .replace("&lt;", "<")
                .replace("&#60;", "<")
                .replace("&gt;", ">")
                .replace("&#62;", ">")
                .replace("&#39;", "'");
    }

    /**
     * 提取节点名称；输出作为后续校验或处理的输入。
     *
     * @param document 文档，供本方法提取节点名称时使用
     * @param nodeId 节点ID，后续用于提取节点名称时定位或关联目标
     * @return 提取后的节点名称文本，供调用方比较或展示
     */
    String extractNodeName(
            Document document,
            String nodeId) {
        NodeList elements =
                document.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (nodeId.equals(element.getAttribute("id"))) {
                String name = element.getAttribute("name");
                return name == null || name.isBlank()
                        ? nodeId
                        : name;
            }
        }
        return nodeId;
    }
}
