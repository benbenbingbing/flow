package com.workflow.process.task.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.ForbiddenException;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.process.task.api.request.TaskAddSignRequest;
import com.workflow.process.task.application.operation.NodeOperationCapabilityService;
import com.workflow.process.task.application.operation.NodeOperationDecisionService;
import com.workflow.process.task.application.operation.NodeOperationPolicy;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSign;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSignUser;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignUserMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 任务加签服务。
 *
 * <p>支持三种加签模式：并行（PARALLEL）、前加签（BEFORE）、后加签（AFTER）。
 * 通过本地任务镜像 + 加签记录表管理加签子任务的生命周期，并在加签完成后延迟提交原任务。
 * 提供加签预览、新增、撤销、加签任务办理与原任务提交处理等能力。</p>
 */
@Service
@RequiredArgsConstructor
public class TaskAddSignService {
    /** 支持的加签类型 */
    private static final List<String> TYPES = List.of("PARALLEL", "BEFORE", "AFTER");

    private final TaskService taskService;
    private final ProcessTaskMapper processTaskMapper;
    private final ProcessTaskAddSignMapper addSignMapper;
    private final ProcessTaskAddSignUserMapper addSignUserMapper;
    private final ProcessOperationLogMapper operationLogMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;
    /** 加签必须在写入记录或创建镜像任务前通过节点开关校验。 */
    private final NodeOperationCapabilityService nodeOperationCapabilityService;
    /** 任务动作服务（用于加签完成后延迟提交原任务），延迟加载避免循环依赖 */
    @Lazy
    private final TaskActionService taskActionService;
    /** 与认领和审批共用身份校验，避免业务用户组候选人被引擎身份库误判。 */
    private final TaskIdentityAccessService taskIdentityAccessService;

