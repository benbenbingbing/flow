package com.workflow.process.form.infrastructure.adapter;

import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;
import com.workflow.contracts.entity.ui.port.UiRuntimeAccessPort;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import com.workflow.process.task.application.LocalAddSignTaskAccessService;
import com.workflow.process.task.application.TaskIdentityAccessService;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.util.Map;
import java.util.Objects;

/** 每次请求按真实待办和流程发布快照恢复授权，页面上的 taskId 或 mode 本身不授予权限。 */
@Component
@RequiredArgsConstructor
public class UiRuntimeAccessAdapter implements UiRuntimeAccessPort {
    private final TaskService taskService;
    private final HistoryService historyService;
    private final ProcessTaskMapper taskMapper;
    private final TaskIdentityAccessService taskAccess;
    private final LocalAddSignTaskAccessService addSignAccess;
    private final ProcessInstanceAccessService instanceAccess;
    private final ProcessPublishedSnapshotService snapshots;
    private final EntityDefinitionMapper definitions;
    private final UiConfigReleaseService releases;

    /**
     * 活动任务始终重新校验办理身份；历史详情只授予实例读取能力。
     * 返回坐标全部来自服务端，浏览器额外声明的记录/节点只能作为一致性约束。
     */
    @Override
    public UiRuntimeResolutionContext resolve(String formId, Map<String, Object> runtimeContext) {
        Map<String, Object> input = runtimeContext == null ? Map.of() : runtimeContext;
        String taskId = text(input.get("taskId"));
        String instanceId = text(input.get("processInstanceId"));
        if (taskId != null) return active(formId, taskId, instanceId, input);
        if (instanceId == null) {
            if (text(input.get("nodeId")) != null) throw denied();
            return UiRuntimeResolutionContext.standalone();
        }
        instanceAccess.requireReadAccess(instanceId);
        var instance = historyService.createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        if (instance == null) throw denied();
        String nodeId = text(input.get("nodeId"));
        String entityCode = variable(instanceId, "entityCode");
        String recordId = variable(instanceId, "entityDataId");
        var history = snapshots.getVersionByProcessDefinitionId(instance.getProcessDefinitionId());
        if (history == null || entityCode == null || recordId == null) throw denied();
        requireForm(formId, instance.getProcessDefinitionId(), nodeId, entityCode);
        requireCoordinate(input, "entityCode", entityCode);
        requireCoordinate(input, "recordId", recordId);
        return new UiRuntimeResolutionContext(UiRuntimePurpose.HISTORICAL, history.getId(), nodeId,
                null, instanceId, entityCode, recordId);
    }

    private UiRuntimeResolutionContext active(String formId, String taskId, String requestedInstance,
            Map<String, Object> input) {
        var local = taskMapper.selectByTaskId(taskId);
        if (local == null) throw denied();
        Task task;
        if ("ADD_SIGN".equals(local.getNodeType())) {
            // 本地加签必须沿用仍存活的源节点，不能把人工加签节点当成新的表单授权入口。
            task = addSignAccess.requireCurrentUserAccess(taskId, requestedInstance).sourceTask();
        } else {
            task = taskService.createTaskQuery().taskId(taskId).singleResult();
            if (task == null) throw denied();
            taskAccess.requireCurrentUserAccess(task);
        }
        if (!Objects.equals(local.getProcessInstanceId(), task.getProcessInstanceId())
                || requestedInstance != null && !Objects.equals(requestedInstance, task.getProcessInstanceId())) throw denied();
        String entityCode = variable(task.getProcessInstanceId(), "entityCode");
        String recordId = variable(task.getProcessInstanceId(), "entityDataId");
        if (entityCode == null || recordId == null || !Objects.equals(entityCode, local.getEntityCode())
                || !Objects.equals(recordId, local.getEntityDataId())) throw denied();
        requireCoordinate(input, "nodeId", task.getTaskDefinitionKey());
        requireCoordinate(input, "entityCode", entityCode);
        requireCoordinate(input, "recordId", recordId);
        requireForm(formId, task.getProcessDefinitionId(), task.getTaskDefinitionKey(), entityCode);
        var history = snapshots.getVersionByProcessDefinitionId(task.getProcessDefinitionId());
        if (history == null) throw denied();
        return UiRuntimeResolutionContext.activeTask(history.getId(), task.getTaskDefinitionKey(), taskId,
                task.getProcessInstanceId(), entityCode, recordId);
    }

    /** 显式绑定优先；没有绑定时只允许实体当前默认表单，不允许客户端任意指定另一张表单。 */
    private void requireForm(String formId, String definitionId, String nodeId, String entityCode) {
        if (!StringUtils.hasText(formId)) throw denied();
        var bindings = StringUtils.hasText(nodeId) ? snapshots.getNodeFormsByProcessDefinitionId(definitionId, nodeId) : null;
        if (bindings != null && bindings.stream().anyMatch(binding -> StringUtils.hasText(binding.getFormId()))) {
            if (bindings.stream().noneMatch(binding -> Objects.equals(formId, binding.getFormId()))) throw denied();
            return;
        }
        var entity = definitions.findByEntityCode(entityCode).orElseThrow(UiRuntimeAccessAdapter::denied);
        var form = releases.currentDefaultForm(entity.getId());
        if (form == null || !Objects.equals(formId, form.getId())) throw denied();
    }

    private String variable(String instanceId, String name) {
        var variable = historyService.createHistoricVariableInstanceQuery().processInstanceId(instanceId)
                .variableName(name).singleResult();
        return variable == null ? null : text(variable.getValue());
    }

    private static void requireCoordinate(Map<String, Object> input, String key, String actual) {
        String supplied = text(input.get(key));
        if (supplied != null && !Objects.equals(supplied, actual)) throw denied();
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private static BusinessForbiddenException denied() {
        return new BusinessForbiddenException("FORM_RUNTIME_CONTEXT_MISMATCH", "表单与当前可访问的任务或流程记录不一致");
    }
}
