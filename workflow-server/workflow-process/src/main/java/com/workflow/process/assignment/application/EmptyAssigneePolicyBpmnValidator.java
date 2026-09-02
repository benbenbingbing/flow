package com.workflow.process.assignment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.resolver.PersonPrincipal;
import com.workflow.contracts.identity.resolver.PersonPrincipalType;
import com.workflow.process.assignment.domain.AssigneeResolutionResult;
import com.workflow.process.assignment.domain.EmptyAssigneePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 发布边界的空办理人策略校验器，运行时和预检共用同一策略解析器。 */
@Component
@RequiredArgsConstructor
public class EmptyAssigneePolicyBpmnValidator {

    private final ObjectMapper objectMapper;
    private final EmptyAssigneePolicyResolver policyResolver;
    private final AssigneeResolutionService resolutionService;

    /** 校验流程默认策略、每个用户任务的有效策略、兜底身份和静态空结果。 */
    public void validate(String bpmnXml) {
        Document document = parse(bpmnXml);
        Element process = firstElement(document, "process");
        String processDefault = property(process, EmptyAssigneePolicyResolver.PROCESS_PROPERTY);
        // 即使没有显式默认配置，也会解析为安全默认 BLOCK_PUBLISH。
        policyResolver.resolve(processDefault, Map.of());
        NodeList tasks = document.getElementsByTagNameNS("*", "userTask");
        for (int i = 0; i < tasks.getLength(); i++) {
            Element task = (Element) tasks.item(i);
            String nodeId = task.getAttribute("id");
            Map<String, Object> assigneeConfig = readMap(
                    property(task, "assigneeConfig"), nodeId);
            Map<String, Object> multiInstanceConfig = readMap(
                    property(task, "multiInstanceConfig"), nodeId);
            Map<String, Object> effectiveConfig =
                    LegacyMultiInstanceAssignmentParser.mergeConfigs(
                            assigneeConfig, multiInstanceConfig);
            EmptyAssigneePolicy policy;
            try {
                policy = policyResolver.resolve(
                        processDefault, effectiveConfig);
                validateFallbackIdentity(policy, nodeId);
                validateStaticAssignment(
                        task,
                        effectiveConfig,
                        policy,
                        nodeId);
            } catch (RuntimeException error) {
                throw new IllegalArgumentException(
                        "EMPTY_ASSIGNEE_POLICY_INVALID [" + nodeId + "]: "
                                + error.getMessage(), error);
            }
        }
    }

    private void validateFallbackIdentity(
            EmptyAssigneePolicy policy,
            String nodeId) {
        List<PersonPrincipal> principals = switch (policy.strategy()) {
            case FALLBACK_USER -> List.of(PersonPrincipal.user(policy.fallbackUser()));
            case FALLBACK_GROUP -> List.of(new PersonPrincipal(
                    PersonPrincipalType.GROUP, policy.fallbackGroup()));
            default -> List.of();
        };
        if (principals.isEmpty()) {
            return;
        }
        AssigneeResolutionResult result = resolutionService.resolvePrincipals(
                principals, "FALLBACK_INVALID");
        if (!result.resolved()) {
            throw new IllegalArgumentException(
                    nodeId + " 的兜底用户或用户组无有效成员：" + result.reasonMessage());
        }
    }

