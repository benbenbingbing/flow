package com.workflow.process.runtime;

import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.SysGroupMapper;
import com.workflow.service.SysUserService;
import com.workflow.vo.ProcessDetailVO;
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
            detail.setStatus(historicInstance.getEndTime() != null ? "COMPLETED" : "SUSPENDED");
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
        detail.setNodeAssigneeMap(buildNodeAssigneeMap(instanceId, processInstance, historicTasks));
        loadFormData(detail, historicInstance);
        return detail;
    }

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
                log.debug("获取流程发起人变量失败: instanceId={}", detail.getInstanceId());
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

    private void loadCompletedNodes(ProcessDetailVO detail, String instanceId) {
        List<HistoricActivityInstance> historicActivities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(instanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();

        List<String> completedNodes = historicActivities.stream()
                .filter(h -> h.getEndTime() != null && !"sequenceFlow".equals(h.getActivityType()))
                .map(HistoricActivityInstance::getActivityId)
                .distinct()
                .collect(Collectors.toList());
        detail.setCompletedNodes(completedNodes);
    }

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
            if (!assigneeId.equals(displayName)) {
                history.setAssigneeName(displayName);
            }
            history.setAction("通过");
            history.setStartTime(formatDate(task.getStartTime()));
            history.setEndTime(formatDate(task.getEndTime()));
            history.setDuration(task.getDurationInMillis());
            loadTaskVariables(history, task);
            historyList.add(history);
        }

        return mergeMultiInstanceHistory(historyList);
    }

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
                history.setVariables(vars);
            }
        } catch (Exception e) {
            log.warn("查询任务变量失败: taskId={}", task.getId(), e);
        }
    }

    private List<ProcessDetailVO.HistoryVO> mergeMultiInstanceHistory(List<ProcessDetailVO.HistoryVO> historyList) {
        Map<String, List<ProcessDetailVO.HistoryVO>> historyGroup = new LinkedHashMap<>();
        for (ProcessDetailVO.HistoryVO history : historyList) {
            historyGroup.computeIfAbsent(history.getTaskName(), key -> new ArrayList<>()).add(history);
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
        merged.setAction(hasActive ? "进行中" : "通过");
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
            assignee.setAction("通过");
            assignee.setStatus("completed");
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

    private void fillCandidateAssignee(Task task, ProcessDetailVO.AssigneeVO assignee) {
        try {
            List<org.flowable.identitylink.api.IdentityLink> identityLinks = taskService.getIdentityLinksForTask(task.getId());
            List<String> groupIds = new ArrayList<>();
            List<String> groupNames = new ArrayList<>();
            List<String> candidateUserIds = new ArrayList<>();
            for (org.flowable.identitylink.api.IdentityLink link : identityLinks) {
                if (link.getGroupId() != null) {
                    groupIds.add(link.getGroupId());
                    com.workflow.entity.SysGroup group = sysGroupMapper.selectByGroupCode(link.getGroupId());
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

    private void loadFormData(ProcessDetailVO detail, HistoricProcessInstance historicInstance) {
        if (historicInstance == null || historicInstance.getProcessVariables() == null) {
            return;
        }
        Map<String, Object> formData = historicInstance.getProcessVariables().entrySet().stream()
                .filter(e -> !e.getKey().startsWith("flowable_") && !e.getKey().startsWith("_"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        detail.setFormData(formData);
    }

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

    private String getBpmnXmlByInstanceId(String instanceId) {
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (historicInstance == null) {
            return null;
        }
        return getBpmnXmlByProcessDefinitionId(historicInstance.getProcessDefinitionId());
    }

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

            String resourceName = processDefinition.getResourceName();
            if (resourceName != null) {
                org.flowable.engine.repository.Deployment deployment = repositoryService.createDeploymentQuery()
                        .deploymentId(processDefinition.getDeploymentId())
                        .singleResult();
                if (deployment != null) {
                    java.io.InputStream resourceStream = repositoryService.getResourceAsStream(deployment.getId(), resourceName);
                    if (resourceStream != null) {
                        return new String(resourceStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取 BPMN XML 失败", e);
        }
        return null;
    }

    private String formatDate(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }
}
