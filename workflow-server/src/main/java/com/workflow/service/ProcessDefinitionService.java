package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.*;
import com.workflow.entity.*;
import com.workflow.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final FlowActionService flowActionService;
    
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
        
        return convertToDTO(existing);
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
        
        // 清理前端 camunda 命名空间（Flowable 不识别 camunda 扩展）
        // 策略：1) 移除camunda命名空间声明 2) 提取camunda属性值并转换为flowable 3) 移除camunda元素和属性
        
        // 1. 移除 camunda 命名空间声明
        bpmnXml = bpmnXml.replaceAll("\\s+xmlns:camunda=\"[^\"]*\"", "");
        
        // 2. 提取 camunda:assignee/candidateGroups/candidateUsers 的值，然后移除这些属性
        // 先提取值保存到变量，后续用于设置flowable属性
        java.util.regex.Pattern assigneePattern = java.util.regex.Pattern.compile("camunda:assignee=\"([^\"]*)\"");
        java.util.regex.Matcher assigneeMatcher = assigneePattern.matcher(bpmnXml);
        while (assigneeMatcher.find()) {
            String value = assigneeMatcher.group(1);
            // 移除camunda:assignee，后面会添加flowable:assignee
            bpmnXml = bpmnXml.replace(assigneeMatcher.group(0), "");
            // 同时移除普通assignee（如果有），避免重复
            bpmnXml = bpmnXml.replaceAll("(?<!flowable:)assignee=\"" + java.util.regex.Pattern.quote(value) + "\"", "");
            // 添加flowable:assignee
            bpmnXml = bpmnXml.replace(assigneeMatcher.group(0) + "", " flowable:assignee=\"" + value + "\"");
        }
        
        // 3. 移除所有camunda元素（properties等）
        java.util.regex.Pattern camundaElementPattern = java.util.regex.Pattern.compile(
            "<camunda:[^>]*>[\\s\\S]*?</camunda:[^>]*>", 
            java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher matcher;
        int maxIterations = 10;
        for (int i = 0; i < maxIterations; i++) {
            matcher = camundaElementPattern.matcher(bpmnXml);
            if (!matcher.find()) break;
            bpmnXml = matcher.replaceAll("");
        }
        
        // 4. 移除所有剩余的camunda属性
        bpmnXml = bpmnXml.replaceAll("\\s+camunda:[^=\\s]*=\"[^\"]*\"", "");
        
        // 5. 清理其他无效属性，并将无命名空间的属性转为flowable
        bpmnXml = bpmnXml.replaceAll("\\s+extensionProperties=\"[^\"]*\"", "");
        // 注意：只转换那些还没有flowable:前缀的属性
        bpmnXml = bpmnXml.replaceAll("(?<!flowable:)candidateGroups=\"([^\"]*)\"", "flowable:candidateGroups=\"$1\"");
        bpmnXml = bpmnXml.replaceAll("(?<!flowable:)candidateUsers=\"([^\"]*)\"", "flowable:candidateUsers=\"$1\"");
        // assignee要特别小心，可能已经处理了
        bpmnXml = bpmnXml.replaceAll("(?<!flowable:)\\sassignee=\"([^\"]*)\"", " flowable:assignee=\"$1\"");
        
        // 处理 skipNode 配置：为设置了 skipNode=true 的用户任务添加 skipExpression
        bpmnXml = processSkipNodeTasks(bpmnXml);
        
        // 添加 flowable 命名空间声明（如果不存在）
        if (!bpmnXml.contains("xmlns:flowable")) {
            bpmnXml = bpmnXml.replace(
                "xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\""
            );
        }
        
        // 替换 <bpmn:process id="xxx" 为 <bpmn:process id="{processKey}"
        bpmnXml = bpmnXml.replaceAll("<bpmn:process\\s+id=\"[^\"]+\"", 
                "<bpmn:process id=\"" + config.getProcessKey() + "\"");
        // 只替换 BPMNPlane 的 bpmnElement（指向 process），不替换 BPMNShape 的（指向具体元素）
        // BPMNPlane 的格式: <bpmndi:BPMNPlane id="..." bpmnElement="xxx"
        bpmnXml = bpmnXml.replaceAll(
                "(<bpmndi:BPMNPlane[^>]*\\s)bpmnElement=\"[^\"]+\"", 
                "$1bpmnElement=\"" + config.getProcessKey() + "\"");
        
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
     * 处理 skipNode 配置：为设置了 skipNode=true 的用户任务添加 skipExpression
     * 使用 Flowable 的 skipExpression 功能，当表达式为 true 时自动跳过任务
     */
    private String processSkipNodeTasks(String bpmnXml) {
        // 使用正则表达式查找所有用户任务
        java.util.regex.Pattern taskPattern = java.util.regex.Pattern.compile(
            "<bpmn:userTask([^>]*)>(.*?)</bpmn:userTask>", 
            java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher taskMatcher = taskPattern.matcher(bpmnXml);
        
        StringBuffer result = new StringBuffer();
        while (taskMatcher.find()) {
            String taskAttrs = taskMatcher.group(1);
            String taskContent = taskMatcher.group(2);
            
            // 检查是否包含 skipNode=true 的 Property（可能是 camunda:Property 或普通 Property）
            boolean hasSkipNode = taskContent.contains("name=\"skipNode\" value=\"true\"") ||
                                  taskContent.contains("name=\"skipNode\" value=\"true\"");
            
            if (hasSkipNode) {
                // 提取任务ID
                java.util.regex.Matcher idMatcher = java.util.regex.Pattern.compile("id=\"([^\"]+)\"").matcher(taskAttrs);
                String taskId = idMatcher.find() ? idMatcher.group(1) : "";
                
                // 添加 flowable:skipExpression 属性到任务标签
                if (!taskAttrs.contains("flowable:skipExpression")) {
                    taskAttrs += " flowable:skipExpression=\"${skipNodeEnabled}\"";
                }
                
                // 重新组装任务标签
                String newTask = "<bpmn:userTask" + taskAttrs + ">" + taskContent + "</bpmn:userTask>";
                taskMatcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(newTask));
                
                log.info("为用户任务 [{}] 添加 skipExpression: ${skipNodeEnabled}", taskId);
            }
        }
        taskMatcher.appendTail(result);
        
        return result.toString();
    }
}