    private void validateStaticAssignment(
            Element task,
            Map<String, Object> config,
            EmptyAssigneePolicy policy,
            String nodeId) {
        if (policy.strategy() != EmptyAssigneePolicy.Strategy.BLOCK_PUBLISH) {
            return;
        }
        boolean multiInstance = task.getElementsByTagNameNS(
                "*", "multiInstanceLoopCharacteristics")
                .getLength() > 0;
        LegacyMultiInstanceAssignmentParser.LegacyAssignment legacy =
                LegacyMultiInstanceAssignmentParser.parse(config);
        if (LegacyMultiInstanceAssignmentParser
                .usesLegacyMultiInstanceAssignment(
                        config, multiInstance)) {
            if (legacy.resolver()) {
                // 动态解析器的目录、用途及参数由发布 sanitizer 校验。
                return;
            }
            List<PersonPrincipal> principals = new ArrayList<>();
            legacy.userKeys().forEach(value ->
                    principals.add(PersonPrincipal.user(value)));
            legacy.groupKeys().forEach(value ->
                    principals.add(new PersonPrincipal(
                            PersonPrincipalType.GROUP, value)));
            legacy.roleKeys().forEach(value ->
                    principals.add(new PersonPrincipal(
                            PersonPrincipalType.ROLE, value)));
            requireResolvedStatic(principals, nodeId);
            return;
        }
        String type = text(config.get("assigneeType"));
        if (SetLike.dynamic(type)) {
            return;
        }
        List<PersonPrincipal> principals = new ArrayList<>();
        if ("group".equalsIgnoreCase(type) || "role".equalsIgnoreCase(type)) {
            addGroups(principals, config.get("assigneeValue"), "role".equalsIgnoreCase(type));
        } else {
            addUsers(principals, config.get("assigneeValue"));
            addUsers(principals, config.get("candidateUsers"));
        }
        addUsers(principals, attribute(task, "assignee"));
        addUsers(principals, attribute(task, "candidateUsers"));
        addGroups(principals, attribute(task, "candidateGroups"), false);
        requireResolvedStatic(principals, nodeId);
    }

    private void requireResolvedStatic(
            List<PersonPrincipal> principals,
            String nodeId) {
        AssigneeResolutionResult result = resolutionService
                .resolvePrincipals(
                        principals, "STATIC_ASSIGNEE_EMPTY");
        if (!result.resolved()) {
            throw new IllegalArgumentException(
                    nodeId + " 使用 BLOCK_PUBLISH，但静态配置无法解析到有效办理人");
        }
    }

    private Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalArgumentException("BPMN XML 无法解析", error);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String value, String nodeId) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, Map.class);
        } catch (Exception error) {
            throw new IllegalArgumentException(nodeId + " 的 assigneeConfig 不是合法 JSON", error);
        }
    }

    private Element firstElement(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private String property(Element owner, String name) {
        if (owner == null) {
            return null;
        }
        for (Node child = owner.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element extension)
                    || !"extensionElements".equals(extension.getLocalName())) {
                continue;
            }
            NodeList properties = extension.getElementsByTagNameNS("*", "property");
            for (int i = 0; i < properties.getLength(); i++) {
                Element property = (Element) properties.item(i);
                if (name.equals(property.getAttribute("name"))) {
                    return property.getAttribute("value");
                }
            }
        }
        return null;
    }

    private String attribute(Element element, String localName) {
        if (element.hasAttribute(localName)) {
            return element.getAttribute(localName);
        }
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Node attribute = element.getAttributes().item(i);
            if (localName.equals(attribute.getLocalName())) {
                return attribute.getNodeValue();
            }
        }
        return null;
    }

    private void addUsers(List<PersonPrincipal> target, Object raw) {
        values(raw).forEach(value -> {
            if (!value.contains("${") && !value.contains("#{")) {
                target.add(PersonPrincipal.user(value));
            }
        });
    }

    private void addGroups(List<PersonPrincipal> target, Object raw, boolean forceRole) {
        values(raw).forEach(value -> {
            boolean role = forceRole || value.startsWith("ROLE_");
            target.add(new PersonPrincipal(
                    role ? PersonPrincipalType.ROLE : PersonPrincipalType.GROUP,
                    role && value.startsWith("ROLE_") ? value.substring(5) : value));
        });
    }

    private List<String> values(Object raw) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (raw instanceof Iterable<?> iterable) {
            iterable.forEach(value -> add(values, value));
        } else if (raw != null) {
            for (String value : String.valueOf(raw).split(",")) {
                add(values, value);
            }
        }
        return List.copyOf(values);
    }

    private void add(LinkedHashSet<String> values, Object raw) {
        String value = raw == null ? null : String.valueOf(raw).trim();
        if (StringUtils.hasText(value)) {
            values.add(value);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private static final class SetLike {
        private static boolean dynamic(String type) {
            return java.util.Set.of("resolver", "interface", "expression", "node_reference")
                    .contains(type == null ? "" : type.toLowerCase(Locale.ROOT));
        }
    }
}
