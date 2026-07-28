package com.workflow.process.definition.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.configuration.api.model.NodeConfigDTO;
import com.workflow.process.configuration.infrastructure.persistence.record.AssigneeConfig;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFlowStatusMapping;
import com.workflow.entity.form.infrastructure.persistence.record.FormConfig;
import com.workflow.process.configuration.infrastructure.persistence.record.NodeConfig;
import com.workflow.process.configuration.infrastructure.persistence.record.ProcessNodeApproval;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.configuration.infrastructure.persistence.mapper.AssigneeConfigMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.FormConfigMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.ProcessNodeApprovalMapper;
import com.workflow.process.configuration.application.ProcessNodeApprovalOptionService;
import com.workflow.process.form.infrastructure.persistence.mapper.ProcessNodeFormMapper;
import com.workflow.entity.data.application.EntityFlowStatusService;
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
 * 流程节点配置同步服务
 * 负责在流程保存/发布时解析 BPMN XML，将节点、执行人、表单、审批、多实例、
 * 状态映射等配置同步到对应映射表，保持 BPMN 与业务配置表的一致性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDefinitionNodeSyncService {

    /** 节点配置 Mapper */
    private final NodeConfigMapper nodeMapper;
    /** 审批人配置 Mapper */
    private final AssigneeConfigMapper assigneeMapper;
    /** 表单配置 Mapper */
    private final FormConfigMapper formMapper;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper;
    /** 实体流程状态服务，保存状态映射 */
    private final EntityFlowStatusService entityFlowStatusService;
    /** 实体定义 Mapper，查询流程关联实体 */
    private final EntityDefinitionMapper entityDefinitionMapper;
    /** 节点表单绑定 Mapper */
    private final ProcessNodeFormMapper nodeFormMapper;
    /** 节点审批配置 Mapper */
    private final ProcessNodeApprovalMapper nodeApprovalMapper;
    /** 节点审批选项服务，同步审批选项 */
    private final ProcessNodeApprovalOptionService approvalOptionService;
    /** JDBC 模板，用于合并节点配置 JSON */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 同步草稿节点配置（先清空后插入）。
     *
     * @param processConfigId 流程定义配置ID
     * @param nodes           节点配置DTO列表
     */
    public void syncDraftNodes(String processConfigId, List<NodeConfigDTO> nodes) {
        nodeMapper.deleteByProcessConfigId(processConfigId);
        for (NodeConfigDTO nodeDTO : nodes) {
            NodeConfig node = toNodeConfig(nodeDTO);
            node.setProcessConfigId(processConfigId);
            nodeMapper.insert(node);
        }
    }

    /**
     * 从 BPMN XML 同步节点表单绑定与节点审批配置。
     *
     * @param processConfigId 流程定义配置ID
     * @param bpmnXml         BPMN XML 内容
     */
    public void syncBpmnNodeBindings(String processConfigId, String bpmnXml) {
        syncNodeFormsFromBpmn(processConfigId, bpmnXml);
        syncNodeApprovalsFromBpmn(processConfigId, bpmnXml);
    }

    /**
     * 从 BPMN XML 同步用户任务的节点表单绑定。
     * <p>
     * 解析 userTask 扩展属性中的表单ID与只读设置，重建节点表单绑定。
     *
     * @param processConfigId 流程定义配置ID
     * @param bpmnXml         BPMN XML 内容
     */
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
                List<ProcessNodeForm> existingBindings =
                        nodeFormMapper.selectListByNodeId(processConfigId, nodeId);
                boolean usesExtensionBinding = !entityFormIds.isEmpty();
                if (!usesExtensionBinding) {
                    entityFormIds = parseFormIdList(resolveFormKey(userTask));
                }
                Integer configuredReadonly =
                        isTruthy(extensionProperties.get("entityFormReadonly")) ? 1 : 0;

                nodeFormMapper.deleteByProcessConfigIdAndNodeId(processConfigId, nodeId);
                for (int sortOrder = 0; sortOrder < entityFormIds.size(); sortOrder++) {
                    String formId = entityFormIds.get(sortOrder);
                    ProcessNodeForm nodeForm = new ProcessNodeForm();
                    nodeForm.setProcessConfigId(processConfigId);
                    nodeForm.setNodeId(nodeId);
                    nodeForm.setNodeName(nodeName);
                    nodeForm.setFormId(formId);
                    nodeForm.setIsReadonly(usesExtensionBinding
                            ? configuredReadonly
                            : existingReadonly(existingBindings, formId, configuredReadonly));
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
            throw new IllegalStateException("同步节点表单配置失败: processConfigId=" + processConfigId, e);
        }
    }

    /**
     * 从 BPMN XML 同步用户任务的节点审批配置。
     * <p>
     * 解析 userTask 扩展属性中的 approvalConfig，新增或更新审批配置及审批选项；
     * 无审批配置时清除既有配置。
     *
     * @param processConfigId 流程定义配置ID
     * @param bpmnXml         BPMN XML 内容
     */
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
                        approvalOptionService.replaceFromDocument(
                                existing.getId(), optionsJson);
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
                        approvalOptionService.replaceFromDocument(
                                nodeApproval.getId(), optionsJson);
                    }
                    log.debug("同步节点审批配置: processConfigId={}, nodeId={}", processConfigId, nodeId);
                } else {
                    ProcessNodeApproval existing = nodeApprovalMapper.selectByNodeId(processConfigId, nodeId);
                    if (existing != null) {
                        approvalOptionService.delete(existing.getId());
                        nodeApprovalMapper.deleteById(existing.getId());
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("同步节点审批配置失败: processConfigId=" + processConfigId, e);
        }
    }

    /**
     * 从 BPMN XML 提取连线上的实体状态映射并保存。
     * <p>
     * 解析 sequenceFlow 中 name="entityStatusCode" 的扩展属性，构造状态映射列表保存。
     * 流程未绑定实体时跳过。
     *
     * @param processConfigId 流程定义配置ID
     * @param processKey      流程标识
     * @param bpmnXml         BPMN XML 内容
     */
    public void syncStatusMappingsFromBpmn(String processConfigId, String processKey, String bpmnXml) {
        String entityCode = getEntityCodeByProcessId(processConfigId);
        if (entityCode == null || entityCode.isEmpty()) {
            log.debug("流程未绑定实体，跳过状态映射提取: processConfigId={}", processConfigId);
            return;
        }

        try {
            Document document = parseDocument(bpmnXml);
            NodeList sequenceFlows = document.getElementsByTagNameNS("*", "sequenceFlow");
            List<EntityFlowStatusMapping> mappings = new ArrayList<>();

            for (int i = 0; i < sequenceFlows.getLength(); i++) {
                Element sequenceFlow = (Element) sequenceFlows.item(i);
                String statusCode = readExtensionProperties(sequenceFlow).get("entityStatusCode");
                if (statusCode == null || statusCode.isBlank()) {
                    continue;
                }
                String sourceRef = sequenceFlow.getAttribute("sourceRef");
                String targetRef = sequenceFlow.getAttribute("targetRef");
                EntityFlowStatusMapping mapping = new EntityFlowStatusMapping();
                mapping.setSequenceFlowId(sequenceFlow.getAttribute("id"));
                mapping.setSourceNodeId(sourceRef);
                mapping.setSourceNodeName(extractNodeName(document, sourceRef));
                mapping.setTargetNodeId(targetRef);
                mapping.setTargetNodeName(extractNodeName(document, targetRef));
                mapping.setEntityStatusCode(statusCode);
                mappings.add(mapping);
            }

            entityFlowStatusService.replaceStatusMappings(processConfigId, processKey, entityCode, mappings);
            log.info("同步流程状态映射: processConfigId={}, count={}", processConfigId, mappings.size());
        } catch (Exception e) {
            throw new IllegalStateException("同步流程状态映射失败: processConfigId=" + processConfigId, e);
        }
    }

    /**
     * 解析 BPMN XML 全量节点配置并保存（先清空后插入）。
     * <p>
     * 按开始/结束事件、用户任务、各类任务、网关、调用活动、子流程等类型逐一解析。
     *
     * @param processConfigId 流程定义配置ID
     * @param bpmnXml         BPMN XML 内容
     */
    public void parseAndSaveNodeConfigs(String processConfigId, String bpmnXml) {
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
        savedCount += parseNodesByType(processConfigId, bpmnXml, "eventBasedGateway", NodeConfig.NodeType.EVENT_BASED_GATEWAY);
        savedCount += parseNodesByType(processConfigId, bpmnXml, "callActivity", NodeConfig.NodeType.CALL_ACTIVITY);
        savedCount += parseNodesByType(processConfigId, bpmnXml, "subProcess", NodeConfig.NodeType.SUB_PROCESS);
        log.info("解析保存了 {} 个节点配置: processConfigId={}", savedCount, processConfigId);
        if (savedCount == 0) {
            log.warn("未解析到节点: processConfigId={}", processConfigId);
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

    private String resolveFormKey(Element userTask) {
        String formKey = userTask.getAttributeNS("http://flowable.org/bpmn", "formKey");
        if (formKey == null || formKey.isBlank()) {
            formKey = userTask.getAttribute("formKey");
        }
        if (formKey == null || formKey.isBlank()) {
            formKey = userTask.getAttribute("flowable:formKey");
        }
        return decodeXmlAttributeValue(formKey);
    }

    private Integer existingReadonly(
            List<ProcessNodeForm> existingBindings,
            String formId,
            Integer fallback) {
        if (existingBindings == null || existingBindings.isEmpty()) {
            return fallback;
        }
        return existingBindings.stream()
                .filter(binding -> formId.equals(binding.getFormId()))
                .map(ProcessNodeForm::getIsReadonly)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(fallback);
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
        EntityDefinition entityDef = entityDefinitionMapper.findByProcessDefinitionId(processConfigId).orElse(null);
        if (entityDef != null) {
            return entityDef.getEntityCode();
        }
        return null;
    }

    private String extractNodeName(Document document, String nodeId) {
        NodeList elements = document.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (nodeId.equals(element.getAttribute("id"))) {
                String name = element.getAttribute("name");
                return name == null || name.isBlank() ? nodeId : name;
            }
        }
        return nodeId;
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
                "<(?:bpmn:)?userTask\\b([^>]*?)(?:/\\s*>|>(.*?)</(?:bpmn:)?userTask\\s*>)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(bpmnXml);
        while (matcher.find()) {
            String startTag = matcher.group(1);
            String content = matcher.group(2) == null ? "" : matcher.group(2);
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
        NodeConfig node = new NodeConfig();
        node.setProcessConfigId(processConfigId);
        node.setNodeId(nodeId);
        node.setNodeName(nodeName);
        node.setNodeType(nodeType);
        node.setSkipNode(resolveSkipNode(nodeType, content));
        nodeMapper.insert(node);

        String nodeConfigId = resolveNodeConfigId(processConfigId, nodeId);
        if (nodeConfigId == null) {
            throw new IllegalStateException("无法获取节点配置ID: nodeId=" + nodeId);
        }

        if (nodeType == NodeConfig.NodeType.USER_TASK) {
            parseAndSaveAssigneeConfigs(nodeConfigId, content);
            parseAndSaveFormConfig(nodeConfigId, content);
            parseAndSaveMultiInstanceConfig(nodeConfigId, content);
            parseAndSaveApprovalConfig(nodeConfigId, content);
        }
        return nodeConfigId;
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
                JsonNode config = objectMapper.readTree(
                        assigneeConfigJson);
                mergeConfigJson(
                        nodeConfigId,
                        Map.of(
                                "assigneeConfig",
                                objectMapper.convertValue(
                                        config,
                                        Map.class)));

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
            throw new IllegalStateException(
                    "解析执行人配置失败: nodeConfigId="
                            + nodeConfigId,
                    e);
        }
    }

    private String readExtensionPropertyValue(String content, String propertyName) {
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
            throw new IllegalStateException(
                    "解析表单配置失败: nodeConfigId="
                            + nodeConfigId,
                    e);
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
            throw new IllegalStateException(
                    "解析多实例配置失败: nodeConfigId="
                            + nodeConfigId,
                    e);
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
            throw new IllegalStateException(
                    "解析审批配置失败: nodeConfigId="
                            + nodeConfigId,
                    e);
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
                    "SELECT config_json FROM process_node_config WHERE id = ?",
                    String.class, nodeConfigId);
            Map<String, Object> mergedConfig = new HashMap<>();
            if (existingJson != null && !existingJson.isEmpty()) {
                mergedConfig = objectMapper.readValue(
                        existingJson,
                        HashMap.class);
            }
            mergedConfig.putAll(newConfig);
            int updated = jdbcTemplate.update(
                    "UPDATE process_node_config SET config_json = ? WHERE id = ?",
                    objectMapper.writeValueAsString(mergedConfig), nodeConfigId);
            if (updated != 1) {
                throw new IllegalStateException(
                        "节点配置更新数量异常: nodeConfigId="
                                + nodeConfigId
                                + ", updated="
                                + updated);
            }
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException(
                    "节点不存在，无法合并配置: nodeConfigId="
                            + nodeConfigId,
                    e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "合并节点配置失败: nodeConfigId="
                            + nodeConfigId,
                    e);
        }
    }

    private boolean saveNodeWithDefault(String processConfigId,
                                        String nodeId,
                                        String nodeName,
                                        NodeConfig.NodeType nodeType,
                                        String defaultFlow) {
        NodeConfig node = new NodeConfig();
        node.setProcessConfigId(processConfigId);
        node.setNodeId(nodeId);
        node.setNodeName(nodeName);
        node.setNodeType(nodeType);
        node.setSkipNode(false);
        if (defaultFlow != null && !defaultFlow.isEmpty()) {
            Map<String, Object> config = new HashMap<>();
            config.put("defaultFlow", defaultFlow);
            try {
                node.setConfigJson(objectMapper.writeValueAsString(config));
            } catch (Exception e) {
                throw new IllegalStateException("序列化节点配置失败: nodeId=" + nodeId, e);
            }
        }
        nodeMapper.insert(node);
        return true;
    }
}
