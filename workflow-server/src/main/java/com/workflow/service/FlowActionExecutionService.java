package com.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.FlowAction;
import com.workflow.entity.FlowActionExecution;
import com.workflow.mapper.FlowActionExecutionMapper;
import com.workflow.process.action.FlowActionTriggerEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlowActionExecutionService {

    private final FlowActionExecutionMapper executionMapper;
    private final ObjectMapper objectMapper;

    public FlowActionExecution create(
            FlowAction action,
            FlowActionTriggerEvent event,
            String idempotencyKey,
            FlowActionExecution.Status status) {
        FlowActionExecution execution = new FlowActionExecution();
        execution.setActionId(action.getId());
        execution.setVersionId(event.getVersionId());
        execution.setProcessInstanceId(event.getProcessInstanceId());
        execution.setProcessDefinitionId(event.getProcessDefinitionId());
        execution.setExecutionId(event.getExecutionId());
        execution.setTaskId(event.getTaskId());
        execution.setScopeType(event.getScopeType());
        execution.setElementId(event.getElementId());
        execution.setTriggerTiming(event.getTriggerTiming());
        execution.setIdempotencyKey(idempotencyKey);
        execution.setPayloadJson(writePayload(event));
        execution.setStatus(status.name());
        execution.setRetryCount(0);
        execution.setMaxRetries(resolveMaxRetries(action.getRetryConfig()));
        execution.setCreatedAt(LocalDateTime.now());
        execution.setUpdatedAt(LocalDateTime.now());
        if (status == FlowActionExecution.Status.RUNNING) {
            execution.setStartedAt(LocalDateTime.now());
        }
        executionMapper.insert(execution);
        return execution;
    }

    public void markSuccess(FlowActionExecution execution) {
        execution.setStatus(FlowActionExecution.Status.SUCCESS.name());
        execution.setFinishedAt(LocalDateTime.now());
        execution.setUpdatedAt(LocalDateTime.now());
        execution.setErrorMessage(null);
        executionMapper.updateById(execution);
    }

    public void markFinalFailure(FlowActionExecution execution, Throwable error) {
        execution.setStatus(FlowActionExecution.Status.DEAD.name());
        execution.setFinishedAt(LocalDateTime.now());
        execution.setUpdatedAt(LocalDateTime.now());
        execution.setErrorMessage(errorMessage(error));
        executionMapper.updateById(execution);
    }

    public void markRetryFailure(FlowActionExecution execution, Throwable error) {
        int retryCount = execution.getRetryCount() == null ? 1 : execution.getRetryCount() + 1;
        execution.setRetryCount(retryCount);
        execution.setErrorMessage(errorMessage(error));
        execution.setUpdatedAt(LocalDateTime.now());
        if (retryCount >= execution.getMaxRetries()) {
            execution.setStatus(FlowActionExecution.Status.DEAD.name());
            execution.setFinishedAt(LocalDateTime.now());
            execution.setNextRetryTime(null);
        } else {
            execution.setStatus(FlowActionExecution.Status.FAILED.name());
            execution.setNextRetryTime(LocalDateTime.now().plusSeconds(retryDelaySeconds(retryCount)));
        }
        executionMapper.updateById(execution);
    }

    public FlowActionExecution get(String id) {
        return executionMapper.selectById(id);
    }

    public List<FlowActionExecution> findReady(int limit) {
        return executionMapper.findReady(LocalDateTime.now(), limit);
    }

    public int recoverStale() {
        LocalDateTime now = LocalDateTime.now();
        return executionMapper.recoverStale(now, now.minusMinutes(10));
    }

    public boolean claim(String id) {
        return executionMapper.claim(id, LocalDateTime.now()) == 1;
    }

    public List<FlowActionExecution> findByProcessInstanceId(String processInstanceId) {
        return executionMapper.findByProcessInstanceId(processInstanceId);
    }

    public void retry(String id) {
        FlowActionExecution execution = executionMapper.selectById(id);
        if (execution == null) {
            throw new RuntimeException("流程动作执行记录不存在");
        }
        if (!FlowActionExecution.Status.DEAD.name().equals(execution.getStatus())
                && !FlowActionExecution.Status.FAILED.name().equals(execution.getStatus())) {
            throw new RuntimeException("只有失败或死信动作可以重试");
        }
        execution.setStatus(FlowActionExecution.Status.PENDING.name());
        execution.setRetryCount(0);
        execution.setNextRetryTime(LocalDateTime.now());
        execution.setFinishedAt(null);
        execution.setErrorMessage(null);
        execution.setUpdatedAt(LocalDateTime.now());
        executionMapper.updateById(execution);
    }

    public FlowActionTriggerEvent readEvent(FlowActionExecution execution) {
        try {
            return objectMapper.readValue(execution.getPayloadJson(), FlowActionTriggerEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("流程动作执行上下文解析失败", e);
        }
    }

    private String writePayload(FlowActionTriggerEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("流程动作执行上下文序列化失败", e);
        }
    }

    private int resolveMaxRetries(String retryConfig) {
        if (!StringUtils.hasText(retryConfig)) {
            return 5;
        }
        try {
            JsonNode node = objectMapper.readTree(retryConfig);
            return Math.max(0, Math.min(20, node.path("maxRetries").asInt(5)));
        } catch (Exception e) {
            return 5;
        }
    }

    private long retryDelaySeconds(int retryCount) {
        long delay = 60L * (long) Math.pow(3, Math.max(0, retryCount - 1));
        return Math.min(delay, 21600L);
    }

    private String errorMessage(Throwable error) {
        String message = error == null ? "未知错误" : error.getMessage();
        if (!StringUtils.hasText(message) && error != null) {
            message = error.getClass().getName();
        }
        return message == null ? "未知错误" : message.substring(0, Math.min(message.length(), 4000));
    }
}