    /**
     * 查询任务当前可执行的操作集合及进行中的加签信息。
     *
     * @param taskId 任务ID
     * @return 操作集合（含审批、转办、加签是否可用及加签类型）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> operations(String taskId) {
        Task task = requireSourceTask(taskId);
        requireTaskOperator(task);
        ProcessTaskAddSign current = findOpenBySourceTask(taskId);
        return Map.of(
                "approve", true,
                "reject", true,
                "transfer", current == null,
                "addSign", current == null,
                "manualCc", true,
                "addSignTypes", TYPES,
                "activeAddSign", current == null ? Map.of() : addSignView(current));
    }

    /**
     * 加签预览：解析加签人员并返回去重、禁用、无效等校验结果。
     *
     * @param taskId            任务ID
     * @param requestedUserIds  加签人员标识列表
     * @param requestedType     加签类型
     * @return 预览结果（含人员、重复/禁用/无效列表及结构说明）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(String taskId, List<String> requestedUserIds, String requestedType) {
        Task task = requireSourceTask(taskId);
        requireTaskOperator(task);
        String type = normalizeType(requestedType);
        Resolution resolution = resolveUsers(requestedUserIds, task.getAssignee());
        return Map.of(
                "users", resolution.users().stream().map(this::userView).toList(),
                "duplicates", resolution.duplicates(),
                "disabled", resolution.disabled(),
                "invalid", resolution.invalid(),
                "taskCount", resolution.users().size(),
                "type", type,
                "structure", structureSummary(type));
    }

    /**
     * 新增加签。
     *
     * <p>创建加签记录，为每个加签人生成镜像任务（后加签时先挂起），并记录操作日志。</p>
     *
     * @param taskId  任务ID
     * @param request 加签请求
     * @return 加签结果（含加签ID、生成的任务ID及结构说明）
     * @throws IllegalArgumentException 完成策略不支持或无可用加签人员时抛出
     * @throws IllegalStateException    任务镜像不存在或已存在进行中加签时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.ADD_SIGN,
            operation = "发起任务加签",
            risk = AuditRiskLevel.HIGH,
            targetType = "PROCESS_TASK",
            targetIdArg = 0,
            captureResult = true)
    public Map<String, Object> addSign(String taskId, TaskAddSignRequest request) {
        Task sourceTask = requireSourceTask(taskId);
        String operator = requireTaskOperator(sourceTask);
        String type = normalizeType(request.getType());
        requireAddSignAllowed(taskId, request, type);
        if (!"ALL".equalsIgnoreCase(request.getCompletionPolicy())) {
            throw new IllegalArgumentException("当前加签完成策略仅支持全部完成");
        }
        ProcessTask sourceMirror = processTaskMapper.selectByTaskIdForUpdate(taskId);
        if (sourceMirror == null) {
            throw new IllegalStateException("任务镜像不存在，请先同步流程任务");
        }
        if (findOpenBySourceTask(taskId) != null) {
            throw new IllegalStateException("当前任务已存在进行中的加签");
        }

        Resolution resolution = resolveUsers(request.getUserIds(), sourceTask.getAssignee());
        if (resolution.users().isEmpty()) {
            throw new IllegalArgumentException("没有可用的加签人员");
        }

        ProcessTaskAddSign addSign = new ProcessTaskAddSign();
        addSign.setProcessInstanceId(sourceTask.getProcessInstanceId());
        addSign.setSourceTaskId(taskId);
        addSign.setNodeId(sourceTask.getTaskDefinitionKey());
        addSign.setOperationType(type);
        addSign.setOperatorId(operator);
        addSign.setComment(request.getComment());
        addSign.setStatus("ACTIVE");
        addSign.setEngineExecutionId(sourceTask.getExecutionId());
        addSign.setSourceCompleted(false);
        addSign.setCreateTime(LocalDateTime.now());
        addSignMapper.insert(addSign);

        boolean held = "AFTER".equals(type);
        List<String> generatedTaskIds = new ArrayList<>();
        int sortOrder = 0;
        for (SysUser user : resolution.users()) {
            String generatedTaskId = "addsign-" + UUID.randomUUID();
            processTaskMapper.insert(copyAsAddSignTask(sourceMirror, generatedTaskId, user, held));

            ProcessTaskAddSignUser addSignUser = new ProcessTaskAddSignUser();
            addSignUser.setAddSignId(addSign.getId());
            addSignUser.setUserId(user.getUsername());
            addSignUser.setUserNameSnapshot(displayName(user));
            addSignUser.setGeneratedTaskId(generatedTaskId);
            addSignUser.setStatus(held ? "HOLD" : "TODO");
            addSignUser.setSortOrder(sortOrder++);
            addSignUserMapper.insert(addSignUser);
            generatedTaskIds.add(generatedTaskId);
        }
        writeOperationLog(
                addSign,
                taskId,
                "ADD_SIGN",
                operator,
                request.getComment(),
                Map.of(
                        "type", type,
                        "users", resolution.users().stream().map(SysUser::getUsername).toList(),
                        "generatedTaskIds", generatedTaskIds));

        return Map.of(
                "addSignId", addSign.getId(),
                "type", type,
                "generatedTaskIds", generatedTaskIds,
                "users", resolution.users().stream().map(this::userView).toList(),
                "summary", structureSummary(type));
    }

    /**
     * 在任何加签持久化副作用前统一校验新开关及存量矩阵。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param request 本次请求，后续经校验后用于校验并获取添加签名允许
     * @param type 类型标识，决定后续添加签名允许采用的处理分支
     */
    private void requireAddSignAllowed(
            String taskId,
            TaskAddSignRequest request,
            String type) {
        NodeOperationPolicy.Operation operation = switch (type) {
            case "BEFORE" -> NodeOperationPolicy.Operation.ADD_SIGN_BEFORE;
            case "AFTER" -> NodeOperationPolicy.Operation.ADD_SIGN_AFTER;
            default -> NodeOperationPolicy.Operation.ADD_SIGN_PARALLEL;
        };
        nodeOperationCapabilityService.requireAllowed(
                taskId,
                operation,
                NodeOperationDecisionService.CheckContext.ofTarget(
                        request.getComment(),
                        request.getUserIds() == null
                                ? Set.of()
                                : new java.util.LinkedHashSet<>(request.getUserIds()),
                        null,
                        type,
                        Map.of("completionPolicy",
                                request.getCompletionPolicy() == null
                                        ? ""
                                        : request.getCompletionPolicy())));
    }

    /**
     * 判断指定任务是否为加签生成的子任务。
     *
     * @param taskId 任务ID
     * @return true 表示是加签子任务
     */
    @Transactional(readOnly = true)
    public boolean isAddSignTask(String taskId) {
        return addSignUserMapper.findByGeneratedTaskId(taskId) != null;
    }

