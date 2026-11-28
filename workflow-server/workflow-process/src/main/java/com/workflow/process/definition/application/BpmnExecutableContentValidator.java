package com.workflow.process.definition.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fail-closed validator for Flowable executable extension points.
 */
public final class BpmnExecutableContentValidator {

    private static final Pattern SKIP_EXPRESSION_TOKENS = Pattern.compile(
            "(?<![\\w.])var:is(?:Not)?Empty\\('[A-Za-z_]\\w*'\\)"
                    + "|'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"",
            Pattern.DOTALL);

    private static final Pattern LOOP_ELEMENT_VARIABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> TASK_ASSIGNMENT_ATTRIBUTES = Set.of("assignee", "candidateUsers", "candidateGroups");
    private static final Set<String> ASSIGNMENT_SOURCE_TYPES = Set.of(
            "", "user", "candidate", "group", "role", "dept", "leader",
            "resolver", "interface", "node_reference", "nodereference");

    private static final Set<String> ALLOWED_DELEGATE_EXPRESSIONS = Set.of(
            "${ccNotificationDelegate}",
            "${configuredDmnTaskDelegate}",
            "${configuredSendTaskDelegate}",
            "${receiveTaskTimeoutDelegate}",
            "${relativeOrgPositionCollectionHandler}",
            "${restServiceTaskDelegate}",
            "${sequenceFlowExecutionListener}");

    /**
     * 初始化BPMN{@code executable}内容校验器，保存构造参数供后续方法使用。
     */
    private BpmnExecutableContentValidator() {
    }

