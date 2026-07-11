package com.workflow.process.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.NodeConfigDTO;
import com.workflow.entity.AssigneeConfig;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityFlowStatusMapping;
import com.workflow.entity.FormConfig;
import com.workflow.entity.NodeConfig;
import com.workflow.entity.ProcessNodeApproval;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.mapper.AssigneeConfigMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.FormConfigMapper;
import com.workflow.mapper.NodeConfigMapper;
import com.workflow.mapper.ProcessNodeApprovalMapper;
import com.workflow.mapper.ProcessNodeFormMapper;
import com.workflow.service.EntityFlowStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流程节点配置同步。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDefinitionNodeSyncService {

    private final NodeConfigMapper nodeMapper;
    private final AssigneeConfigMapper assigneeMapper;
    private final FormConfigMapper formMapper;
    private final ObjectMapper objectMapper;
    private final EntityFlowStatusService entityFlowStatusService;
    private final EntityDefinitionMapper entityDefinitionMapper;
    private final ProcessNodeFormMapper nodeFormMapper;
    private final ProcessNodeApprovalMapper nodeApprovalMapper;
    private final JdbcTemplate jdbcTemplate;

    public void syncDraftNodes(String processConfigId, List<NodeConfigDTO> nodes) {
        nodeMapper.deleteByProcessConfigId(processConfigId);
        for (NodeConfigDTO nodeDTO : nodes) {
            NodeConfig node = toNodeConfig(nodeDTO);
            node.setProcessConfigId(processConfigId);
            nodeMapper.insert(node);
        }
    }

    public void syncBpmnNodeBindings(String processConfigId, String bpmnXml) {
        syncNodeFormsFromBpmn(processConfigId, bpmnXml);
        syncNodeApprovalsFromBpmn(processConfigId, bpmnXml);
    }

    public void syncNodeFormsFromBpmn(String processConfigId, String bpmnXml) {
        try {
            Document doc = parseDocument(bpmnXml);
            NodeList userTasks = doc.getElementsByTagNameNS("*", "userTask");
            for (int i = 0; i < userTasks.getLength(); i++) {
                Element userTask = (Element) userTasks.item(i);
                String nodeId = userTask.getAttribute("id");
                String nodeName = userTask.getAttribute("name");
                if (nodeId == null || nodeId.isEmpty()) {
                    continue;
                }

                Map<String, String> extensionProperties = readExtensionProperties(userTask);
                List<String> entityFormIds = resolveEntityFormIds(extensionProperties);
                Integer isReadonly = isTruthy(extensionProperties.get("entityFormReadonly")) ? 1 : 0;

                nodeFormMapper.deleteByProcessConfigIdAndNodeId(processConfigId, nodeId);
                for (int sortOrder = 0; sortOrder < entityFormIds.size(); sortOrder++) {
                    ProcessNodeForm nodeForm = new ProcessNodeForm();
                    nodeForm.setProcessConfigId(processConfigId);
                    nodeForm.setNodeId(nodeId);
                    nodeForm.setNodeName(nodeName);
                    nodeForm.setFormId(entityFormIds.get(sortOrder));
                    nodeForm.setIsReadonly(isReadonly);
                    nodeForm.setSortOrder(sortOrder);
                    nodeForm.setCreateTime(LocalDateTime.now());
                    nodeForm.setUpdateTime(LocalDateTime.now());
                    nodeFormMapper.insert(nodeForm);
                }
                if (!entityFormIds.isEmpty()) {
                    log.debug("同步节点表单绑定: processConfigId={}, nodeId={}, formIds={}",
                            processConfigId, nodeId, entityFormIds);
                }
            }
        } catch (Exception e) {
            log.warn("同步节点表单配置失败: {}", e.getMessage());
        }
    }

    public void syncNodeApprovalsFromBpmn(String processConfigId, String bpmnXml) {
        try {
            Document doc = parseDocument(bpmnXml);
            NodeList userTasks = doc.getElementsByTagNameNS("*", "userTask");
            for (int i = 0; i < userTasks.getLength(); i++) {
                Element userTask = (Element) userTasks.item(i);
                String nodeId = userTask.getAttribute("id");
                String nodeName = userTask.getAttribute("name");
                if (nodeId == null || nodeId.isEmpty()) {
                    continue;
                }

                String approvalConfigJson = readExtensionProperties(userTask).get("approvalConfig");
                if (approvalConfigJson != null && !approvalConfigJson.isEmpty()) {
                    JsonNode config = objectMapper.readTree(approvalConfigJson);
                    boolean enabled = !config.has("enabled") || config.get("enabled").asBoolean();
                    String commentLabel = config.has("commentLabel") ? config.get("commentLabel").asText() : "审批意见";
                    String optionsJson = config.has("options") && config.get("options").isArray()
                            ? config.get("options").toString()
                            : null;

                    ProcessNodeApproval existing = nodeApprovalMapper.selectByNodeId(processConfigId, nodeId);
                    if (existing != null) {
                        existing.setEnabled(enabled ? 1 : 0);
                        existing.setCommentLabel(commentLabel);
                        existing.setOptionsJson(optionsJson);
                        existing.setUpdateTime(LocalDateTime.now());
                        nodeApprovalMapper.updateById(existing);
                    } else {
                        ProcessNodeApproval nodeApproval = new ProcessNodeApproval();
                        nodeApproval.setProcessConfigId(processConfigId);
                        nodeApproval.setNodeId(nodeId);
                        nodeApproval.setNodeName(nodeName);
                        nodeApproval.setEnabled(enabled ? 1 : 0);
                        nodeApproval.setCommentLabel(commentLabel);
                        nodeApproval.setOptionsJson(optionsJson);
                        nodeApproval.setCreateTime(LocalDateTime.now());
                        nodeApproval.setUpdateTime(LocalDateTime.now());
                        nodeApprovalMapper.insert(nodeApproval);
                    }
                    log.debug("同步节点审批配置: processConfigId={}, nodeId={}", processConfigId, nodeId);
                } else {
                    ProcessNodeApproval existing = nodeApprovalMapper.selectByNodeId(processConfigId, nodeId);
                    if (existing != null) {
                        nodeApprovalMapper.deleteById(existing.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("同步节点审批配置失败: {}", e.getMessage());
        }
    }

    public void syncStatusMappingsFromBpmn(String processConfigId, String processKey, String bpmnXml) {
        String entityCode = getEntityCodeByProcessId(processConfigId);
        if (entityCode == null || entityCode.isEmpty()) {
            log.debug("流程未绑定实体，跳过状态映射提取: processConfigId={}", processConfigId);
            return;
        }

        try {
            Pattern flowPattern = Pattern.compile(
                    "<bpmn:sequenceFlow[^>]*id=\"([^\"]+)\"[^>]*sourceRef=\"([^\"]+)\"[^>]*targetRef=\"([^\"]+)\"[^>]*>",
                    Pattern.DOTALL);
            Matcher flowMatcher = flowPattern.matcher(bpmnXml);
            List<EntityFlowStatusMapping> mappings = new ArrayList<>();

            while (flowMatcher.find()) {
                String flowId = flowMatcher.group(1);
                String sourceRef = flowMatcher.group(2);
                String targetRef = flowMatcher.group(3);
                int flowStart = flowMatcher.start();
                int flowEnd = bpmnXml.indexOf("</bpmn:sequenceFlow>", flowStart);
                if (flowEnd == -1) {
                    continue;
                }

                String flowContent = bpmnXml.substring(flowStart, flowEnd + 20);
                Matcher statusMatcher = Pattern.compile("name=\"entityStatusCode\"\\s+value=\"([^\"]+)\"")
                        .matcher(flowContent);
                if (statusMatcher.find()) {
                    EntityFlowStatusMapping mapping = new EntityFlowStatusMapping();
                    mapping.setSequenceFlowId(flowId);
                    mapping.setSourceNodeId(sourceRef);
                    mapping.setSourceNodeName(extractNodeName(bpmnXml, sourceRef));
                    mapping.setTargetNodeId(targetRef);
                    mapping.setTargetNodeName(extractNodeName(bpmnXml, targetRef));
                    mapping.setEntityStatusCode(statusMatcher.group(1));
                    mappings.add(mapping);
                }
            }

            if (!mappings.isEmpty()) {
                entityFlowStatusService.saveStatusMappings(processConfigId, processKey, entityCode, mappings);
                log.info("保存流程状态映射: processConfigId={}, count={}", processConfigId, mappings.size());
            }
        } catch (Exception e) {
            log.warn("提取状态映射失败: processConfigId={}", processConfigId, e);
        }
    }

    public void parseAndSaveNodeConfigs(String processConfigId, String bpmnXml) {
        try {
            nodeMapper.deleteByProcessConfigId(processConfigId);
            int savedCount = 0;
            savedCount += parseNodesByType(processConfigId, bpmnXml, "startEvent", NodeConfig.NodeType.START);
            savedCount += parseNodesByType(processConfigId, bpmnXml, "endEvent", NodeConfig.NodeType.END);
            savedCount += parseUserTasks(processConfigId, bpmnXml);
            savedCount += parseNodesByType(processConfigId, bpmnXml, "serviceTask", NodeConfig.NodeType.SERVICE_TASK);
            savedCount += parseNodesByType(processConfigId, bpmnXml, "scriptTask", NodeConfig.NodeType.SCRIPT_TASK);
            savedCount += parseNodesByType(processConfigId, bpmnXml, "sendTask", NodeConfig.NodeType.SEND_TASK);
            savedCount += parseNodesByType(processConfigId, bpmnXml, "receiveTask", NodeConfig.NodeType.RECEIVE_TASK);
            savedCount += parseNodesByType(processConfigId, bpmnXml, "manualTask", NodeConfig.NodeType.MANUAL_TASK);
            savedCount += parseNodesByType(processConfigId, bpmnXml, "businessRuleTask", NodeConfig.NodeType.BUSINESS_RULE_TASK);
            savedCount += parseNodesByType(processConfigId, bpmnXml, "exclusiveGateway", NodeConfig.NodeType.EXCLUSIVE_GATEWAY);
            savedCount += parseNodesByType(processConfigId, bpmnXml, "parallelGateway", NodeConfig.NodeType.PARALLEL_GATEWAY);
            savedCount += parseNodesByType(processConfigId, bpmnXml, "inclusiveGateway", NodeConfig.NodeType.INCLUSIVE_GATEWAY);
            savedCount += parseNodesByType(processConfigId, bpmnXml, "callActivity", NodeConfig.NodeType.CALL_ACTIVITY);
            savedCount += parseNodesByType(processConfigId, bpmnXml, "subProcess", NodeConfig.NodeType.SUB_PROCESS);
            log.info("解析保存了 {} 个节点配置: processConfigId={}", savedCount, processConfigId);
            if (savedCount == 0) {
                log.warn("未解析到节点: processConfigId={}", processConfigId);
            }
        } catch (Exception e) {
            log.error("解析节点配置失败", e);
        }
    }

    private NodeConfig toNodeConfig(NodeConfigDTO dto) {
        NodeConfig node = new NodeConfig();
        node.setId(dto.getId());
        node.setNodeId(dto.getNodeId());
        node.setNodeName(dto.getNodeName());
        node.setNodeType(dto.getNodeType());
        node.setConfigJson(dto.getConfigJson());
        node.setSkipNode(dto.getSkipNode());
        return node;
    }

    private Document parseDocument(String bpmnXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, String> readExtensionProperties(Element userTask) {
        Map<String, String> values = new HashMap<>();
        NodeList extElements = userTask.getElementsByTagNameNS("*", "extensionElements");
        for (int j = 0; j < extElements.getLength(); j++) {
            Element extElement = (Element) extElements.item(j);
            NodeList properties = extElement.getElementsByTagNameNS("*", "properties");
            for (int k = 0; k < properties.getLength(); k++) {
                Element props = (Element) properties.item(k);
                NodeList propList = props.getElementsByTagNameNS("*", "property");
                for (int m = 0; m < propList.getLength(); m++) {
                    Element prop = (Element) propList.item(m);
                    String name = prop.getAttribute("name");
                    String value = prop.getAttribute("value");
                    if (name != null && !name.isEmpty() && value != null) {
                        values.put(name, decodeXmlAttributeValue(value));
                    }
                }
            }
        }
        return values;
    }

    private List<String> resolveEntityFormIds(Map<String, String> extensionProperties) {
        List<String> formIds = parseFormIdList(extensionProperties.get("entityFormIds"));
        if (!formIds.isEmpty()) {
            return formIds;
        }
        return parseFormIdList(extensionProperties.get("entityFormId"));
    }

    private List<String> parseFormIdList(String value) {
        LinkedHashSet<String> formIds = new LinkedHashSet<>();
        String normalized = decodeXmlAttributeValue(value);
        if (normalized == null || normalized.isBlank()) {
            return new ArrayList<>();
        }

        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            try {
                JsonNode node = objectMapper.readTree(normalized);
                if (node.isArray()) {
                    node.forEach(item -> {
                        if (item.isTextual() && !item.asText().isBlank()) {
                            formIds.add(item.asText().trim());
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("解析 entityFormIds 失败，按列表处理: {}", e.getMessage());
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

    private boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
    }

    private String decodeXmlAttributeValue(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&amp;", "&")
                .replace("&#38;", "&")
                .replace("&lt;", "<")
                .replace("&#60;", "<")
                .replace("&gt;", ">")
                .replace("&#62;", ">")
                .replace("&#39;", "'");
    }

    private String getEntityCodeByProcessId(String processConfigId) {
        try {
            EntityDefinition entityDef = entityDefinitionMapper.findByProcessDefinitionId(processConfigId).orElse(null);
            if (entityDef != null) {
                return entityDef.getEntityCode();
            }
        } catch (Exception e) {
            log.warn("获取流程关联实体失败: processConfigId={}", processConfigId, e);
        }
        return null;
    }

    private String extractNodeName(String bpmnXml, String nodeId) {
        Pattern namePattern = Pattern.compile(
                "<bpmn:[^>]+id=\"" + Pattern.quote(nodeId) + "\"[^>]*name=\"([^\"]+)\"");
        Matcher matcher = namePattern.matcher(bpmnXml);
        return matcher.find() ? matcher.group(1) : nodeId;
    }

    private int parseNodesByType(String processConfigId, String bpmnXml, String tagName, NodeConfig.NodeType nodeType) {
        int count = 0;
        Pattern pattern = Pattern.compile(
                "<(bpmn:)?" + tagName + "[^>]*?id=\"([^\"]+)\"[^>]*?>",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(bpmnXml);

        while (matcher.find()) {
            String fullTag = matcher.group(0);
            String nodeId = matcher.group(2);
            Matcher nameMatcher = Pattern.compile("name=\"([^\"]*)\"").matcher(fullTag);
            String nodeName = nameMatcher.find() ? nameMatcher.group(1) : "";
            String defaultFlow = null;
            if (nodeType.name().contains("GATEWAY")) {
                Matcher defaultMatcher = Pattern.compile("default=\"([^\"]+)\"").matcher(fullTag);
                if (defaultMatcher.find()) {
                    defaultFlow = defaultMatcher.group(1);
                }
            }
            if (nodeId != null && saveNodeWithDefault(processConfigId, nodeId, nodeName, nodeType, defaultFlow)) {
                count++;
            }
        }
        return count;
    }

    private int parseUserTasks(String processConfigId, String bpmnXml) {
        int count = 0;
        Pattern pattern = Pattern.compile(
                "<(bpmn:)?userTask([^>]*)>(.*?)</(bpmn:)?userTask>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(bpmnXml);
        while (matcher.find()) {
            String startTag = matcher.group(2);
            String content = matcher.group(3);
            Matcher idMatcher = Pattern.compile("id=\"([^\"]+)\"").matcher(startTag);
            if (!idMatcher.find()) {
                continue;
            }
            String nodeId = idMatcher.group(1);
            Matcher nameMatcher = Pattern.compile("name=\"([^\"]*)\"").matcher(startTag);
            String nodeName = nameMatcher.find() ? nameMatcher.group(1) : "";
            String fullContent = startTag + ">" + content;
            if (saveNodeAndGetId(processConfigId, nodeId, nodeName, NodeConfig.NodeType.USER_TASK, fullContent) != null) {
                count++;
            }
        }
        return count;
    }

    private String saveNodeAndGetId(String processConfigId,
                                    String nodeId,
                                    String nodeName,
                                    NodeConfig.NodeType nodeType,
                                    String content) {
        try {
            NodeConfig node = new NodeConfig();
            node.setProcessConfigId(processConfigId);
            node.setNodeId(nodeId);
            node.setNodeName(nodeName);
            node.setNodeType(nodeType);
            node.setSkipNode(resolveSkipNode(nodeType, content));
            nodeMapper.insert(node);

            String nodeConfigId = resolveNodeConfigId(processConfigId, nodeId);
            if (nodeConfigId == null) {
                log.error("无法获取节点配置ID: nodeId={}", nodeId);
                return null;
            }

            if (nodeType == NodeConfig.NodeType.USER_TASK) {
                parseAndSaveAssigneeConfigs(nodeConfigId, content);
                parseAndSaveFormConfig(nodeConfigId, content);
                parseAndSaveMultiInstanceConfig(nodeConfigId, content);
                parseAndSaveApprovalConfig(nodeConfigId, content);
            }
            return nodeConfigId;
        } catch (Exception e) {
            log.error("保存节点失败: nodeId={}", nodeId, e);
        }
        return null;
    }

    private boolean resolveSkipNode(NodeConfig.NodeType nodeType, String content) {
        if (nodeType != NodeConfig.NodeType.USER_TASK) {
            return false;
        }
        Matcher skipMatcher = Pattern.compile("flowable:skipExpression=\"([^\"]+)\"").matcher(content);
        Matcher skipElemMatcher = Pattern.compile("<flowable:skipExpression>([^<]+)</flowable:skipExpression>")
                .matcher(content);
        return skipMatcher.find() || skipElemMatcher.find();
    }

    private String resolveNodeConfigId(String processConfigId, String nodeId) {
        List<NodeConfig> nodes = nodeMapper.findByProcessConfigId(processConfigId);
        for (NodeConfig node : nodes) {
            if (nodeId.equals(node.getNodeId())) {
                return node.getId();
            }
        }
        return null;
    }

    private void parseAndSaveAssigneeConfigs(String nodeConfigId, String content) {
        try {
            int priority = 0;

            // 优先读取扩展属性 assigneeConfig（多实例节点 BPMN 属性会被替换为元素变量表达式，
            // 实际执行人配置保存在扩展属性中）
            String assigneeConfigJson = readExtensionPropertyValue(content, "assigneeConfig");
            if (assigneeConfigJson != null && !assigneeConfigJson.isEmpty()) {
                try {
                    JsonNode config = objectMapper.readTree(assigneeConfigJson);

                    // 处理多实例会签人员配置（新增独立字段）
                    String miUsernames = config.has("multiInstanceUsernames") ? config.get("multiInstanceUsernames").asText() : "";
                    String miGroupCodes = config.has("multiInstanceGroupCodes") ? config.get("multiInstanceGroupCodes").asText() : "";
                    String miRoleCodes = config.has("multiInstanceRoleCodes") ? config.get("multiInstanceRoleCodes").asText() : "";
                    boolean hasMultiInstanceUsers = !miUsernames.isEmpty() || !miGroupCodes.isEmpty() || !miRoleCodes.isEmpty();
                    if (hasMultiInstanceUsers) {
                        for (String user : miUsernames.split(",")) {
                            String v = user.trim();
                            if (!v.isEmpty()) {
                                priority = saveUserAssignee(nodeConfigId, v, priority);
                            }
                        }
                        for (String group : miGroupCodes.split(",")) {
                            String v = group.trim();
                            if (!v.isEmpty()) {
                                priority = saveRoleAssignee(nodeConfigId, v, priority);
                            }
                        }
                        for (String role : miRoleCodes.split(",")) {
                            String v = role.trim();
                            if (!v.isEmpty()) {
                                priority = saveRoleAssignee(nodeConfigId, "ROLE_" + v, priority);
                            }
                        }
                        return;
                    }

                    // 兜底：处理旧版/普通节点的 assigneeType + assigneeValue
                    String type = config.has("assigneeType") ? config.get("assigneeType").asText() : "";
                    String value = config.has("assigneeValue") ? config.get("assigneeValue").asText() : "";
                    if (!value.isEmpty()) {
                        if ("user".equals(type)) {
                            for (String user : value.split(",")) {
                                String v = user.trim();
                                if (!v.isEmpty()) {
                                    priority = saveUserAssignee(nodeConfigId, v, priority);
                                }
                            }
                            return;
                        } else if ("group".equals(type) || "role".equals(type)) {
                            for (String group : value.split(",")) {
                                String v = group.trim();
                                if (!v.isEmpty()) {
                                    priority = saveRoleAssignee(nodeConfigId, v, priority);
                                }
                            }
                            return;
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析 assigneeConfig 扩展属性失败: nodeConfigId={}", nodeConfigId, e);
                }
            }

            // 兜底：从 BPMN 属性解析（兼容旧数据/普通节点）
            Matcher assigneeMatcher = Pattern.compile("flowable:assignee=\"([^\"]+)\"").matcher(content);
            if (assigneeMatcher.find()) {
                priority = saveAssignee(nodeConfigId, assigneeMatcher.group(1), priority);
            }

            Matcher candidateUsersMatcher = Pattern.compile("flowable:candidateUsers=\"([^\"]+)\"").matcher(content);
            if (candidateUsersMatcher.find()) {
                for (String user : candidateUsersMatcher.group(1).split(",")) {
                    String value = user.trim();
                    if (!value.isEmpty()) {
                        priority = saveAssignee(nodeConfigId, value, priority);
                    }
                }
            }

            Matcher candidateGroupsMatcher = Pattern.compile("flowable:candidateGroups=\"([^\"]+)\"").matcher(content);
            if (candidateGroupsMatcher.find()) {
                for (String group : candidateGroupsMatcher.group(1).split(",")) {
                    String value = group.trim();
                    if (!value.isEmpty()) {
                        priority = saveRoleAssignee(nodeConfigId, value, priority);
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析执行人配置失败: nodeConfigId={}", nodeConfigId, e);
        }
    }

    private String readExtensionPropertyValue(String content, String propertyName) {
        try {
            Matcher propsMatcher = Pattern.compile(
                    "<(?:flowable|camunda):properties[^>]*>(.*?)</(?:flowable|camunda):properties>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(content);
            while (propsMatcher.find()) {
                String propsContent = propsMatcher.group(1);
                Matcher propMatcher = Pattern.compile(
                        "<(?:flowable|camunda):property\\s+name=\"" + Pattern.quote(propertyName) + "\"\\s+value=\"([^\"]*)\"",
                        Pattern.CASE_INSENSITIVE).matcher(propsContent);
                if (propMatcher.find()) {
                    return decodeXmlAttributeValue(propMatcher.group(1));
                }
            }
        } catch (Exception e) {
            log.warn("读取扩展属性失败: propertyName={}", propertyName, e);
        }
        return null;
    }

    private int saveUserAssignee(String nodeConfigId, String value, int priority) {
        AssigneeConfig assignee = new AssigneeConfig();
        assignee.setNodeConfigId(nodeConfigId);
        assignee.setAssigneeType(AssigneeConfig.AssigneeType.USER);
        assignee.setAssigneeValue(value);
        assignee.setPriority(priority);
        assigneeMapper.insert(assignee);
        return priority + 1;
    }

    private int saveRoleAssignee(String nodeConfigId, String value, int priority) {
        AssigneeConfig assignee = new AssigneeConfig();
        assignee.setNodeConfigId(nodeConfigId);
        assignee.setAssigneeType(AssigneeConfig.AssigneeType.ROLE);
        assignee.setAssigneeValue(value);
        assignee.setPriority(priority);
        assigneeMapper.insert(assignee);
        return priority + 1;
    }

    private int saveAssignee(String nodeConfigId, String assigneeValue, int priority) {
        AssigneeConfig assignee = new AssigneeConfig();
        assignee.setNodeConfigId(nodeConfigId);
        if (assigneeValue.startsWith("${") && assigneeValue.endsWith("}")) {
            assignee.setAssigneeType(AssigneeConfig.AssigneeType.EXPRESSION);
        } else {
            assignee.setAssigneeType(AssigneeConfig.AssigneeType.USER);
        }
        assignee.setAssigneeValue(assigneeValue);
        assignee.setPriority(priority);
        assigneeMapper.insert(assignee);
        return priority + 1;
    }

    private void parseAndSaveFormConfig(String nodeConfigId, String content) {
        try {
            Matcher formIdMatcher = Pattern.compile("<flowable:entityFormId>([^<]+)</flowable:entityFormId>")
                    .matcher(content);
            Matcher formKeyMatcher = Pattern.compile("flowable:formKey=\"([^\"]+)\"").matcher(content);
            Matcher formReadonlyMatcher = Pattern.compile("<flowable:entityFormReadonly>([^<]+)</flowable:entityFormReadonly>")
                    .matcher(content);

            FormConfig formConfig = new FormConfig();
            formConfig.setNodeConfigId(nodeConfigId);
            if (formIdMatcher.find()) {
                String formId = formIdMatcher.group(1);
                formConfig.setFormKey(formId);
                formConfig.setFormName("实体表单-" + formId);
            } else if (formKeyMatcher.find()) {
                String formKey = formKeyMatcher.group(1);
                formConfig.setFormKey(formKey);
                formConfig.setFormName("自定义表单-" + formKey);
            } else {
                return;
            }

            boolean isReadonly = formReadonlyMatcher.find() && isTruthy(formReadonlyMatcher.group(1));
            formConfig.setIsReadonly(isReadonly);
            formMapper.insert(formConfig);
        } catch (Exception e) {
            log.error("解析表单配置失败: nodeConfigId={}", nodeConfigId, e);
        }
    }

    private void parseAndSaveMultiInstanceConfig(String nodeConfigId, String content) {
        try {
            Matcher miMatcher = Pattern.compile(
                    "<(bpmn:)?multiInstanceLoopCharacteristics[^>]*>",
                    Pattern.CASE_INSENSITIVE).matcher(content);
            if (!miMatcher.find()) {
                return;
            }

            Map<String, Object> miConfig = new HashMap<>();
            miConfig.put("multiInstance", true);
            Matcher seqMatcher = Pattern.compile("isSequential=\"(true|false)\"").matcher(content);
            if (seqMatcher.find()) {
                boolean isSequential = "true".equalsIgnoreCase(seqMatcher.group(1));
                miConfig.put("isSequential", isSequential);
                miConfig.put("type", isSequential ? "sequential" : "parallel");
            }
            Matcher collMatcher = Pattern.compile("(?:flowable:)?collection=\"([^\"]+)\"").matcher(content);
            if (collMatcher.find()) {
                miConfig.put("collection", collMatcher.group(1));
            }
            Matcher varMatcher = Pattern.compile("(?:flowable:)?elementVariable=\"([^\"]+)\"").matcher(content);
            if (varMatcher.find()) {
                miConfig.put("elementVariable", varMatcher.group(1));
            }
            Matcher ccMatcher = Pattern.compile(
                    "<completionCondition[^>]*>([^<]+)</completionCondition>",
                    Pattern.CASE_INSENSITIVE).matcher(content);
            if (ccMatcher.find()) {
                miConfig.put("completionCondition", ccMatcher.group(1).trim());
            }
            mergeConfigJson(nodeConfigId, miConfig);
        } catch (Exception e) {
            log.error("解析多实例配置失败: nodeConfigId={}", nodeConfigId, e);
        }
    }

    private void parseAndSaveApprovalConfig(String nodeConfigId, String content) {
        try {
            String approvalConfigJson = findApprovalConfigJson(content);
            if (approvalConfigJson == null) {
                return;
            }
            Map<String, Object> approvalConfig = objectMapper.readValue(
                    decodeXmlAttributeValue(approvalConfigJson), HashMap.class);
            mergeConfigJson(nodeConfigId, approvalConfig);
        } catch (Exception e) {
            log.error("解析审批配置失败: nodeConfigId={}", nodeConfigId, e);
        }
    }

    private String findApprovalConfigJson(String content) {
        Pattern propPattern = Pattern.compile(
                "<(?:flowable:|camunda:)?property[^>]*name=\"approvalConfig\"[^>]*value=\"([^\"]*)\"",
                Pattern.CASE_INSENSITIVE);
        Matcher propMatcher = propPattern.matcher(content);
        if (propMatcher.find()) {
            return propMatcher.group(1);
        }

        propPattern = Pattern.compile(
                "<(?:flowable:|camunda:)?property[^>]*value=\"([^\"]*)\"[^>]*name=\"approvalConfig\"",
                Pattern.CASE_INSENSITIVE);
        propMatcher = propPattern.matcher(content);
        if (propMatcher.find()) {
            return propMatcher.group(1);
        }

        Matcher elemMatcher = Pattern.compile(
                "<(?:flowable:|camunda:)?approvalConfig>([^<]+)</(?:flowable:|camunda:)?approvalConfig>",
                Pattern.CASE_INSENSITIVE).matcher(content);
        return elemMatcher.find() ? elemMatcher.group(1).trim() : null;
    }

    private void mergeConfigJson(String nodeConfigId, Map<String, Object> newConfig) {
        try {
            String existingJson = jdbcTemplate.queryForObject(
                    "SELECT config_json FROM node_config WHERE id = ?",
                    String.class, nodeConfigId);
            Map<String, Object> mergedConfig = new HashMap<>();
            if (existingJson != null && !existingJson.isEmpty()) {
                try {
                    mergedConfig = objectMapper.readValue(existingJson, HashMap.class);
                } catch (Exception e) {
                    log.warn("解析节点配置 JSON 失败，将覆盖: nodeConfigId={}", nodeConfigId);
                }
            }
            mergedConfig.putAll(newConfig);
            jdbcTemplate.update(
                    "UPDATE node_config SET config_json = ? WHERE id = ?",
                    objectMapper.writeValueAsString(mergedConfig), nodeConfigId);
        } catch (EmptyResultDataAccessException e) {
            log.warn("节点不存在，无法合并配置: nodeConfigId={}", nodeConfigId);
        } catch (Exception e) {
            log.error("合并节点配置失败: nodeConfigId={}", nodeConfigId, e);
        }
    }

    private boolean saveNodeWithDefault(String processConfigId,
                                        String nodeId,
                                        String nodeName,
                                        NodeConfig.NodeType nodeType,
                                        String defaultFlow) {
        try {
            NodeConfig node = new NodeConfig();
            node.setProcessConfigId(processConfigId);
            node.setNodeId(nodeId);
            node.setNodeName(nodeName);
            node.setNodeType(nodeType);
            node.setSkipNode(false);
            if (defaultFlow != null && !defaultFlow.isEmpty()) {
                Map<String, Object> config = new HashMap<>();
                config.put("defaultFlow", defaultFlow);
                node.setConfigJson(objectMapper.writeValueAsString(config));
            }
            nodeMapper.insert(node);
            return true;
        } catch (Exception e) {
            log.error("保存节点失败: nodeId={}", nodeId, e);
            return false;
        }
    }
}