    /**
     * 若为加签子任务则校验当前办理人并返回 true；非加签子任务返回 false。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 添加签名任务访问条件成立时为 true，否则为 false
     */
    @Transactional(readOnly = true)
    public boolean requireAddSignTaskAccess(String taskId) {
        ProcessTaskAddSignUser task =
                addSignUserMapper.findByGeneratedTaskId(taskId);
        if (task == null) {
            return false;
        }
        if (!task.getUserId().equals(currentUsername())) {
            throw new ForbiddenException("当前用户不是该加签任务办理人");
        }
        return true;
    }

    /**
     * 判断指定 Flowable 原任务是否正处于加签编排中。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 添加签名来源任务条件成立时为 true，否则为 false
     */
    @Transactional(readOnly = true)
    public boolean isAddSignSourceTask(String taskId) {
        return findOpenBySourceTask(taskId) != null;
    }

    /**
     * 处理原任务的提交（在加签场景下拦截原任务办理）。
     *
     * <p>暂存原任务提交动作：驳回则直接结束加签并提交；后加签则激活挂起的子任务；
     * 否则等待所有加签子任务完成后再提交原任务。</p>
     *
     * @param taskId      原任务ID
     * @param userId     操作人
     * @param action     操作类型
     * @param comment    审批意见
     * @param actionLabel 操作显示文本
     * @param formData   表单数据
     * @return true 表示已作为加签场景处理，false 表示非加签原任务
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean handleSourceCompletion(
            String taskId,
            String userId,
            String action,
            String comment,
            String actionLabel,
            Map<String, Object> formData) {
        ProcessTaskAddSign addSign = findOpenBySourceTaskForUpdate(taskId);
        if (addSign == null) {
            return false;
        }
        Task sourceTask = requireSourceTask(taskId);
        requireTaskOperator(sourceTask);
        String type = addSign.getOperationType();
        String normalizedAction = normalizeAction(action);

        if ("transfer".equals(normalizedAction)) {
            throw new IllegalStateException("加签期间不能转办，请先撤销加签");
        }
        if ("BEFORE".equals(type)) {
            throw new IllegalStateException("前加签尚未全部完成，原任务暂不能提交");
        }

        saveSourceSubmission(addSign, normalizedAction, comment, actionLabel, formData);
        markSourceWaiting(taskId, normalizedAction, comment, actionLabel);

        if ("reject".equals(normalizedAction)) {
            finishRemainingChildren(addSign.getId(), "源任务已驳回");
            finalizeSource(addSign);
            return true;
        }
        if ("AFTER".equals(type)) {
            activateHeldChildren(addSign.getId());
            return true;
        }
        if (addSignUserMapper.countPending(addSign.getId()) == 0) {
            finalizeSource(addSign);
        } else {
            addSign.setStatus("ACTIVE");
            addSignMapper.updateById(addSign);
        }
        return true;
    }

    /**
     * 办理加签子任务（仅支持通过/驳回）。
     *
     * <p>完成本地镜像任务 -> 更新加签用户任务状态 -> 记录操作日志。
     * 驳回则结束其余加签任务并提交原任务（驳回）；全部通过则按加签类型推进（前加签完成、后加签等待/提交原任务）。</p>
     *
     * @param taskId  加签子任务ID
     * @param action  操作类型（approve/reject）
     * @param comment 审批意见
     * @throws IllegalArgumentException 加签任务不存在或状态异常时抛出
     * @throws ForbiddenException       当前用户非加签任务办理人时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.APPROVE,
            operation = "办理加签任务",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "PROCESS_TASK",
            targetIdArg = 0)
    public void completeAddSignTask(String taskId, String action, String comment) {
        ProcessTaskAddSignUser taskLookup = addSignUserMapper.findByGeneratedTaskId(taskId);
        if (taskLookup == null) {
            throw new IllegalArgumentException("加签任务不存在或尚未激活/已处理");
        }
        ProcessTaskAddSign addSign = addSignMapper.selectByIdForUpdate(taskLookup.getAddSignId());
        ProcessTaskAddSignUser userTask = addSignUserMapper.findByGeneratedTaskIdForUpdate(taskId);
        if (addSign == null || userTask == null || !"TODO".equals(userTask.getStatus())) {
            throw new IllegalArgumentException("加签任务不存在或尚未激活/已处理");
        }
        if (!userTask.getUserId().equals(currentUsername())) {
            throw new ForbiddenException("当前用户不是该加签任务办理人");
        }
        String normalizedAction = normalizeAction(action);
        if (!List.of("approve", "reject").contains(normalizedAction)) {
            throw new IllegalArgumentException("加签任务仅支持通过或驳回");
        }

        completeLocalChild(taskId, normalizedAction, comment);
        addSignUserMapper.completeByGeneratedTaskId(taskId);
        writeOperationLog(
                addSign,
                taskId,
                "reject".equals(normalizedAction) ? "ADD_SIGN_REJECT" : "ADD_SIGN_APPROVE",
                currentUsername(),
                comment,
                Map.of("generatedTaskId", taskId, "action", normalizedAction));
        if ("reject".equals(normalizedAction)) {
            saveRejectedSource(addSign, comment);
            finishRemainingChildren(addSign.getId(), "其他加签人已驳回");
            finalizeSource(addSign);
            return;
        }
        if (addSignUserMapper.countPending(addSign.getId()) > 0) {
            return;
        }
        if ("BEFORE".equals(addSign.getOperationType())) {
            addSign.setStatus("COMPLETED");
            addSign.setCompleteTime(LocalDateTime.now());
            addSignMapper.updateById(addSign);
            return;
        }
        if (Boolean.TRUE.equals(addSign.getSourceCompleted())) {
            finalizeSource(addSign);
        } else {
            addSign.setStatus("WAITING_SOURCE");
            addSignMapper.updateById(addSign);
        }
    }

    /**
     * 撤销加签。
     *
     * <p>仅加签发起人可撤销，且原任务未提交、无加签子任务被处理时才允许。</p>
     *
     * @param addSignId 加签记录ID
     * @throws IllegalArgumentException 加签记录不存在时抛出
     * @throws ForbiddenException       非加签发起人操作时抛出
     * @throws IllegalStateException    原任务已提交或已有加签任务被处理时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.CANCEL_ADD_SIGN,
            operation = "撤销任务加签",
            risk = AuditRiskLevel.HIGH,
            targetType = "PROCESS_ADD_SIGN",
            targetIdArg = 0)
    public void cancel(String addSignId) {
        ProcessTaskAddSign addSign = addSignMapper.selectByIdForUpdate(addSignId);
        if (addSign == null) {
            throw new IllegalArgumentException("加签记录不存在");
        }
        if (!currentUsername().equals(addSign.getOperatorId())) {
            throw new ForbiddenException("只有加签发起人可以撤销");
        }
        if (Boolean.TRUE.equals(addSign.getSourceCompleted())) {
            throw new IllegalStateException("原任务已经提交，不能撤销加签");
        }
        List<ProcessTaskAddSignUser> users = listUsers(addSignId);
        boolean handled = users.stream().anyMatch(item -> !List.of("TODO", "HOLD").contains(item.getStatus()));
        if (handled) {
            throw new IllegalStateException("已有加签任务被处理，不能撤销");
        }
        finishRemainingChildren(addSignId, "加签已撤销");
        addSignMapper.cancel(addSignId);
        writeOperationLog(
                addSign,
                addSign.getSourceTaskId(),
                "ADD_SIGN_CANCEL",
                currentUsername(),
                addSign.getComment(),
                Map.of("addSignId", addSignId, "type", addSign.getOperationType()));
    }

    /**
     * 加签全部完成或驳回后，延迟提交原任务（执行暂存的提交动作与表单数据）
     *
     * @param addSign 添加签名，作为 {@code addSignMapper.updateById} 的输入影响后续处理
     */
    private void finalizeSource(ProcessTaskAddSign addSign) {
        Task sourceTask = taskService.createTaskQuery().taskId(addSign.getSourceTaskId()).singleResult();
        if (sourceTask == null) {
            addSign.setStatus("COMPLETED");
            addSign.setCompleteTime(LocalDateTime.now());
            addSignMapper.updateById(addSign);
            return;
        }
        Map<String, Object> formData = readFormData(
                addSign.getSourceFormData());
        if (taskActionService
                .requiresManualNextApproverForDeferredCompletion(
                        addSign.getSourceTaskId(),
                        addSign.getSourceAction(),
                        addSign.getSourceComment(),
                        addSign.getSourceActionLabel(),
                        formData)) {
            restoreSourceForNextApproverConfirmation(addSign);
            return;
        }
        addSign.setStatus("COMPLETED");
        addSign.setCompleteTime(LocalDateTime.now());
        addSignMapper.updateById(addSign);
        taskActionService.completeDeferredTask(
                addSign.getSourceTaskId(),
                addSign.getOperatorId(),
                addSign.getSourceAction(),
                addSign.getSourceComment(),
                null,
                addSign.getSourceActionLabel(),
                formData);
    }

