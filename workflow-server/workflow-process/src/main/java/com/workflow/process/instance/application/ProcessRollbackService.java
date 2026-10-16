package com.workflow.process.instance.application;

import com.workflow.core.logging.LogValue;
import com.workflow.process.task.application.ProcessTaskService;

import com.workflow.core.result.Result;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 流程退回服务
 * 处理驳回重提、流程退回等复杂流转
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessRollbackService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.workflow.contracts.process.task.port.ProcessTaskCommandPort taskCommands;

    @org.springframework.beans.factory.annotation.Autowired
    private com.workflow.process.status.application.ProcessEntityStatusPolicy statusPolicy;

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final ProcessTaskService processTaskService;
    private final ProcessTaskMapper processTaskMapper;
    private final EntityMutationPort entityMutationPort;
    private final ProcessDefinitionConfigMapper processDefinitionConfigMapper;
    private final org.flowable.engine.RepositoryService repositoryService;

    /**
     * 驳回兼容入口：未指定目标按发布连线执行；指定目标则验证实际到达，不能任意跳转。
     *
     * @param taskId    当前任务ID
     * @param userId    当前用户
     * @param comment   驳回原因
     * @param targetNodeId 发布策略允许的目标节点（可选；省略时不强制指定落点）
     * @return 处理后的驳回任务结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.REJECT,
            operation = "驳回流程任务",
            risk = AuditRiskLevel.HIGH,
            targetType = "PROCESS_TASK",
            targetIdArg = 0)
    public Result<Void> rejectTask(String taskId, String userId, String comment, String targetNodeId) {
        var meta = com.workflow.process.command.application.ProcessHttpCommands.meta(null);
        var submission = com.workflow.contracts.process.task.model.TaskSubmission.comment(comment);
        // 历史签名的 userId 不再作为授权来源；公共命令始终验证真实执行身份。
        // 未指定目标沿发布驳回连线执行，指定目标则要求实际到达该节点。
        if (targetNodeId == null || targetNodeId.isBlank()) {
            taskCommands.complete(new com.workflow.contracts.process.task.model.CompleteTaskCommand(meta, taskId, "reject", submission));
        } else {
            taskCommands.returnToNode(new com.workflow.contracts.process.task.model.ReturnTaskCommand(meta, taskId, targetNodeId, submission));
        }
        return Result.success();
    }

    /**
     * 重新提交流程（发起人在被驳回后重新提交）
     *
     * @param processInstanceId 流程实例ID
     * @param userId            当前用户（必须是发起人）
     * @param formData          更新的表单数据
     * @param comment           重新提交备注
     * @return 处理后的{@code resubmit}流程结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.RESUBMIT,
            operation = "重新提交流程",
            risk = AuditRiskLevel.HIGH,
            targetType = "PROCESS_INSTANCE",
            targetIdArg = 0)
    public Result<Void> resubmitProcess(String processInstanceId, String userId, 
                                         Map<String, Object> formData, String comment) {
        // 旧接口只提供实例 ID。多个退回待办时不能随意选第一条，调用方须改用 taskId 命令。
        String identity = com.workflow.admin.security.context.UserContext.requireUsernameOrId();
        var candidates = taskService.createTaskQuery().processInstanceId(processInstanceId)
                .taskAssignee(identity).list();
        if (candidates.size() != 1) throw new com.workflow.contracts.process.error.ProcessOperationException(
                "PROCESS_STATE_CHANGED", "需要唯一的重新提交待办，请使用明确的 taskId", processInstanceId);
        taskCommands.resubmit(new com.workflow.contracts.process.task.model.ResubmitTaskCommand(
                com.workflow.process.command.application.ProcessHttpCommands.meta(null), candidates.get(0).getId(),
                new com.workflow.contracts.process.task.model.TaskSubmission(comment, "重新提交", formData, null, null)));
        return Result.success();
    }

    /**
     * 检查流程是否被驳回（发起人使用）
     *
     * @param processInstanceId 流程实例ID
     * @return 驳回信息，如果没有被驳回返回null
     */
    public Map<String, Object> checkRejectedStatus(String processInstanceId) {
        ProcessInstance processInstance = runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) {
            return null;
        }

        // 检查流程变量中是否有驳回标记
        Map<String, Object> vars = runtimeService.getVariables(processInstanceId);
        
        // 统一办理路径以 approved 决定连线；旧退回入口还会留下带下划线的标记。
        // 两者都读取，避免旧页面在收口后看不到可以重新提交的退回待办。
        boolean rejected = Boolean.TRUE.equals(vars.get("_rejected_")) || "reject".equals(vars.get("approved"));
        if (rejected) {
            Map<String, Object> result = new HashMap<>();
            result.put("rejected", true);
            result.put("rejectBy", vars.getOrDefault("_rejectBy_", vars.get("rejectBy")));
            result.put("rejectTime", vars.getOrDefault("_rejectTime_", vars.get("rejectTime")));
            result.put("rejectComment", vars.getOrDefault("_rejectComment_", vars.get("comment")));
            result.put("canResubmit", true);
            return result;
        }

        return null;
    }

    /**
     * 找到流程的发起节点
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @return 查询后的启动节点ID文本，供调用方比较或展示
     */
    private String findStartNodeId(String processDefinitionId) {
        try {
            org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService
                    .getBpmnModel(processDefinitionId);
            
            org.flowable.bpmn.model.Process process = bpmnModel.getMainProcess();
            Collection<org.flowable.bpmn.model.FlowElement> elements = process.getFlowElements();
            
            for (org.flowable.bpmn.model.FlowElement element : elements) {
                if (element instanceof org.flowable.bpmn.model.StartEvent) {
                    // 找到开始事件，返回它的下一个节点
                    List<org.flowable.bpmn.model.SequenceFlow> outgoingFlows = 
                            ((org.flowable.bpmn.model.StartEvent) element).getOutgoingFlows();
                    if (!outgoingFlows.isEmpty()) {
                        return outgoingFlows.get(0).getTargetRef();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查找发起节点失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 更新实体状态为"被驳回"
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     */
    private void updateEntityStatusToRejected(
            String processInstanceId,
            String taskId,
            String userId) {
        try {
            Map<String, Object> vars = runtimeService.getVariables(processInstanceId);
            String entityCode = (String) vars.get("entityCode");
            String entityDataId = (String) vars.get("entityDataId");

            if (entityCode != null
                    && entityDataId != null) {
                String rejectedStatusCode =
                        findRejectedStatusCode(entityCode);
                String key = String.join(
                        ":",
                        "process-reject",
                        processInstanceId,
                        taskId);
                entityMutationPort.execute(
                        new EntityMutationCommand(
                                key,
                                entityCode,
                                entityDataId,
                                EntityMutationOperationType.STATUS_CHANGE,
                                Map.of(
                                        "status",
                                        rejectedStatusCode),
                                EntityMutationContext.builder(
                                                EntityMutationSourceType.APPROVAL_TASK,
                                                "PROCESS_REJECTED",
                                                "流程驳回")
                                        .sourceId(taskId)
                                        .sourceRecord(
                                                entityCode,
                                                entityDataId)
                                        .process(
                                                processDefinitionId(
                                                        processInstanceId),
                                                processInstanceId,
                                                taskId)
                                        .operator(
                                                userId,
                                                userId)
                                        .trace(key, key)
                                        .build()));
                log.debug(
                        "实体数据 {} 状态已通过变更管道更新为被驳回",
                        entityDataId);
            }
        } catch (Exception e) {
            log.warn("更新实体驳回状态失败: {}", e.getMessage());
        }
    }
    
    /**
     * 查找驳回状态编码
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 查询后的已拒绝状态编码文本，供调用方比较或展示
     */
    private String findRejectedStatusCode(String entityCode) {
        try {
            // 尝试从 entity_status 表查找类型为 REJECTED 的状态
            // 这里简化处理，直接返回常用的驳回状态码
            return "REJECTED";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 更新实体数据
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param formData 表单数据，供本方法更新实体数据时使用
     */
    private void updateEntityData(
            String processInstanceId,
            String taskId,
            String userId,
            Map<String, Object> formData) {
        try {
            Map<String, Object> vars = runtimeService.getVariables(processInstanceId);
            String entityCode = (String) vars.get("entityCode");
            String entityDataId = (String) vars.get("entityDataId");

            if (entityCode != null
                    && entityDataId != null
                    && !formData.isEmpty()) {
                String key = String.join(
                        ":",
                        "process-resubmit",
                        processInstanceId,
                        taskId);
                entityMutationPort.execute(
                        new EntityMutationCommand(
                                key,
                                entityCode,
                                entityDataId,
                                EntityMutationOperationType.UPDATE,
                                Map.of(
                                        "data",
                                        new LinkedHashMap<>(
                                                formData)),
                                EntityMutationContext.builder(
                                                EntityMutationSourceType.APPROVAL_TASK,
                                                "RESUBMIT_FORM_EDIT",
                                                "驳回后重新提交")
                                        .sourceId(taskId)
                                        .sourceRecord(
                                                entityCode,
                                                entityDataId)
                                        .process(
                                                processDefinitionId(
                                                        processInstanceId),
                                                processInstanceId,
                                                taskId)
                                        .operator(
                                                userId,
                                                userId)
                                        .trace(key, key)
                                        .build()));
                log.debug(
                        "实体数据 {} 已通过变更管道更新",
                        entityDataId);
            }
        } catch (Exception e) {
            log.warn("更新实体数据失败: {}", e.getMessage());
        }
    }

    /**
     * 生成定义ID文本，供后续匹配或展示。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 处理后的定义ID文本，供调用方比较或展示
     */
    private String processDefinitionId(
            String processInstanceId) {
        ProcessInstance instance = runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return instance == null
                ? null
                : instance.getProcessDefinitionId();
    }
}
