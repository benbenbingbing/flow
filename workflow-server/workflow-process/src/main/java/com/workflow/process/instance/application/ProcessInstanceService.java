package com.workflow.process.instance.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import com.workflow.process.instance.api.response.ProcessProgressDTO;
import com.workflow.process.task.api.request.ReceiveTaskTriggerRequest;
import com.workflow.process.task.application.operation.NodeOperationCapabilityService;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.instance.application.ProcessDetailRuntimeService;
import com.workflow.process.instance.application.ProcessProgressRuntimeService;
import com.workflow.process.instance.application.ProcessTerminationService;
import com.workflow.process.workbench.api.response.MyStartedProcessVO;
import com.workflow.entity.definition.application.EntityStatusService;
import com.workflow.process.instance.api.response.ProcessDetailVO;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 流程实例服务
 * 用于查询流程实例的执行进度、历史记录等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessInstanceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @org.springframework.beans.factory.annotation.Autowired
    private com.workflow.process.instance.infrastructure.persistence.mapper.StartedProcessPageMapper startedPageMapper;
    
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final ProcessDefinitionConfigMapper processConfigMapper;
    private final SysUserService sysUserService;
    private final com.workflow.entity.data.application.EntityDataDynamicService entityDataDynamicService;
    private final ProcessProgressRuntimeService processProgressRuntimeService;
    private final ProcessInstanceAccessService processInstanceAccessService;
    private final ProcessDetailRuntimeService processDetailRuntimeService;
    private final ProcessTerminationService processTerminationService;
    private final NodeOperationCapabilityService nodeOperationCapabilityService;
    private final EntityStatusService entityStatusService;
    
    
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
        return getProcessProgress(processInstanceId, null);
    }

    public ProcessProgressDTO getProcessProgress(
            String processInstanceId,
            String taskId) {
        processInstanceAccessService.requireReadAccess(processInstanceId);
        return processProgressRuntimeService.getProcessProgress(
                processInstanceId, taskId);
    }
    
    /**
     * 根据流程实例ID获取BPMN XML（公共方法）
     * 
     * @param processInstanceId 流程实例ID
     * @return BPMN XML
     */
    public String getBpmnXmlByProcessInstanceId(String processInstanceId) {
        processInstanceAccessService.requireReadAccess(processInstanceId);
        return getBpmnXmlByInstanceId(processInstanceId);
    }

    /**
     * 触发流程中处于等待状态的接收任务（ReceiveTask）继续向下流转。
     *
     * @param processInstanceId 流程实例ID
     * @param request           触发请求（executionId/activityId 二选一定位，可校验消息标识）
     * @return 被触发的执行实例ID
     * @throws IllegalArgumentException 流程实例不存在、未处于接收任务节点或消息标识不匹配时抛出
     */
    public String triggerReceiveTask(
            String processInstanceId,
            ReceiveTaskTriggerRequest request) {
        processInstanceAccessService.requireSignalAccess(processInstanceId);
        if (request == null) {
            throw new IllegalArgumentException("接收任务触发参数不能为空");
        }
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (processInstance == null) {
            throw new IllegalArgumentException("流程实例不存在或已结束: " + processInstanceId);
        }

        Execution execution = resolveReceiveExecution(processInstanceId, request);
        org.flowable.bpmn.model.FlowElement flowElement = repositoryService
                .getBpmnModel(processInstance.getProcessDefinitionId())
                .getFlowElement(execution.getActivityId());
        if (!(flowElement instanceof org.flowable.bpmn.model.ReceiveTask)) {
            throw new IllegalArgumentException("当前执行不在接收任务节点: " + execution.getActivityId());
        }
        validateReceiveMessage(flowElement, request.getMessageRef());
        Map<String, Object> variables = WorkflowReservedVariables
                .sanitizeRuntimeMutation(request.getVariables());
        runtimeService.trigger(execution.getId(), variables);
        return execution.getId();
    }

    /**
     * 解析接收任务对应的执行实例。
     *
     * <p>优先使用 executionId；未提供时按 activityId 定位，存在多个时要求显式指定 executionId。</p>
     *
     * @param processInstanceId 流程实例ID
     * @param request          触发请求
     * @return 命中的执行实例
     * @throws IllegalArgumentException 执行实例不存在、不属于当前流程或存在多个时抛出
     */
    private Execution resolveReceiveExecution(
            String processInstanceId,
            ReceiveTaskTriggerRequest request) {
        if (request.getExecutionId() != null && !request.getExecutionId().isBlank()) {
            Execution execution = runtimeService.createExecutionQuery()
                    .executionId(request.getExecutionId())
                    .singleResult();
            if (execution == null || !processInstanceId.equals(execution.getProcessInstanceId())) {
                throw new IllegalArgumentException("执行实例不存在或不属于当前流程: " + request.getExecutionId());
            }
            return execution;
        }
        if (request.getActivityId() == null || request.getActivityId().isBlank()) {
            throw new IllegalArgumentException("activityId 与 executionId 至少填写一个");
        }
        List<Execution> executions = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .activityId(request.getActivityId())
                .list();
        if (executions.isEmpty()) {
            throw new IllegalArgumentException("接收任务未处于等待状态: " + request.getActivityId());
        }
        if (executions.size() > 1) {
            throw new IllegalArgumentException("存在多个接收任务执行实例，请指定 executionId");
        }
        return executions.get(0);
    }

    /**
     * 校验接收任务配置的期望消息标识与请求携带的是否一致。
     *
     * <p>读取节点扩展属性 receiveConfig 中的 messageRef；未配置或为空时不校验。</p>
     *
     * @param flowElement 接收任务节点
     * @param messageRef  请求携带的消息标识
     * @throws IllegalArgumentException 消息标识不匹配或配置无效时抛出
     */
    private void validateReceiveMessage(
            org.flowable.bpmn.model.FlowElement flowElement,
            String messageRef) {
        String configDocument = ConfiguredTaskPropertyReader.read(flowElement, "receiveConfig");
        if (configDocument == null || configDocument.isBlank()) {
            return;
        }
        try {
            JsonNode config = OBJECT_MAPPER.readTree(configDocument);
            String expected = config.path("messageRef").asText("");
            if (!expected.isBlank() && !expected.equals(messageRef)) {
                throw new IllegalArgumentException("消息标识不匹配，期望: " + expected);
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("接收任务配置无效: " + exception.getMessage(), exception);
        }
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
        processInstanceAccessService.requireReadAccess(instanceId);
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
     * @param startDate 发起日期下限（可选）
     * @param endDate 发起日期上限（可选，包含当天）
     * @return 流程列表
     */
    public PageResult<MyStartedProcessVO> getMyStartedList(
            String userId,
            Integer pageNum,
            Integer pageSize,
            String processName,
            LocalDate startDate,
            LocalDate endDate) {
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        // 查询历史流程实例（包含运行中和已结束的）
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
                .startedBy(userId)
                .orderByProcessInstanceStartTime()
                .desc();

        ZoneId zoneId = ZoneId.systemDefault();
        if (startDate != null) {
            query.startedAfter(Date.from(startDate.atStartOfDay(zoneId).toInstant()));
        }
        if (endDate != null) {
            query.startedBefore(Date.from(endDate.plusDays(1).atStartOfDay(zoneId).toInstant()));
        }

        // 名称来自定义或配置回退；先解析匹配的定义 ID，再由数据库分页实例。
        // 不再为整批历史实例补充实体和节点后才在内存分页。
        Map<String, ProcessDefinition> definitions = new HashMap<>();
        Map<String, String> displayNames = new HashMap<>();
        if (processName != null && !processName.isBlank()) {
            for (ProcessDefinition definition : repositoryService.createProcessDefinitionQuery().list()) {
                String name = processDisplayName(definition);
                if (name.contains(processName)) {
                    definitions.put(definition.getId(), definition);
                    displayNames.put(definition.getId(), name);
                }
            }
            if (definitions.isEmpty()) return new PageResult<>(List.of(), 0L, safePageNum, safePageSize);

        }
        boolean filteredByName = processName != null && !processName.isBlank();
        Date start = startDate == null ? null : Date.from(startDate.atStartOfDay(zoneId).toInstant());
        Date end = endDate == null ? null : Date.from(endDate.plusDays(1).atStartOfDay(zoneId).toInstant());
        long total = filteredByName
                ? startedPageMapper.count(userId, definitions.keySet(), start, end) : query.count();
        long offset = (long) (safePageNum - 1) * safePageSize;
        if (offset >= total) return new PageResult<>(List.of(), total, safePageNum, safePageSize);
        List<HistoricProcessInstance> historicInstances;
        if (filteredByName) {
            List<String> ids = startedPageMapper.page(userId, definitions.keySet(), start, end, offset, safePageSize);
            historicInstances = ids.isEmpty() ? List.of() : query.processInstanceIds(new HashSet<>(ids))
                    .includeProcessVariables().listPage(0, safePageSize);
        } else {
            historicInstances = query.includeProcessVariables().listPage((int) Math.min(offset, Integer.MAX_VALUE), safePageSize);
        }

        // 转换为VO
        Map<String, String> userNames = new HashMap<>();
        Map<String, com.workflow.entity.data.api.response.EntityDataDTO> entityRecords = new HashMap<>();
        Map<String, Map<String, String>> entityStatusNames = new HashMap<>();
        List<MyStartedProcessVO> list = new ArrayList<>();
        for (HistoricProcessInstance historicInstance : historicInstances) {
            MyStartedProcessVO vo = new MyStartedProcessVO();
            vo.setCanTerminate(false);
            vo.setProcessInstanceId(historicInstance.getId());
            vo.setProcessDefinitionId(historicInstance.getProcessDefinitionId());
            vo.setBusinessKey(historicInstance.getBusinessKey());
            String startUserId = historicInstance.getStartUserId();
            vo.setStartUser(startUserId);
            if (startUserId != null && !startUserId.isEmpty()) {
                vo.setStartUserName(userNames.computeIfAbsent(startUserId, sysUserService::getDisplayName));
            }
            vo.setStartTime(formatDate(historicInstance.getStartTime()));
            vo.setEndTime(formatDate(historicInstance.getEndTime()));
            
            // 获取流程名称
            String processDefinitionId = historicInstance.getProcessDefinitionId();
            ProcessDefinition processDefinition = definitions.computeIfAbsent(processDefinitionId,
                    key -> repositoryService.createProcessDefinitionQuery().processDefinitionId(key).singleResult());
            if (processDefinition != null) {
                vo.setProcessKey(processDefinition.getKey());
                vo.setProcessName(displayNames.computeIfAbsent(processDefinitionId,
                        key -> processDisplayName(processDefinition)));
            }

            // 展示实体当前业务状态；下方的流程状态仍按本条历史实例计算，两者不能互相覆盖。
            try {
                String entityDataId = (String) Optional.ofNullable(historicInstance.getProcessVariables()).orElse(Map.of()).get("entityDataId");
                String entityCode = (String) Optional.ofNullable(historicInstance.getProcessVariables()).orElse(Map.of()).get("entityCode");
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
                    com.workflow.entity.data.api.response.EntityDataDTO entityData = null;
                    if (entityCode != null) {
                        try {
                            String recordKey = entityCode + ":" + entityDataId;
                            entityData = entityRecords.get(recordKey);
                            if (!entityRecords.containsKey(recordKey)) {
                                entityData = entityDataDynamicService.findById(entityCode, entityDataId);
                                entityRecords.put(recordKey, entityData);
                            }
                        } catch (Exception ex) {
                            // fallback
                        }
                    }
                    if (entityData != null) {
                        if (entityData.getData() != null) {
                            vo.setDataName((String) entityData.getData().get("name"));
                        }
                        vo.setName(entityData.getName());
                        vo.setCode(entityData.getCode());
                        vo.setEntityStatus(entityData.getStatus());
                        if (entityData.getStatus() != null && entityCode != null) {
                            vo.setEntityStatusText(entityStatusNames.computeIfAbsent(
                                    entityCode, entityStatusService::getStatusNameMap).get(entityData.getStatus()));
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("获取实体列表摘要失败: {}", e.getMessage());
            }
            
            // 实例历史包含未结束实例，结束时间已足够判断生命周期；不追加 runtime 查询。
            // 历史实例不能使用实体当前那一代流程的状态。
            boolean running = historicInstance.getEndTime() == null;
            vo.setStatus(running ? "RUNNING" : "COMPLETED");
            vo.setStatusText(running ? "运行中" : "已完成");
            vo.setCurrentNodeName("-");
            if (running) {
                List<Execution> executions = runtimeService.createExecutionQuery()
                        .processInstanceId(historicInstance.getId()).list();
                vo.setCurrentNodeName(executions.stream().filter(e -> e.getActivityId() != null)
                        .map(e -> getActivityName(e.getActivityId(), processDefinitionId))
                        .findFirst().orElse("处理中"));
            }

            list.add(vo);
        }
        
        List<MyStartedProcessVO> pageRecords = list;
        // 终止能力涉及活动任务和部署模型查询，仅计算当前页，避免列表总量放大查询次数。
        for (MyStartedProcessVO item : pageRecords) {
            if ("RUNNING".equals(item.getStatus())) {
                item.setCanTerminate(nodeOperationCapabilityService.canTerminateProcess(
                        item.getProcessInstanceId(), userId));
            }
        }
        return new PageResult<>(
                pageRecords,
                total,
                safePageNum,
                safePageSize);
    }
    
    /** 实例列表的名称回退只解析一次定义，筛选与显示共用同一规则。 */
    private String processDisplayName(ProcessDefinition definition) {
        if (definition.getName() != null && !definition.getName().isEmpty()) return definition.getName();
        return processConfigMapper.findByProcessKey(definition.getKey())
                .map(ProcessDefinitionConfig::getProcessName)
                .filter(name -> !name.isEmpty()).orElse(definition.getKey());
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