    /**
     * 加签已全部结束，但下一节点需要人工选择且没有默认审批人时，关闭加签编排并
     * 恢复原 Flowable 任务镜像，交回原办理人从正常审批面板重新确认。
     *
     * @param addSign 添加签名，作为 {@code processTaskMapper.selectByTaskId} 的输入影响后续处理
     */
    private void restoreSourceForNextApproverConfirmation(
            ProcessTaskAddSign addSign) {
        String action = addSign.getSourceAction();
        ProcessTask source = processTaskMapper.selectByTaskId(
                addSign.getSourceTaskId());
        if (source == null) {
            throw new IllegalStateException(
                    "无法恢复原审批任务，请刷新后重试");
        }
        source.setStatus(ProcessTask.STATUS_TODO);
        source.setAction(null);
        source.setActionLabel(null);
        source.setComment(null);
        source.setEndTime(null);
        source.setDuration(null);
        source.setUpdateTime(LocalDateTime.now());
        processTaskMapper.updateById(source);

        addSign.setStatus("COMPLETED");
        addSign.setSourceCompleted(false);
        addSign.setSourceAction(null);
        addSign.setSourceActionLabel(null);
        addSign.setSourceComment(null);
        addSign.setSourceFormData(null);
        addSign.setCompleteTime(LocalDateTime.now());
        addSignMapper.updateById(addSign);
        writeOperationLog(
                addSign,
                addSign.getSourceTaskId(),
                "ADD_SIGN_NEXT_APPROVER_CONFIRMATION_REQUIRED",
                addSign.getOperatorId(),
                "下一节点审批人需要人工确认，已恢复原待办",
                Map.of(
                        "sourceAction", action == null ? "" : action,
                        "sourceTaskRestored", true));
    }

