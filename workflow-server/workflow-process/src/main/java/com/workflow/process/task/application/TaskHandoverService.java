package com.workflow.process.task.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.contracts.audit.model.*;
import com.workflow.contracts.identity.model.IdentityHandoverUser;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.result.PageResult;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.sla.runtime.application.TaskSlaRuntimeService;
import com.workflow.process.task.api.request.TaskHandoverRequest;
import com.workflow.process.task.api.response.TaskHandoverTask;
import com.workflow.process.task.infrastructure.persistence.mapper.*;
import com.workflow.process.task.infrastructure.persistence.record.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理员人员交接：不推进流程，也不套用普通办理人的转办开关。
 * 来源可以禁用或已删除；接收人必须正常。引擎、本地加签、SLA 与审计同事务提交。
 */
@Service
@RequiredArgsConstructor
public class TaskHandoverService {
    private final IdentityDirectoryPort directory;
    private final TaskHandoverMapper handover;
    private final ProcessTaskMapper tasks;
    private final ProcessTaskAddSignMapper addSigns;
    private final ProcessTaskAddSignUserMapper addSignUsers;
    private final TaskInboxProjectionMapper projection;
    private final TaskInboxProjectionService inbox;
    private final TaskService engine;
    private final ProcessTaskService processTasks;
    private final TaskSlaRuntimeService sla;
    private final ProcessOperationLogMapper logs;

