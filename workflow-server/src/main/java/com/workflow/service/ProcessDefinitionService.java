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
        
        return convertToDTO(config);
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
}