    /**
     * 记录原任务被驳回的提交动作（加签人驳回后用于回填原任务提交）
     *
     * @param addSign 添加签名，作为 {@code addSignMapper.updateById} 的输入影响后续处理
     * @param comment 注释，作为 {@code addSign.setSourceComment} 的输入影响后续处理
     */
    private void saveRejectedSource(ProcessTaskAddSign addSign, String comment) {
        addSign.setSourceCompleted(true);
        addSign.setSourceAction("reject");
        addSign.setSourceActionLabel("加签驳回");
        addSign.setSourceComment(comment);
        addSignMapper.updateById(addSign);
    }

    /**
     * 暂存原任务的提交动作、意见与表单数据，供加签完成后延迟提交使用
     *
     * @param addSign 添加签名，作为 {@code addSignMapper.updateById} 的输入影响后续处理
     * @param action 动作标识，决定后续来源提交采用的处理分支
     * @param comment 注释，作为 {@code addSign.setSourceComment} 的输入影响后续处理
     * @param actionLabel 动作标签，后续用于保存来源提交时匹配或展示
     * @param formData 表单数据，作为 {@code addSign.setSourceFormData} 的输入影响后续处理
     */
    private void saveSourceSubmission(
            ProcessTaskAddSign addSign,
            String action,
            String comment,
            String actionLabel,
            Map<String, Object> formData) {
        addSign.setSourceCompleted(true);
        addSign.setSourceAction(action);
        addSign.setSourceComment(comment);
        addSign.setSourceActionLabel(actionLabel);
        addSign.setSourceFormData(writeFormData(formData));
        addSignMapper.updateById(addSign);
    }

    /**
     * 激活后加签中挂起的子任务（将 HOLD 状态改为待办）
     *
     * @param addSignId 添加签名ID，后续用于激活{@code held}子节点时定位或关联目标
     */
    private void activateHeldChildren(String addSignId) {
        addSignUserMapper.activateHeld(addSignId);
        for (ProcessTaskAddSignUser user : listUsers(addSignId)) {
            if ("TODO".equals(user.getStatus())) {
                ProcessTask task = processTaskMapper.selectByTaskId(user.getGeneratedTaskId());
                if (task != null && ProcessTask.STATUS_HOLD.equals(task.getStatus())) {
                    task.setStatus(ProcessTask.STATUS_TODO);
                    task.setStartTime(LocalDateTime.now());
                    task.setUpdateTime(LocalDateTime.now());
                    processTaskMapper.updateById(task);
                }
            }
        }
    }

