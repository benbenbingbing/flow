package com.workflow.process.engine.infrastructure.flowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.port.EntityRecordPort;
import com.workflow.contracts.entity.port.EntityRecordQueryPort;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.process.assignment.application.InitiatorOrganizationSnapshotService;
import com.workflow.process.assignment.application.RelativeOrgPositionProcessInspector;
import com.workflow.process.assignment.infrastructure.flowable.MultiInstanceCollectionListener;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.instance.application.CalledProcessRecordSource;
import com.workflow.process.instance.application.WorkflowReservedVariables;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import com.workflow.process.status.application.ProcessEntityStatusPolicy;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 在子流程开始事件分发业务动作前建立独立实体上下文；旧调用活动和内嵌子流程保持原行为。 */
@Component
@RequiredArgsConstructor
public class CalledProcessEntityListener implements FlowableEventListener {
    private final ObjectMapper objectMapper;
    private final ProcessDefinitionConfigMapper configs;
    private final EntityDefinitionMapper entities;
    private final EntityRecordPort records;
    private final EntityRecordQueryPort queries;
    private final EntityProcessLinkMapper links;
    private final ProcessEntityStatusPolicy statusPolicy;
    private final RelativeOrgPositionProcessInspector relativePositions;
    private final InitiatorOrganizationSnapshotService snapshots;
    private final MultiInstanceCollectionListener multiInstance;
    private final org.flowable.engine.RuntimeService runtimeService;

    /**
     * PROCESS_STARTED 在输入映射完成后、首个节点执行前触发。所有写入加入引擎事务，
     * 记录不存在、重复绑定或初始化失败时抛异常，让父流程停留在原任务并可修正后重试。
     */
    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() != FlowableEngineEventType.PROCESS_STARTED
                || !(event instanceof FlowableEntityEvent entityEvent)
                || !(entityEvent.getEntity() instanceof ExecutionEntity execution)) return;
        ExecutionEntity child = execution.getProcessInstance();
        ExecutionEntity caller = child.getSuperExecution();
        if (caller == null || !(caller.getCurrentFlowElement() instanceof CallActivity call)) return;
        String document = ProcessEntityStatusPolicy.property(call, "callConfig");
        if (document == null || document.isBlank()) return;
        try {
            JsonNode config = objectMapper.readTree(document);
            if (!"existing".equals(config.path("entityBinding").asText())) return;
            bind(child, caller, config);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("子流程实体配置无效", exception);
        }
    }

    /** 仅目标流程自身的实体绑定是权威来源，不接受父流程传入 entityCode 冒充目标实体。 */
    private void bind(ExecutionEntity child, ExecutionEntity caller, JsonNode config) {
        String key = child.getProcessDefinitionKey();
        for (ExecutionEntity ancestor = caller.getProcessInstance(); ancestor != null;
                ancestor = ancestor.getSuperExecution() == null ? null : ancestor.getSuperExecution().getProcessInstance()) {
            if (key.equals(ancestor.getProcessDefinitionKey())) {
                throw new IllegalArgumentException("子流程不能循环调用: " + key);
            }
        }
        ProcessDefinitionConfig target = configs.findByProcessKey(key)
                .orElseThrow(() -> new IllegalArgumentException("子流程不存在: " + key));
        if (target.getStatus() != ProcessDefinitionConfig.ProcessStatus.PUBLISHED) {
            throw new IllegalArgumentException("子流程未发布或已禁用: " + key);
        }
        var bindings = entities.findAllByProcessDefinitionId(target.getId());
        if (bindings.size() != 1) throw new IllegalArgumentException("子流程必须唯一绑定一个实体: " + key);
        String entityCode = bindings.get(0).getEntityCode();
        String recordId = CalledProcessRecordSource.resolve(config.path("entityRecordId").asText(), caller::getVariable);
        String submitterId = text(caller.getVariable("submitterId"));
        String submitterName = text(caller.getVariable("submitterName"));

        // 聚合管道先锁实体记录，再写关联表，锁序与普通发起一致，避免两个父流程重复占用记录。
        records.bindCalledProcess(entityCode, recordId, child.getProcessDefinitionId(), child.getId(),
                submitterId, submitterName, statusPolicy.usesTransitions(child.getProcessDefinitionId()) ? null : "PENDING");
        if (links.selectLatestForUpdate(entityCode, recordId) != null) {
            throw new IllegalArgumentException("子流程记录已有流程关联，不能重复发起");
        }
        var record = queries.findById(entityCode, recordId);
        if (record == null) throw new IllegalArgumentException("子流程关联记录不存在: " + recordId);
        EntityProcessLink link = new EntityProcessLink();
        link.setId(UUID.randomUUID().toString().replace("-", ""));
        link.setEntityCode(entityCode);
        link.setEntityRecordId(recordId);
        link.setGeneration(1);
        link.setProcessDefinitionKey(key);
        link.setProcessInstanceId(child.getId());
        link.setState("ACTIVE");
        link.setRequestId("called:" + child.getId());
        link.setEntityStatus(record.getStatus());
        link.setVersion(0L);
        links.insert(link);

        // 先加载目标业务字段，再应用显式输入映射；平台身份最后写入，绝不整包继承父实体数据。
        Map<String, Object> variables = new LinkedHashMap<>(WorkflowReservedVariables.sanitize(record.getData()));
        // 系统名称不在 record.data 中；首个子流程网关也必须能读取它（空名称保留 null）。
        variables.put("name", record.getName());
        variables.putAll(WorkflowReservedVariables.sanitize(child.getVariables()));
        variables.put("entityCode", entityCode);
        variables.put("entityDataId", recordId);
        variables.put("code", record.getCode());
        variables.put("submitterId", submitterId);
        variables.put("submitterName", submitterName);
        variables.put("startUserId", submitterId);
        variables.put("initiator", submitterId);
        if (relativePositions.requiresInitiatorOrganizationSnapshot(child.getProcessDefinitionId())) {
            snapshots.captureTrustedSnapshot(variables, submitterId);
        }
        multiInstance.prepareVariables(child.getProcessDefinitionId(), variables);
        child.setVariables(variables);
        // 与普通实体流程保持一致，未显式指定业务键时使用目标记录 ID，便于历史检索。
        if (child.getBusinessKey() == null || child.getBusinessKey().isBlank()) {
            runtimeService.updateBusinessKey(child.getId(), recordId);
        }
    }

    private static String text(Object value) { return value == null ? null : value.toString(); }

    @Override public boolean isFailOnException() { return true; }
    @Override public boolean isFireOnTransactionLifecycleEvent() { return false; }
    @Override public String getOnTransaction() { return null; }
}
