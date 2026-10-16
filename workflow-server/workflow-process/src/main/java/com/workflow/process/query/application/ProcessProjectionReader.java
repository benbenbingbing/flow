package com.workflow.process.query.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.contracts.process.model.*;
import com.workflow.contracts.process.query.model.*;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.status.application.ProcessEndReason;
import com.workflow.process.task.application.TaskIdentityAccessService;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

/**
 * 共用轻量投影，不加载实体正文、表单布局或页面凭据。这里不授予访问权，
 * 调用方必须在返回投影前通过实例/任务范围校验。
 */
@Component
@RequiredArgsConstructor
public class ProcessProjectionReader {
    private final RuntimeService runtime;
    private final HistoryService history;
    private final RepositoryService repository;
    private final EntityProcessLinkMapper links;
    private final ProcessVersionHistoryMapper versions;
    private final TaskIdentityAccessService taskAccess;
    private final ProcessQueryTime time;

    /**
     * 精确部署决定版本，历史归档后不能回退到配置表的“最新版本”。
     * 老实例缺失平台发布记录时保留 null，仍返回可证实的引擎定义标识。
     */
    public ProcessInstanceView instance(HistoricProcessInstance instance) {
        var active = runtime.createProcessInstanceQuery().processInstanceId(instance.getId()).singleResult();
        var definition = repository.createProcessDefinitionQuery().processDefinitionId(instance.getProcessDefinitionId()).singleResult();
        var version = definition == null ? null : versions.selectOne(Wrappers.<ProcessVersionHistory>lambdaQuery()
                .select(ProcessVersionHistory::getId, ProcessVersionHistory::getProcessConfigId)
                .eq(ProcessVersionHistory::getDeploymentId, definition.getDeploymentId()));
        var link = links.findByProcessInstanceId(instance.getId());
        ProcessBusinessReference business = link == null ? legacyBusiness(instance.getId())
                : new ProcessBusinessReference(link.getEntityCode(), link.getEntityRecordId());
        ProcessLifecycle lifecycle = instance.getEndTime() != null ? ProcessLifecycle.ENDED
                : active != null && active.isSuspended() ? ProcessLifecycle.SUSPENDED : ProcessLifecycle.RUNNING;
        return new ProcessInstanceView(instance.getId(), version == null ? null : version.getProcessConfigId(),
                instance.getProcessDefinitionId(), instance.getProcessDefinitionKey(), definition == null ? null : definition.getVersion(),
                version == null ? null : version.getId(), business, instance.getStartUserId(), lifecycle,
                lifecycle == ProcessLifecycle.ENDED ? ProcessEndType.valueOf(ProcessEndReason.category(instance.getDeleteReason())) : null,
                time.instant(instance.getStartTime()), time.instant(instance.getEndTime()));
    }

    /** 普通任务实时身份来自引擎；投影可能滞后，不能仅依据旧 assignee 判断可认领。 */
    public ProcessTaskView task(Task task) {
        boolean canClaim = (task.getAssignee() == null || task.getAssignee().isBlank())
                && !task.isSuspended() && taskAccess.canCurrentUserAccess(task);
        return new ProcessTaskView(task.getId(), task.getId(), "ENGINE", task.getProcessInstanceId(),
                task.getTaskDefinitionKey(), task.getName(), task.isSuspended() ? "suspended" : "todo",
                task.getAssignee(), time.instant(task.getCreateTime()), null, canClaim);
    }

    /** 镜像历史保留平台状态，本地加签永远没有独立 engineTaskId 或认领能力。 */
    public ProcessTaskView mirror(ProcessTask task) {
        boolean addSign = "ADD_SIGN".equals(task.getNodeType());
        return new ProcessTaskView(task.getTaskId(), addSign ? null : task.getTaskId(), addSign ? "ADD_SIGN" : "ENGINE",
                task.getProcessInstanceId(), task.getNodeId(), task.getNodeName(), task.getStatus(), task.getAssigneeId(),
                time.instant(task.getStartTime() == null ? task.getCreateTime() : task.getStartTime()),
                time.instant(task.getEndTime()), false);
    }

    /** 兼容尚无 entity_process_link 的实例，只读取两个根变量，不能吸收任务/分支同名值。 */
    private ProcessBusinessReference legacyBusiness(String instanceId) {
        String entity = rootString(instanceId, "entityCode");
        String record = rootString(instanceId, "entityDataId");
        return entity == null || record == null ? null : new ProcessBusinessReference(entity, record);
    }

    private String rootString(String instanceId, String name) {
        return history.createHistoricVariableInstanceQuery().processInstanceId(instanceId).variableName(name).list().stream()
                .filter(value -> value.getTaskId() == null && instanceId.equals(value.getExecutionId()))
                .map(value -> value.getValue()).filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }
}