    /**
     * 结束剩余未办理的加签子任务（撤销/驳回时将待办与挂起任务置为跳过并取消）
     *
     * @param addSignId 添加签名ID，后续用于处理{@code finish}{@code remaining}子节点时定位或关联目标
     * @param reason 原因，供本方法处理{@code finish}{@code remaining}子节点时使用
     */
    private void finishRemainingChildren(String addSignId, String reason) {
        for (ProcessTaskAddSignUser user : listUsers(addSignId)) {
            if (!List.of("TODO", "HOLD").contains(user.getStatus())) {
                continue;
            }
            ProcessTask task = processTaskMapper.selectByTaskId(user.getGeneratedTaskId());
            if (task != null && List.of(ProcessTask.STATUS_TODO, ProcessTask.STATUS_HOLD).contains(task.getStatus())) {
                processTaskMapper.completeTask(task.getId(), ProcessTask.STATUS_SKIP, "cancel", reason, 0L);
            }
            user.setStatus("CANCELLED");
            user.setCompleteTime(LocalDateTime.now());
            addSignUserMapper.updateById(user);
        }
    }

    /**
     * 完成本地加签子任务镜像（计算处理时长并标记完成）
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param action 动作标识，决定后续完成本地子级采用的处理分支
     * @param comment 注释，供本方法处理完成本地子级时使用
     */
    private void completeLocalChild(String taskId, String action, String comment) {
        ProcessTask mirror = processTaskMapper.selectByTaskId(taskId);
        if (mirror == null || !ProcessTask.STATUS_TODO.equals(mirror.getStatus())) {
            throw new IllegalArgumentException("加签待办不存在或已处理");
        }
        long duration = mirror.getStartTime() == null
                ? 0L
                : java.time.Duration.between(mirror.getStartTime(), LocalDateTime.now()).toMillis();
        processTaskMapper.completeTask(mirror.getId(), ProcessTask.STATUS_DONE, action, comment, duration);
    }

    /**
     * 将原任务镜像置为等待状态（加签完成前原任务暂存）
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param action 动作标识，决定后续来源{@code waiting}采用的处理分支
     * @param comment 注释，作为 {@code source.setComment} 的输入影响后续处理
     * @param actionLabel 动作标签，后续用于标记来源{@code waiting}时匹配或展示
     */
    private void markSourceWaiting(String taskId, String action, String comment, String actionLabel) {
        ProcessTask source = processTaskMapper.selectByTaskId(taskId);
        if (source == null) {
            return;
        }
        source.setStatus(ProcessTask.STATUS_WAITING);
        source.setAction(action);
        source.setActionLabel(actionLabel);
        source.setComment(comment);
        source.setUpdateTime(LocalDateTime.now());
        processTaskMapper.updateById(source);
    }

    /**
     * 按来源任务查询流程任务添加签名；结果供后续展示或处理。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 符合条件的流程任务添加签名结果，供调用方继续处理
     */
    private ProcessTaskAddSign findOpenBySourceTask(String taskId) {
        return addSignMapper.findOpenBySourceTaskId(taskId);
    }

    /**
     * 按来源任务更新查询流程任务添加签名；结果供后续展示或处理。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 符合条件的流程任务添加签名结果，供调用方继续处理
     */
    private ProcessTaskAddSign findOpenBySourceTaskForUpdate(String taskId) {
        return addSignMapper.findOpenBySourceTaskIdForUpdate(taskId);
    }

    /**
     * 列出用户集合；查询结果供调用方展示或继续处理。
     *
     * @param addSignId 添加签名ID，后续用于列出用户集合时定位或关联目标
     * @return 流程任务添加签名用户集合，供调用方遍历或展示
     */
    private List<ProcessTaskAddSignUser> listUsers(String addSignId) {
        return addSignUserMapper.selectList(new LambdaQueryWrapper<ProcessTaskAddSignUser>()
                .eq(ProcessTaskAddSignUser::getAddSignId, addSignId)
                .orderByAsc(ProcessTaskAddSignUser::getSortOrder));
    }

