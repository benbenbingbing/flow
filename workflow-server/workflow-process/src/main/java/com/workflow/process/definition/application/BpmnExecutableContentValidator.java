package com.workflow.process.definition.application;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Set;

/**
 * Fail-closed validator for Flowable executable extension points.
 */
final class BpmnExecutableContentValidator {

    private static final Set<String> ALLOWED_DELEGATE_EXPRESSIONS = Set.of(
            "${ccNotificationDelegate}",
            "${configuredDmnTaskDelegate}",
            "${configuredSendTaskDelegate}",
            "${receiveTaskTimeoutDelegate}",
            "${restServiceTaskDelegate}",
            "${sequenceFlowExecutionListener}");

    private BpmnExecutableContentValidator() {
    }

    static void validate(String bpmnXml) {
        try {
            Document document = parse(bpmnXml);
            NodeList elements = document.getElementsByTagName("*");
            for (int index = 0; index < elements.getLength(); index++) {
                validateElement((Element) elements.item(index));
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "BPMN_EXECUTABLE_SURFACE_INVALID: 无法验证流程执行边界",
                    exception);
        }
    }

    private static void validateElement(Element element) {
        for (int index = 0; index < element.getAttributes().getLength(); index++) {
            Node attribute = element.getAttributes().item(index);
            String localName = attribute.getLocalName() == null
                    ? attribute.getNodeName()
                    : attribute.getLocalName();
            if ("class".equals(localName) || "expression".equals(localName)) {
                throw rejected(element, localName);
            }
            if ("delegateExpression".equals(localName)
                    && !ALLOWED_DELEGATE_EXPRESSIONS.contains(attribute.getNodeValue())) {
                throw rejected(element, localName);
            }
        }
        if ("conditionExpression".equals(element.getLocalName())) {
            validateDataExpression(element.getTextContent(), element);
        }
    }

    private static void validateDataExpression(String expression, Element element) {
        if (expression == null || !expression.startsWith("${")
                || !expression.endsWith("}") || expression.length() > 1002) {
            throw rejected(element, "conditionExpression");
        }
        String body = stripQuotedLiterals(expression.substring(2, expression.length() - 1));
        String withoutDecimals = body.replaceAll("(?<=\\d)\\.(?=\\d)", "");
        if (withoutDecimals.matches(".*[.\\[\\]{};:@?#\\\\].*")
                || withoutDecimals.matches(".*\\b[A-Za-z_][A-Za-z0-9_]*\\s*\\(.*")
                || !withoutDecimals.matches("[A-Za-z0-9_\\s=!<>&|()+\\-*/%,]*")) {
            throw rejected(element, "conditionExpression");
        }
    }

    private static String stripQuotedLiterals(String value) {
        StringBuilder result = new StringBuilder(value.length());
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                result.append(' ');
            } else if (current == '\'' || current == '"') {
                quote = current;
                result.append(' ');
            } else {
                result.append(current);
            }
        }
        if (quote != 0) {
            throw new IllegalArgumentException(
                    "BPMN_EXECUTABLE_SURFACE_REJECTED: 条件表达式字符串未闭合");
        }
        return result.toString();
    }

    private static Document parse(String bpmnXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(bpmnXml)));
    }

    private static IllegalArgumentException rejected(Element element, String feature) {
        return new IllegalArgumentException(
                "BPMN_EXECUTABLE_SURFACE_REJECTED: 禁止发布可执行扩展 "
                        + feature + ", element=" + element.getAttribute("id"));
    }
}
