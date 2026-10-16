package com.workflow.process.query.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.contracts.process.error.ProcessOperationException;
import com.workflow.contracts.process.model.*;
import com.workflow.contracts.process.query.model.*;
import com.workflow.contracts.process.query.port.ProcessQueryPort;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.command.application.ProcessInvocationAccess;
import com.workflow.process.command.infrastructure.persistence.mapper.ProcessCommandRecordMapper;
import com.workflow.process.command.infrastructure.persistence.record.ProcessCommandRecord;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import com.workflow.process.status.application.ProcessEndReason;
import com.workflow.process.task.application.*;
import com.workflow.process.task.api.request.NextApprovalPreviewRequest;
import com.workflow.process.task.api.request.NextApproverOptionsRequest;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import com.workflow.process.task.application.operation.NodeOperationDecisionService;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignUserMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 流程 Java 查询入口。范围来自现有身份与待办规则，结果只使用 port 中的值对象。
 * 先完成权限与业务过滤再计数分页，不能把“本页可见条数”误当作 total。
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcessQueryAdapter implements ProcessQueryPort {
    private final HistoryService history;
    private final RuntimeService runtime;
    private final TaskService tasks;
    private final ProcessInvocationAccess access;
    private final ProcessInstanceAccessService instanceAccess;
    private final ProcessProjectionReader projection;
    private final ProcessQueryTime time;
    private final ProcessTaskService processTasks;
    private final ProcessTaskAddSignUserMapper addSignUsers;
    private final ProcessTaskAddSignMapper addSigns;
    private final LocalAddSignTaskAccessService localAccess;
    private final EntityProcessLinkMapper links;
    private final ProcessOperationLogMapper operations;
    private final ProcessCommandRecordMapper commands;
    private final TaskOperationApplicationService taskOperations;
    private final TaskApprovalQueryService approvalQueries;
    private final NodeOperationDecisionService decisions;

    /** 存在但不可见时抛授权异常；只有实际缺失才返回 empty。 */
    @Override
    public Optional<ProcessInstanceView> findInstance(String instanceId) {
        ProcessValues.required(instanceId, "processInstanceId");
        var instance = historic(instanceId);
        if (instance == null) return Optional.empty();
        access.requireRead(instanceId);
        return Optional.of(projection.instance(instance));
    }

    @Override
    public ProcessInstanceView requireInstance(String instanceId) {
        return findInstance(instanceId).orElseThrow(() -> new ProcessOperationException(
                "PROCESS_NOT_FOUND", "流程实例不存在", instanceId));
    }

    /**
     * MY_STARTED 兼容 ID/用户名；PARTICIPATED 不因管理员角色扩大，READABLE 复用详情读取范围。
     * 历史数据尚无统一权限投影，按有序批次扫描并在分页前过滤，避免一次加载全库对象。
     */
    @Override
    public ProcessPage<ProcessInstanceView> pageInstances(ProcessInstanceQuery query) {
        var actor = access.requireActor();
        var source = history.createHistoricProcessInstanceQuery();
        if (hasText(query.processKey())) source.processDefinitionKey(query.processKey());
        // Flowable 时间比较不统一支持 [from,to)，因此在扫描中执行精确边界过滤。
        source.orderByProcessInstanceStartTime();
        if (query.page().sort() == ProcessSort.NEWEST_FIRST) source.desc(); else source.asc();
        source.orderByProcessInstanceId();
        if (query.page().sort() == ProcessSort.NEWEST_FIRST) source.desc(); else source.asc();
        long total = 0;
        List<ProcessInstanceView> page = new ArrayList<>();
        for (int offset = 0; ; offset += 200) {
            List<HistoricProcessInstance> batch = source.listPage(offset, 200);
            for (var item : batch) {
                if (!time.inRange(time.instant(item.getStartTime()), query.from(), query.to())) continue;
                boolean visible = switch (query.scope()) {
                    case MY_STARTED -> item.getStartUserId() != null
                            && (item.getStartUserId().equals(actor.userId()) || item.getStartUserId().equals(actor.username()));
                    case PARTICIPATED -> legacy(item.getId(), () -> instanceAccess.isParticipant(item));
                    case READABLE -> legacy(item.getId(), () -> instanceAccess.canRead(item));
                };
                if (!visible) continue;
                var view = projection.instance(item);
                if (!matches(query.processConfigId(), view.processConfigId())
                        || query.businessReference() != null && !query.businessReference().equals(view.businessReference())
                        || query.lifecycle() != null && query.lifecycle() != view.lifecycle()
                        || query.endType() != null && query.endType() != view.endType()) continue;
                if (total >= query.page().offset() && page.size() < query.page().pageSize()) page.add(view);
                total++;
            }
            if (batch.size() < 200) break;
        }
        return new ProcessPage<>(page, total, query.page().pageNumber(), query.page().pageSize());
    }

    /** 同一业务记录的每轮单独授权；动作范围只覆盖当前一轮，不借关联读取其他实例。 */
    @Override
    public List<ProcessRoundView> listRounds(String anchor) {
        requireInstance(anchor);
        var link = links.findByProcessInstanceId(anchor);
        if (link == null) return List.of(round(requireInstance(anchor), 1));
        List<ProcessRoundView> result = new ArrayList<>();
        for (var candidate : links.selectList(Wrappers.<EntityProcessLink>lambdaQuery()
                .eq(EntityProcessLink::getEntityCode, link.getEntityCode())
                .eq(EntityProcessLink::getEntityRecordId, link.getEntityRecordId())
                .orderByDesc(EntityProcessLink::getGeneration))) {
            if (!hasText(candidate.getProcessInstanceId())) continue;
            try {
                findInstance(candidate.getProcessInstanceId()).ifPresent(view -> result.add(round(view,
                        candidate.getGeneration() == null ? 1 : candidate.getGeneration())));
            } catch (ProcessOperationException denied) {
                // 只跳过明确的范围拒绝，数据库、历史反序列化等故障必须继续向外传播。
                if (!Set.of("PROCESS_ACCESS_DENIED", "PROCESS_ACTOR_REQUIRED").contains(denied.getErrorCode())) throw denied;
            }
        }
        return List.copyOf(result);
    }

    /** 本地加签按子任务、父编排和源任务共同判断存活，不能只看 taskId 前缀。 */
    @Override
    public Optional<ProcessTaskView> findActiveTask(String taskId) {
        ProcessValues.required(taskId, "taskId");
        var task = tasks.createTaskQuery().taskId(taskId).singleResult();
        if (task != null) {
            access.requireRead(task.getProcessInstanceId());
            return Optional.of(engineTaskView(task));
        }
        var mirror = processTasks.getTaskByTaskId(taskId);
        if (mirror == null || !isActiveLocal(mirror)) return Optional.empty();
        access.requireRead(mirror.getProcessInstanceId());
        return Optional.of(projection.mirror(mirror));
    }

    /** 返回全部活动分支及已激活的本地加签任务；排序稳定，不隐式挑选第一项。 */
    @Override
    public List<ProcessTaskView> listActiveTasks(String instanceId) {
        requireInstance(instanceId);
        var result = new ArrayList<ProcessTaskView>();
        tasks.createTaskQuery().processInstanceId(instanceId).list().stream().map(this::engineTaskView).forEach(result::add);
        processTasks.getTasksByProcessInstance(instanceId).stream().filter(this::isActiveLocal)
                .map(projection::mirror).forEach(result::add);
        return result.stream().sorted(order(ProcessTaskView::createTime, ProcessTaskView::taskId, ProcessSort.OLDEST_FIRST)).toList();
    }

    /** 引擎判断任务是否存活，平台镜像补充加签造成的等待状态；不以旧镜像替换实时办理人。 */
    private ProcessTaskView engineTaskView(org.flowable.task.api.Task task) {
        var view = projection.task(task);
        var mirror = processTasks.getTaskByTaskId(task.getId());
        if (mirror == null || !Objects.equals(view.instanceId(), mirror.getProcessInstanceId())
                || !(ProcessTask.STATUS_WAITING.equals(mirror.getStatus()) || ProcessTask.STATUS_HOLD.equals(mirror.getStatus()))
                || task.isSuspended()) return view;
        return new ProcessTaskView(view.taskId(), view.engineTaskId(), view.kind(), view.instanceId(), view.nodeId(),
                view.taskName(), mirror.getStatus(), view.assigneeId(), view.createTime(), view.endTime(), false);
    }

    /** 待办/已办范围复用平台 SQL，包含业务用户组、角色及本地加签有效性。 */
    @Override
    public ProcessPage<ProcessTaskView> pageTasks(ProcessTaskQuery query) {
        List<ProcessTaskView> visible;
        Map<String, String> names = new HashMap<>();
        Map<String, String> keys = new HashMap<>();
        if (query.scope() == ProcessTaskScope.INSTANCE_ACTIVE) {
            visible = listActiveTasks(query.processInstanceId());
        } else {
            var actor = access.requireActor();
            String identity = hasText(actor.userId()) ? actor.userId() : actor.username();
            var source = query.scope() == ProcessTaskScope.MY_TODO
                    ? processTasks.getTodoList(identity) : processTasks.getDoneList(identity);
            visible = source.stream().filter(item -> matches(query.processInstanceId(), item.getProcessInstanceId()))
                    .peek(item -> { names.put(item.getProcessInstanceId(), item.getProcessName()); keys.put(item.getProcessInstanceId(), item.getProcessKey()); })
                    .map(item -> query.scope() == ProcessTaskScope.MY_TODO
                            ? findActiveTask(item.getTaskId()).orElse(null) : projection.mirror(item))
                    .filter(Objects::nonNull).toList();
        }
        var matching = visible.stream().filter(task -> {
            // 按实例查询没有镜像元数据时才读取实例历史，避免为了关键词加载实体全文。
            if (!keys.containsKey(task.instanceId())) {
                var instance = historic(task.instanceId());
                keys.put(task.instanceId(), instance == null ? null : instance.getProcessDefinitionKey());
                names.put(task.instanceId(), instance == null ? null : instance.getProcessDefinitionName());
            }
            return matches(query.processKey(), keys.get(task.instanceId())) && contains(task.taskName(), query.taskName())
                    && (!hasText(query.keyword()) || contains(task.taskName(), query.keyword()) || contains(names.get(task.instanceId()), query.keyword()))
                    && time.inRange(task.createTime(), query.from(), query.to());
        }).sorted(order(ProcessTaskView::createTime, ProcessTaskView::taskId, query.page().sort())).toList();
        return page(matching, query.page());
    }

    /** 镜像一行对应一个历史项；引擎历史只补充没有镜像的旧任务，不按 taskId 覆盖转办记录。 */
    @Override
    public ProcessPage<ProcessTaskHistoryView> pageTaskHistory(ProcessHistoryQuery query) {
        requireInstance(query.processInstanceId());
        List<ProcessTaskHistoryView> result = new ArrayList<>();
        Set<String> mirrored = new HashSet<>();
        var archived = history.createHistoricTaskInstanceQuery().processInstanceId(query.processInstanceId()).list();
        Map<String, String> cancellations = new HashMap<>();
        archived.forEach(task -> cancellations.put(task.getId(),
                ProcessEndReason.isCancelled(task.getDeleteReason()) ? ProcessEndReason.comment(task.getDeleteReason()) : null));
        for (var item : processTasks.getTasksByProcessInstance(query.processInstanceId())) {
            mirrored.add(item.getTaskId());
            var view = projection.mirror(item);
            result.add(new ProcessTaskHistoryView("mirror:" + item.getId(), item.getTaskId(), view.kind(), item.getNodeId(),
                    item.getAssigneeId(), item.getAction(), item.getComment(), view.createTime(), view.endTime(), cancellations.get(item.getTaskId())));
        }
        for (var task : archived) {
            if (mirrored.contains(task.getId())) continue;
            result.add(new ProcessTaskHistoryView("engine:" + task.getId(), task.getId(), "ENGINE", task.getTaskDefinitionKey(),
                    task.getAssignee(), null, null, time.instant(task.getStartTime()), time.instant(task.getEndTime()), cancellations.get(task.getId())));
        }
        return page(result.stream().filter(item -> matches(query.taskId(), item.taskId()) && matches(query.nodeId(), item.nodeId())
                && time.inRange(item.startTime(), query.from(), query.to()))
                .sorted(order(ProcessTaskHistoryView::startTime, ProcessTaskHistoryView::historyId, query.page().sort())).toList(), query.page());
    }

    /**
     * 业务操作日志与命令回执各保留独立标识：一条命令可产生多个步骤日志，不能按 taskId 去重。
     * 旧日志没有 requestId，明确保留 null；回执携带可用于业务重试的请求标识。
     */
    @Override
    public ProcessPage<ProcessOperationView> pageOperations(ProcessHistoryQuery query) {
        requireInstance(query.processInstanceId());
        Set<String> nodeTasks = new HashSet<>();
        if (hasText(query.nodeId())) processTasks.getTasksByProcessInstance(query.processInstanceId()).stream()
                .filter(item -> query.nodeId().equals(item.getNodeId())).map(ProcessTask::getTaskId).forEach(nodeTasks::add);
        var result = new ArrayList<ProcessOperationView>();
        for (var item : operations.selectList(Wrappers.<ProcessOperationLog>lambdaQuery()
                .eq(ProcessOperationLog::getProcessInstanceId, query.processInstanceId()))) {
            result.add(new ProcessOperationView("log:" + item.getId(), null, item.getProcessInstanceId(), item.getTaskId(),
                    item.getOperationType(), item.getOperatorId(), time.instant(item.getOperationTime()), item.getOperationComment()));
        }
        for (var item : commands.selectList(Wrappers.<ProcessCommandRecord>lambdaQuery()
                .eq(ProcessCommandRecord::getProcessInstanceId, query.processInstanceId()).eq(ProcessCommandRecord::getStatus, "SUCCESS"))) {
            String operator = item.getInvocationScope().startsWith("user:") ? item.getInvocationScope().substring(5) : null;
            // 命令回执显式以 UTC 写入，与旧业务表日期采用不同且已声明的存储约定。
            result.add(new ProcessOperationView(item.getId(), item.getRequestId(), item.getProcessInstanceId(), item.getTaskId(),
                    item.getCommandType(), operator, item.getCreateTime().toInstant(ZoneOffset.UTC), null));
        }
        return page(result.stream().filter(item -> matches(query.taskId(), item.taskId())
                && (!hasText(query.nodeId()) || nodeTasks.contains(item.taskId()))
                && time.inRange(item.time(), query.from(), query.to()))
                .sorted(order(ProcessOperationView::time, ProcessOperationView::operationId, query.page().sort())).toList(), query.page());
    }

    /** 历史活动的 unfinished 范围保留同一节点的多个执行分支；已结束实例返回空集合。 */
    @Override
    public List<ProcessActivityView> listActiveActivities(String instanceId) {
        if (requireInstance(instanceId).lifecycle() == ProcessLifecycle.ENDED) return List.of();
        return history.createHistoricActivityInstanceQuery().processInstanceId(instanceId).unfinished().list().stream()
                .map(item -> new ProcessActivityView(item.getActivityId(), item.getActivityType(), item.getExecutionId(), true))
                .sorted(Comparator.comparing(ProcessActivityView::activityId).thenComparing(ProcessActivityView::executionId,
                        Comparator.nullsLast(Comparator.naturalOrder()))).toList();
    }

    /** 操作能力用于提示，命令执行仍需重新检查发布规则和任务状态。 */
    @Override
    public TaskCapabilities getTaskCapabilities(String taskId) {
        access.requireActor();
        return legacy(taskId, () -> {
            if (addSignUsers.findByGeneratedTaskId(taskId) != null) {
                localAccess.requireCurrentUserAccess(taskId, null);
                Map<String, ActionCapability> actions = new LinkedHashMap<>();
                for (String action : List.of("approve", "reject", "transfer", "addSign", "manualCc", "claim", "returnToNode", "resubmit")) {
                    boolean allowed = Set.of("approve", "reject").contains(action);
                    actions.put(action, new ActionCapability(allowed, allowed ? null : "LOCAL_ADD_SIGN_UNSUPPORTED",
                            allowed ? null : "本地加签任务仅支持通过或驳回"));
                }
                return new TaskCapabilities(actions, List.of(), List.of());
            }
            var intrinsic = taskOperations.operations(taskId);
            var policy = decisions.availableActions(taskId);
            Map<String, ActionCapability> actions = new LinkedHashMap<>();
            intrinsic.forEach((key, value) -> {
                if (!(value instanceof Boolean allowed)) return;
                var decision = policy.get(key);
                actions.put(key, new ActionCapability(allowed, allowed ? null : decision != null && !decision.allowed()
                        ? decision.reasonCode() : "TASK_STATE_RESTRICTED", allowed ? null : decision != null && !decision.allowed()
                        ? decision.message() : "当前任务状态不允许该操作"));
            });
            var task = findActiveTask(taskId).orElseThrow(() -> new ProcessOperationException("TASK_NOT_FOUND", "任务不存在", taskId));
            actions.put("claim", new ActionCapability(task.canClaim(), task.canClaim() ? null : "TASK_NOT_CLAIMABLE", null));
            var reject = policy.get("reject");
            List<String> targets = reject == null || !reject.allowed() ? List.of() : reject.rule().allowedRejectTargets().stream().sorted().toList();
            boolean addSignOpen = addSigns.countBlockingBySourceTaskId(taskId) > 0;
            boolean canReturn = !targets.isEmpty() && !addSignOpen && Boolean.TRUE.equals(intrinsic.get("reject"));
            actions.put("returnToNode", new ActionCapability(canReturn,
                    canReturn ? null : "TASK_ACTION_NOT_ALLOWED", canReturn ? null : "当前没有可立即退回的目标"));
            var actor = access.requireActor();
            var instance = historic(task.instanceId());
            String starter = instance == null ? null : instance.getStartUserId();
            boolean canResubmit = !addSignOpen && starter != null
                    && (starter.equals(actor.userId()) || starter.equals(actor.username()))
                    && (Boolean.TRUE.equals(runtime.getVariable(task.instanceId(), "_rejected_"))
                    || "reject".equals(runtime.getVariable(task.instanceId(), "approved")));
            actions.put("resubmit", new ActionCapability(canResubmit,
                    canResubmit ? null : "TASK_ACTION_NOT_ALLOWED", canResubmit ? null : "当前不是发起人的退回待办"));
            List<String> types = ((List<?>) intrinsic.getOrDefault("addSignTypes", List.of())).stream().map(String::valueOf).toList();
            return new TaskCapabilities(actions, types, targets);
        });
    }

    @Override
    public NextApprovalPreview previewNextApproval(NextApprovalQuery query) {
        access.requireActor();
        return legacy(query.taskId(), () -> {
            var preview = approvalQueries.preview(query.taskId(), previewRequest(query));
            return new NextApprovalPreview(preview.getTaskId(), preview.getProcessDefinitionId(),
                    ApprovalPreviewStatus.valueOf(preview.getStatus().name()), preview.getScopeKey(), preview.getNodes().stream()
                    .map(node -> new NextApprovalNode(node.getNodeId(), node.getNodeName(), node.isVisible(), node.isEditable(),
                            node.getAssignmentMode(), node.isMultiple(), node.getSourceType(), node.getAssignees().stream().map(this::candidate).toList())).toList(),
                    preview.getMessage());
        });
    }

    @Override
    public ProcessPage<ApproverOption> pageNextApproverOptions(NextApproverOptionsQuery query) {
        access.requireActor();
        return legacy(query.approval().taskId(), () -> {
            var request = new NextApproverOptionsRequest();
            request.setAction(query.approval().actionCode()); request.setActionLabel(query.approval().actionLabel());
            request.setComment(query.approval().comment()); request.setFormData(query.approval().formData());
            request.setScopeKey(query.scopeKey()); request.setTargetNodeId(query.targetNodeId()); request.setKeyword(query.keyword());
            request.setPageNum(query.page().pageNumber()); request.setPageSize(query.page().pageSize());
            var result = approvalQueries.options(query.approval().taskId(), request);
            return new ProcessPage<>(result.getRecords().stream().map(this::candidate).toList(), result.getTotal(),
                    (int) result.getPageNum(), (int) result.getPageSize());
        });
    }

    /** 本地任务必须已激活、编排仍开放且真实源任务存活；HOLD 不是可办理的活动任务。 */
    private boolean isActiveLocal(ProcessTask task) {
        if (!"ADD_SIGN".equals(task.getNodeType()) || !"todo".equals(task.getStatus())) return false;
        var child = addSignUsers.findByGeneratedTaskId(task.getTaskId());
        if (child == null || !"TODO".equals(child.getStatus())) return false;
        var parent = addSigns.selectById(child.getAddSignId());
        if (parent == null || !"ACTIVE".equals(parent.getStatus())
                || !Objects.equals(parent.getProcessInstanceId(), task.getProcessInstanceId())) return false;
        var source = tasks.createTaskQuery().taskId(parent.getSourceTaskId()).singleResult();
        return source != null && Objects.equals(source.getProcessInstanceId(), task.getProcessInstanceId());
    }

    private HistoricProcessInstance historic(String id) {
        return history.createHistoricProcessInstanceQuery().processInstanceId(id).singleResult();
    }

    private ProcessRoundView round(ProcessInstanceView instance, int generation) {
        return new ProcessRoundView(instance.instanceId(), generation, instance.lifecycle(), instance.endType(), instance.startTime(), instance.endTime());
    }

    private NextApprovalPreviewRequest previewRequest(NextApprovalQuery query) {
        var request = new NextApprovalPreviewRequest();
        request.setAction(query.actionCode()); request.setActionLabel(query.actionLabel());
        request.setComment(query.comment()); request.setFormData(query.formData());
        return request;
    }

    private ApproverOption candidate(NextApproverCandidateDTO item) {
        return new ApproverOption(item.getUserId(), item.getUsername(), item.getDisplayName());
    }

    private static <T> ProcessPage<T> page(List<T> source, ProcessPageRequest request) {
        return new ProcessPage<>(source.stream().skip(request.offset()).limit(request.pageSize()).toList(),
                source.size(), request.pageNumber(), request.pageSize());
    }

    /** 同时按时间和稳定标识排序，避免多行同毫秒时跨页重复或遗漏。 */
    private static <T> Comparator<T> order(Function<T, Instant> timestamp, Function<T, String> id, ProcessSort sort) {
        Comparator<T> comparator = Comparator.comparing(timestamp, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(id);
        return sort == ProcessSort.NEWEST_FIRST ? comparator.reversed() : comparator;
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static boolean matches(String filter, String value) { return !hasText(filter) || filter.equals(value); }
    private static boolean contains(String value, String keyword) { return !hasText(keyword) || value != null && value.contains(keyword); }

    /** 仅转换现有明确的授权拒绝，技术异常保留原因链并交由统一错误处理记录。 */
    private static <T> T legacy(String target, Supplier<T> work) {
        try { return work.get(); }
        catch (ForbiddenException denied) { throw new ProcessOperationException("PROCESS_ACCESS_DENIED", denied.getMessage(), target, denied); }
    }
}
