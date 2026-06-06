package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.*;
import com.workflow.entity.*;
import com.workflow.mapper.*;
import com.workflow.process.definition.ProcessBpmnPublishSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDefinitionService {
    
    private final ProcessDefinitionConfigMapper processMapper;
    private final ProcessVersionHistoryMapper versionHistoryMapper;
    private final NodeConfigMapper nodeMapper;
    private final AssigneeConfigMapper assigneeMapper;
    private final FormConfigMapper formMapper;
    private final FormFieldConfigMapper fieldMapper;
    private final RepositoryService activitiRepositoryService;
    private final ObjectMapper objectMapper;
    private final ProcessBpmnPublishSanitizer bpmnPublishSanitizer;
    private final FlowActionService flowActionService;
    private final EntityFlowStatusService entityFlowStatusService;
    private final com.workflow.mapper.EntityDefinitionMapper entityDefinitionMapper;
    private final ProcessNodeFormMapper nodeFormMapper;
    private final ProcessNodeApprovalMapper nodeApprovalMapper;
    
    @Transactional(readOnly = true)
    public List<ProcessDefinitionDTO> findAll() {
        return processMapper.findAllActive().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ProcessDefinitionDTO> findByStatus(ProcessDefinitionConfig.ProcessStatus status) {
        return processMapper.findByStatus(status.name()).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 查询所有未被实体绑定的流程
     * 用于实体绑定流程时选择
     */
    @Transactional(readOnly = true)
    public List<ProcessDefinitionDTO> findAllUnbound() {
        List<ProcessDefinitionConfig> unbound = processMapper.findAllUnbound();
        if (unbound == null) {
            return new java.util.ArrayList<>();
        }
        return unbound.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 查询所有可用于绑定的流程（包括当前已绑定的）
     * 用于实体绑定流程时选择，包括当前已绑定的流程和未绑定的流程
     * @param currentProcessId 当前绑定的流程ID（可为空）
     * @return 可用于绑定的流程列表
     */
    @Transactional(readOnly = true)
    public List<ProcessDefinitionDTO> findAllBindable(String currentProcessId) {
        // 获取所有未绑定的流程
        List<ProcessDefinitionConfig> unbound = processMapper.findAllUnbound();
        
        // 如果返回null，使用空列表
        if (unbound == null) {
            unbound = new java.util.ArrayList<>();
        }
        
        // 如果指定了当前流程ID，需要将其加入列表
        if (currentProcessId != null && !currentProcessId.trim().isEmpty()) {
            ProcessDefinitionConfig current = processMapper.selectById(currentProcessId);
            if (current != null) {
                // 检查是否已经在未绑定列表中（理论上不应该在，但为了安全）
                boolean exists = unbound.stream()
                        .anyMatch(p -> p.getId().equals(currentProcessId));
                if (!exists) {
                    unbound.add(0, current); // 添加到列表开头
                }
            }
        }
        
        return unbound.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public ProcessDefinitionDTO findById(String id) {
        ProcessDefinitionConfig config = processMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("Process not found: " + id);
        }
        return convertToDTO(config);
    }
    
    @Transactional(readOnly = true)
    public ProcessDefinitionDTO findByProcessKey(String processKey) {
        ProcessDefinitionConfig config = processMapper.findByProcessKey(processKey)
                .orElseThrow(() -> new RuntimeException("Process not found: " + processKey));
        return convertToDTO(config);
    }
    
    /**
     * 查询流程的所有历史版本
     */
    @Transactional(readOnly = true)
    public List<ProcessVersionHistoryDTO> findVersionsByProcessId(String processId) {
        List<ProcessVersionHistory> versions = versionHistoryMapper.findByProcessConfigId(processId);
        return versions.stream()
                .map(this::convertVersionToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 查询指定版本的流程历史记录
     */
    @Transactional(readOnly = true)
    public ProcessVersionHistoryDTO findVersionById(String versionId) {
        ProcessVersionHistory version = versionHistoryMapper.selectById(versionId);
        if (version == null) {
            throw new RuntimeException("Version not found: " + versionId);
        }
        return convertVersionToDTO(version);
    }
    
    @Transactional
    public ProcessDefinitionDTO save(ProcessDefinitionDTO dto) {
        ProcessDefinitionConfig config = convertToEntity(dto);
        config.setVersion(0); // 初始版本为0，表示从未发布
        config.setStatus(ProcessDefinitionConfig.ProcessStatus.DRAFT);
        processMapper.insert(config);
        return convertToDTO(config);
    }
    
    @Transactional
    public ProcessDefinitionDTO update(String id, ProcessDefinitionDTO dto) {
        ProcessDefinitionConfig existing = processMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("Process not found: " + id);
        }
        
        // 已发布的流程不能直接编辑，必须先创建新版本
        if (existing.getStatus() == ProcessDefinitionConfig.ProcessStatus.PUBLISHED) {
            // 允许更新基本信息和XML（作为草稿修改）
            existing.setProcessName(dto.getProcessName());
            existing.setDescription(dto.getDescription());
            existing.setCategory(dto.getCategory());
            existing.setBpmnXml(dto.getBpmnXml());
            // 状态保持PUBLISHED，表示这是基于已发布版本的修改
        } else {
            existing.setProcessName(dto.getProcessName());
            existing.setDescription(dto.getDescription());
            existing.setCategory(dto.getCategory());
            existing.setBpmnXml(dto.getBpmnXml());
        }
        
        processMapper.updateById(existing);
        
        // 更新节点配置
        if (dto.getNodes() != null) {
            nodeMapper.deleteByProcessConfigId(id);
            for (NodeConfigDTO nodeDTO : dto.getNodes()) {
                NodeConfig node = convertToEntity(nodeDTO);
                node.setProcessConfigId(id);
                nodeMapper.insert(node);
            }
        }
        
        // 同步 BPMN XML 中的节点表单配置到 process_node_form 表
        if (dto.getBpmnXml() != null && !dto.getBpmnXml().isEmpty()) {
            syncNodeFormsFromBpmn(id, dto.getBpmnXml());
            syncNodeApprovalsFromBpmn(id, dto.getBpmnXml());
        }
        
        return convertToDTO(existing);
    }
    
    /**
     * 从 BPMN XML 中同步节点表单配置到 process_node_form 表
     */
    private void syncNodeFormsFromBpmn(String processConfigId, String bpmnXml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(new java.io.ByteArrayInputStream(bpmnXml.getBytes("UTF-8")));
            
            org.w3c.dom.NodeList userTasks = doc.getElementsByTagNameNS("*", "userTask");
            for (int i = 0; i < userTasks.getLength(); i++) {
                org.w3c.dom.Element userTask = (org.w3c.dom.Element) userTasks.item(i);
                String nodeId = userTask.getAttribute("id");
                String nodeName = userTask.getAttribute("name");
                if (nodeId == null || nodeId.isEmpty()) {
                    continue;
                }
                
                Map<String, String> extensionProperties = readExtensionProperties(userTask);
                List<String> entityFormIds = resolveEntityFormIds(extensionProperties);
                Integer isReadonly = isTruthy(extensionProperties.get("entityFormReadonly")) ? 1 : 0;
                
                if (!entityFormIds.isEmpty()) {
                    nodeFormMapper.deleteByProcessConfigIdAndNodeId(processConfigId, nodeId);
                    for (int sortOrder = 0; sortOrder < entityFormIds.size(); sortOrder++) {
                        ProcessNodeForm nodeForm = new ProcessNodeForm();
                        nodeForm.setProcessConfigId(processConfigId);
                        nodeForm.setNodeId(nodeId);
                        nodeForm.setNodeName(nodeName);
                        nodeForm.setFormId(entityFormIds.get(sortOrder));
                        nodeForm.setIsReadonly(isReadonly);
                        nodeForm.setSortOrder(sortOrder);
                        nodeForm.setCreateTime(java.time.LocalDateTime.now());
                        nodeForm.setUpdateTime(java.time.LocalDateTime.now());
                        nodeFormMapper.insert(nodeForm);
                    }
                    log.debug("同步节点表单绑定: processConfigId={}, nodeId={}, formIds={}", processConfigId, nodeId, entityFormIds);
                } else {
                    // 如果 BPMN 中没有表单配置，删除已有的绑定
                    nodeFormMapper.deleteByProcessConfigIdAndNodeId(processConfigId, nodeId);
                }
            }
        } catch (Exception e) {
            log.warn("同步节点表单配置失败: {}", e.getMessage());
        }
    }

    private Map<String, String> readExtensionProperties(org.w3c.dom.Element userTask) {
        Map<String, String> values = new java.util.HashMap<>();
        org.w3c.dom.NodeList extElements = userTask.getElementsByTagNameNS("*", "extensionElements");
        for (int j = 0; j < extElements.getLength(); j++) {
            org.w3c.dom.Element extElement = (org.w3c.dom.Element) extElements.item(j);
            org.w3c.dom.NodeList properties = extElement.getElementsByTagNameNS("*", "properties");
            for (int k = 0; k < properties.getLength(); k++) {
                org.w3c.dom.Element props = (org.w3c.dom.Element) properties.item(k);
                org.w3c.dom.NodeList propList = props.getElementsByTagNameNS("*", "property");
                for (int m = 0; m < propList.getLength(); m++) {
                    org.w3c.dom.Element prop = (org.w3c.dom.Element) propList.item(m);
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
                ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(normalized);
                if (node.isArray()) {
                    node.forEach(item -> {
                        if (item.isTextual() && !item.asText().isBlank()) {
                            formIds.add(item.asText().trim());
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("解析 entityFormIds 失败，按逗号列表兼容处理: {}", e.getMessage());
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
    
    /**
     * 从 BPMN XML 中同步节点审批配置到 process_node_approval 表
     */
    private void syncNodeApprovalsFromBpmn(String processConfigId, String bpmnXml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(new java.io.ByteArrayInputStream(bpmnXml.getBytes("UTF-8")));
            
            org.w3c.dom.NodeList userTasks = doc.getElementsByTagNameNS("*", "userTask");
            for (int i = 0; i < userTasks.getLength(); i++) {
                org.w3c.dom.Element userTask = (org.w3c.dom.Element) userTasks.item(i);
                String nodeId = userTask.getAttribute("id");
                String nodeName = userTask.getAttribute("name");
                if (nodeId == null || nodeId.isEmpty()) {
                    continue;
                }
                
                // 查找 extensionElements -> properties -> property name="approvalConfig"
                String approvalConfigJson = null;
                org.w3c.dom.NodeList extElements = userTask.getElementsByTagNameNS("*", "extensionElements");
                for (int j = 0; j < extElements.getLength(); j++) {
                    org.w3c.dom.Element extElement = (org.w3c.dom.Element) extElements.item(j);
                    org.w3c.dom.NodeList properties = extElement.getElementsByTagNameNS("*", "properties");
                    for (int k = 0; k < properties.getLength(); k++) {
                        org.w3c.dom.Element props = (org.w3c.dom.Element) properties.item(k);
                        org.w3c.dom.NodeList propList = props.getElementsByTagNameNS("*", "property");
                        for (int m = 0; m < propList.getLength(); m++) {
                            org.w3c.dom.Element prop = (org.w3c.dom.Element) propList.item(m);
                            String name = prop.getAttribute("name");
                            String value = prop.getAttribute("value");
                            if ("approvalConfig".equals(name) && value != null && !value.isEmpty()) {
                                approvalConfigJson = value
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
                        }
                    }
                }
                
                if (approvalConfigJson != null && !approvalConfigJson.isEmpty()) {
                    com.fasterxml.jackson.databind.JsonNode config = 
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(approvalConfigJson);
                    
                    boolean enabled = config.has("enabled") ? config.get("enabled").asBoolean() : true;
                    String commentLabel = config.has("commentLabel") ? config.get("commentLabel").asText() : "审批意见";
                    String optionsJson = null;
                    if (config.has("options") && config.get("options").isArray()) {
                        optionsJson = config.get("options").toString();
                    }
                    
                    // 检查是否已存在
                    ProcessNodeApproval existing = nodeApprovalMapper.selectByNodeId(processConfigId, nodeId);
                    if (existing != null) {
                        existing.setEnabled(enabled ? 1 : 0);
                        existing.setCommentLabel(commentLabel);
                        existing.setOptionsJson(optionsJson);
                        existing.setUpdateTime(java.time.LocalDateTime.now());
                        nodeApprovalMapper.updateById(existing);
                    } else {
                        ProcessNodeApproval nodeApproval = new ProcessNodeApproval();
                        nodeApproval.setProcessConfigId(processConfigId);
                        nodeApproval.setNodeId(nodeId);
                        nodeApproval.setNodeName(nodeName);
                        nodeApproval.setEnabled(enabled ? 1 : 0);
                        nodeApproval.setCommentLabel(commentLabel);
                        nodeApproval.setOptionsJson(optionsJson);
                        nodeApproval.setCreateTime(java.time.LocalDateTime.now());
                        nodeApproval.setUpdateTime(java.time.LocalDateTime.now());
                        nodeApprovalMapper.insert(nodeApproval);
                    }
                    log.debug("同步节点审批配置: processConfigId={}, nodeId={}", processConfigId, nodeId);
                } else {
                    // 如果 BPMN 中没有 approvalConfig，删除已有的配置
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
    
    @Transactional
    public void delete(String id) {
        ProcessDefinitionConfig config = processMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("Process not found: " + id);
        }
        
        // 逻辑删除流程定义
        config.setDeleted(1);
        config.setStatus(ProcessDefinitionConfig.ProcessStatus.DISABLED);
        config.setUpdatedAt(java.time.LocalDateTime.now());
        processMapper.updateById(config);
        
        log.info("Logic deleted process {}", id);
    }
    
    /**
     * 发布流程 - 每次发布创建一个新版本
     */
    @Transactional
    public ProcessDefinitionDTO publish(String id, String versionDescription) {
        ProcessDefinitionConfig config = processMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("Process not found: " + id);
        }
        
        if (config.getBpmnXml() == null || config.getBpmnXml().isEmpty()) {
            throw new RuntimeException("BPMN XML is required for publishing");
        }
        
        // 计算新版本号
        Integer maxVersion = versionHistoryMapper.findMaxVersionByProcessConfigId(id);
        int newVersion = (maxVersion == null ? 0 : maxVersion) + 1;
        
        // 将 processKey 写入 XML 的 process id 属性，确保 Flowable 使用正确的 key
        String bpmnXml = config.getBpmnXml();
        
        // 在清理 camunda 命名空间之前，先提取并保存状态映射配置
        // 查找关联的实体编码
        String entityCode = getEntityCodeByProcessId(id);
        saveStatusMappingsFromBpmn(id, config.getProcessKey(), entityCode, bpmnXml);
        
        bpmnXml = bpmnPublishSanitizer.sanitize(bpmnXml, config.getProcessKey());
        
        // Deploy to Flowable
        Deployment deployment = activitiRepositoryService.createDeployment()
                .addString(config.getProcessKey() + ".bpmn20.xml", bpmnXml)
                .name(config.getProcessName() + " - v" + newVersion)
                .deploy();
        
        log.info("Deployed process {} version {} with deployment id {}", 
                config.getProcessKey(), newVersion, deployment.getId());
        
        // 保存修改后的XML到流程配置
        config.setBpmnXml(bpmnXml);
        
        // 保存版本历史记录
        ProcessVersionHistory versionHistory = new ProcessVersionHistory();
        versionHistory.setProcessConfigId(id);
        versionHistory.setProcessKey(config.getProcessKey());
        versionHistory.setProcessName(config.getProcessName());
        versionHistory.setVersion(newVersion);
        versionHistory.setVersionDescription(versionDescription);
        versionHistory.setBpmnXml(bpmnXml);
        versionHistory.setPublishedAt(java.time.LocalDateTime.now());
        versionHistory.setDeploymentId(deployment.getId());
        versionHistory.setStatus(ProcessVersionHistory.Status.ACTIVE.name());
        versionHistoryMapper.insert(versionHistory);
        
        // 发布流程动作（将草稿动作复制到版本）
        flowActionService.publishActions(id, versionHistory.getId());
        
        // 从 BPMN XML 解析并保存节点配置
        parseAndSaveNodeConfigs(id, bpmnXml);        
        // 更新主流程配置
        config.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
        config.setVersion(newVersion);
        processMapper.updateById(config);
        
        // 重新查询确保获取最新值
        ProcessDefinitionConfig updatedConfig = processMapper.selectById(id);
        return convertToDTO(updatedConfig);
    }
    
    /**
     * 回滚到指定版本 - 创建新版本而不是直接覆盖
     */
    @Transactional
    public ProcessDefinitionDTO rollbackToVersion(String processId, String versionId, String reason) {
        ProcessDefinitionConfig config = processMapper.selectById(processId);
        if (config == null) {
            throw new RuntimeException("Process not found: " + processId);
        }
        
        ProcessVersionHistory targetVersion = versionHistoryMapper.selectById(versionId);
        if (targetVersion == null) {
            throw new RuntimeException("Version not found: " + versionId);
        }
        
        // 使用目标版本的XML作为新版本的起点
        config.setBpmnXml(targetVersion.getBpmnXml());
        config.setStatus(ProcessDefinitionConfig.ProcessStatus.DRAFT);
        processMapper.updateById(config);
        
        log.info("Process {} rolled back to version {} for reason: {}", 
                processId, targetVersion.getVersion(), reason);
        
        return convertToDTO(config);
    }
    
    @Transactional
    public ProcessDefinitionDTO disable(String id) {
        ProcessDefinitionConfig config = processMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("Process not found: " + id);
        }
        config.setStatus(ProcessDefinitionConfig.ProcessStatus.DISABLED);
        processMapper.updateById(config);
        return convertToDTO(config);
    }
    
    @Transactional
    public void deleteVersion(String versionId) {
        ProcessVersionHistory version = versionHistoryMapper.selectById(versionId);
        if (version == null) {
            throw new RuntimeException("Version not found: " + versionId);
        }
        
        // 逻辑删除版本关联的流程动作
        flowActionService.deleteActionsByVersionId(versionId);
        
        // 逻辑删除版本记录
        version.setDeleted(1);
        versionHistoryMapper.updateById(version);
        
        log.info("Logic deleted version {}", versionId);
    }
    
    // Convert methods
    private ProcessDefinitionDTO convertToDTO(ProcessDefinitionConfig config) {
        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setId(config.getId());
        dto.setProcessKey(config.getProcessKey());
        dto.setProcessName(config.getProcessName());
        dto.setDescription(config.getDescription());
        dto.setCategory(config.getCategory());
        dto.setVersion(config.getVersion());
        dto.setStatus(config.getStatus());
        dto.setBpmnXml(config.getBpmnXml());
        dto.setCreatedAt(config.getCreatedAt());
        dto.setUpdatedAt(config.getUpdatedAt());
        dto.setCreatedBy(config.getCreatedBy());
        
        return dto;
    }
    
    private ProcessVersionHistoryDTO convertVersionToDTO(ProcessVersionHistory version) {
        ProcessVersionHistoryDTO dto = new ProcessVersionHistoryDTO();
        dto.setId(version.getId());
        dto.setProcessConfigId(version.getProcessConfigId());
        dto.setProcessKey(version.getProcessKey());
        dto.setProcessName(version.getProcessName());
        dto.setVersion(version.getVersion());
        dto.setVersionDescription(version.getVersionDescription());
        dto.setBpmnXml(version.getBpmnXml());
        dto.setPublishedAt(version.getPublishedAt());
        dto.setPublishedBy(version.getPublishedBy());
        dto.setDeploymentId(version.getDeploymentId());
        dto.setStatus(version.getStatus());
        return dto;
    }
    
    private NodeConfig convertToEntity(NodeConfigDTO dto) {
        NodeConfig node = new NodeConfig();
        node.setId(dto.getId());
        node.setNodeId(dto.getNodeId());
        node.setNodeName(dto.getNodeName());
        node.setNodeType(dto.getNodeType());
        node.setConfigJson(dto.getConfigJson());
        return node;
    }
    
    private ProcessDefinitionConfig convertToEntity(ProcessDefinitionDTO dto) {
        ProcessDefinitionConfig config = new ProcessDefinitionConfig();
        config.setId(dto.getId());
        config.setProcessKey(dto.getProcessKey());
        config.setProcessName(dto.getProcessName());
        config.setDescription(dto.getDescription());
        config.setCategory(dto.getCategory());
        config.setVersion(dto.getVersion());
        config.setStatus(dto.getStatus());
        config.setBpmnXml(dto.getBpmnXml());
        config.setCreatedBy(dto.getCreatedBy());
        return config;
    }
    
    /**
     * 从 BPMN XML 中提取状态映射配置并保存到数据库
     */
    private void saveStatusMappingsFromBpmn(String processConfigId, String processKey, String entityCode, String bpmnXml) {
        if (entityCode == null || entityCode.isEmpty()) {
            log.debug("流程未绑定实体，跳过状态映射提取: processConfigId={}", processConfigId);
            return;
        }
        
        try {
            // 解析 BPMN XML 提取 sequenceFlow 的状态映射配置
            java.util.regex.Pattern flowPattern = java.util.regex.Pattern.compile(
                "<bpmn:sequenceFlow[^>]*id=\"([^\"]+)\"[^>]*sourceRef=\"([^\"]+)\"[^>]*targetRef=\"([^\"]+)\"[^>]*>",
                java.util.regex.Pattern.DOTALL
            );
            java.util.regex.Matcher flowMatcher = flowPattern.matcher(bpmnXml);
            
            List<com.workflow.entity.EntityFlowStatusMapping> mappings = new java.util.ArrayList<>();
            
            while (flowMatcher.find()) {
                String flowId = flowMatcher.group(1);
                String sourceRef = flowMatcher.group(2);
                String targetRef = flowMatcher.group(3);
                
                // 查找该 sequenceFlow 的扩展属性中的状态配置
                // 提取 sequenceFlow 标签到结束标签之间的内容
                int flowStart = flowMatcher.start();
                int flowEnd = bpmnXml.indexOf("</bpmn:sequenceFlow>", flowStart);
                if (flowEnd == -1) continue;
                
                String flowContent = bpmnXml.substring(flowStart, flowEnd + 20);
                
                // 提取 entityStatusCode
                java.util.regex.Pattern statusPattern = java.util.regex.Pattern.compile(
                    "name=\"entityStatusCode\"\\s+value=\"([^\"]+)\""
                );
                java.util.regex.Matcher statusMatcher = statusPattern.matcher(flowContent);
                
                if (statusMatcher.find()) {
                    String statusCode = statusMatcher.group(1);
                    
                    // 提取 sourceNodeName 和 targetNodeName
                    String sourceName = extractNodeName(bpmnXml, sourceRef);
                    String targetName = extractNodeName(bpmnXml, targetRef);
                    
                    com.workflow.entity.EntityFlowStatusMapping mapping = 
                        new com.workflow.entity.EntityFlowStatusMapping();
                    mapping.setSequenceFlowId(flowId);
                    mapping.setSourceNodeId(sourceRef);
                    mapping.setSourceNodeName(sourceName);
                    mapping.setTargetNodeId(targetRef);
                    mapping.setTargetNodeName(targetName);
                    mapping.setEntityStatusCode(statusCode);
                    
                    mappings.add(mapping);
                    log.debug("提取状态映射: flowId={}, source={}, target={}, status={}", 
                            flowId, sourceRef, targetRef, statusCode);
                }
            }
            
            // 保存到数据库
            if (!mappings.isEmpty()) {
                entityFlowStatusService.saveStatusMappings(processConfigId, processKey, entityCode, mappings);
                log.info("保存流程状态映射: processConfigId={}, count={}", processConfigId, mappings.size());
            }
        } catch (Exception e) {
            log.warn("提取状态映射失败: processConfigId={}", processConfigId, e);
        }
    }
    
    /**
     * 根据流程定义ID获取关联的实体编码
     */
    private String getEntityCodeByProcessId(String processConfigId) {
        try {
            // 通过 entity_definition 表的 process_definition_id 字段查找关联的实体
            com.workflow.entity.EntityDefinition entityDef = entityDefinitionMapper
                    .findByProcessDefinitionId(processConfigId)
                    .orElse(null);
            if (entityDef != null) {
                return entityDef.getEntityCode();
            }
        } catch (Exception e) {
            log.warn("获取流程关联的实体编码失败: processConfigId={}", processConfigId, e);
        }
        return null;
    }
    
    /**
     * 从 BPMN XML 中提取节点名称
     */
    private String extractNodeName(String bpmnXml, String nodeId) {
        java.util.regex.Pattern namePattern = java.util.regex.Pattern.compile(
            "<bpmn:[^>]+id=\"" + java.util.regex.Pattern.quote(nodeId) + "\"[^>]*name=\"([^\"]+)\""
        );
        java.util.regex.Matcher matcher = namePattern.matcher(bpmnXml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return nodeId;
    }
    
    /**
     * 从 BPMN XML 解析节点配置并保存到 node_config 表
     */
    private void parseAndSaveNodeConfigs(String processConfigId, String bpmnXml) {
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
            
            // 测试：如果解析不到节点，输出BPMN XML前500字符用于调试
            if (savedCount == 0) {
                log.warn("未解析到任何节点，BPMN XML前500字符: {}", bpmnXml.substring(0, Math.min(500, bpmnXml.length())));
            }
        } catch (Exception e) {
            log.error("解析节点配置失败", e);
        }
    }
    
    private int parseNodesByType(String processConfigId, String bpmnXml, String tagName, NodeConfig.NodeType nodeType) {
        int count = 0;
        log.debug("解析节点类型: tagName={}, nodeType={}", tagName, nodeType);
        
        // 使用更安全的正则：匹配整个标签并提取id和name
        String patternStr = "<(bpmn:)?" + tagName + "[^>]*?id=\"([^\"]+)\"[^>]*?>";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr, java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(bpmnXml);
        
        while (matcher.find()) {
            String fullTag = matcher.group(0);
            String nodeId = matcher.group(2);
            
            // 在完整标签中提取name
            java.util.regex.Matcher nameMatcher = java.util.regex.Pattern.compile("name=\"([^\"]*)\"").matcher(fullTag);
            String nodeName = nameMatcher.find() ? nameMatcher.group(1) : "";
            
            // 对于网关类型，提取default属性（默认流）
            String defaultFlow = null;
            if (nodeType.name().contains("GATEWAY")) {
                java.util.regex.Matcher defaultMatcher = java.util.regex.Pattern.compile(
                    "default=\"([^\"]+)\"").matcher(fullTag);
                if (defaultMatcher.find()) {
                    defaultFlow = defaultMatcher.group(1);
                }
            }
            
            log.debug("发现节点: id={}, name={}, type={}, defaultFlow={}", nodeId, nodeName, nodeType, defaultFlow);
            if (nodeId != null && saveNodeWithDefault(processConfigId, nodeId, nodeName, nodeType, defaultFlow)) {
                count++;
            }
        }
        return count;
    }
    
    private int parseUserTasks(String processConfigId, String bpmnXml) {
        int count = 0;
        // 匹配整个userTask标签（包括内容）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "<(bpmn:)?userTask([^>]*)>(.*?)</(bpmn:)?userTask>",
            java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(bpmnXml);
        while (matcher.find()) {
            String startTag = matcher.group(2);  // 开始标签的属性部分
            String content = matcher.group(3);   // 标签内容
            
            // 从属性中提取id
            java.util.regex.Matcher idMatcher = java.util.regex.Pattern.compile("id=\"([^\"]+)\"").matcher(startTag);
            if (!idMatcher.find()) continue;
            String nodeId = idMatcher.group(1);
            
            // 提取name
            java.util.regex.Matcher nameMatcher = java.util.regex.Pattern.compile("name=\"([^\"]*)\"").matcher(startTag);
            String nodeName = nameMatcher.find() ? nameMatcher.group(1) : "";
            
            log.debug("发现用户任务: id={}, name={}", nodeId, nodeName);
            // 合并startTag和content，用于解析assignee等属性
            String fullContent = startTag + ">" + content;
            if (saveNodeAndGetId(processConfigId, nodeId, nodeName, NodeConfig.NodeType.USER_TASK, fullContent) != null) {
                count++;
            }
        }
        return count;
    }
    
    private String saveNodeAndGetId(String processConfigId, String nodeId, String nodeName, NodeConfig.NodeType nodeType, String content) {
        try {
            // 解析 skipNode 配置
            boolean skipNode = false;
            String skipExpression = null;
            if (nodeType == NodeConfig.NodeType.USER_TASK) {
                // 检查属性形式：flowable:skipExpression="..."
                java.util.regex.Matcher skipMatcher = java.util.regex.Pattern.compile(
                    "flowable:skipExpression=\"([^\"]+)\"").matcher(content);
                // 检查子元素形式：<flowable:skipExpression>...</flowable:skipExpression>
                java.util.regex.Matcher skipElemMatcher = java.util.regex.Pattern.compile(
                    "<flowable:skipExpression>([^<]+)</flowable:skipExpression>").matcher(content);
                
                if (skipMatcher.find()) {
                    skipNode = true;
                    skipExpression = skipMatcher.group(1);
                } else if (skipElemMatcher.find()) {
                    skipNode = true;
                    skipExpression = skipElemMatcher.group(1).trim();
                }
            }
            
            NodeConfig node = new NodeConfig();
            node.setProcessConfigId(processConfigId);
            node.setNodeId(nodeId);
            node.setNodeName(nodeName);
            node.setNodeType(nodeType);
            node.setSkipNode(skipNode);
            nodeMapper.insert(node);
            
            // 获取刚插入的节点ID
            String nodeConfigId = null;
            java.util.List<NodeConfig> nodes = nodeMapper.findByProcessConfigId(processConfigId);
            for (NodeConfig n : nodes) {
                if (n.getNodeId().equals(nodeId)) {
                    nodeConfigId = n.getId();
                    break;
                }
            }
            
            if (nodeConfigId == null) {
                log.error("无法获取刚保存的节点ID: nodeId={}", nodeId);
                return null;
            }
            
            // 解析并保存用户任务的执行人配置
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
    
    /**
     * 解析并保存执行人配置
     */
    private void parseAndSaveAssigneeConfigs(String nodeConfigId, String content) {
        try {
            int priority = 0;
            
            // 1. 解析 flowable:assignee（指定执行人）
            java.util.regex.Matcher assigneeMatcher = java.util.regex.Pattern.compile(
                "flowable:assignee=\"([^\"]+)\"").matcher(content);
            if (assigneeMatcher.find()) {
                String assigneeValue = assigneeMatcher.group(1);
                AssigneeConfig assignee = new AssigneeConfig();
                assignee.setNodeConfigId(nodeConfigId);
                
                // 判断是表达式还是固定用户
                if (assigneeValue.startsWith("${") && assigneeValue.endsWith("}")) {
                    assignee.setAssigneeType(AssigneeConfig.AssigneeType.EXPRESSION);
                } else {
                    assignee.setAssigneeType(AssigneeConfig.AssigneeType.USER);
                }
                assignee.setAssigneeValue(assigneeValue);
                assignee.setPriority(priority++);
                assigneeMapper.insert(assignee);
                log.debug("保存执行人配置: nodeConfigId={}, type={}, value={}", 
                    nodeConfigId, assignee.getAssigneeType(), assigneeValue);
            }
            
            // 2. 解析 flowable:candidateUsers（候选人）
            java.util.regex.Matcher candidateUsersMatcher = java.util.regex.Pattern.compile(
                "flowable:candidateUsers=\"([^\"]+)\"").matcher(content);
            if (candidateUsersMatcher.find()) {
                String usersValue = candidateUsersMatcher.group(1);
                String[] users = usersValue.split(",");
                for (String user : users) {
                    user = user.trim();
                    if (user.isEmpty()) continue;
                    
                    AssigneeConfig assignee = new AssigneeConfig();
                    assignee.setNodeConfigId(nodeConfigId);
                    
                    // 判断是表达式还是固定用户
                    if (user.startsWith("${") && user.endsWith("}")) {
                        assignee.setAssigneeType(AssigneeConfig.AssigneeType.EXPRESSION);
                    } else {
                        assignee.setAssigneeType(AssigneeConfig.AssigneeType.USER);
                    }
                    assignee.setAssigneeValue(user);
                    assignee.setPriority(priority++);
                    assigneeMapper.insert(assignee);
                    log.debug("保存候选人配置: nodeConfigId={}, user={}", nodeConfigId, user);
                }
            }
            
            // 3. 解析 flowable:candidateGroups（候选组）
            java.util.regex.Matcher candidateGroupsMatcher = java.util.regex.Pattern.compile(
                "flowable:candidateGroups=\"([^\"]+)\"").matcher(content);
            if (candidateGroupsMatcher.find()) {
                String groupsValue = candidateGroupsMatcher.group(1);
                String[] groups = groupsValue.split(",");
                for (String group : groups) {
                    group = group.trim();
                    if (group.isEmpty()) continue;
                    
                    AssigneeConfig assignee = new AssigneeConfig();
                    assignee.setNodeConfigId(nodeConfigId);
                    assignee.setAssigneeType(AssigneeConfig.AssigneeType.ROLE);
                    assignee.setAssigneeValue(group);
                    assignee.setPriority(priority++);
                    assigneeMapper.insert(assignee);
                    log.debug("保存候选组配置: nodeConfigId={}, group={}", nodeConfigId, group);
                }
            }
        } catch (Exception e) {
            log.error("解析执行人配置失败: nodeConfigId={}", nodeConfigId, e);
        }
    }
    
    /**
     * 解析并保存表单配置
     */
    private void parseAndSaveFormConfig(String nodeConfigId, String content) {
        try {
            // 从 extensionElements 中解析 entityFormId
            java.util.regex.Matcher formIdMatcher = java.util.regex.Pattern.compile(
                "<flowable:entityFormId>([^<]+)</flowable:entityFormId>").matcher(content);
            java.util.regex.Matcher formKeyMatcher = java.util.regex.Pattern.compile(
                "flowable:formKey=\"([^\"]+)\"").matcher(content);
            java.util.regex.Matcher formReadonlyMatcher = java.util.regex.Pattern.compile(
                "<flowable:entityFormReadonly>([^<]+)</flowable:entityFormReadonly>").matcher(content);
            
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
                // 没有表单配置
                return;
            }
            
            // 解析只读配置
            boolean isReadonly = false;
            if (formReadonlyMatcher.find()) {
                String readonlyValue = formReadonlyMatcher.group(1);
                isReadonly = "true".equalsIgnoreCase(readonlyValue) || "1".equals(readonlyValue);
            }
            formConfig.setIsReadonly(isReadonly);
            
            formMapper.insert(formConfig);
            log.debug("保存表单配置: nodeConfigId={}, formKey={}, readonly={}", 
                nodeConfigId, formConfig.getFormKey(), isReadonly);
        } catch (Exception e) {
            log.error("解析表单配置失败: nodeConfigId={}", nodeConfigId, e);
        }
    }
    
    /**
     * 解析并保存多实例配置到config_json
     */
    private void parseAndSaveMultiInstanceConfig(String nodeConfigId, String content) {
        try {
            // 检查是否有多实例配置（兼容 <bpmn:multiInstanceLoopCharacteristics> 和 <multiInstanceLoopCharacteristics>）
            java.util.regex.Matcher miMatcher = java.util.regex.Pattern.compile(
                "<(bpmn:)?multiInstanceLoopCharacteristics[^>]*>",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(content);
            
            if (!miMatcher.find()) {
                return; // 没有多实例配置
            }
            
            java.util.Map<String, Object> miConfig = new java.util.HashMap<>();
            miConfig.put("multiInstance", true);
            
            // 解析 isSequential（串行/并行）
            java.util.regex.Matcher seqMatcher = java.util.regex.Pattern.compile(
                "isSequential=\"(true|false)\"").matcher(content);
            if (seqMatcher.find()) {
                boolean isSequential = "true".equalsIgnoreCase(seqMatcher.group(1));
                miConfig.put("isSequential", isSequential);
                miConfig.put("type", isSequential ? "sequential" : "parallel");
            }
            
            // 解析 collection 表达式（兼容 flowable:collection 和无前缀 collection）
            java.util.regex.Matcher collMatcher = java.util.regex.Pattern.compile(
                "(?:flowable:)?collection=\"([^\"]+)\"").matcher(content);
            if (collMatcher.find()) {
                miConfig.put("collection", collMatcher.group(1));
            }
            
            // 解析 elementVariable（兼容 flowable:elementVariable 和无前缀 elementVariable）
            java.util.regex.Matcher varMatcher = java.util.regex.Pattern.compile(
                "(?:flowable:)?elementVariable=\"([^\"]+)\"").matcher(content);
            if (varMatcher.find()) {
                miConfig.put("elementVariable", varMatcher.group(1));
            }
            
            // 解析 completionCondition
            java.util.regex.Matcher ccMatcher = java.util.regex.Pattern.compile(
                "<completionCondition[^>]*>([^<]+)</completionCondition>",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(content);
            if (ccMatcher.find()) {
                miConfig.put("completionCondition", ccMatcher.group(1).trim());
            }
            
            // 合并到 node_config 的 config_json
            mergeConfigJson(nodeConfigId, miConfig);
            
            log.debug("保存多实例配置: nodeConfigId={}, config={}", nodeConfigId, miConfig);
        } catch (Exception e) {
            log.error("解析多实例配置失败: nodeConfigId={}", nodeConfigId, e);
        }
    }
    
    /**
     * 解析并保存审批配置到 config_json
     */
    private void parseAndSaveApprovalConfig(String nodeConfigId, String content) {
        try {
            // 从 flowable:Properties 中解析 approvalConfig
            // BPMN XML 中存储为: <flowable:property name="approvalConfig" value="{...}" />
            // 先尝试匹配 name="approvalConfig" 在前、value 在后的顺序
            java.util.regex.Pattern propPattern = java.util.regex.Pattern.compile(
                "<(?:flowable:|camunda:)?property[^>]*name=\"approvalConfig\"[^>]*value=\"([^\"]*)\"",
                java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher propMatcher = propPattern.matcher(content);
            
            // 如果失败，尝试 value 在前、name 在后的顺序
            if (!propMatcher.find()) {
                propPattern = java.util.regex.Pattern.compile(
                    "<(?:flowable:|camunda:)?property[^>]*value=\"([^\"]*)\"[^>]*name=\"approvalConfig\"",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
                propMatcher = propPattern.matcher(content);
            }
            
            if (!propMatcher.find()) {
                // 尝试从子元素形式匹配
                java.util.regex.Matcher elemMatcher = java.util.regex.Pattern.compile(
                    "<(?:flowable:|camunda:)?approvalConfig>([^<]+)</(?:flowable:|camunda:)?approvalConfig>",
                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(content);
                if (elemMatcher.find()) {
                    String approvalConfigJson = elemMatcher.group(1).trim();
                    java.util.Map<String, Object> approvalConfig = objectMapper.readValue(approvalConfigJson, java.util.HashMap.class);
                    mergeConfigJson(nodeConfigId, approvalConfig);
                    log.debug("保存审批配置(子元素): nodeConfigId={}, config={}", nodeConfigId, approvalConfigJson);
                }
                return;
            }
            
            String approvalConfigJson = propMatcher.group(1);
            // 处理可能的 XML 转义
            approvalConfigJson = approvalConfigJson.replace("&quot;", "\"")
                                                   .replace("&amp;", "&")
                                                   .replace("&lt;", "<")
                                                   .replace("&gt;", ">");
            java.util.Map<String, Object> approvalConfig = objectMapper.readValue(approvalConfigJson, java.util.HashMap.class);
            mergeConfigJson(nodeConfigId, approvalConfig);
            
            log.debug("保存审批配置: nodeConfigId={}, config={}", nodeConfigId, approvalConfigJson);
        } catch (Exception e) {
            log.error("解析审批配置失败: nodeConfigId={}", nodeConfigId, e);
        }
    }
    
    /**
     * 合并配置到 node_config 的 config_json 中
     */
    private void mergeConfigJson(String nodeConfigId, java.util.Map<String, Object> newConfig) {
        try {
            // 读取现有 config_json
            String existingJson = jdbcTemplate.queryForObject(
                "SELECT config_json FROM node_config WHERE id = ?",
                String.class, nodeConfigId);
            
            java.util.Map<String, Object> mergedConfig = new java.util.HashMap<>();
            if (existingJson != null && !existingJson.isEmpty()) {
                try {
                    mergedConfig = objectMapper.readValue(existingJson, java.util.HashMap.class);
                } catch (Exception e) {
                    log.warn("解析现有 config_json 失败，将覆盖: nodeConfigId={}", nodeConfigId);
                }
            }
            
            // 合并新配置
            mergedConfig.putAll(newConfig);
            
            // 写回数据库
            String configJson = objectMapper.writeValueAsString(mergedConfig);
            jdbcTemplate.update(
                "UPDATE node_config SET config_json = ? WHERE id = ?",
                configJson, nodeConfigId);
                
            log.debug("合并 config_json: nodeConfigId={}, result={}", nodeConfigId, configJson);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            log.warn("节点不存在，无法合并配置: nodeConfigId={}", nodeConfigId);
        } catch (Exception e) {
            log.error("合并 config_json 失败: nodeConfigId={}", nodeConfigId, e);
        }
    }
    
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    
    private boolean saveNode(String processConfigId, String nodeId, String nodeName, NodeConfig.NodeType nodeType) {
        return saveNodeAndGetId(processConfigId, nodeId, nodeName, nodeType, "") != null;
    }
    
    private boolean saveNodeWithDefault(String processConfigId, String nodeId, String nodeName, NodeConfig.NodeType nodeType, String defaultFlow) {
        try {
            NodeConfig node = new NodeConfig();
            node.setProcessConfigId(processConfigId);
            node.setNodeId(nodeId);
            node.setNodeName(nodeName);
            node.setNodeType(nodeType);
            node.setSkipNode(false);
            
            // 如果有默认流，保存到config_json
            if (defaultFlow != null && !defaultFlow.isEmpty()) {
                java.util.Map<String, Object> config = new java.util.HashMap<>();
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
    
    /**
     * 测试节点解析（开发测试用）
     */
    public void testParseNodes(String processConfigId) {
        ProcessDefinitionConfig config = processMapper.selectById(processConfigId);
        if (config == null) {
            throw new RuntimeException("Process not found");
        }
        String bpmnXml = config.getBpmnXml();
        log.info("测试解析流程 {}, BPMN XML长度: {}", processConfigId, bpmnXml.length());
        parseAndSaveNodeConfigs(processConfigId, bpmnXml);
    }
}
