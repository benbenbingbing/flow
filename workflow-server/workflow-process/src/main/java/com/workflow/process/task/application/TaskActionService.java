package com.workflow.process.task.application;

import com.workflow.process.status.application.ProcessEndReason;

import com.workflow.core.logging.LogValue;
import com.workflow.process.cc.application.ProcessCcService;
import com.workflow.process.engine.infrastructure.flowable.EntityStatusUpdateListener;
import com.workflow.process.form.application.NodeFormSubmissionService;
import com.workflow.process.task.api.request.NextApproverSelectionRequest;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideService;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.contracts.entity.port.EntityRecordPort;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import com.workflow.process.task.api.response.TaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程任务办理服务。统一处理审批、驳回、转办、认领和撤回，并提供历史与工作台统计。
 * 办理时先检查节点操作权限和任务归属，再写节点表单、推进 Flowable、同步本地待办，
 * 避免引擎状态与实体活动记录分叉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskActionService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final ProcessTaskService processTaskService;
    private final org.flowable.engine.RepositoryService repositoryService;
    private final com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper operationLogMapper;
    private final SysUserService sysUserService;
    private final NodeFormSubmissionService nodeFormSubmissionService;
    private final EntityRecordPort entityRecordPort;
    /** 抄送/知会服务：用于统计未读抄送数等 */
    private final ProcessCcService processCcService;
    /** 下一审批人预览与覆盖必须存在，避免生产配置异常时静默跳过权威重验。 */
    private final NextApproverOverrideService nextApproverOverrideService;
    /** 多实例通过人数、否决标记与汇聚判断的唯一入口。 */
    private final MultiInstanceOutcomeService multiInstanceOutcomeService;
    /** 转办必须在产生任何任务副作用前通过节点开关校验。 */
    private final com.workflow.process.task.application.operation.NodeOperationCapabilityService
            nodeOperationCapabilityService;
    /** 待办、认领、审批与其他任务操作共用业务候选身份口径。 */
    private final TaskIdentityAccessService taskIdentityAccessService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.workflow.process.task.application.operation.NodeOperationDecisionService
            nodeOperationDecisionService;

    /**
     * 办理任务的兼容入口；转办、审批和驳回最终都进入同一事务处理。
     *
     * @param taskId      Flowable 任务 ID，用于重新读取并校验最新任务归属
     * @param userId      当前办理人 ID，用于审批变量与操作日志
     * @param action      操作类型：approve/reject/transfer；自定义值按普通完成处理
     * @param comment     审批意见，记录到任务结果及实体活动
     * @param transferTo  转办目标，仅 transfer 时使用
     * @param actionLabel 用户看到的操作文本，后续历史记录按此展示
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.APPROVE,
            operation = "办理流程任务",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "PROCESS_TASK",
            targetIdArg = 0)
    public void completeTask(String taskId, String userId, String action, String comment, String transferTo, String actionLabel) {
        completeTask(taskId, userId, action, comment, transferTo, actionLabel, null);
    }

    /**
     * 带节点表单数据的办理入口；formData 先按已发布节点表单处理，
     * 再完成 Flowable 任务，避免流程推进时仍读取旧实体字段。
     *
     * @param taskId Flowable 任务 ID，提交前重新读取以确认任务仍活动
     * @param userId 办理人 ID，写入审批变量和处理日志
     * @param action 审批、驳回或转办动作，先归一化再决定权限和流程走向
     * @param comment 审批意见，保存到任务结果及实体活动
     * @param transferTo 转办目标，仅转办动作使用
     * @param actionLabel 用户可见的动作文本，供审批历史展示
     * @param formData 本次可编辑字段补丁，后续交给节点表单服务校验和保存
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.APPROVE,
            operation = "办理流程任务",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "PROCESS_TASK",
            targetIdArg = 0)
    public void completeTask(String taskId, String userId, String action, String comment, String transferTo,
                             String actionLabel, Map<String, Object> formData) {
        completeTaskInternal(
                taskId,
                userId,
                action,
                comment,
                transferTo,
                actionLabel,
                formData,
                null,
                List.of(),
                true,
                true);
    }

    /**
     * 带下一审批人选择的办理入口。scopeKey 与 selections 由预览结果产生，
     * 提交时会重新校验并暂存给后续节点分配，不能直接信任前端选择。
     *
     * @param taskId Flowable 任务 ID，提交前重新读取以确认任务仍活动
     * @param userId 办理人 ID，写入审批变量和处理日志
     * @param action 审批、驳回或转办动作，先归一化再决定权限和流程走向
     * @param comment 审批意见，保存到任务结果及实体活动
     * @param transferTo 转办目标，仅转办动作使用
     * @param actionLabel 用户可见的动作文本，供审批历史展示
     * @param formData 本次节点表单补丁，先按发布表单校验并保存再推进任务
     * @param nextApprovalScopeKey 预览绑定的选择作用域键，用于拒绝跨节点复用
     * @param nextApproverSelections 用户对下一节点的选择，转办时必须为空
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.APPROVE,
            operation = "办理流程任务",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "PROCESS_TASK",
            targetIdArg = 0)
    public void completeTask(
            String taskId,
            String userId,
            String action,
            String comment,
            String transferTo,
            String actionLabel,
            Map<String, Object> formData,
            String nextApprovalScopeKey,
            List<NextApproverSelectionRequest> nextApproverSelections) {
        completeTaskInternal(
                taskId,
                userId,
                action,
                comment,
                transferTo,
                actionLabel,
                formData,
                nextApprovalScopeKey,
                nextApproverSelections,
                true,
                true);
    }

    /**
     * 加签收口时办理先前暂存的原任务动作；发起加签时已校验原任务权限，
     * 收口前先判断下一审批人是否需原办理人手动选择，因此此处不再以
     * 当前登录人做交互式办理校验。
     *
     * @param taskId Flowable 任务 ID，提交前重新读取以确认任务仍活动
     * @param userId 办理人 ID，写入审批变量和处理日志
     * @param action 审批、驳回或转办动作，先归一化再决定权限和流程走向
     * @param comment 审批意见，保存到任务结果及实体活动
     * @param transferTo 转办目标，仅转办动作使用
     * @param actionLabel 用户可见的动作文本，供审批历史展示
     * @param formData 本次节点表单补丁，先按发布表单校验并保存再推进任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeDeferredTask(String taskId, String userId, String action, String comment, String transferTo,
                                     String actionLabel, Map<String, Object> formData) {
        completeTaskInternal(
                taskId,
                userId,
                action,
                comment,
                transferTo,
                actionLabel,
                formData,
                null,
                List.of(),
                false,
                false);
    }

    /**
     * 加签等后台收口在完成原任务前调用。返回 true 时必须恢复源待办，让原办理人
     * 在正常审批面板中选择下一审批人，不能静默创建无人任务。
     *
     * @param taskId 加签后等待收口的原任务 ID
     * @param action 原办理动作，归一化后参与下一审批人规则判断
     * @param comment 原审批意见，缺失时按空字符串传入预览
     * @param actionLabel 原操作文本，用于计算下一审批人预览结果
     * @param formData 暂存的节点表单数据，参与下一审批人规则求值
     * @return 需要原办理人手动选择下一审批人时为 true
     * @throws IllegalArgumentException 原任务已不存在或已处理
     */
    public boolean requiresManualNextApproverForDeferredCompletion(
            String taskId,
            String action,
            String comment,
            String actionLabel,
            Map<String, Object> formData) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        if (task == null) {
            throw new IllegalArgumentException(
                    "任务不存在或已处理: " + taskId);
        }
        return nextApproverOverrideService
                .requiresManualSelectionForDeferredCompletion(
                        task,
                        multiInstanceOutcomeService.normalizeAction(action),
                        actionLabel,
                        comment == null ? "" : comment,
                        formData);
    }

    /**
     * 统一办理顺序：授权与认领、节点表单写入、下一审批人重验、引擎完成和本地待办同步。
     * checkAccess/validateNextApprover 仅由已完成对应校验的后台收口路径关闭；
     * 任一步失败都依赖外层事务回滚，避免引擎任务与本地记录状态分叉。
     *
     * @param taskId Flowable 任务 ID，提交前重新读取以确认任务仍活动
     * @param userId 办理人 ID，写入审批变量和处理日志
     * @param action 审批、驳回或转办动作，先归一化再决定权限和流程走向
     * @param comment 审批意见，保存到任务结果及实体活动
     * @param transferTo 转办目标，仅转办动作使用
     * @param actionLabel 用户可见的动作文本，供审批历史展示
     * @param formData 本次节点表单补丁，先按发布表单校验并保存再推进任务
     * @param nextApprovalScopeKey 预览作用域键，提交时校验其与当前节点一致
     * @param nextApproverSelections 用户选择的后续办理人，由覆盖服务重验
     * @param checkAccess 交互办理时为 true；后台加签收口已提前授权时关闭
     * @param validateNextApprover 交互办理时为 true；后台收口前已判断手选需求时关闭
     */
    private void completeTaskInternal(String taskId, String userId, String action, String comment, String transferTo,
                                      String actionLabel, Map<String, Object> formData,
                                      String nextApprovalScopeKey,
                                      List<NextApproverSelectionRequest> nextApproverSelections,
                                      boolean checkAccess,
                                      boolean validateNextApprover) {
        comment = comment == null ? "" : comment;
        requireConfiguredNodeOperation(taskId, action, comment, transferTo, formData);
        // 验证任务是否存在
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

        if (task == null) {
            throw taskAlreadyCompleted();
        }

        if (checkAccess) {
            // 待办办理人/候选身份授予当前任务的审批权，无需额外实体菜单权限；
            // 提交前必须重新检查归属，提交字段仍由节点表单约束。
            requireTaskProcessingAccess(task);
        }

        String assignee = task.getAssignee();
        String processInstanceId = task.getProcessInstanceId();
        String entityCode = asString(runtimeService.getVariable(processInstanceId, "entityCode"));
        String entityDataId = asString(runtimeService.getVariable(processInstanceId, "entityDataId"));
        if (checkAccess) {
            String currentIdentity = currentTaskIdentity();
            if (!StringUtils.hasText(assignee)) {
                claimTaskForCurrentUser(task, currentIdentity);
            } else {
                processTaskService.synchronizeClaimedTask(taskId, processInstanceId, assignee);
            }
        } else if (!StringUtils.hasText(assignee)) {
            taskService.setAssignee(taskId, userId);
        }

        String normalizedAction = multiInstanceOutcomeService.normalizeAction(action);
        // 预览可能因提交前数据源有副作用而延期；该标记交给重验逻辑决定
        // 本次能否直接确定下一审批人，而不是由客户端声称预览已完成。
        boolean nextApprovalPreviewDeferred = false;
        if (validateNextApprover
                && !"transfer".equals(normalizedAction)) {
            nextApprovalPreviewDeferred = nextApproverOverrideService
                    .previewIsDeferred(
                            task,
                            normalizedAction,
                            actionLabel,
                            comment,
                            formData);
        }

        nodeFormSubmissionService.applyEditableData(task, formData);

        // 检查是否是多实例任务（会签/或签），以已部署 BPMN 为准
        boolean isMultiInstance = multiInstanceOutcomeService.isMultiInstance(task);

        if ("transfer".equals(normalizedAction)) {
            if (nextApproverSelections != null
                    && !nextApproverSelections.isEmpty()) {
                throw new IllegalArgumentException(
                        "转办操作不能同时指定下一节点审批人");
            }
        } else if (validateNextApprover) {
            nextApproverOverrideService.validateAndStage(
                    task,
                    normalizedAction,
                    actionLabel,
                    comment,
                    nextApprovalScopeKey,
                    nextApproverSelections,
                    nextApprovalPreviewDeferred);
        }

        // 根据不同操作类型处理
        switch (normalizedAction) {
            case "approve":
                handleApprove(task, userId, comment, isMultiInstance, actionLabel);
                break;

            case "reject":
                handleReject(task, userId, comment, isMultiInstance, actionLabel);
                break;

            case "transfer":
                // 转办
                if (transferTo == null || transferTo.isEmpty()) {
                    throw new RuntimeException("转办人不能为空");
                }
                taskService.setAssignee(taskId, transferTo);
                processTaskService.completeTask(taskId, "transfer", "转办给: " + transferTo);
                
                // 引擎办理人、本地新待办和转办记录属于同一次业务变更，必须一并提交。
                // 不吞掉数据库异常后继续事务：PostgreSQL 可能已终止事务，Spring 的参与事务
                // 也可能已标记 rollback-only；对所有数据库都让外层事务回滚，避免转办后无人可见。
                Task transferredTask = taskService.createTaskQuery().taskId(taskId).singleResult();
                if (transferredTask == null) {
                    throw taskAlreadyCompleted();
                }
                Map<String, Object> variables = runtimeService.getVariables(transferredTask.getProcessInstanceId());
                processTaskService.createTask(transferredTask, variables);
                log.info("已为转办人 {} 创建新待办: taskId={}",
                        LogValue.safe(transferTo), LogValue.safe(taskId));

                com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog transferLog =
                        new com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog();
                transferLog.setProcessInstanceId(task.getProcessInstanceId());
                transferLog.setTaskId(taskId);
                transferLog.setOperationType("TRANSFER");
                transferLog.setOperatorId(userId);
                transferLog.setOperatorName(sysUserService.getDisplayName(userId));
                transferLog.setOperationTime(LocalDateTime.now());
                transferLog.setOperationComment(comment);
                transferLog.setOldValue(assignee);
                transferLog.setNewValue(transferTo);
                transferLog.setOldValueFormat("PLAIN_TEXT");
                transferLog.setNewValueFormat("PLAIN_TEXT");
                operationLogMapper.insert(transferLog);

                log.info("任务 {} 已转办给 {}", LogValue.safe(taskId), LogValue.safe(transferTo));
                break;

            default:
                // 自定义操作类型：按普通审批完成，approved 使用原始 action 值。
                // 自定义项不加通过人数、不触发否决，避免会签阈值被非审批动作污染。
                Map<String, Object> customVars = new HashMap<>();
                putApprovedOutcome(customVars, task, normalizedAction);
                customVars.put("action", normalizedAction);
                if (actionLabel != null && !actionLabel.isBlank()) {
                    customVars.put("actionLabel", actionLabel);
                }
                customVars.put("comment", comment);
                customVars.put("approver", userId);

                List<String> customApprovers = (List<String>) runtimeService.getVariable(task.getProcessInstanceId(), "_approvers_");
                if (customApprovers == null) {
                    customApprovers = new ArrayList<>();
                }
                customApprovers.add(userId);
                customVars.put("_approvers_", customApprovers);

                // 将操作显示文本存为任务本地变量，便于后续按任务ID读取
                if (actionLabel != null && !actionLabel.isBlank()) {
                    taskService.setVariableLocal(taskId, "actionLabel", actionLabel);
                }

                taskService.complete(taskId, customVars);
                processTaskService.completeTask(taskId, normalizedAction, comment, actionLabel);

                log.info("任务 {} 已通过自定义操作完成: action={}, user={}",
                        LogValue.safe(taskId), LogValue.safe(normalizedAction), LogValue.safe(userId));
                break;
        }

        // 同步更新待办状态（实体状态由 EntityStatusUpdateListener 监听器自动更新）
        if (processInstanceId != null) {
            processTaskService.syncTasksFromFlowable(processInstanceId);
            // 注意：实体数据状态由 EntityStatusUpdateListener 监听器自动更新
            // 不需要在这里手动更新，避免重复更新
        }
        if (StringUtils.hasText(entityCode) && StringUtils.hasText(entityDataId)) {
            entityRecordPort.recordActivity(
                    entityCode,
                    entityDataId,
                    normalizedAction.toUpperCase(Locale.ROOT),
                    StringUtils.hasText(actionLabel) ? actionLabel : comment,
                    processInstanceId,
                    taskId);
        }
    }

    /**
     * 将完成任务动作映射到新三开关或存量矩阵，并在副作用前完成授权。
     *
     * @param taskId 待办 ID，用于读取发布节点的操作权限
     * @param action 原始动作值，归一化后映射为同意、驳回或转办
     * @param comment 审批意见，构造权限检查上下文
     * @param transferTo 转办目标，转办开关检查时作为目标集合
     * @param formData 表单提交值，兼容提取驳回目标节点并传给旧权限矩阵
     */
    private void requireConfiguredNodeOperation(
            String taskId,
            String action,
            String comment,
            String transferTo,
            Map<String, Object> formData) {
        // 授权映射必须与执行层共用同一归一化规则，否则 TRANSFERRED
        // 等同义状态值会在执行层变成转办，却绕过转办开关。
        String normalized = multiInstanceOutcomeService
                .normalizeAction(action)
                .toUpperCase(Locale.ROOT);
        com.workflow.process.task.application.operation.NodeOperationPolicy.Operation operation =
                switch (normalized) {
                    case "REJECT", "ROLLBACK" ->
                            com.workflow.process.task.application.operation.NodeOperationPolicy.Operation.REJECT;
                    case "TRANSFER" ->
                            com.workflow.process.task.application.operation.NodeOperationPolicy.Operation.TRANSFER;
                    default ->
                            com.workflow.process.task.application.operation.NodeOperationPolicy.Operation.APPROVE;
                };
        java.util.Set<String> targets = StringUtils.hasText(transferTo)
                ? java.util.Set.of(transferTo)
                : java.util.Set.of();
        Map<String, Object> requestVariables = formData == null ? Map.of() : formData;
        String targetNodeId = firstText(
                requestVariables, "targetNodeId", "targetActivityId", "rejectTarget");
        com.workflow.process.task.application.operation.NodeOperationDecisionService.CheckContext context =
                com.workflow.process.task.application.operation.NodeOperationDecisionService.CheckContext
                        .ofTarget(comment, targets, targetNodeId, null, requestVariables);
        if (operation == com.workflow.process.task.application.operation.NodeOperationPolicy.Operation.TRANSFER) {
            nodeOperationCapabilityService.requireAllowed(taskId, operation, context);
            return;
        }
        // 新三开关不再约束同意和驳回；仅无新字段的存量矩阵继续保持原行为。
        if (nodeOperationDecisionService != null) {
            nodeOperationDecisionService.requireAllowed(taskId, operation, context);
        }
    }

    /**
     * 按兼容字段名顺序读取目标节点 ID，供新旧驳回请求共用节点权限校验。
     *
     * @param values 包含新旧兼容字段的表单变量
     * @param keys 按优先级排序的候选字段名，用于查找目标节点
     * @return 首个非空字段文本；均不存在时为 null
     */
    private String firstText(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * 只检查当前用户能否读取该待办，不执行认领；详情页用它阻止越权查看。
     *
     * @param taskId 详情页待办 ID，重新读取并检查当前用户的候选或办理身份
     */
    @Transactional(readOnly = true)
    public void requireTaskAccess(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw taskAlreadyCompleted();
        }
        requireTaskIdentityAccess(task);
    }

    /**
     * 主动认领候选任务，并同步本地待办与活动记录供工作台显示。
     *
     * @param taskId 待认领任务 ID，先校验当前用户身份再执行原子认领
     */
    @Transactional(rollbackFor = Exception.class)
    public void claimTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw taskAlreadyCompleted();
        }
        requireTaskProcessingAccess(task);
        claimTaskForCurrentUser(task, currentTaskIdentity());
    }

    /**
     * 复用统一的候选人与办理人身份规则，防止详情和办理入口采用不同授权口径。
     *
     * @param task 当前 Flowable 任务，用于检查当前登录人的访问身份
     */
    private void requireTaskIdentityAccess(Task task) {
        taskIdentityAccessService.requireCurrentUserAccess(task);
    }

    /**
     * 提交时重新检查归属；候选人打开表单后被别人接手属于状态冲突，不自动抢回任务。
     *
     * @param task 准备提交的 Flowable 任务，用于重验办理人及候选身份
     */
    private void requireTaskProcessingAccess(Task task) {
        String userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        if ((StringUtils.hasText(userId) || StringUtils.hasText(username))
                && StringUtils.hasText(task.getAssignee())
                && !matchesCurrentUser(task.getAssignee(), userId, username)) {
            throw new BusinessConflictException("TASK_ALREADY_CLAIMED", "任务已被其他办理人认领，请刷新待办列表");
        }
        requireTaskIdentityAccess(task);
    }

    /**
     * 构造任务已结束的冲突响应，供认领和办理入口提示前端刷新待办。
     *
     * @return 带稳定错误码的业务冲突异常
     */
    private BusinessConflictException taskAlreadyCompleted() {
        return new BusinessConflictException("TASK_ALREADY_COMPLETED", "任务不存在或已被处理，请刷新待办列表");
    }

    /**
     * 使用 Flowable 原子认领结果作为权威状态；若并发认领抛错，重读任务以区分
     * 自己已认领和他人抢先认领，只有首次成功时才新增认领日志。
     *
     * @param task 待认领 Flowable 任务，用于原子 claim 和本地镜像同步
     * @param currentIdentity 与 Flowable 候选身份一致的用户名或用户 ID，写入办理人
     */
    private void claimTaskForCurrentUser(Task task, String currentIdentity) {
        String assignee = task.getAssignee();
        if (StringUtils.hasText(assignee)) {
            if (!matchesCurrentUser(assignee, UserContext.getUserId(), UserContext.getUsername())) {
                throw new BusinessConflictException("TASK_ALREADY_CLAIMED", "任务已被其他办理人认领");
            }
            processTaskService.synchronizeClaimedTask(
                    task.getId(),
                    task.getProcessInstanceId(),
                    assignee);
            return;
        }

        try {
            taskService.claim(task.getId(), currentIdentity);
        } catch (RuntimeException exception) {
            Task latest = taskService.createTaskQuery().taskId(task.getId()).singleResult();
            if (latest != null && matchesCurrentUser(
                    latest.getAssignee(),
                    UserContext.getUserId(),
                    UserContext.getUsername())) {
                processTaskService.synchronizeClaimedTask(
                        latest.getId(),
                        latest.getProcessInstanceId(),
                        latest.getAssignee());
                return;
            }
            if (latest != null && StringUtils.hasText(latest.getAssignee())) {
                throw new BusinessConflictException("TASK_ALREADY_CLAIMED", "任务已被其他办理人认领");
            }
            throw exception;
        }

        processTaskService.synchronizeClaimedTask(
                task.getId(),
                task.getProcessInstanceId(),
                currentIdentity);
        recordTaskClaim(task, currentIdentity);
    }

    /**
     * 首次认领后写操作日志和实体活动，供流程历史与业务记录同时展示。
     *
     * @param task 首次认领成功的任务，提供日志与实体活动关联坐标
     * @param currentIdentity 实际认领身份，供日志展示和后续办理人同步
     */
    private void recordTaskClaim(Task task, String currentIdentity) {
        com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog operationLog =
                new com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog();
        operationLog.setProcessInstanceId(task.getProcessInstanceId());
        operationLog.setTaskId(task.getId());
        operationLog.setOperationType("CLAIM");
        operationLog.setOperatorId(UserContext.getUserId());
        operationLog.setOperatorName(sysUserService.getDisplayName(currentIdentity));
        operationLog.setOperationTime(LocalDateTime.now());
        operationLog.setOperationComment("认领任务");
        operationLog.setNewValue(currentIdentity);
        operationLog.setNewValueFormat("PLAIN_TEXT");
        operationLogMapper.insert(operationLog);

        String entityCode = asString(runtimeService.getVariable(task.getProcessInstanceId(), "entityCode"));
        String entityDataId = asString(runtimeService.getVariable(task.getProcessInstanceId(), "entityDataId"));
        if (StringUtils.hasText(entityCode) && StringUtils.hasText(entityDataId)) {
            entityRecordPort.recordActivity(
                    entityCode,
                    entityDataId,
                    "CLAIM",
                    "认领任务",
                    task.getProcessInstanceId(),
                    task.getId());
        }
        log.info("任务已认领: taskId={}, processInstanceId={}, assignee={}",
                task.getId(), task.getProcessInstanceId(), currentIdentity);
    }

    /**
     * 优先使用 Flowable 候选人配置所用用户名，缺失时退回用户 ID。
     *
     * @return 优先用户名、其次用户 ID 的 Flowable 任务身份
     * @throws ForbiddenException 当前请求缺少登录身份
     */
    private String currentTaskIdentity() {
        String username = UserContext.getUsername();
        if (StringUtils.hasText(username)) {
            return username;
        }
        String userId = UserContext.getUserId();
        if (StringUtils.hasText(userId)) {
            return userId;
        }
        throw new ForbiddenException("用户未登录");
    }

    /**
     * 兼容任务办理人历史上保存的用户 ID 或用户名，供认领冲突判断。
     *
     * @param value 任务中保存的办理人身份，可为用户名或用户 ID
     * @param userId 当前用户 ID，用于兼容历史办理人值
     * @param username 当前用户名，用于匹配 Flowable 候选身份
     * @return 办理人字段属于当前用户时为 true
     */
    private boolean matchesCurrentUser(String value, String userId, String username) {
        return StringUtils.hasText(value)
                && (value.equals(userId) || value.equals(username));
    }

    /**
     * 保留 null 为缺失坐标，避免把空流程变量写进实体活动。
     *
     * @param value 流程变量值，可为空
     * @return 字符串值；为空时保留 null 供后续业务坐标校验
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 处理通过操作。会签只给本节点通过人数 +1；或签同样 +1，完成条件 count>=1
     * 会让 Flowable 删除其余实例。若节点已被否决，approved 保持 reject。
     *
     * @param task 当前任务，完成前写入多实例通过结果
     * @param userId 办理人 ID，加入流程审批人变量
     * @param comment 审批意见，传给 Flowable 与本地待办
     * @param isMultiInstance 是否为会签或或签节点，决定是否累计通过票
     * @param actionLabel 用户可见动作文本，保存在任务变量和本地结果
     */
    private void handleApprove(Task task, String userId, String comment, boolean isMultiInstance, String actionLabel) {
        String taskId = task.getId();
        if (isMultiInstance) {
            multiInstanceOutcomeService.recordApprove(task);
        }

        Map<String, Object> vars = new HashMap<>();
        putApprovedOutcome(vars, task, "approve");
        vars.put("action", "approve");
        if (actionLabel != null && !actionLabel.isBlank()) {
            vars.put("actionLabel", actionLabel);
        }
        vars.put("comment", comment);
        vars.put("approver", userId);

        List<String> approvers = (List<String>) runtimeService.getVariable(task.getProcessInstanceId(), "_approvers_");
        if (approvers == null) {
            approvers = new ArrayList<>();
        }
        approvers.add(userId);
        vars.put("_approvers_", approvers);

        if (actionLabel != null && !actionLabel.isBlank()) {
            taskService.setVariableLocal(taskId, "actionLabel", actionLabel);
        }

        taskService.complete(taskId, vars);
        processTaskService.completeTask(taskId, "approve", comment, actionLabel);

        log.info("任务 {} 已通过，处理人: {}，是否多实例: {}", taskId, userId, isMultiInstance);
    }

    /**
     * 处理驳回。会签按票数模型：驳回不加通过人数；仅当剩下的人全通过
     * 也达不到阈值，或已全部办完且未达标时，才结束节点。或签仍一票否决。
     *
     * @param task 当前任务，完成前写入多实例否决结果
     * @param userId 驳回人 ID，写入流程变量
     * @param comment 驳回意见，传给 Flowable 与本地待办
     * @param isMultiInstance 是否为会签或或签节点，决定否决票汇聚方式
     * @param actionLabel 用户可见动作文本，保存在任务变量和本地结果
     */
    private void handleReject(Task task, String userId, String comment, boolean isMultiInstance, String actionLabel) {
        String taskId = task.getId();
        if (isMultiInstance) {
            multiInstanceOutcomeService.recordReject(task);
        }

        Map<String, Object> vars = new HashMap<>();
        putApprovedOutcome(vars, task, "reject");
        vars.put("action", "reject");
        if (actionLabel != null && !actionLabel.isBlank()) {
            vars.put("actionLabel", actionLabel);
        }
        vars.put("comment", comment);
        vars.put("rejectBy", userId);
        vars.put("rejectTime", new Date());

        if (actionLabel != null && !actionLabel.isBlank()) {
            taskService.setVariableLocal(taskId, "actionLabel", actionLabel);
        }

        taskService.complete(taskId, vars);
        processTaskService.completeTask(taskId, "reject", comment, actionLabel);

        log.info("任务 {} 已驳回，处理人: {}，是否多实例: {}", taskId, userId, isMultiInstance);
    }

    /**
     * 将会签/或签汇总结果写入流程变量，后续 BPMN 网关依据 approved 决定走向。
     *
     * @param vars 待提交的流程变量 Map，必要时加入 approved 供网关判断
     * @param task 当前任务，用于读取多实例汇总状态
     * @param action 本次通过或驳回动作，用于计算最终网关结果
     */
    private void putApprovedOutcome(Map<String, Object> vars, Task task, String action) {
        String approved = multiInstanceOutcomeService.resolveApprovedOutcome(task, action);
        if (approved != null) {
            vars.put("approved", approved);
        }
    }

    /**
     * 发起人撤回尚在运行的流程，先校验独立撤回开关，再删除引擎实例和本地待办。
     * 实体活动在删除前记录，供业务记录保留撤回原因。
     *
     * @param processInstanceId 待撤回的流程实例 ID，用于校验运行状态并删除引擎实例
     * @param userId 发起撤回的用户 ID，用于权限判断和实体活动记录
     * @param reason 撤回原因，删除前保存到流程及业务历史
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.WITHDRAW,
            operation = "撤回流程",
            risk = AuditRiskLevel.HIGH,
            targetType = "PROCESS_INSTANCE",
            targetIdArg = 0)
    public void withdrawProcess(String processInstanceId, String userId, String reason) {
        // 验证流程实例是否存在
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) {
            throw new RuntimeException("流程实例不存在或已结束");
        }

        // 发起人信息在运行时缺失时回退到历史表；仍无法确认则失败关闭。
        String startUserId = processInstance.getStartUserId();
        if (!StringUtils.hasText(startUserId)) {
            HistoricProcessInstance historic = historyService
                    .createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            startUserId = historic == null ? null : historic.getStartUserId();
        }
        if (!StringUtils.hasText(startUserId) || !startUserId.equals(userId)) {
            throw new RuntimeException("只有发起人才能撤回流程");
        }

        // 与 PC 列表及移动端能力查询共用撤回门禁；不再绑定“允许终止”。
        nodeOperationCapabilityService.requireWithdrawAllowed(processInstanceId,
                com.workflow.process.task.application.operation.NodeOperationDecisionService.CheckContext
                        .ofReason(reason));

        try {
            String entityCode = asString(runtimeService.getVariable(processInstanceId, "entityCode"));
            String entityDataId = asString(runtimeService.getVariable(processInstanceId, "entityDataId"));
            if (StringUtils.hasText(entityCode) && StringUtils.hasText(entityDataId)) {
                entityRecordPort.requireProcessEndStatus(entityCode, "WITHDRAWN");
                entityRecordPort.recordActivity(
                        entityCode,
                        entityDataId,
                        "WITHDRAW",
                        reason,
                        processInstanceId,
                        null);
            }

            // 结束类型与自由意见分离，结束监听和对账不会再根据意见文字猜测动作。
            runtimeService.deleteProcessInstance(processInstanceId, ProcessEndReason.encode("WITHDRAWN", reason));
            var operationLog = new com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog();
            operationLog.setProcessInstanceId(processInstanceId);
            operationLog.setOperationType("WITHDRAW");
            operationLog.setOperatorId(userId);
            operationLog.setOperatorName(sysUserService.getDisplayName(userId));
            operationLog.setOperationTime(LocalDateTime.now());
            operationLog.setOperationComment(reason);
            // 撤回记录与引擎取消同事务保存，确保结束后时间线有明确的操作人和原因。
            operationLogMapper.insert(operationLog);

            // 清理本地待办
            processTaskService.deleteTasksByProcessInstance(processInstanceId);

            log.info("流程实例 {} 已被用户 {} 撤回，原因: {}",
                    LogValue.safe(processInstanceId), LogValue.safe(userId), LogValue.safe(reason));
        } catch (Exception e) {
            log.error("撤回流程失败: processInstanceId={}, failureType={}",
                    LogValue.safe(processInstanceId), LogValue.failureType(e));
            throw new RuntimeException("撤回失败: " + e.getMessage());
        }
    }

    /**
     * 合并流程发起、已办任务、当前任务和转办日志，供流程详情展示完整时间线。
     *
     * @param processInstanceId 流程实例 ID，用于合并发起、已办、当前待办及转办记录
     * @return 按时间线展示的历史任务列表
     */
    public List<TaskVO> getProcessHistory(String processInstanceId) {
        List<TaskVO> historyList = new ArrayList<>();

        // 1. 获取流程发起信息
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (historicInstance != null) {
            TaskVO startVo = new TaskVO();
            startVo.setTaskName("流程发起");
            startVo.setAssignee(historicInstance.getStartUserId());
            startVo.setResult("start");
            if (historicInstance.getStartTime() != null) {
                startVo.setCreateTime(historicInstance.getStartTime());
            }
            historyList.add(startVo);
        }

        // 2. 获取历史任务（已完成）
        List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .asc()
                .list();

        for (HistoricTaskInstance historicTask : historicTasks) {
            TaskVO vo = convertHistoricTaskToVO(historicTask);
            historyList.add(vo);
        }

        // 3. 获取当前活动任务（未完成的）
        List<Task> activeTasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list();

        for (Task task : activeTasks) {
            TaskVO vo = new TaskVO();
            vo.setTaskId(task.getId());
            vo.setTaskName(task.getName());
            vo.setAssignee(task.getAssignee());
            vo.setProcessInstanceId(task.getProcessInstanceId());
            vo.setCreateTime(task.getCreateTime());
            
            // 获取任务评论判断是否是转办
            List<org.flowable.engine.task.Comment> comments = taskService.getTaskComments(task.getId());
            String result = "processing";
            for (org.flowable.engine.task.Comment c : comments) {
                if (c.getFullMessage() != null && c.getFullMessage().contains("转办给:")) {
                    result = "transfer";
                    break;
                }
            }
            vo.setResult(result);
            
            historyList.add(vo);
        }

        // 4. 合并转办记录
        try {
            List<com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog> transferLogs = operationLogMapper
                    .selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog>()
                            .eq(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog::getProcessInstanceId, processInstanceId)
                            .eq(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog::getOperationType, "TRANSFER")
                            .orderByAsc(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog::getOperationTime));
            
            for (com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog log : transferLogs) {
                TaskVO vo = new TaskVO();
                vo.setTaskId(log.getTaskId());
                vo.setProcessInstanceId(processInstanceId);
                
                // 查找任务名称
                var ht = historyService.createHistoricTaskInstanceQuery().taskId(log.getTaskId()).singleResult();
                vo.setTaskName(ht != null ? ht.getName() : "任务转办");
                vo.setAssignee(log.getOperatorId());
                vo.setResult("transfer");
                vo.setComment(log.getNewValue() != null ? "转办给: " + log.getNewValue() : log.getOperationComment());
                vo.setCreateTime(log.getOperationTime() != null ? 
                    Date.from(log.getOperationTime().atZone(ZoneId.systemDefault()).toInstant()) : null);
                
                // 插入到对应任务记录之前
                int insertIndex = -1;
                for (int i = 0; i < historyList.size(); i++) {
                    if (log.getTaskId() != null && log.getTaskId().equals(historyList.get(i).getTaskId())) {
                        insertIndex = i;
                        break;
                    }
                }
                if (insertIndex >= 0) {
                    historyList.add(insertIndex, vo);
                } else {
                    historyList.add(vo);
                }
            }
        } catch (Exception e) {
            log.warn("合并转办记录到历史失败", e);
        }

        // 与流程进度接口保持相同结束操作记录，旧客户端不能漏掉撤回事件。
        var endLogs = operationLogMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog>()
                .eq(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog::getProcessInstanceId, processInstanceId)
                .in(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog::getOperationType, "TERMINATE", "WITHDRAW")
                .orderByAsc(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog::getOperationTime));
        for (var operation : endLogs) {
            TaskVO end = new TaskVO();
            boolean withdrawn = "WITHDRAW".equals(operation.getOperationType());
            end.setProcessInstanceId(processInstanceId);
            end.setTaskName(withdrawn ? "流程撤回" : "流程终止");
            end.setResult(withdrawn ? "withdraw" : "terminate");
            end.setAssignee(operation.getOperatorId());
            end.setAssigneeName(operation.getOperatorName());
            end.setComment(operation.getOperationComment());
            if (operation.getOperationTime() != null) {
                end.setEndTime(Date.from(operation.getOperationTime().atZone(ZoneId.systemDefault()).toInstant()));
            }
            historyList.add(end);
        }
        if (endLogs.isEmpty() && historicInstance != null && historicInstance.getEndTime() != null
                && ProcessEndReason.isCancelled(historicInstance.getDeleteReason())) {
            TaskVO end = new TaskVO();
            boolean withdrawn = "WITHDRAWN".equals(ProcessEndReason.category(historicInstance.getDeleteReason()));
            end.setProcessInstanceId(processInstanceId);
            end.setTaskName(withdrawn ? "流程撤回" : "流程终止");
            end.setResult(withdrawn ? "withdraw" : "terminate");
            end.setComment(ProcessEndReason.comment(historicInstance.getDeleteReason()));
            end.setEndTime(historicInstance.getEndTime());
            historyList.add(end);
        }

        return historyList;
    }

    /**
     * 获取当前用户的任务统计信息，供首页/工作台统计卡片与页签徽标使用。
     *
     * 返回的统计项：
     * <ul>
     *   <li>todoCount      待办任务数（未处理的流程任务）</li>
     *   <li>doneCount      已办任务数（已处理完成的任务）</li>
     *   <li>processCount   我发起的流程数（当前用户作为发起人的历史流程实例数）</li>
     *   <li>avgProcessTime 已办任务的平均处理时长（小时，保留一位小数）</li>
     *   <li>unreadCcCount  未读抄送/知会数（用于"抄送我的"页签徽标，仅统计未读）</li>
     * </ul>
     *
     * @param userId 当前登录用户 ID，分别用于查询待办、已办、发起和未读抄送数量
     * @return 统计信息 Map，key 为统计项名称，value 为数值
     */
    public Map<String, Object> getTaskStatistics(String userId) {
        Map<String, Object> statistics = new HashMap<>();

        // 待办任务数：当前用户待处理的流程任务数量
        Long todoCount = processTaskService.countTodo(userId);
        statistics.put("todoCount", todoCount);

        // 数量与时长在同一次数据库聚合中读取，避免加载历史全集和两次读取的口径漂移。
        var doneStatistics = processTaskService.getDoneStatistics(userId);
        statistics.put("doneCount", doneStatistics.getTaskCount());

        // 我发起的流程数：当前用户作为发起人的历史流程实例数量
        long myProcessCount = historyService.createHistoricProcessInstanceQuery()
                .startedBy(userId)
                .count();
        statistics.put("processCount", myProcessCount);

        statistics.put("avgProcessTime", doneStatistics.averageHours());

        // 任务统计入口通常传业务用户 ID，自动知会和收件箱接口则使用用户名。
        // 先统一收件身份，避免已有知会记录但首页未读徽标始终为零；兼容直接传用户名的调用。
        var ccUser = sysUserService.getById(userId);
        String ccUsername = ccUser != null && StringUtils.hasText(ccUser.getUsername())
                ? ccUser.getUsername() : userId;
        statistics.put("unreadCcCount", processCcService.countUnreadCc(ccUsername));

        return statistics;
    }

    /**
     * 将 Flowable 历史任务与本地待办信息合成展示记录；评论和动作变量
     * 缺失时回退到本地镜像，保证历史列表仍能显示办理结果。
     *
     * @param historicTask Flowable 历史任务，提供节点、时间和本地变量关联 ID
     * @return 融合评论、动作与本地镜像后的历史展示对象
     */
    private TaskVO convertHistoricTaskToVO(HistoricTaskInstance historicTask) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(historicTask.getId());
        vo.setTaskName(historicTask.getName());
        vo.setProcessInstanceId(historicTask.getProcessInstanceId());
        vo.setProcessDefinitionId(historicTask.getProcessDefinitionId());
        vo.setAssignee(historicTask.getAssignee());
        vo.setCreateTime(historicTask.getCreateTime());
        vo.setEndTime(historicTask.getEndTime());
        vo.setDuration(historicTask.getDurationInMillis());

        // 被取消任务的结束时间不是审批时间，不得落入默认通过的兼容分支。
        if (StringUtils.hasText(historicTask.getDeleteReason()) && !"completed".equals(historicTask.getDeleteReason())) {
            vo.setResult("cancelled");
            vo.setComment(ProcessEndReason.comment(historicTask.getDeleteReason()));
            return vo;
        }
        // 获取任务评论
        List<org.flowable.engine.task.Comment> comments = taskService.getTaskComments(historicTask.getId());
        String commentMsg = comments.isEmpty() ? null : comments.get(0).getFullMessage();
        ProcessTask localTask = processTaskService.getTaskByTaskId(historicTask.getId());
        if ((commentMsg == null || commentMsg.isBlank()) && localTask != null) {
            commentMsg = localTask.getComment();
        }
        vo.setComment(commentMsg);
        
        // 判断是否是转办
        if (commentMsg != null && commentMsg.contains("转办给:")) {
            vo.setResult("transfer");
        } else {
            // 从变量中获取审批结果
            String action = getTaskVariable(historicTask.getId(), "action");
            if ((action == null || action.isBlank()) && localTask != null) {
                action = localTask.getAction();
            }
            vo.setResult(action != null ? action : "completed");
        }

        return vo;
    }

    /**
     * 读取历史任务本地变量，供已办列表还原操作类型；查询失败时返回 null，
     * 由调用方使用本地镜像或默认展示值兜底。
     *
     * @param taskId 历史任务 ID，用于读取其本地变量
     * @param variableName 要恢复的动作变量名，缺失时由调用方回退
     * @return 历史变量文本；缺失或查询失败时为 null
     */
    private String getTaskVariable(String taskId, String variableName) {
        try {
            HistoricTaskInstance task = historyService.createHistoricTaskInstanceQuery()
                    .taskId(taskId)
                    .includeTaskLocalVariables()
                    .singleResult();
            if (task != null && task.getTaskLocalVariables() != null) {
                Object value = task.getTaskLocalVariables().get(variableName);
                return value != null ? value.toString() : null;
            }
        } catch (Exception e) {
            log.warn("获取任务变量失败: taskId={}, variable={}", taskId, variableName);
        }
        return null;
    }
}