    /**
     * 校验BPMN{@code executable}内容；不满足约束时阻止后续处理。
     *
     * @param bpmnXml BPMNXML，作为 {@code parse} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 校验元素；不满足约束时阻止后续处理。
     *
     * @param element 元素，作为 {@code rejected} 的输入影响后续处理
     */
    private static void validateElement(Element element) {
        if ("userTask".equals(element.getLocalName())) {
            validateTaskAssignmentAttributes(element);
        }
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
            if ("skipExpression".equals(localName)) {
                validateDataExpression(
                        attribute.getNodeValue(), element,
                        "skipExpression");
            }
        }
        if ("conditionExpression".equals(element.getLocalName())) {
            validateDataExpression(
                    element.getTextContent(), element,
                    "conditionExpression");
        } else if ("skipExpression".equals(element.getLocalName())) {
            validateDataExpression(
                    element.getTextContent(), element,
                    "skipExpression");
        }
    }

    /**
     * 校验原生办理人属性，避免导入或发布的 XML 绕过人员来源配置。
     *
     * @param task 待导入或发布的用户任务元素
     * @throws IllegalArgumentException 办理人属性包含可执行表达式，循环元素绑定除外
     */
    public static void validateTaskAssignmentAttributes(Element task) {
        for (int index = 0; index < task.getAttributes().getLength(); index++) {
            Node attribute = task.getAttributes().item(index);
            String name = attribute.getLocalName() == null ? attribute.getNodeName() : attribute.getLocalName();
            String value = attribute.getNodeValue();
            if (TASK_ASSIGNMENT_ATTRIBUTES.contains(name) && containsExpression(value)
                    && !("assignee".equals(name) && isLoopElementAssignee(task, value))) {
                throw rejected(task, "办理人表达式 " + name);
            }
        }
    }

    /**
     * 校验节点办理人配置，覆盖关闭下一审批人展示的任务和迁移包中的用户任务。
     *
     * @param nodeId 节点 ID，用于定位配置错误
     * @param config 待校验的办理人配置，人员或组字段使用字面量标识
     * @throws IllegalArgumentException 来源类型或人员标识不受支持
     */
    public static void validateAssignmentSource(String nodeId, JsonNode config) {
        String type = config.path("assigneeType").asText("").trim().toLowerCase(Locale.ROOT);
        if (!ASSIGNMENT_SOURCE_TYPES.contains(type)) {
            throw new IllegalArgumentException("ASSIGNEE_SOURCE_INVALID: 不支持的办理人来源 "
                    + type + ", element=" + nodeId);
        }
        for (String field : List.of("assigneeValue", "candidateUsers", "candidateGroups")) {
            if (containsExpression(config.path(field))) {
                throw new IllegalArgumentException("ASSIGNEE_SOURCE_INVALID: 办理人配置应使用字面量标识 "
                        + field + ", element=" + nodeId);
            }
        }
    }

    /** 检查人员标识的文本或数组，数组元素使用相同的字面量约束。 */
    static boolean containsExpression(JsonNode value) {
        if (value == null) {
            return false;
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (containsExpression(item)) {
                    return true;
                }
            }
            return false;
        }
        return containsExpression(value.asText(""));
    }

    static boolean containsExpression(String value) {
        return value != null && (value.contains("${") || value.contains("#{"));
    }

    /**
     * 判断 assignee 是否只绑定当前任务的循环元素变量。Flowable 通过此绑定分配每条会签或顺序任务。
     *
     * @param task 持有循环特征的用户任务
     * @param assignee 任务的原生 assignee 属性
     * @return 属性严格引用当前循环声明的简单变量时返回 true
     */
    static boolean isLoopElementAssignee(Element task, String assignee) {
        if (assignee == null || assignee.isBlank()) {
            return false;
        }
        for (Node child = task.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element loop && "multiInstanceLoopCharacteristics".equals(loop.getLocalName())) {
                String variable = loop.getAttributeNS("http://flowable.org/bpmn", "elementVariable");
                if (variable.isBlank()) {
                    variable = loop.getAttributeNS("http://camunda.org/schema/1.0/bpmn", "elementVariable");
                }
                if (variable.isBlank()) {
                    variable = loop.getAttribute("elementVariable");
                }
                String value = assignee.trim();
                return LOOP_ELEMENT_VARIABLE.matcher(variable).matches()
                        && (value.equals("${" + variable + "}") || value.equals("#{" + variable + "}"));
            }
        }
        return false;
    }

    /**
     * 校验数据表达式；不满足约束时阻止后续处理。
     *
     * @param expression 表达式，供本方法校验数据表达式时使用
     * @param element 元素，作为 {@code rejected} 的输入影响后续处理
     * @param feature {@code feature}，作为 {@code rejected} 的输入影响后续处理
     */
    private static void validateDataExpression(
            String expression,
            Element element,
            String feature) {
        // 只豁免本连线上已声明业务接口配置的固定调用，不能放开普通表达式的方法访问。
        if ("conditionExpression".equals(feature)
                && element.getParentNode() instanceof Element flow
                && "sequenceFlow".equals(flow.getLocalName())
                && isConfiguredInterfaceExpression(expression, flow)) return;
        boolean safe = "skipExpression".equals(feature)
                ? isSafeSkipExpression(expression)
                : isSafeDataExpression(expression);
        if (!safe) {
            throw rejected(element, feature);
        }
    }

    private static boolean isConfiguredInterfaceExpression(String expression, Element flow) {
        try {
            if (!com.workflow.process.condition.application.SequenceFlowConditionConfig
                    .expression(flow.getAttribute("id")).equals(expression)) return false;
            var properties = flow.getElementsByTagNameNS("*", "property");
            for (int i = 0; i < properties.getLength(); i++) {
                if ("sequenceFlowCondition".equals(((Element) properties.item(i)).getAttribute("name"))) return true;
            }
            return false;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 校验顺序流及路由预览用的数据表达式。
     *
     * @param expression 表达式，作为 {@code expression.substring} 的输入影响后续处理
     * @return 安全数据表达式条件成立时为 true，否则为 false
     */
    public static boolean isSafeDataExpression(String expression) {
        return isSafeDataExpression(expression, false);
    }

    /** 校验设计器的跳过表达式。仅跳过表达式允许针对固定变量名的 Flowable 原生空值判断。 */
    static boolean isSafeSkipExpression(String expression) {
        return isSafeDataExpression(expression, true);
    }

    private static boolean isSafeDataExpression(String expression, boolean allowEmptyCheck) {
        if (expression == null || !expression.startsWith("${")
                || !expression.endsWith("}") || expression.length() > 1002) {
            return false;
        }
        String rawBody = expression.substring(
                2, expression.length() - 1);
        if (rawBody.isBlank()) {
            return false;
        }
        String body;
        try {
            // 空值调用返回布尔值。用常量代换精确调用后继续检查组合表达式，保留字符串字面量原样。
            if (allowEmptyCheck) {
                rawBody = SKIP_EXPRESSION_TOKENS.matcher(rawBody).replaceAll(token ->
                        token.group().startsWith("var:") ? "true" : Matcher.quoteReplacement(token.group()));
            }
            body = stripQuotedLiterals(com.workflow.process.condition.application.WorkflowSelectionCondition
                    .substitute(rawBody, null));
        } catch (IllegalArgumentException exception) {
            return false;
        }
        String withoutDecimals = body.replaceAll("(?<=\\d)\\.(?=\\d)", "");
        return !withoutDecimals.contains("->")
                && !hasUnsupportedEquals(withoutDecimals)
                && !withoutDecimals.matches(".*\\)\\s*\\(.*")
                && !withoutDecimals.matches(".*[.\\[\\]{};:@?#\\\\].*")
                && !withoutDecimals.matches(
                        ".*\\b[A-Za-z_][A-Za-z0-9_]*\\s*\\(.*")
                && withoutDecimals.matches(
                        "[A-Za-z0-9_\\s=!<>&|()+\\-*/%]*");
    }

    /**
     * 判断等号是否只作为受支持的二元比较运算符出现。
     *
     * <p>统一 EL 支持赋值和 lambda；简单字符白名单若允许单个 {@code =}，历史导入
     * 表达式便可能在开启 skipExpression 后修改上下文。这里按运算符逐个消费，只接受
     * {@code ==}、{@code !=}、{@code >=} 与 {@code <=} 中的等号，并拒绝三等号等
     * 非结构化条件生成器产物。</p>
     *
     * @param expression 表达式，供本方法判断是否具有{@code unsupported}相等时使用
     * @return {@code unsupported}相等条件成立时为 true，否则为 false
     */
    private static boolean hasUnsupportedEquals(String expression) {
        for (int index = 0; index < expression.length(); index++) {
            char current = expression.charAt(index);
            if ((current == '!' || current == '>' || current == '<')
                    && index + 1 < expression.length()
                    && expression.charAt(index + 1) == '=') {
                index++;
                continue;
            }
            if (current != '=') {
                continue;
            }
            if (index + 1 < expression.length()
                    && expression.charAt(index + 1) == '=') {
                index++;
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * 生成{@code strip}{@code quoted}{@code literals}文本，供后续匹配或展示。
     *
     * @param value 待处理{@code strip}{@code quoted}{@code literals}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code strip}{@code quoted}{@code literals}文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
                    "BPMN_EXECUTABLE_SURFACE_REJECTED: 数据表达式字符串未闭合");
        }
        return result.toString();
    }

    /**
     * 解析BPMN{@code executable}内容；输出作为后续校验或处理的输入。
     *
     * @param bpmnXml BPMNXML，供本方法解析BPMN{@code executable}内容时使用
     * @return 解析后的BPMN{@code executable}内容结果，供调用方继续处理
     * @throws Exception 下游操作失败时向调用方传递
     */
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

    /**
     * 构造已拒绝异常，供调用方区分失败原因。
     *
     * @param element 元素，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param feature {@code feature}，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 处理后的已拒绝结果，供调用方继续处理
     */
    private static IllegalArgumentException rejected(Element element, String feature) {
        return new IllegalArgumentException(
                "BPMN_EXECUTABLE_SURFACE_REJECTED: 禁止发布可执行扩展 "
                        + feature + ", element=" + element.getAttribute("id"));
    }
}