    /**
     * 查询来源人员的全部未完成责任，分页不影响后续“全部交接”的范围。
     * 加签等待/暂缓任务保留原状态，避免交接激活尚未到达的审批环节。
     */
    @Transactional(readOnly = true)
    public PageResult<TaskHandoverTask> findPage(String sourceUserId, long pageNum, long pageSize) {
        requireSource(sourceUserId);
        if (pageNum < 1 || pageSize < 1 || pageSize > 200 || pageNum > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页参数无效，每页最多查询 200 条");
        }
        long total = handover.count(sourceUserId);
        List<TaskHandoverTask> records = (pageNum - 1) * pageSize >= total ? List.of()
                : handover.selectPage(new Page<>(pageNum, pageSize, false), sourceUserId);
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    /**
     * 执行一批交接；任何任务已被完成/转走或接收人异常都会令整批失败，不返回半成功。
     * 接收人行锁与账号停用互斥，持锁后再次校验任务，防止旧页面覆盖最新办理结果。
     *
     * @param request 指定来源、正常接收人、选中任务或全部任务及必填原因
     * @return 实际交接数量；提交时已无待办则返回 0
     * @throws BusinessConflictException 任务归属、加签状态或接收人状态已变化
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @SystemAudit(module = AuditModule.SYSTEM, action = AuditAction.TRANSFER,
            operation = "人员待办批量交接", risk = AuditRiskLevel.HIGH, required = true,
            targetType = "TASK_HANDOVER", captureArguments = true, captureResult = true)
    public int transfer(TaskHandoverRequest request) {
        validateRequest(request);
        IdentityHandoverUser source = requireSource(request.sourceUserId());
        IdentityHandoverUser target = directory.lockHandoverUser(request.targetUserId())
                .orElseThrow(() -> new IllegalArgumentException("接收人员不存在"));
        if (target.deleted() || !"0".equals(target.status()) || !StringUtils.hasText(target.username())) {
            throw new BusinessConflictException("HANDOVER_TARGET_UNAVAILABLE", "接收人员必须为正常状态，请重新选择");
        }
        List<String> taskIds = new TreeSet<>(request.all()
                ? handover.selectAllTaskIds(source.id()) : request.taskIds()).stream().toList();
        if (taskIds.isEmpty()) return 0;

        List<TransferContext> contexts = lockAndValidate(source, taskIds);
        Set<String> instances = new TreeSet<>();
        for (TransferContext context : contexts) {
            if (context.child() != null) {
                transferAddSign(context, target);
            } else {
                // 候选待办一旦交接就明确分配给接收人，与正常认领后的独占办理语义一致。
                engine.setAssignee(context.task().getTaskId(), target.username());
                inbox.synchronizeTask(context.task().getTaskId());
                transferOpenSourceResponsibility(context, target);
            }
            // 交接不是审批或响应：仅更新 SLA 办理人，保留原截止时间、响应状态及已耗时。
            sla.updateAssignee(context.task().getTaskId(), target.username());
            writeLog(context, source, target, request.reason().trim());
            instances.add(context.task().getProcessInstanceId());
        }
        for (String instanceId : instances) processTasks.refreshAssignmentSummary(instanceId);
        return contexts.size();
    }

    /**
     * 先锁加签编排，再锁引擎任务和本地镜像，与加签完成/取消及身份投影保持顺序。
     * 全批次预检结束后才修改归属；共享候选任务被别人认领时整批提示刷新。
     */
    private List<TransferContext> lockAndValidate(IdentityHandoverUser source, List<String> taskIds) {
        Map<String, ProcessTaskAddSignUser> childLookups = new HashMap<>();
        Map<String, String> parentByTask = new HashMap<>();
        SortedSet<String> parentIds = new TreeSet<>();
        SortedSet<String> engineIds = new TreeSet<>();
        for (String taskId : taskIds) {
            ProcessTask mirror = tasks.selectByTaskId(taskId);
            if (mirror == null) throw changed();
            if ("ADD_SIGN".equals(mirror.getNodeType())) {
                ProcessTaskAddSignUser child = addSignUsers.findByGeneratedTaskId(taskId);
                if (child == null) throw changed();
                childLookups.put(taskId, child);
                parentByTask.put(taskId, child.getAddSignId());
                parentIds.add(child.getAddSignId());
            } else {
                engineIds.add(taskId);
                ProcessTaskAddSign parent = addSigns.findOpenBySourceTaskId(taskId);
                if (parent != null) {
                    parentByTask.put(taskId, parent.getId());
                    parentIds.add(parent.getId());
                }
            }
        }
        Map<String, ProcessTaskAddSign> parents = new HashMap<>();
        for (String id : parentIds) {
            ProcessTaskAddSign parent = addSigns.selectByIdForUpdate(id);
            if (parent == null || !List.of("ACTIVE", "WAITING_SOURCE").contains(parent.getStatus())) throw changed();
            parents.put(id, parent);
            engineIds.add(parent.getSourceTaskId());
        }
        for (String id : engineIds) if (projection.lockEngineTask(id) == null) throw changed();

        List<TransferContext> contexts = new ArrayList<>();
        for (String taskId : taskIds) {
            // 子任务完成同样先锁编排和明细；不能只换镜像，遗留明细会拒绝新办理人审批。
            ProcessTaskAddSignUser child = childLookups.containsKey(taskId)
                    ? addSignUsers.findByGeneratedTaskIdForUpdate(taskId) : null;
            ProcessTask task = tasks.selectByTaskIdForUpdate(taskId);
            TaskHandoverTask eligible = handover.findEligible(source.id(), taskId);
            if (task == null || eligible == null) throw changed();
            ProcessTaskAddSign parent = parents.get(parentByTask.get(taskId));
            if ("ADD_SIGN".equals(task.getNodeType())) {
                if (child == null || parent == null || !"ACTIVE".equals(parent.getStatus())
                        || !Objects.equals(parent.getId(), child.getAddSignId())
                        || !List.of("TODO", "HOLD").contains(child.getStatus())
                        || !matches(source, child.getUserId())) throw changed();
            } else {
                Task runtimeTask = engine.createTaskQuery().taskId(taskId).singleResult();
                if (runtimeTask == null || StringUtils.hasText(runtimeTask.getAssignee())
                        && !matches(source, runtimeTask.getAssignee())) throw changed();
                // 预读后若新增加签，不能越过未锁定的编排写入；下一次刷新会按新结构加锁。
                ProcessTaskAddSign current = addSigns.findOpenBySourceTaskId(taskId);
                if (!Objects.equals(current == null ? null : current.getId(), parentByTask.get(taskId))) throw changed();
            }
            contexts.add(new TransferContext(task, parent, child));
        }
        return contexts;
    }

    /** 保留加签 TODO/HOLD 以及表单、处理顺序，只同步明细与镜像的办理身份。 */
    private void transferAddSign(TransferContext context, IdentityHandoverUser target) {
        ProcessTaskAddSignUser child = context.child();
        child.setUserId(target.username());
        child.setUserNameSnapshot(displayName(target));
        if (addSignUsers.updateById(child) != 1) throw changed();
        if (projection.updateIdentity(context.task().getId(), target.username(), displayName(target), "user") != 1) {
            throw changed();
        }
    }

    /**
     * 原任务尚未提交时，将加签编排的后续办理/撤销责任移交新办理人；原创建人仍在操作日志中。
     * 原审批已经暂存时保留其提交身份，不能把已经做出的审批追记为接收人作出。
     */
    private void transferOpenSourceResponsibility(TransferContext context, IdentityHandoverUser target) {
        ProcessTaskAddSign parent = context.parent();
        if (parent != null && !Boolean.TRUE.equals(parent.getSourceCompleted())) {
            parent.setOperatorId(target.username());
            if (addSigns.updateById(parent) != 1) throw changed();
        }
    }

    /** 每项交接保留操作者、原责任人、接收人和原因；不会生成已审批或已完成记录。 */
    private void writeLog(TransferContext context, IdentityHandoverUser source, IdentityHandoverUser target, String reason) {
        ProcessOperationLog log = new ProcessOperationLog();
        log.setProcessInstanceId(context.task().getProcessInstanceId());
        log.setTaskId(context.task().getTaskId());
        log.setOperationType("TRANSFER");
        log.setOperatorId(UserContext.getUserId());
        log.setOperatorName(directory.getDisplayName(UserContext.getUserId()));
        log.setOperationTime(LocalDateTime.now());
        log.setOperationComment("人员交接：" + reason);
        log.setOldValue(displayName(source));
        log.setNewValue(displayName(target));
        log.setOldValueFormat("PLAIN_TEXT");
        log.setNewValueFormat("PLAIN_TEXT");
        if (logs.insert(log) != 1) throw new IllegalStateException("人员交接记录写入失败");
    }

    private IdentityHandoverUser requireSource(String id) {
        if (!StringUtils.hasText(id)) throw new IllegalArgumentException("请选择待交接人员");
        return directory.findHandoverUser(id).orElseThrow(() -> new IllegalArgumentException("待交接人员不存在"));
    }

    /** Service 层也校验入参，避免内部调用绕开 Web Bean Validation。 */
    private void validateRequest(TaskHandoverRequest request) {
        if (request == null || !StringUtils.hasText(request.sourceUserId()) || !StringUtils.hasText(request.targetUserId())) {
            throw new IllegalArgumentException("请选择待交接人员和接收人员");
        }
        if (request.sourceUserId().equals(request.targetUserId())) throw new IllegalArgumentException("接收人员不能与待交接人员相同");
        if (!StringUtils.hasText(request.reason()) || request.reason().length() > 500) {
            throw new IllegalArgumentException("请填写交接原因，最多 500 字");
        }
        if (!request.all() && (request.taskIds() == null || request.taskIds().isEmpty()
                || request.taskIds().stream().anyMatch(id -> !StringUtils.hasText(id) || id.length() > 64))) {
            throw new IllegalArgumentException("请选择需要交接的待办");
        }
    }

    private static boolean matches(IdentityHandoverUser user, String value) {
        return Objects.equals(user.id(), value) || Objects.equals(user.username(), value);
    }

    private static String displayName(IdentityHandoverUser user) {
        return StringUtils.hasText(user.nickname()) && !user.nickname().equals(user.username())
                ? user.nickname() + "(" + user.username() + ")" : user.username();
    }

    private static BusinessConflictException changed() {
        return new BusinessConflictException("HANDOVER_TASK_CHANGED", "待办已被处理、交接或加签状态发生变化，请刷新后重新交接");
    }

    private record TransferContext(ProcessTask task, ProcessTaskAddSign parent,
                                   ProcessTaskAddSignUser child) {}
}
