package com.workflow.process.task.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.core.result.PageResult;
import com.workflow.core.result.PageRequest;
import com.workflow.process.task.api.response.TaskVO;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.workflow.entity.definition.application.EntityStatusService;
import java.time.LocalDate;
import java.util.HashMap;

/**
 * 任务列表统一应用入口。新接口使用读模型并保留未就绪回退，旧接口保留 Flowable
 * 待办范围、区分大小写名称条件及创建时间筛选；这些协议差异不能用同一 SQL 强行替代。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskListQueryService {
    private final org.flowable.engine.TaskService flowableTaskService;
    private final HistoryService historyService;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final ProcessTaskService processTaskService;
    private final com.workflow.entity.data.application.EntityDataDynamicService entityDataDynamicService;
    private final com.workflow.admin.identity.user.application.SysUserService sysUserService;
    private final EntityStatusService entityStatusService;
    private final TaskInboxQueryService taskInboxQueryService;

    /** 读模型关闭或存在未就绪行时，保持与原接口相同的实时回退。 */
    public PageResult<TaskVO> findInbox(String userId, String status, Integer pageNum, Integer pageSize,
                                       String keyword, String startUserName, String priority,
                                       LocalDate startDate, LocalDate endDate) {
        var page = taskInboxQueryService.findPage(new TaskInboxQuery(userId, status, pageNum, pageSize,
                keyword, startUserName, priority, startDate, endDate));
        if (page.isPresent()) return page.get();
        List<ProcessTask> tasks = "todo".equals(status)
                ? processTaskService.getTodoList(userId) : processTaskService.getDoneList(userId);
        Map<String, Map<String, String>> statusNames = new HashMap<>();
        var views = tasks.stream().map(task -> liveMirrorView(task, statusNames)).toList();
        return page(TaskListFilter.filter(views, keyword, startUserName, priority, startDate, endDate), pageNum, pageSize);
    }

    /** 旧待办接口：办理/候选范围来自统一授权查询，名称条件先于分页。 */
    public PageResult<TaskVO> findLegacyTodo(Integer pageNum, Integer pageSize, String processName, String taskName, String timeRange) {
        PageRequest page = PageRequest.normalize(pageNum, pageSize, 10, 100);
        List<ProcessTask> visibleTasks = processTaskService.getTodoList(UserContext.requireUsernameOrId());
        if (visibleTasks.isEmpty()) {
            return new PageResult<>(List.of(), 0L, page.pageNumber(), page.pageSize());
        }
        List<String> engineTaskIds = visibleTasks.stream()
                .filter(task -> !"ADD_SIGN".equals(task.getNodeType()))
                .map(ProcessTask::getTaskId).toList();
        List<TaskVO> visible = new java.util.ArrayList<>();
        if (!engineTaskIds.isEmpty()) {
            TaskQuery query = flowableTaskService.createTaskQuery()
                    .taskIds(engineTaskIds)
                    .active()
                    .orderByTaskCreateTime()
                    .desc();
            query.list().stream().map(this::convertToTodoVO).forEach(visible::add);
        }
        // ADD_SIGN 仅有本地任务，已由统一待办 SQL 校验编排和办理人；不能再要求子引擎任务存在。
        visibleTasks.stream().filter(task -> "ADD_SIGN".equals(task.getNodeType()))
                .map(this::convertAddSignToTodoVO).forEach(visible::add);
        Date startDate = StringUtils.hasText(timeRange) ? getStartDateByRange(timeRange) : null;
        List<TaskVO> matching = visible.stream()
                .filter(vo -> startDate == null || vo.getCreateTime() != null && vo.getCreateTime().after(startDate))
                .filter(vo -> !StringUtils.hasText(processName)
                        || vo.getProcessName() != null && vo.getProcessName().contains(processName))
                .filter(vo -> !StringUtils.hasText(taskName)
                        || vo.getTaskName() != null && vo.getTaskName().contains(taskName))
                .sorted(java.util.Comparator.comparing(TaskVO::getCreateTime,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();
        List<TaskVO> records = matching.stream().skip(page.offset()).limit(page.pageSize()).toList();
        return new PageResult<>(records, (long) matching.size(), page.pageNumber(), page.pageSize());
    }

    /** 供旧待办和任务详情共用，保留引擎身份、实时业务摘要及 SLA 的既有展示语义。 */
    TaskVO convertToTodoVO(Task task) {
        TaskVO vo = TaskListViewMapper.fromEngine(task);
        vo.setClaimRequired(!StringUtils.hasText(task.getAssignee()));
        vo.setCanClaim(!StringUtils.hasText(task.getAssignee()));
        vo.setAssigneeType(vo.getCanClaim() ? "group" : "user");
        applySlaSummary(vo, task.getId());
        
        fillProcessMetadata(vo, task.getProcessDefinitionId(), task.getProcessInstanceId());

        // 获取数据标题、编码、当前任务名（从实体数据）
        try {
            String entityCode = (String) runtimeService.getVariable(task.getProcessInstanceId(), "entityCode");
            String entityDataId = (String) runtimeService.getVariable(task.getProcessInstanceId(), "entityDataId");
            fillBusinessData(vo, entityCode, entityDataId, null);
        } catch (Exception e) {
            log.debug("获取数据标题失败: {}", e.getMessage());
        }
        
        return vo;
    }

    /** 本地加签没有独立引擎任务；保留专用类型，办理人已确定且不能再认领。 */
    private TaskVO convertAddSignToTodoVO(ProcessTask task) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setTaskName(task.getNodeName());
        vo.setNodeType("ADD_SIGN");
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setProcessDefinitionId(task.getProcessDefinitionId());
        vo.setProcessName(task.getProcessName());
        vo.setBusinessKey(task.getBusinessKey());
        LocalDateTime created = task.getStartTime() != null ? task.getStartTime() : task.getCreateTime();
        vo.setCreateTime(created == null ? null : Date.from(created.atZone(ZoneId.systemDefault()).toInstant()));
        vo.setPriority(task.getPriority());
        vo.setAssignee(task.getAssigneeId());
        vo.setAssigneeName(task.getAssigneeName());
        vo.setAssigneeType("user");
        vo.setClaimRequired(false);
        vo.setCanClaim(false);
        vo.setEntityCode(task.getEntityCode());
        vo.setEntityDataId(task.getEntityDataId());
        vo.setFormKey(task.getFormKey());
        vo.setSlaStatus(task.getSlaStatus());
        vo.setResponseDueTime(toDate(task.getResponseDueTime()));
        vo.setDueTime(toDate(task.getDueTime()));
        return vo;
    }

    private void applySlaSummary(TaskVO vo, String taskId) {
        ProcessTask local = processTaskService.getTaskByTaskId(taskId);
        if (local == null) {
            return;
        }
        vo.setSlaStatus(local.getSlaStatus());
        vo.setResponseDueTime(toDate(local.getResponseDueTime()));
        vo.setDueTime(toDate(local.getDueTime()));
    }

    private Date toDate(LocalDateTime value) {
        return value == null
                ? null
                : Date.from(value.toInstant(ZoneOffset.UTC));
    }

    private Date getStartDateByRange(String range) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        switch (range) {
            case "week":
                return Date.from(now.minusWeeks(1).atZone(ZoneId.systemDefault()).toInstant());
            case "month":
                return Date.from(now.minusMonths(1).atZone(ZoneId.systemDefault()).toInstant());
            case "year":
                return Date.from(now.minusYears(1).atZone(ZoneId.systemDefault()).toInstant());
            default:
                return null;
        }
    }

    /** 回退路径读取当前业务数据和真实发起人，状态名称缓存限定在单次请求内。 */
    private TaskVO liveMirrorView(ProcessTask task, Map<String, Map<String, String>> statusNames) {
        TaskVO vo = TaskListViewMapper.fromMirror(task);
        // 发起人名称从流程实例历史记录中查询，不能复用 assigneeName（候选组任务时 assigneeName 是组名）
        String startUserName = null;
        try {
            org.flowable.engine.history.HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult();
            if (hpi != null && hpi.getStartUserId() != null) {
                startUserName = sysUserService.getDisplayName(hpi.getStartUserId());
            }
        } catch (Exception e) {
            // 历史信息缺失时保持发起人为空，不能回填成当前办理人。
        }
        vo.setStartUserName(startUserName);
        fillBusinessData(vo, task.getEntityCode(), task.getEntityDataId(), statusNames);
        return vo;
    }

    private void fillProcessMetadata(TaskVO vo, String definitionId, String instanceId) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(definitionId).singleResult();
        if (definition != null) vo.setProcessName(definition.getName());
        HistoricProcessInstance instance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId).singleResult();
        if (instance != null) {
            vo.setStartUserName(sysUserService.getDisplayName(instance.getStartUserId()));
            vo.setBusinessKey(instance.getBusinessKey());
        }
    }

    /** 摘要读取失败保持原先的空展示回退；不影响待办范围，也不把实体状态当作审批结果。 */
    private void fillBusinessData(TaskVO vo, String entityCode, String entityDataId,
                                  Map<String, Map<String, String>> statusNames) {
        if (entityCode == null || entityDataId == null) return;
        try {
            var data = entityDataDynamicService.findById(entityCode, entityDataId);
            if (data == null) return;
            if (data.getData() != null) vo.setDataName((String) data.getData().get("name"));
            vo.setName(data.getName());
            vo.setCode(data.getCode());
            vo.setCurrentTaskName(data.getCurrentTaskName());
            if (statusNames != null) {
                vo.setEntityStatus(data.getStatus());
                if (data.getStatus() != null) vo.setEntityStatusText(statusNames
                        .computeIfAbsent(entityCode, entityStatusService::getStatusNameMap).get(data.getStatus()));
            }
        } catch (Exception exception) {
            log.debug("读取任务业务摘要失败: entityCode={}, recordId={}", entityCode, entityDataId, exception);
        }
    }

    private PageResult<TaskVO> page(List<TaskVO> tasks, Integer requestedPage, Integer requestedSize) {
        PageRequest page = PageRequest.normalize(requestedPage, requestedSize, 10, 100);
        int start = page.startIndex(tasks.size());
        int end = (int) Math.min((long) start + page.pageSize(), tasks.size());
        return new PageResult<>(tasks.subList(start, end), tasks.size(), page.pageNumber(), page.pageSize());
    }

}
