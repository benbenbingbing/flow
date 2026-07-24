package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.ForbiddenException;
import com.workflow.common.UserContext;
import com.workflow.dto.TaskAddSignRequest;
import com.workflow.entity.ProcessTask;
import com.workflow.entity.ProcessTaskAddSign;
import com.workflow.entity.ProcessTaskAddSignUser;
import com.workflow.entity.ProcessOperationLog;
import com.workflow.entity.SysUser;
import com.workflow.mapper.ProcessOperationLogMapper;
import com.workflow.mapper.ProcessTaskAddSignMapper;
import com.workflow.mapper.ProcessTaskAddSignUserMapper;
import com.workflow.mapper.ProcessTaskMapper;
import com.workflow.mapper.SysUserMapper;
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
    /** 任务动作服务（用于加签完成后延迟提交原任务），延迟加载避免循环依赖 */
    @Lazy
    private final TaskActionService taskActionService;

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
    public Map<String, Object> addSign(String taskId, TaskAddSignRequest request) {
        Task sourceTask = requireSourceTask(taskId);
        String operator = requireTaskOperator(sourceTask);
        String type = normalizeType(request.getType());
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

    /** 加签全部完成或驳回后，延迟提交原任务（执行暂存的提交动作与表单数据） */
    private void finalizeSource(ProcessTaskAddSign addSign) {
        Task sourceTask = taskService.createTaskQuery().taskId(addSign.getSourceTaskId()).singleResult();
        if (sourceTask == null) {
            addSign.setStatus("COMPLETED");
            addSign.setCompleteTime(LocalDateTime.now());
            addSignMapper.updateById(addSign);
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
                readFormData(addSign.getSourceFormData()));
    }

    /** 记录原任务被驳回的提交动作（加签人驳回后用于回填原任务提交） */
    private void saveRejectedSource(ProcessTaskAddSign addSign, String comment) {
        addSign.setSourceCompleted(true);
        addSign.setSourceAction("reject");
        addSign.setSourceActionLabel("加签驳回");
        addSign.setSourceComment(comment);
        addSignMapper.updateById(addSign);
    }

    /** 暂存原任务的提交动作、意见与表单数据，供加签完成后延迟提交使用 */
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

    /** 激活后加签中挂起的子任务（将 HOLD 状态改为待办） */
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

    /** 结束剩余未办理的加签子任务（撤销/驳回时将待办与挂起任务置为跳过并取消） */
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

    /** 完成本地加签子任务镜像（计算处理时长并标记完成） */
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

    /** 将原任务镜像置为等待状态（加签完成前原任务暂存） */
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

    private ProcessTaskAddSign findOpenBySourceTask(String taskId) {
        return addSignMapper.findOpenBySourceTaskId(taskId);
    }

    private ProcessTaskAddSign findOpenBySourceTaskForUpdate(String taskId) {
        return addSignMapper.findOpenBySourceTaskIdForUpdate(taskId);
    }

    private List<ProcessTaskAddSignUser> listUsers(String addSignId) {
        return addSignUserMapper.selectList(new LambdaQueryWrapper<ProcessTaskAddSignUser>()
                .eq(ProcessTaskAddSignUser::getAddSignId, addSignId)
                .orderByAsc(ProcessTaskAddSignUser::getSortOrder));
    }

    /** 校验并返回指定任务（任务不存在时抛出异常） */
    private Task requireSourceTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或已处理: " + taskId);
        }
        return task;
    }

    /** 校验当前用户是否为任务办理人或候选办理人，返回用户名，否则抛出禁止异常 */
    private String requireTaskOperator(Task task) {
        String username = currentUsername();
        String userId = UserContext.getUserId();
        if (StringUtils.hasText(task.getAssignee())
                && !task.getAssignee().equals(username)
                && !task.getAssignee().equals(userId)) {
            throw new ForbiddenException("当前任务已分配给其他办理人");
        }
        if (!StringUtils.hasText(task.getAssignee())) {
            boolean candidate = taskService.createTaskQuery().taskId(task.getId()).taskCandidateUser(username).count() > 0;
            if (!candidate && StringUtils.hasText(userId)) {
                candidate = taskService.createTaskQuery().taskId(task.getId()).taskCandidateUser(userId).count() > 0;
            }
            if (!candidate) {
                throw new ForbiddenException("当前用户不是该任务候选办理人");
            }
        }
        return username;
    }

    /** 解析加签人员：去重、过滤禁用/无效/与原办理人重复，并返回分类结果 */
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

    /** 根据原任务镜像复制生成加签子任务镜像（后加签时为挂起状态） */
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
        return child;
    }

    private String normalizeType(String type) {
        String normalized = StringUtils.hasText(type) ? type.trim().toUpperCase(Locale.ROOT) : "PARALLEL";
        if (!TYPES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的加签类型: " + type);
        }
        return normalized;
    }

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

    private String structureSummary(String type) {
        return switch (type) {
            case "BEFORE" -> "加签人员先处理；全部通过后原办理人继续审批";
            case "AFTER" -> "原办理人先提交；随后激活加签任务；全部完成后流程继续";
            default -> "原办理人与加签人员可并行提交；全部完成后流程继续";
        };
    }

    private Map<String, Object> addSignView(ProcessTaskAddSign addSign) {
        return Map.of(
                "id", addSign.getId(),
                "type", addSign.getOperationType(),
                "status", addSign.getStatus(),
                "sourceCompleted", Boolean.TRUE.equals(addSign.getSourceCompleted()));
    }

    private Map<String, Object> userView(SysUser user) {
        return Map.of("id", user.getId(), "username", user.getUsername(), "name", displayName(user));
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() + "(" + user.getUsername() + ")" : user.getUsername();
    }

    /** 获取当前登录用户名，未登录时抛出禁止异常 */
    private String currentUsername() {
        String username = UserContext.getUsername();
        if (!StringUtils.hasText(username)) {
            throw new ForbiddenException("用户未登录");
        }
        return username;
    }

    /** 将表单数据序列化为JSON，空数据返回 null */
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

    /** 写入流程操作日志（记录加签相关操作及详情） */
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

    /** 从JSON反序列化暂存的表单数据 */
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

    /** 加签人员解析结果：可用人员、重复、禁用、无效列表 */
    private record Resolution(List<SysUser> users, List<String> duplicates, List<String> disabled, List<String> invalid) {
    }
}
