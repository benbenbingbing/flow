package com.workflow.process.instance.application;

import com.workflow.core.logging.LogValue;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.process.instance.api.response.ProcessDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import com.workflow.process.status.application.ProcessEndReason;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程详情运行时服务
 * 负责组装流程实例详情视图，包含流程状态、当前节点、已完成节点、审批历史、
 * 节点处理人映射、表单数据与 BPMN XML，供前端流程详情页展示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDetailRuntimeService {
    @org.springframework.beans.factory.annotation.Autowired
    private ProcessOperationLogMapper operationLogMapper;


    /** Flowable 运行时服务，查询运行中流程实例与执行 */
    private final RuntimeService runtimeService;
    /** Flowable 历史服务，查询历史实例、活动与变量 */
    private final HistoryService historyService;
    /** Flowable 仓库服务，查询流程定义与 BPMN 模型 */
    private final RepositoryService repositoryService;
    /** Flowable 任务服务，查询当前任务 */
    private final TaskService taskService;
    /** 流程定义配置 Mapper，补充流程名称 */
    private final ProcessDefinitionConfigMapper processConfigMapper;
    /** 用户服务，转换用户ID为显示名 */
    private final SysUserService sysUserService;
    /** 用户组 Mapper，查询候选组名称 */
    private final SysGroupMapper sysGroupMapper;
    private final PublishedBpmnReader publishedBpmnReader;

    /**
     * 获取流程实例详情。
     * <p>
     * 聚合流程状态、定义信息、发起人、当前节点、已完成节点、审批历史、节点处理人、表单数据与 BPMN XML。
     *
     * @param instanceId 流程实例ID
     * @return 流程详情视图对象
     */
    public ProcessDetailVO getProcessDetail(String instanceId) {
        ProcessDetailVO detail = new ProcessDetailVO();
        detail.setInstanceId(instanceId);

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();

        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();

        if (processInstance != null) {
            detail.setStatus("RUNNING");
            detail.setProcessDefinitionId(processInstance.getProcessDefinitionId());
        } else if (historicInstance != null) {
            detail.setStatus(historicInstance.getEndTime() != null ? "COMPLETED" : "RUNNING");
            detail.setProcessDefinitionId(historicInstance.getProcessDefinitionId());
        }

        String processKey = loadProcessDefinition(detail);
        loadHistoricInstance(detail, historicInstance, processKey);
        loadCurrentNode(detail, processInstance, instanceId);
        detail.setBpmnXml(getBpmnXmlByInstanceId(instanceId));
        loadCompletedNodes(detail, instanceId);

        List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(instanceId)
                .finished()
                .orderByHistoricTaskInstanceEndTime().asc()
                .list();
        detail.setHistory(buildHistory(instanceId, historicInstance, historicTasks));
        if (historicInstance != null && historicInstance.getEndTime() != null) {
            detail.setEndType(ProcessEndReason.category(historicInstance.getDeleteReason()));
            detail.setEndReason(ProcessEndReason.comment(historicInstance.getDeleteReason()));
        }
        detail.setNodeAssigneeMap(buildNodeAssigneeMap(instanceId, processInstance, historicTasks));
        loadFormData(detail, historicInstance);
        return detail;
    }

    /**
     * 加载流程定义；查询结果供调用方展示或继续处理。
     *
     * @param detail 详情，供本方法加载流程定义时使用
     * @return 加载后的流程定义文本，供调用方比较或展示
     */
    private String loadProcessDefinition(ProcessDetailVO detail) {
        String processKey = null;
        if (detail.getProcessDefinitionId() == null) {
            return null;
        }
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(detail.getProcessDefinitionId())
                .singleResult();
        if (processDefinition != null) {
            String processName = processDefinition.getName();
            processKey = processDefinition.getKey();
            if ((processName == null || processName.isEmpty()) && processKey != null) {
                ProcessDefinitionConfig config = processConfigMapper.findByProcessKey(processKey).orElse(null);
                if (config != null) {
                    processName = config.getProcessName();
                }
            }
            detail.setProcessName(processName != null ? processName : processKey);
        }
        return processKey;
    }

    /**
     * 加载历史实例；查询结果供调用方展示或继续处理。
     *
     * @param detail 详情，作为 {@code LogValue.safe} 的输入影响后续处理
     * @param historicInstance 历史实例，作为 {@code detail.setStartTime} 的输入影响后续处理
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的流程详情结果，供调用方继续处理
     */
    private void loadHistoricInstance(ProcessDetailVO detail, HistoricProcessInstance historicInstance, String processKey) {
        if (historicInstance == null) {
            return;
        }
        String startUser = historicInstance.getStartUserId();
        if (startUser == null || startUser.isEmpty()) {
            try {
                startUser = (String) historyService.createHistoricVariableInstanceQuery()
                        .processInstanceId(detail.getInstanceId())
                        .variableName("initiator")
                        .singleResult()
                        .getValue();
            } catch (Exception e) {
                log.debug("获取流程发起人变量失败: instanceId={}", LogValue.safe(detail.getInstanceId()));
            }
        }
        if ((startUser == null || startUser.isEmpty()) && processKey != null) {
            ProcessDefinitionConfig config = processConfigMapper.findByProcessKey(processKey).orElse(null);
            if (config != null && config.getCreatedBy() != null) {
                startUser = config.getCreatedBy();
            }
        }
        detail.setStartUser(startUser != null ? startUser : "系统");
        detail.setStartTime(formatDate(historicInstance.getStartTime()));
        detail.setBusinessKey(historicInstance.getBusinessKey() != null ? historicInstance.getBusinessKey() : "-");
    }

    /**
     * 加载当前节点；查询结果供调用方展示或继续处理。
     *
     * @param detail 详情，供本方法加载当前节点时使用
     * @param processInstance 流程实例，供本方法加载当前节点时使用
     * @param instanceId 实例ID，后续用于加载当前节点时定位或关联目标
     * @return 符合条件的流程详情结果，供调用方继续处理
     */
    private void loadCurrentNode(ProcessDetailVO detail, ProcessInstance processInstance, String instanceId) {
        if (processInstance == null) {
            return;
        }
        List<Execution> executions = runtimeService.createExecutionQuery()
                .processInstanceId(instanceId)
                .list();
        executions.stream()
                .filter(e -> e.getActivityId() != null)
                .findFirst()
                .ifPresent(e -> {
                    detail.setCurrentNodeId(e.getActivityId());
                    detail.setCurrentNode(getActivityName(e.getActivityId(), detail.getProcessDefinitionId()));
                });
    }

    /**
     * 加载{@code completed}节点集合；查询结果供调用方展示或继续处理。
     *
     * @param detail 详情，供本方法加载{@code completed}节点集合时使用
     * @param instanceId 实例ID，后续用于加载{@code completed}节点集合时定位或关联目标
     * @return 符合条件的流程详情结果，供调用方继续处理
     */
    private void loadCompletedNodes(ProcessDetailVO detail, String instanceId) {
        List<HistoricActivityInstance> historicActivities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(instanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();

        List<String> completedNodes = historicActivities.stream()
                .filter(h -> h.getEndTime() != null && !ProcessEndReason.isCancelled(h.getDeleteReason())
                        && !"sequenceFlow".equals(h.getActivityType()))
                .map(HistoricActivityInstance::getActivityId)
                .distinct()
                .collect(Collectors.toList());
        detail.setCompletedNodes(completedNodes);
    }

    /**
     * 构建历史；结果供后续流程传递或持久化。
     *
     * @param instanceId 实例ID，后续用于构建历史时定位或关联目标
     * @param historicInstance 历史实例，作为 {@code startHistory.setStartTime} 的输入影响后续处理
     * @param historicTasks 历史任务集合，供本方法构建历史时使用
     * @return 流程详情集合，供调用方遍历或展示
     */
    private List<ProcessDetailVO.HistoryVO> buildHistory(String instanceId,
                                                         HistoricProcessInstance historicInstance,
                                                         List<HistoricTaskInstance> historicTasks) {
        List<ProcessDetailVO.HistoryVO> historyList = new ArrayList<>();
        if (historicInstance != null) {
            ProcessDetailVO.HistoryVO startHistory = new ProcessDetailVO.HistoryVO();
            startHistory.setTaskName("流程发起");
            String startUserId = historicInstance.getStartUserId();
            startHistory.setAssignee(startUserId);
            startHistory.setAssigneeName(sysUserService.getDisplayName(startUserId));
            startHistory.setAction("发起");
            startHistory.setStartTime(formatDate(historicInstance.getStartTime()));
            startHistory.setEndTime(formatDate(historicInstance.getStartTime()));
            historyList.add(startHistory);
        }

        for (HistoricTaskInstance task : historicTasks) {
            ProcessDetailVO.HistoryVO history = new ProcessDetailVO.HistoryVO();
            history.setTaskName(task.getName());
            String assigneeId = task.getAssignee();
            history.setAssignee(assigneeId);
            String displayName = sysUserService.getDisplayName(assigneeId);
            if (!java.util.Objects.equals(assigneeId, displayName)) {
                history.setAssigneeName(displayName);
            }
            // 引擎删除的历史任务同样有结束时间，不能因此把取消记为通过。
            boolean cancelled = ProcessEndReason.isCancelled(task.getDeleteReason());
            history.setAction(cancelled ? "已取消" : "完成");
            if (cancelled) history.setComment(ProcessEndReason.comment(task.getDeleteReason()));
            history.setStartTime(formatDate(task.getStartTime()));
            history.setEndTime(formatDate(task.getEndTime()));
            history.setDuration(task.getDurationInMillis());
            loadTaskVariables(history, task);
            if (!cancelled && history.getVariables() != null) {
                Object action = history.getVariables().get("action");
                history.setAction("approve".equals(action) ? "通过" : "reject".equals(action) ? "驳回" : "完成");
            }
            historyList.add(history);
        }

        List<ProcessDetailVO.HistoryVO> result = mergeMultiInstanceHistory(historyList);
        if (historicInstance != null && historicInstance.getEndTime() != null
                && ProcessEndReason.isCancelled(historicInstance.getDeleteReason())) {
            // 详情接口与进度接口使用相同的结构化结束事实和可靠操作日志。
            List<ProcessOperationLog> logs = operationLogMapper.selectList(Wrappers.<ProcessOperationLog>lambdaQuery()
                    .eq(ProcessOperationLog::getProcessInstanceId, instanceId)
                    .in(ProcessOperationLog::getOperationType, "TERMINATE", "WITHDRAW")
                    .orderByAsc(ProcessOperationLog::getOperationTime));
            for (ProcessOperationLog log : logs) {
                var end = new ProcessDetailVO.HistoryVO();
                boolean withdrawn = "WITHDRAW".equals(log.getOperationType());
                end.setTaskName(withdrawn ? "流程撤回" : "流程终止");
                end.setAction(withdrawn ? "撤回" : "终止");
                end.setAssignee(log.getOperatorId()); end.setAssigneeName(log.getOperatorName());
                end.setComment(log.getOperationComment());
                end.setEndTime(log.getOperationTime() == null ? null : log.getOperationTime().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                result.add(end);
            }
            if (logs.isEmpty()) {
                var end = new ProcessDetailVO.HistoryVO();
                boolean withdrawn = "WITHDRAWN".equals(ProcessEndReason.category(historicInstance.getDeleteReason()));
                end.setTaskName(withdrawn ? "流程撤回" : "流程终止"); end.setAction(withdrawn ? "撤回" : "终止");
                end.setComment(ProcessEndReason.comment(historicInstance.getDeleteReason()));
                end.setEndTime(formatDate(historicInstance.getEndTime())); result.add(end);
            }
        }
        return result;
    }

    /**
     * 加载任务流程变量；查询结果供调用方展示或继续处理。
     *
     * @param history 历史，供本方法加载任务流程变量时使用
     * @param task 任务，供本方法加载任务流程变量时使用
     * @return 符合条件的流程详情结果，供调用方继续处理
     */
    private void loadTaskVariables(ProcessDetailVO.HistoryVO history, HistoricTaskInstance task) {
        try {
            List<org.flowable.variable.api.history.HistoricVariableInstance> taskVars =
                    historyService.createHistoricVariableInstanceQuery()
                            .taskId(task.getId())
                            .list();
            if (taskVars.isEmpty() && task.getExecutionId() != null) {
                taskVars = historyService.createHistoricVariableInstanceQuery()
                        .executionId(task.getExecutionId())
                        .list();
            }
            if (!taskVars.isEmpty()) {
                Map<String, Object> vars = new HashMap<>();
                for (org.flowable.variable.api.history.HistoricVariableInstance var : taskVars) {
                    vars.put(var.getVariableName(), var.getValue());
                }
                WorkflowReservedVariables.removeInternalVariables(vars);
                history.setVariables(vars);
            }
        } catch (Exception e) {
            log.warn("查询任务变量失败: taskId={}", task.getId(), e);
        }
    }

    /**
     * 合并多实例历史；结果供后续流程传递或持久化。
     *
     * @param historyList 历史列表，供本方法合并多实例历史时使用
     * @return 流程详情集合，供调用方遍历或展示
     */
    private List<ProcessDetailVO.HistoryVO> mergeMultiInstanceHistory(List<ProcessDetailVO.HistoryVO> historyList) {
        Map<String, List<ProcessDetailVO.HistoryVO>> historyGroup = new LinkedHashMap<>();
        for (ProcessDetailVO.HistoryVO history : historyList) {
            // 按处理结果分组，取消的会签分支不得和已通过分支合并成“通过”。
            historyGroup.computeIfAbsent(history.getTaskName() + "|" + history.getAction(), key -> new ArrayList<>()).add(history);
        }

        List<ProcessDetailVO.HistoryVO> mergedHistory = new ArrayList<>();
        for (Map.Entry<String, List<ProcessDetailVO.HistoryVO>> entry : historyGroup.entrySet()) {
            List<ProcessDetailVO.HistoryVO> list = entry.getValue();
            if (list.size() > 1 && !"流程发起".equals(list.get(0).getTaskName())) {
                mergedHistory.add(mergeHistoryGroup(list));
            } else {
                mergedHistory.addAll(list);
            }
        }
        return mergedHistory;
    }

    /**
     * 合并历史分组；结果供后续流程传递或持久化。
     *
     * @param list 列表，作为 {@code merged.setTaskName} 的输入影响后续处理
     * @return 合并后的历史分组结果，供调用方继续处理
     */
    private ProcessDetailVO.HistoryVO mergeHistoryGroup(List<ProcessDetailVO.HistoryVO> list) {
        ProcessDetailVO.HistoryVO merged = new ProcessDetailVO.HistoryVO();
        merged.setTaskName(list.get(0).getTaskName());

        Set<String> assignees = new LinkedHashSet<>();
        Set<String> assigneeNames = new LinkedHashSet<>();
        for (ProcessDetailVO.HistoryVO history : list) {
            if (history.getAssignee() != null) {
                assignees.add(history.getAssignee());
            }
            if (history.getAssigneeName() != null) {
                assigneeNames.add(history.getAssigneeName());
            }
        }
        merged.setAssignee(String.join(",", assignees));
        merged.setAssigneeName(String.join(",", assigneeNames));
        boolean hasActive = list.stream().anyMatch(h -> h.getEndTime() == null);
        merged.setAction(hasActive ? "进行中" : list.get(0).getAction());
        merged.setStartTime(list.get(0).getStartTime());
        merged.setEndTime(list.stream()
                .map(ProcessDetailVO.HistoryVO::getEndTime)
                .filter(java.util.Objects::nonNull)
                .max(String::compareTo)
                .orElse(null));
        long totalDuration = list.stream()
                .mapToLong(h -> h.getDuration() != null ? h.getDuration() : 0)
                .sum();
        merged.setDuration(totalDuration > 0 ? totalDuration : null);
        String comments = list.stream()
                .map(ProcessDetailVO.HistoryVO::getComment)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining("; "));
        merged.setComment(comments.isEmpty() ? null : comments);

        Map<String, Object> mergedVars = new LinkedHashMap<>();
        for (ProcessDetailVO.HistoryVO history : list) {
            if (history.getVariables() != null) {
                mergedVars.putAll(history.getVariables());
            }
        }
        if (!mergedVars.isEmpty()) {
            merged.setVariables(mergedVars);
        }
        return merged;
    }

    /**
     * 构建节点办理人映射；结果供后续流程传递或持久化。
     *
     * @param instanceId 实例ID，后续用于构建节点办理人映射时定位或关联目标
     * @param processInstance 流程实例，供本方法构建节点办理人映射时使用
     * @param historicTasks 历史任务集合，供本方法构建节点办理人映射时使用
     * @return 节点办理人映射键值结果，供调用方继续处理
     */
    private Map<String, ProcessDetailVO.AssigneeVO> buildNodeAssigneeMap(String instanceId,
                                                                          ProcessInstance processInstance,
                                                                          List<HistoricTaskInstance> historicTasks) {
        Map<String, ProcessDetailVO.AssigneeVO> nodeAssigneeMap = new HashMap<>();
        for (HistoricTaskInstance task : historicTasks) {
            ProcessDetailVO.AssigneeVO assignee = new ProcessDetailVO.AssigneeVO();
            String userId = task.getAssignee();
            String displayName = sysUserService.getDisplayName(userId);
            assignee.setAssigneeId(userId);
            assignee.setAssigneeName(displayName);
            assignee.setHandleTime(formatDate(task.getEndTime()));
            boolean cancelled = ProcessEndReason.isCancelled(task.getDeleteReason());
            assignee.setAction(cancelled ? "已取消" : "完成");
            assignee.setStatus(cancelled ? "cancelled" : "completed");
            nodeAssigneeMap.put(task.getTaskDefinitionKey(), assignee);
        }

        if (processInstance != null) {
            List<Task> activeTasks = taskService.createTaskQuery()
                    .processInstanceId(instanceId)
                    .list();
            for (Task task : activeTasks) {
                nodeAssigneeMap.put(task.getTaskDefinitionKey(), buildActiveAssignee(task));
            }
        }
        return nodeAssigneeMap;
    }

    /**
     * 构建活动办理人；结果供后续流程传递或持久化。
     *
     * @param task 任务，作为 {@code fillCandidateAssignee} 的输入影响后续处理
     * @return 构建后的活动办理人结果，供调用方继续处理
     */
    private ProcessDetailVO.AssigneeVO buildActiveAssignee(Task task) {
        ProcessDetailVO.AssigneeVO assignee = new ProcessDetailVO.AssigneeVO();
        String userId = task.getAssignee();
        if (userId != null && !userId.isEmpty()) {
            String displayName = sysUserService.getDisplayName(userId);
            assignee.setAssigneeId(userId);
            assignee.setAssigneeName(displayName);
        } else {
            fillCandidateAssignee(task, assignee);
        }
        assignee.setHandleTime(formatDate(task.getCreateTime()));
        assignee.setAction("待处理");
        assignee.setStatus("processing");
        return assignee;
    }

    /**
     * 处理{@code fill}候选人办理人，并将结果传给后续步骤。
     *
     * @param task 任务，作为 {@code taskService.getIdentityLinksForTask} 的输入影响后续处理
     * @param assignee 办理人，供本方法处理{@code fill}候选人办理人时使用
     */
    private void fillCandidateAssignee(Task task, ProcessDetailVO.AssigneeVO assignee) {
        try {
            List<org.flowable.identitylink.api.IdentityLink> identityLinks = taskService.getIdentityLinksForTask(task.getId());
            List<String> groupIds = new ArrayList<>();
            List<String> groupNames = new ArrayList<>();
            List<String> candidateUserIds = new ArrayList<>();
            for (org.flowable.identitylink.api.IdentityLink link : identityLinks) {
                if (link.getGroupId() != null) {
                    groupIds.add(link.getGroupId());
                    com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup group = sysGroupMapper.selectByGroupCode(link.getGroupId());
                    groupNames.add(group != null ? group.getGroupName() : link.getGroupId());
                } else if (link.getUserId() != null) {
                    candidateUserIds.add(link.getUserId());
                }
            }
            if (!groupIds.isEmpty()) {
                assignee.setAssigneeId(String.join(",", groupIds));
                assignee.setAssigneeName(String.join(",", groupNames) + "（组任务）");
            } else if (!candidateUserIds.isEmpty()) {
                assignee.setAssigneeId(String.join(",", candidateUserIds));
                assignee.setAssigneeName(sysUserService.getDisplayNames(candidateUserIds) + "（候选）");
            } else {
                assignee.setAssigneeId("");
                assignee.setAssigneeName("未分配");
            }
        } catch (Exception e) {
            assignee.setAssigneeId("");
            assignee.setAssigneeName("未分配");
        }
    }

    /**
     * 加载表单数据；查询结果供调用方展示或继续处理。
     *
     * @param detail 详情，供本方法加载表单数据时使用
     * @param historicInstance 历史实例，供本方法加载表单数据时使用
     * @return 符合条件的流程详情结果，供调用方继续处理
     */
    private void loadFormData(ProcessDetailVO detail, HistoricProcessInstance historicInstance) {
        if (historicInstance == null || historicInstance.getProcessVariables() == null) {
            return;
        }
        Map<String, Object> formData = historicInstance.getProcessVariables().entrySet().stream()
                .filter(e -> !e.getKey().startsWith("flowable_") && !e.getKey().startsWith("_"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        detail.setFormData(formData);
    }

    /**
     * 读取活动名称；查询结果供调用方展示或继续处理。
     *
     * @param activityId 活动ID，后续用于读取活动名称时定位或关联目标
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @return 读取后的活动名称文本，供调用方比较或展示
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
     * 按实例ID查询流程详情；结果供后续展示或处理。
     *
     * @param instanceId 实例ID，后续用于读取BPMNXML实例ID时定位或关联目标
     * @return 读取后的BPMNXML实例ID文本，供调用方比较或展示
     */
    private String getBpmnXmlByInstanceId(String instanceId) {
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (historicInstance == null) {
            return null;
        }
        return getBpmnXmlByProcessDefinitionId(historicInstance.getProcessDefinitionId());
    }

    /**
     * 按流程定义ID查询流程详情；结果供后续展示或处理。
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @return 读取后的BPMNXML流程定义ID文本，供调用方比较或展示
     */
    private String getBpmnXmlByProcessDefinitionId(String processDefinitionId) {
        return publishedBpmnReader.read(processDefinitionId);
    }

    /**
     * 格式化日期；输出作为后续校验或处理的输入。
     *
     * @param date 日期，后续用于判断有效期或展示该事件的发生时间
     * @return 格式化后的日期文本，供调用方比较或展示
     */
    private String formatDate(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }
}
