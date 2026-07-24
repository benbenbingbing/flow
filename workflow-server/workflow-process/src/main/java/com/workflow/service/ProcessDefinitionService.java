package com.workflow.service;

import com.workflow.dto.*;
import com.workflow.dto.migration.ConfigMigrationPublishRequest;
import com.workflow.entity.*;
import com.workflow.mapper.*;
import com.workflow.contracts.action.FlowActionDesignPort;
import com.workflow.contracts.migration.MigrationAssetRecorder;
import com.workflow.process.definition.ProcessBpmnPublishSanitizer;
import com.workflow.process.definition.ProcessDefinitionNodeSyncService;
import com.workflow.process.definition.ProcessFlowableDeploymentService;
import com.workflow.process.definition.ProcessPublishHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.repository.Deployment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.common.PageResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程定义服务。
 *
 * <p>负责流程定义的增删改查、分页查询、发布（部署到 Flowable 并记录版本历史）、
 * 回滚、禁用及版本管理等核心能力。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDefinitionService {
    
    private final ProcessDefinitionConfigMapper processMapper;
    private final ProcessVersionHistoryMapper versionHistoryMapper;
    private final ProcessFlowableDeploymentService flowableDeploymentService;
    private final ProcessPublishHistoryService publishHistoryService;
    private final ProcessDefinitionNodeSyncService nodeSyncService;
    private final ProcessBpmnPublishSanitizer bpmnPublishSanitizer;
    private final FlowActionDesignPort flowActionDesignPort;
    private final MigrationAssetRecorder migrationAssetRecorder;
    
    /**
     * 查询所有启用的流程定义。
     *
     * @return 流程定义列表
     */
    @Transactional(readOnly = true)
    public List<ProcessDefinitionDTO> findAll() {
        return processMapper.findAllActive().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 分页查询流程定义（支持按状态、分类、关键词筛选）。
     *
     * @param query 查询条件（含分页与筛选参数）
     * @return 流程定义分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ProcessDefinitionDTO> findPage(ProcessDefinitionQueryDTO query) {
        Page<ProcessDefinitionConfig> page = new Page<>(
                query.getPageNum() != null && query.getPageNum() > 0 ? query.getPageNum() : 1,
                query.getPageSize() != null && query.getPageSize() > 0 ? query.getPageSize() : 10
        );

        LambdaQueryWrapper<ProcessDefinitionConfig> wrapper = Wrappers.<ProcessDefinitionConfig>lambdaQuery();
        wrapper.eq(ProcessDefinitionConfig::getDeleted, 0)
               .orderByDesc(ProcessDefinitionConfig::getUpdatedAt);

        if (StringUtils.isNotBlank(query.getStatus())) {
            wrapper.eq(ProcessDefinitionConfig::getStatus, query.getStatus());
        }
        if (StringUtils.isNotBlank(query.getCategory())) {
            wrapper.eq(ProcessDefinitionConfig::getCategory, query.getCategory());
        }
        if (StringUtils.isNotBlank(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(ProcessDefinitionConfig::getProcessName, keyword)
                    .or()
                    .like(ProcessDefinitionConfig::getProcessKey, keyword));
        }

        Page<ProcessDefinitionConfig> resultPage = processMapper.selectPage(page, wrapper);
        List<ProcessDefinitionDTO> records = resultPage.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return new PageResult<>(records, resultPage.getTotal(), resultPage.getCurrent(), resultPage.getSize());
    }
    
    /**
     * 按状态查询流程定义。
     *
     * @param status 流程状态
     * @return 流程定义列表
     */
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
    
    /**
     * 根据流程定义ID查询详情。
     *
     * @param id 流程定义ID
     * @return 流程定义详情
     * @throws RuntimeException 流程不存在时抛出
     */
    @Transactional(readOnly = true)
    public ProcessDefinitionDTO findById(String id) {
        ProcessDefinitionConfig config = processMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("Process not found: " + id);
        }
        return convertToDTO(config);
    }
    
    /**
     * 根据流程标识查询详情。
     *
     * @param processKey 流程标识
     * @return 流程定义详情
     * @throws RuntimeException 流程不存在时抛出
     */
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
    
    /**
     * 新建流程定义（初始版本为0，状态为草稿）。
     *
     * @param dto 流程定义数据
     * @return 创建后的流程定义
     */
    @Transactional
    public ProcessDefinitionDTO save(ProcessDefinitionDTO dto) {
        ProcessDefinitionConfig config = convertToEntity(dto);
        config.setVersion(0); // 初始版本为0，表示从未发布
        config.setStatus(ProcessDefinitionConfig.ProcessStatus.DRAFT);
        processMapper.insert(config);
        return convertToDTO(config);
    }
    
    /**
     * 更新流程定义信息及BPMN XML，并同步节点配置与节点表单绑定。
     *
     * @param id  流程定义ID
     * @param dto 更新的流程定义数据
     * @return 更新后的流程定义
     * @throws RuntimeException 流程不存在时抛出
     */
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
            nodeSyncService.syncDraftNodes(id, dto.getNodes());
        }
        
        // 同步 BPMN XML 中的节点表单配置到 process_node_form 表
        if (dto.getBpmnXml() != null && !dto.getBpmnXml().isEmpty()) {
            nodeSyncService.syncBpmnNodeBindings(id, dto.getBpmnXml());
        }
        
        return convertToDTO(existing);
    }
    
    /**
     * 逻辑删除流程定义（置为已禁用并标记删除）。
     *
     * @param id 流程定义ID
     * @throws RuntimeException 流程不存在时抛出
     */
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
        ConfigMigrationPublishRequest request = new ConfigMigrationPublishRequest();
        request.setVersionDescription(versionDescription);
        return publish(id, request);
    }

    /**
     * 发布流程定义（带完整发布请求）。
     *
     * <p>校验动作配置 -> 生成新版本号 -> 净化BPMN -> 部署到Flowable -> 记录版本历史 ->
     * 解析保存节点配置 -> 更新主配置状态为已发布。</p>
     *
     * @param id      流程定义ID
     * @param request 发布请求（含版本说明等）
     * @return 发布后的流程定义
     * @throws RuntimeException 流程不存在或缺少BPMN XML时抛出
     */
    @Transactional
    public ProcessDefinitionDTO publish(String id, ConfigMigrationPublishRequest request) {
        ProcessDefinitionConfig config = processMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("Process not found: " + id);
        }
        
        if (config.getBpmnXml() == null || config.getBpmnXml().isEmpty()) {
            throw new RuntimeException("BPMN XML is required for publishing");
        }

        // 校验流程动作配置合法性
        flowActionDesignPort.validateForPublish(id);
        
        int newVersion = publishHistoryService.nextVersion(id);
        
        // 将 processKey 写入 XML 的 process id 属性，确保 Flowable 使用正确的 key
        String designBpmnXml = config.getBpmnXml();
        
        nodeSyncService.syncStatusMappingsFromBpmn(id, config.getProcessKey(), designBpmnXml);
        
        String runtimeBpmnXml = bpmnPublishSanitizer.sanitize(designBpmnXml, config.getProcessKey());

        // 清理历史版本中平台注入的顺序流动作监听器；运行时已由统一事件监听器接管
        runtimeBpmnXml = flowActionDesignPort.prepareBpmnForPublish(id, runtimeBpmnXml);

        nodeSyncService.syncBpmnNodeBindings(id, designBpmnXml);
        
        Deployment deployment = flowableDeploymentService.deploy(config, runtimeBpmnXml, newVersion);
        
        ConfigMigrationPublishRequest publishRequest = request == null
                ? new ConfigMigrationPublishRequest() : request;
        ProcessVersionHistory history = publishHistoryService.recordPublish(
                config, runtimeBpmnXml, deployment.getId(), newVersion, publishRequest.getVersionDescription());
        
        nodeSyncService.parseAndSaveNodeConfigs(id, designBpmnXml);
        // 更新主流程配置
        config.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
        config.setVersion(newVersion);
        processMapper.updateById(config);
        migrationAssetRecorder.recordProcess(config.getId(), history.getId(), publishRequest);
        
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
    
    /**
     * 禁用流程定义（禁用后不能发起新实例）。
     *
     * @param id 流程定义ID
     * @return 禁用后的流程定义
     */
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
    
    /**
     * 逻辑删除指定版本（同时逻辑删除版本关联的流程动作）。
     *
     * @param versionId 版本历史记录ID
     * @throws RuntimeException 版本不存在时抛出
     */
    @Transactional
    public void deleteVersion(String versionId) {
        ProcessVersionHistory version = versionHistoryMapper.selectById(versionId);
        if (version == null) {
            throw new RuntimeException("Version not found: " + versionId);
        }
        
        // 逻辑删除版本关联的流程动作
        flowActionDesignPort.deleteActionsByVersionId(versionId);
        
        // 逻辑删除版本记录
        version.setDeleted(1);
        versionHistoryMapper.updateById(version);
        
        log.info("Logic deleted version {}", versionId);
    }
    
    // Convert methods
    /** 将流程定义配置实体转换为DTO */
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
    
    /** 将版本历史实体转换为DTO */
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
    
    /** 将流程定义DTO转换为配置实体 */
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
     * 测试节点解析（开发测试用）
     */
    public void testParseNodes(String processConfigId) {
        ProcessDefinitionConfig config = processMapper.selectById(processConfigId);
        if (config == null) {
            throw new RuntimeException("Process not found");
        }
        String bpmnXml = config.getBpmnXml();
        log.info("测试解析流程 {}, BPMN XML长度: {}", processConfigId, bpmnXml.length());
        nodeSyncService.parseAndSaveNodeConfigs(processConfigId, bpmnXml);
    }
}
