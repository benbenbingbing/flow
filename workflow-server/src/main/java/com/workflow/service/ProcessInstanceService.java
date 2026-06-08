package com.workflow.service;

import com.workflow.common.PageResult;
import com.workflow.common.Result;
import com.workflow.dto.ProcessProgressDTO;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.runtime.ProcessDetailRuntimeService;
import com.workflow.process.runtime.ProcessProgressRuntimeService;
import com.workflow.process.runtime.ProcessTerminationService;
import com.workflow.vo.MyStartedProcessVO;
import com.workflow.vo.ProcessDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 流程实例服务
 * 用于查询流程实例的执行进度、历史记录等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessInstanceService {
    
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final ProcessDefinitionConfigMapper processConfigMapper;
    private final SysUserService sysUserService;
    private final com.workflow.service.EntityDataService entityDataService;
    private final com.workflow.service.EntityDataDynamicService entityDataDynamicService;
    private final ProcessProgressRuntimeService processProgressRuntimeService;
    private final ProcessDetailRuntimeService processDetailRuntimeService;
    private final ProcessTerminationService processTerminationService;
    
    
    /**
     * 格式化日期为字符串
     */
    private String formatDate(java.util.Date date) {
        if (date == null) return null;
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }
    
    /**
     * 获取流程实例的执行进度
     * 
     * @param processInstanceId 流程实例ID
     * @return 流程进度信息
     */
    public ProcessProgressDTO getProcessProgress(String processInstanceId) {
        return processProgressRuntimeService.getProcessProgress(processInstanceId);
    }
    
    /**
     * 根据流程实例ID获取BPMN XML（公共方法）
     * 
     * @param processInstanceId 流程实例ID
     * @return BPMN XML
     */
    public String getBpmnXmlByProcessInstanceId(String processInstanceId) {
        return getBpmnXmlByInstanceId(processInstanceId);
    }
    
    /**
     * 根据流程定义Key获取BPMN XML
     * 
     * @param processKey 流程标识
     * @return BPMN XML
     */
    public String getBpmnXmlByProcessKey(String processKey) {
        ProcessDefinitionConfig config = processConfigMapper.findByProcessKey(processKey).orElse(null);
        if (config != null && config.getBpmnXml() != null) {
            return config.getBpmnXml();
        }
        
        // 从 Flowable 获取
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
        
        if (processDefinition != null) {
            try {
                org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
                // 需要转换为 XML，这里先返回 null，实际使用时从配置表获取
                return null;
            } catch (Exception e) {
                log.error("获取 BPMN XML 失败", e);
            }
        }
        return null;
    }
    
    /**
     * 获取流程实例详情
     * 
     * @param instanceId 流程实例ID
     * @return 流程详情
     */
    public ProcessDetailVO getProcessDetail(String instanceId) {
        return processDetailRuntimeService.getProcessDetail(instanceId);
    }
    
    /**
     * 获取活动节点名称
     */
    private String getActivityName(String activityId, String processDefinitionId) {
        try {
            org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
            if (bpmnModel != null) {
                org.flowable.bpmn.model.FlowElement element = bpmnModel.getFlowElement(activityId);
                if (element != null) {
                    return element.getName();
                }
            }
        } catch (Exception e) {
            log.warn("获取节点名称失败: activityId={}", activityId, e);
        }
        return activityId;
    }
    
    /**
     * 根据流程实例ID获取BPMN XML
     */
    private String getBpmnXmlByInstanceId(String instanceId) {
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        
        if (historicInstance == null) {
            return null;
        }
        
        String processDefinitionId = historicInstance.getProcessDefinitionId();
        return getBpmnXmlByProcessDefinitionId(processDefinitionId);
    }
    
    /**
     * 根据流程定义ID获取BPMN XML
     */
    private String getBpmnXmlByProcessDefinitionId(String processDefinitionId) {
        if (processDefinitionId == null) {
            return null;
        }
        
        try {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId)
                    .singleResult();
            
            if (processDefinition == null) {
                return null;
            }
            
            // 先从 Model 获取
            try {
                org.flowable.engine.repository.Model model = repositoryService.getModel(processDefinition.getId());
                if (model != null) {
                    byte[] modelBytes = repositoryService.getModelEditorSource(model.getId());
                    if (modelBytes != null) {
                        return new String(modelBytes, java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
            } catch (Exception e) {
                log.debug("无法从 Model 获取 BPMN XML", e);
            }
            
            // 从部署资源获取
            String resourceName = processDefinition.getResourceName();
            if (resourceName != null) {
                org.flowable.engine.repository.Deployment deployment = repositoryService.createDeploymentQuery()
                        .deploymentId(processDefinition.getDeploymentId())
                        .singleResult();
                if (deployment != null) {
                    java.io.InputStream resourceStream = repositoryService.getResourceAsStream(
                            deployment.getId(), resourceName);
                    if (resourceStream != null) {
                        return new String(resourceStream.readAllBytes(), 
                                java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取 BPMN XML 失败", e);
        }
        
        return null;
    }
    
    /**
     * 获取我发起的流程列表
     * 
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param processName 流程名称（可选筛选）
     * @return 流程列表
     */
    public PageResult<MyStartedProcessVO> getMyStartedList(String userId, Integer pageNum, Integer pageSize, String processName) {
        // 查询历史流程实例（包含运行中和已结束的）
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
                .startedBy(userId)
                .orderByProcessInstanceStartTime()
                .desc();
        
        // 获取总数
        long total = query.count();
        
        // 分页查询
        int firstResult = (pageNum - 1) * pageSize;
        List<HistoricProcessInstance> historicInstances = query.listPage(firstResult, pageSize);
        
        // 转换为VO
        List<MyStartedProcessVO> list = new ArrayList<>();
        for (HistoricProcessInstance historicInstance : historicInstances) {
            MyStartedProcessVO vo = new MyStartedProcessVO();
            vo.setProcessInstanceId(historicInstance.getId());
            vo.setProcessDefinitionId(historicInstance.getProcessDefinitionId());
            vo.setBusinessKey(historicInstance.getBusinessKey());
            String startUserId = historicInstance.getStartUserId();
            vo.setStartUser(startUserId);
            if (startUserId != null && !startUserId.isEmpty()) {
                String nickname = sysUserService.getNicknameByUsername(startUserId);
                vo.setStartUserName(nickname != null && !nickname.isEmpty() ? nickname : startUserId);
            }
            vo.setStartTime(formatDate(historicInstance.getStartTime()));
            vo.setEndTime(formatDate(historicInstance.getEndTime()));
            
            // 获取流程名称
            String processDefinitionId = historicInstance.getProcessDefinitionId();
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId)
                    .singleResult();
            if (processDefinition != null) {
                vo.setProcessKey(processDefinition.getKey());
                String procName = processDefinition.getName();
                if (procName == null || procName.isEmpty()) {
                    ProcessDefinitionConfig config = processConfigMapper.findByProcessKey(processDefinition.getKey()).orElse(null);
                    if (config != null) {
                        procName = config.getProcessName();
                    }
                }
                vo.setProcessName(procName != null ? procName : processDefinition.getKey());
                
                // 流程名称筛选
                if (processName != null && !processName.isEmpty() && 
                    (vo.getProcessName() == null || !vo.getProcessName().contains(processName))) {
                    continue;
                }
            }
            
            // 获取数据标题（从实体数据）
            try {
                String entityDataId = (String) historicInstance.getProcessVariables().get("entityDataId");
                String entityCode = (String) historicInstance.getProcessVariables().get("entityCode");
                if (entityDataId == null) {
                    // 从历史变量查询
                    var varInstance = historyService.createHistoricVariableInstanceQuery()
                            .processInstanceId(historicInstance.getId())
                            .variableName("entityDataId")
                            .singleResult();
                    if (varInstance != null) {
                        entityDataId = (String) varInstance.getValue();
                    }
                }
                if (entityCode == null) {
                    var codeVar = historyService.createHistoricVariableInstanceQuery()
                            .processInstanceId(historicInstance.getId())
                            .variableName("entityCode")
                            .singleResult();
                    if (codeVar != null) {
                        entityCode = (String) codeVar.getValue();
                    }
                }
                if (entityDataId != null) {
                    com.workflow.dto.EntityDataDTO entityData = null;
                    if (entityCode != null) {
                        try {
                            entityData = entityDataDynamicService.findById(entityCode, entityDataId);
                        } catch (Exception ex) {
                            // fallback
                        }
                    }
                    if (entityData == null) {
                        entityData = entityDataService.findById(entityDataId);
                    }
                    if (entityData != null) {
                        if (entityData.getData() != null) {
                            vo.setDataName((String) entityData.getData().get("name"));
                        }
                        vo.setName(entityData.getName());
                        vo.setCode(entityData.getCode());
                    }
                }
            } catch (Exception e) {
                log.debug("获取数据标题失败: {}", e.getMessage());
            }
            
            // 判断流程状态
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(historicInstance.getId())
                    .singleResult();
            
            if (processInstance != null) {
                // 流程还在运行中
                if (processInstance.isSuspended()) {
                    vo.setStatus("SUSPENDED");
                    vo.setStatusText("已挂起");
                } else {
                    vo.setStatus("RUNNING");
                    vo.setStatusText("运行中");
                }
                
                // 获取当前节点
                List<Execution> executions = runtimeService.createExecutionQuery()
                        .processInstanceId(historicInstance.getId())
                        .list();
                String currentNode = executions.stream()
                        .filter(e -> e.getActivityId() != null)
                        .map(e -> getActivityName(e.getActivityId(), processDefinitionId))
                        .findFirst()
                        .orElse("处理中");
                vo.setCurrentNodeName(currentNode);
            } else {
                // 流程已结束
                if (historicInstance.getEndTime() != null) {
                    // 检查是否是终止（通过检查删除原因）
                    String deleteReason = historicInstance.getDeleteReason();
                    if (deleteReason != null && (deleteReason.contains("终止") || deleteReason.contains("terminated"))) {
                        vo.setStatus("TERMINATED");
                        vo.setStatusText("已终止");
                    } else {
                        vo.setStatus("COMPLETED");
                        vo.setStatusText("已完成");
                    }
                    vo.setCurrentNodeName("-");
                } else {
                    vo.setStatus("UNKNOWN");
                    vo.setStatusText("未知");
                }
            }
            
            list.add(vo);
        }
        
        // 由于可能在循环中过滤，需要重新计算分页
        // 为了简化，这里不做精确分页，如果需要精确分页需要在外层查询后统一过滤
        return new PageResult<>(list, total, pageNum, pageSize);
    }
    
    /**
     * 终止流程实例
     * 
     * @param processInstanceId 流程实例ID
     * @param userId 操作用户ID
     * @param reason 终止原因
     * @return 是否成功
     */
    public Result<Void> terminateProcess(String processInstanceId, String userId, String reason) {
        return processTerminationService.terminateProcess(processInstanceId, userId, reason);
    }


}