    /**
     * 校验并返回指定任务（任务不存在时抛出异常）
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 校验并获取后的来源任务结果，供调用方继续处理
     */
    private Task requireSourceTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或已处理: " + taskId);
        }
        return task;
    }

    /**
     * 校验当前用户是否为任务办理人或候选办理人，返回用户名，否则抛出禁止异常
     *
     * @param task 任务，作为 {@code taskIdentityAccessService.requireCurrentUserAccess} 的输入影响后续处理
     * @return 校验并获取后的任务操作人文本，供调用方比较或展示
     */
    private String requireTaskOperator(Task task) {
        taskIdentityAccessService.requireCurrentUserAccess(task);
        return currentUsername();
    }

    /**
     * 解析加签人员：去重、过滤禁用/无效/与原办理人重复，并返回分类结果
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param sourceAssignee 来源办理人，供本方法解析用户集合时使用
     * @return 解析后的用户集合结果，供调用方继续处理
     */
    private Resolution resolveUsers(List<String> values, String sourceAssignee) {
        LinkedHashMap<String, SysUser> users = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        if (values == null) {
            return new Resolution(List.of(), duplicates, disabled, invalid);
        }
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            SysUser user = userMapper.selectByUsername(value);
            if (user == null) {
                user = userMapper.selectById(value);
            }
            if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
                invalid.add(value);
                continue;
            }
            if (!SysUser.Status.ENABLED.getValue().equals(user.getStatus())) {
                disabled.add(value);
                continue;
            }
            if (user.getUsername().equals(sourceAssignee) || user.getId().equals(sourceAssignee)) {
                duplicates.add(value);
                continue;
            }
            if (users.putIfAbsent(user.getUsername(), user) != null) {
                duplicates.add(value);
            }
        }
        return new Resolution(new ArrayList<>(users.values()), duplicates, disabled, invalid);
    }

    /**
     * 根据原任务镜像复制生成加签子任务镜像（后加签时为挂起状态）
     *
     * @param source 待复制{@code as}添加签名任务的原始输入，结果供调用方继续使用
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param held {@code held}，作为 {@code child.setStatus} 的输入影响后续处理
     * @return 复制后的{@code as}添加签名任务结果，供调用方继续处理
     */
    private ProcessTask copyAsAddSignTask(ProcessTask source, String taskId, SysUser user, boolean held) {
        ProcessTask child = new ProcessTask();
        child.setProcessInstanceId(source.getProcessInstanceId());
        child.setProcessDefinitionId(source.getProcessDefinitionId());
        child.setProcessKey(source.getProcessKey());
        child.setProcessName(source.getProcessName());
        child.setNodeId(source.getNodeId());
        child.setNodeName(source.getNodeName() + "（加签）");
        child.setNodeType("ADD_SIGN");
        child.setTaskId(taskId);
        child.setBusinessKey(source.getBusinessKey());
        child.setEntityCode(source.getEntityCode());
        child.setEntityDataId(source.getEntityDataId());
        child.setAssigneeId(user.getUsername());
        child.setAssigneeName(displayName(user));
        child.setAssigneeType("user");
        child.setFormKey(source.getFormKey());
        child.setStatus(held ? ProcessTask.STATUS_HOLD : ProcessTask.STATUS_TODO);
        child.setStartTime(held ? null : LocalDateTime.now());
        child.setCreateTime(LocalDateTime.now());
        child.setUpdateTime(LocalDateTime.now());
        child.setDeleted(0);
        // 加签没有对应的引擎创建事件；复用源任务摘要，后续业务更新会按记录同步全部子任务。
        child.setStartUserId(source.getStartUserId());
        child.setBusinessName(source.getBusinessName());
        child.setBusinessCode(source.getBusinessCode());
        child.setBusinessDataName(source.getBusinessDataName());
        child.setBusinessCurrentTaskName(source.getBusinessCurrentTaskName());
        child.setBusinessStatus(source.getBusinessStatus());
        child.setInboxSummaryReady(Boolean.TRUE.equals(source.getInboxSummaryReady()));
        child.setInboxIdentityReady(true);
        return child;
    }

    /**
     * 规范化类型；输出作为后续校验或处理的输入。
     *
     * @param type 类型标识，决定后续类型采用的处理分支
     * @return 规范化后的类型文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String normalizeType(String type) {
        String normalized = StringUtils.hasText(type) ? type.trim().toUpperCase(Locale.ROOT) : "PARALLEL";
        if (!TYPES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的加签类型: " + type);
        }
        return normalized;
    }

    /**
     * 规范化动作；输出作为后续校验或处理的输入。
     *
     * @param action 动作标识，决定后续动作采用的处理分支
     * @return 规范化后的动作文本，供调用方比较或展示
     */
    private String normalizeAction(String action) {
        if (!StringUtils.hasText(action)) {
            return "approve";
        }
        return switch (action.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVE", "APPROVED" -> "approve";
            case "REJECT", "REJECTED" -> "reject";
            case "TRANSFER", "TRANSFERRED" -> "transfer";
            default -> action;
        };
    }

    /**
     * 生成{@code structure}摘要文本，供后续匹配或展示。
     *
     * @param type 类型标识，决定后续{@code structure}摘要采用的处理分支
     * @return 处理后的{@code structure}摘要文本，供调用方比较或展示
     */
    private String structureSummary(String type) {
        return switch (type) {
            case "BEFORE" -> "加签人员先处理；全部通过后原办理人继续审批";
            case "AFTER" -> "原办理人先提交；随后激活加签任务；全部完成后流程继续";
            default -> "原办理人与加签人员可并行提交；全部完成后流程继续";
        };
    }

    /**
     * 添加签名视图；结果供后续流程传递或持久化。
     *
     * @param addSign 添加签名，作为 {@code Boolean.TRUE.equals} 的输入影响后续处理
     * @return 签名视图键值结果，供调用方继续处理
     */
    private Map<String, Object> addSignView(ProcessTaskAddSign addSign) {
        return Map.of(
                "id", addSign.getId(),
                "type", addSign.getOperationType(),
                "status", addSign.getStatus(),
                "sourceCompleted", Boolean.TRUE.equals(addSign.getSourceCompleted()));
    }

    /**
     * 整理用户视图数据，供调用方遍历或继续处理。
     *
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 用户视图键值结果，供调用方继续处理
     */
    private Map<String, Object> userView(SysUser user) {
        return Map.of("id", user.getId(), "username", user.getUsername(), "name", displayName(user));
    }

    /**
     * 生成展示名称文本，供后续匹配或展示。
     *
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 处理后的展示名称文本，供调用方比较或展示
     */
    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() + "(" + user.getUsername() + ")" : user.getUsername();
    }

    /**
     * 获取当前登录用户名，未登录时抛出禁止异常
     *
     * @return 处理后的当前用户名文本，供调用方比较或展示
     */
    private String currentUsername() {
        String username = UserContext.getUsername();
        if (!StringUtils.hasText(username)) {
            throw new ForbiddenException("用户未登录");
        }
        return username;
    }

    /**
     * 将表单数据序列化为JSON，空数据返回 null
     *
     * @param formData 表单数据，作为 {@code objectMapper.writeValueAsString} 的输入影响后续处理
     * @return 写入后的表单数据文本，供调用方比较或展示
     */
    private String writeFormData(Map<String, Object> formData) {
        if (formData == null || formData.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(formData);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("表单数据无法序列化", e);
        }
    }

    /**
     * 写入流程操作日志（记录加签相关操作及详情）
     *
     * @param addSign 添加签名，作为 {@code log.setProcessInstanceId} 的输入影响后续处理
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param operationType 操作类型标识，决定后续操作日志采用的处理分支
     * @param operator 操作人，作为 {@code log.setOperatorId} 的输入影响后续处理
     * @param comment 注释，作为 {@code log.setOperationComment} 的输入影响后续处理
     * @param details 详情，作为 {@code log.setNewValue} 的输入影响后续处理
     */
    private void writeOperationLog(
            ProcessTaskAddSign addSign,
            String taskId,
            String operationType,
            String operator,
            String comment,
            Map<String, Object> details) {
        ProcessOperationLog log = new ProcessOperationLog();
        log.setProcessInstanceId(addSign.getProcessInstanceId());
        log.setTaskId(taskId);
        log.setOperationType(operationType);
        log.setOperatorId(operator);
        log.setOperatorName(operator);
        log.setOperationTime(LocalDateTime.now());
        log.setOperationComment(comment);
        log.setNewValue(writeFormData(details));
        log.setNewValueFormat("JSON");
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    /**
     * 从JSON反序列化暂存的表单数据
     *
     * @param json JSON，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return 表单数据键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readFormData(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("加签暂存表单数据无法解析", e);
        }
    }

    /**
     * 加签人员解析结果：可用人员、重复、禁用、无效列表
     *
     * @param users 用户集合，保存在对象中供后续校验、查询或展示
     * @param duplicates {@code duplicates}，保存在对象中供后续校验、查询或展示
     * @param disabled {@code disabled}，保存在对象中供后续校验、查询或展示
     * @param invalid 无效，后续用于处理解析时定位或关联目标
     */
    private record Resolution(List<SysUser> users, List<String> duplicates, List<String> disabled, List<String> invalid) {
    }
}
