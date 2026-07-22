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

@Service
@RequiredArgsConstructor
public class TaskAddSignService {
    private static final List<String> TYPES = List.of("PARALLEL", "BEFORE", "AFTER");

    private final TaskService taskService;
    private final ProcessTaskMapper processTaskMapper;
    private final ProcessTaskAddSignMapper addSignMapper;
    private final ProcessTaskAddSignUserMapper addSignUserMapper;
    private final ProcessOperationLogMapper operationLogMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;
    @Lazy
    private final TaskActionService taskActionService;

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

    @Transactional(readOnly = true)
    public boolean isAddSignTask(String taskId) {
        return addSignUserMapper.findByGeneratedTaskId(taskId) != null;
    }

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

    private void saveRejectedSource(ProcessTaskAddSign addSign, String comment) {
        addSign.setSourceCompleted(true);
        addSign.setSourceAction("reject");
        addSign.setSourceActionLabel("加签驳回");
        addSign.setSourceComment(comment);
        addSignMapper.updateById(addSign);
    }

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

    private Task requireSourceTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或已处理: " + taskId);
        }
        return task;
    }

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

    private String currentUsername() {
        String username = UserContext.getUsername();
        if (!StringUtils.hasText(username)) {
            throw new ForbiddenException("用户未登录");
        }
        return username;
    }

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

    private record Resolution(List<SysUser> users, List<String> duplicates, List<String> disabled, List<String> invalid) {
    }
}
