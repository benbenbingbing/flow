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

    List<String> resolveEntityFormIds(
            Map<String, String> properties) {
        List<String> formIds =
                parseFormIdList(properties.get("entityFormIds"));
        return formIds.isEmpty()
                ? parseFormIdList(properties.get("entityFormId"))
                : formIds;
    }

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

    boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized);
    }

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
