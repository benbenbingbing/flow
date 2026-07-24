package com.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.FlowActionExecutionDetailDTO;
import com.workflow.entity.FlowAction;
import com.workflow.entity.FlowActionExecution;
import com.workflow.mapper.FlowActionMapper;
import com.workflow.mapper.FlowActionExecutionMapper;
import com.workflow.process.action.FlowActionContext;
import com.workflow.process.action.FlowActionTriggerEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 流程动作执行服务。
 *
 * <p>负责流程动作执行记录的全生命周期管理：创建、抢占、状态流转（成功 / 失败 / 重试 / 死信）、
 * 执行轨迹持久化、敏感字段脱敏、重试调度以及执行详情查询。状态回写均使用独立新事务，
 * 保证即使主事务回滚也能保留执行审计。</p>
 */
@Service
@RequiredArgsConstructor
public class FlowActionExecutionService {

    private final FlowActionExecutionMapper executionMapper;
    private final FlowActionMapper flowActionMapper;
    private final ObjectMapper objectMapper;
    private final FlowActionDefinitionService definitionService;

    /**
     * 在主事务内创建执行记录。
     *
     * @param action         动作配置
     * @param event          触发事件
     * @param idempotencyKey 幂等键
     * @param status         初始状态
     * @return 已创建的执行记录
     */
    @Transactional
    public FlowActionExecution create(
            FlowAction action,
            FlowActionTriggerEvent event,
            String idempotencyKey,
            FlowActionExecution.Status status) {
        return createRecord(action, event, idempotencyKey, status);
    }

    /**
     * 在独立新事务内创建执行记录，用于事务内动作的审计写入。
     *
     * @param action         动作配置
     * @param event          触发事件
     * @param idempotencyKey 幂等键
     * @param status         初始状态
     * @return 已创建的执行记录
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FlowActionExecution createInTransactionAudit(
            FlowAction action,
            FlowActionTriggerEvent event,
            String idempotencyKey,
            FlowActionExecution.Status status) {
        return createRecord(action, event, idempotencyKey, status);
    }

    /**
     * 实际构建并落库执行记录，序列化触发事件为 payload 并写入初始轨迹。
     *
     * @param action         动作配置
     * @param event          触发事件
     * @param idempotencyKey 幂等键
     * @param status         初始状态
     * @return 已创建的执行记录
     */
    private FlowActionExecution createRecord(
            FlowAction action,
            FlowActionTriggerEvent event,
            String idempotencyKey,
            FlowActionExecution.Status status) {
        FlowActionExecution execution = new FlowActionExecution();
        execution.setActionId(action.getId());
        execution.setActionName(action.getActionName());
        execution.setHandlerName(action.getInterfaceName());
        execution.setHandlerDisplayName(definitionService.displayName(action.getInterfaceName()));
        execution.setVersionId(event.getVersionId());
        execution.setProcessInstanceId(event.getProcessInstanceId());
        execution.setProcessDefinitionId(event.getProcessDefinitionId());
        execution.setExecutionId(event.getExecutionId());
        execution.setTaskId(event.getTaskId());
        execution.setEntityCode(event.getEntityCode());
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
        appendTrace(
                execution,
                status == FlowActionExecution.Status.PENDING ? "QUEUED" : "EXECUTION_CREATED",
                status == FlowActionExecution.Status.PENDING
                        ? "主事务内已写入提交后执行队列"
                        : "事务内动作执行记录已创建",
                Map.of(
                        "executionMode", valueOrEmpty(action.getExecutionMode()),
                        "failurePolicy", valueOrEmpty(action.getFailurePolicy())));
        executionMapper.insert(execution);
        return execution;
    }

    /**
     * 在独立新事务中标记处理器开始执行，并记录解析后的参数。
     *
     * @param execution 执行记录
     * @param context   执行上下文
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markHandlerStarted(FlowActionExecution execution, FlowActionContext context) {
        if (execution.getStartedAt() == null) {
            execution.setStartedAt(LocalDateTime.now());
        }
        execution.setResolvedParamsJson(writeJson(sanitize(context.getCustomParams())));
        appendTrace(
                execution,
                "HANDLER_STARTED",
                "开始调用流程动作处理器",
                Map.of(
                        "handlerName", valueOrEmpty(execution.getHandlerName()),
                        "triggerTiming", valueOrEmpty(execution.getTriggerTiming())));
        execution.setUpdatedAt(LocalDateTime.now());
        executionMapper.updateById(execution);
    }

    /**
     * 在独立新事务中捕获处理器的执行结果与轨迹，用于成功/失败前的中间状态落盘。
     *
     * @param execution 执行记录
     * @param context   执行上下文
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void captureContext(FlowActionExecution execution, FlowActionContext context) {
        execution.setResolvedParamsJson(writeJson(sanitize(context.getCustomParams())));
        if (context.getExecutionResult() != null) {
            execution.setResultJson(writeJson(sanitize(context.getExecutionResult())));
        }
        if (context.getExecutionTrace() != null) {
            for (Map<String, Object> item : context.getExecutionTrace()) {
                appendTrace(
                        execution,
                        String.valueOf(item.getOrDefault("stage", "HANDLER_STEP")),
                        String.valueOf(item.getOrDefault("message", "处理器执行步骤")),
                        sanitize(item.get("details")));
            }
        }
        execution.setUpdatedAt(LocalDateTime.now());
        executionMapper.updateById(execution);
    }

    /**
     * 在独立新事务中标记执行成功，补充结果并记录耗时。
     *
     * @param execution 执行记录
     * @param context   执行上下文；非空时先捕获中间状态
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(FlowActionExecution execution, FlowActionContext context) {
        if (context != null && execution.getResultJson() == null) {
            captureContext(execution, context);
        }
        execution.setStatus(FlowActionExecution.Status.SUCCESS.name());
        execution.setFinishedAt(LocalDateTime.now());
        execution.setUpdatedAt(LocalDateTime.now());
        execution.setErrorMessage(null);
        execution.setErrorStack(null);
        execution.setDurationMs(duration(execution));
        appendTrace(
                execution,
                "SUCCESS",
                "流程动作执行成功",
                Map.of("durationMs", execution.getDurationMs()));
        executionMapper.updateById(execution);
    }

    /**
     * 在独立新事务中标记最终失败（不再自动重试），记录错误信息与堆栈。
     *
     * @param execution 执行记录
     * @param error      失败异常
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFinalFailure(FlowActionExecution execution, Throwable error) {
        execution.setStatus(FlowActionExecution.Status.DEAD.name());
        execution.setFinishedAt(LocalDateTime.now());
        execution.setUpdatedAt(LocalDateTime.now());
        execution.setErrorMessage(errorMessage(error));
        execution.setErrorStack(errorStack(error));
        execution.setDurationMs(duration(execution));
        appendTrace(
                execution,
                "FAILED_FINAL",
                "流程动作执行失败且不再自动重试",
                Map.of(
                        "errorType", error == null ? "Unknown" : error.getClass().getName(),
                        "errorMessage", execution.getErrorMessage()));
        executionMapper.updateById(execution);
    }

    /**
     * 在独立新事务中处理重试失败：未达上限则安排下次重试，达到上限则进入死信。
     *
     * @param execution 执行记录
     * @param error      失败异常
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetryFailure(FlowActionExecution execution, Throwable error) {
        int retryCount = execution.getRetryCount() == null ? 1 : execution.getRetryCount() + 1;
        execution.setRetryCount(retryCount);
        execution.setErrorMessage(errorMessage(error));
        execution.setErrorStack(errorStack(error));
        execution.setUpdatedAt(LocalDateTime.now());
        if (retryCount >= execution.getMaxRetries()) {
            // 重试次数耗尽，进入死信
            execution.setStatus(FlowActionExecution.Status.DEAD.name());
            execution.setFinishedAt(LocalDateTime.now());
            execution.setNextRetryTime(null);
            execution.setDurationMs(duration(execution));
            appendTrace(
                    execution,
                    "RETRY_EXHAUSTED",
                    "自动重试次数已耗尽，执行记录进入死信",
                    Map.of(
                            "retryCount", retryCount,
                            "maxRetries", execution.getMaxRetries(),
                            "errorMessage", execution.getErrorMessage()));
        } else {
            // 仍有重试机会，按指数退避安排下次执行
            execution.setStatus(FlowActionExecution.Status.FAILED.name());
            execution.setNextRetryTime(LocalDateTime.now().plusSeconds(retryDelaySeconds(retryCount)));
            appendTrace(
                    execution,
                    "RETRY_SCHEDULED",
                    "执行失败，已安排下一次自动重试",
                    Map.of(
                            "retryCount", retryCount,
                            "maxRetries", execution.getMaxRetries(),
                            "nextRetryTime", execution.getNextRetryTime().toString(),
                            "errorMessage", execution.getErrorMessage()));
        }
        executionMapper.updateById(execution);
    }

    /**
     * 按主键查询执行记录。
     *
     * @param id 执行记录 ID
     * @return 执行记录；不存在返回 null
     */
    public FlowActionExecution get(String id) {
        return executionMapper.selectById(id);
    }

    /**
     * 查询当前可执行的就绪记录（PENDING 或已到重试时间的 FAILED）。
     *
     * @param limit 最多返回条数
     * @return 就绪执行记录列表
     */
    public List<FlowActionExecution> findReady(int limit) {
        return executionMapper.findReady(LocalDateTime.now(), limit);
    }

    /**
     * 恢复中断的运行中记录：将超过 10 分钟仍处于 RUNNING 的记录改回 FAILED 以便重新抢占。
     *
     * @return 恢复的记录条数
     */
    public int recoverStale() {
        LocalDateTime now = LocalDateTime.now();
        return executionMapper.recoverStale(now, now.minusMinutes(10));
    }

    /**
     * 抢占执行记录：仅当原状态为 PENDING/FAILED 时可成功。
     *
     * @param id 执行记录 ID
     * @return 抢占成功返回 true，已被其他线程抢占返回 false
     */
    public boolean claim(String id) {
        return executionMapper.claim(id, LocalDateTime.now()) == 1;
    }

    /**
     * 查询流程实例下的全部执行详情。
     *
     * @param processInstanceId 流程实例 ID
     * @return 执行详情 DTO 列表
     */
    public List<FlowActionExecutionDetailDTO> findDetailsByProcessInstanceId(String processInstanceId) {
        return executionMapper.findByProcessInstanceId(processInstanceId).stream()
                .map(this::toDetail)
                .toList();
    }

    /**
     * 超级管理员手动重试死信/失败记录：清空错误信息并重置为 PENDING 立即可执行。
     *
     * @param id 执行记录 ID
     * @throws RuntimeException 记录不存在或状态不可重试时抛出
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
        execution.setDurationMs(null);
        execution.setErrorMessage(null);
        execution.setErrorStack(null);
        appendTrace(execution, "MANUAL_RETRY", "超级管理员发起手工重试", null);
        execution.setUpdatedAt(LocalDateTime.now());
        executionMapper.updateById(execution);
    }

    /**
     * 从执行记录的 payload 中还原触发事件。
     *
     * @param execution 执行记录
     * @return 还原后的触发事件
     * @throws RuntimeException 反序列化失败时抛出
     */
    public FlowActionTriggerEvent readEvent(FlowActionExecution execution) {
        try {
            return objectMapper.readValue(execution.getPayloadJson(), FlowActionTriggerEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("流程动作执行上下文解析失败", e);
        }
    }

    /** 序列化触发事件为 payload JSON */
    private String writePayload(FlowActionTriggerEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("流程动作执行上下文序列化失败", e);
        }
    }

    /**
     * 将执行记录转换为详情 DTO，并在缺失动作名称/处理器名时回查补全。
     *
     * @param execution 执行记录
     * @return 执行详情 DTO
     */
    private FlowActionExecutionDetailDTO toDetail(FlowActionExecution execution) {
        FlowActionExecutionDetailDTO detail = objectMapper.convertValue(
                execution,
                FlowActionExecutionDetailDTO.class);
        if (!StringUtils.hasText(detail.getActionName())
                || !StringUtils.hasText(detail.getHandlerName())) {
            FlowAction action = flowActionMapper.selectById(execution.getActionId());
            if (action != null) {
                if (!StringUtils.hasText(detail.getActionName())) {
                    detail.setActionName(action.getActionName());
                }
                if (!StringUtils.hasText(detail.getHandlerName())) {
                    detail.setHandlerName(action.getInterfaceName());
                }
                if (!StringUtils.hasText(detail.getHandlerDisplayName())) {
                    detail.setHandlerDisplayName(definitionService.displayName(action.getInterfaceName()));
                }
            }
        }
        detail.setTriggerContext(readSanitizedMap(execution.getPayloadJson()));
        detail.setResolvedParams(readSanitizedMap(execution.getResolvedParamsJson()));
        detail.setResult(readSanitizedObject(execution.getResultJson()));
        detail.setExecutionTrace(readTrace(execution.getExecutionTraceJson()));
        return detail;
    }

    /** 追加一条执行轨迹（含时间、阶段、消息、详情）并回写到记录 */
    private void appendTrace(
            FlowActionExecution execution,
            String stage,
            String message,
            Object details) {
        List<Map<String, Object>> trace = readTrace(execution.getExecutionTraceJson());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("time", LocalDateTime.now().toString());
        item.put("stage", stage);
        item.put("message", message);
        if (details != null) {
            item.put("details", details);
        }
        trace.add(item);
        execution.setExecutionTraceJson(writeJson(trace));
    }

    /** 读取执行轨迹 JSON 为可变列表；解析失败返回空列表 */
    private List<Map<String, Object>> readTrace(String value) {
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(value, new TypeReference<>() {}));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 读取并脱敏 JSON 为 map；非对象或解析失败返回空 map */
    private Map<String, Object> readSanitizedMap(String value) {
        Object parsed = readSanitizedObject(value);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, itemValue) -> result.put(String.valueOf(key), itemValue));
            return result;
        }
        return Map.of();
    }

    /** 读取并脱敏 JSON 为对象；解析失败返回原始字符串 */
    private Object readSanitizedObject(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return sanitize(objectMapper.readValue(value, Object.class));
        } catch (Exception e) {
            return value;
        }
    }

    /** 将对象序列化为 JSON；失败返回 null */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    /** 递归脱敏：对 map 中敏感键替换为 ******，对集合/数组递归处理 */
    private Object sanitize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, itemValue) -> {
                String name = String.valueOf(key);
                sanitized.put(name, isSensitiveKey(name) ? "******" : sanitize(itemValue));
            });
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::sanitize).toList();
        }
        if (value != null && value.getClass().isArray()) {
            return objectMapper.convertValue(value, List.class).stream()
                    .map(this::sanitize)
                    .toList();
        }
        return value;
    }

    /** 判断字段名是否为敏感键（password/secret/token/authorization/cookie/credential） */
    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("authorization")
                || normalized.contains("cookie")
                || normalized.contains("credential");
    }

    /** 计算执行耗时（毫秒），起始时间为 startedAt 或 createdAt */
    private long duration(FlowActionExecution execution) {
        LocalDateTime start = execution.getStartedAt() == null
                ? execution.getCreatedAt()
                : execution.getStartedAt();
        LocalDateTime finish = execution.getFinishedAt() == null
                ? LocalDateTime.now()
                : execution.getFinishedAt();
        return Math.max(0, Duration.between(start, finish).toMillis());
    }

    /** 从重试配置 JSON 解析最大重试次数，限制在 0~20，缺省或异常返回 5 */
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

    /** 计算第 retryCount 次重试的延迟秒数，按 60 * 3^(n-1) 指数退避，上限 6 小时 */
    private long retryDelaySeconds(int retryCount) {
        long delay = 60L * (long) Math.pow(3, Math.max(0, retryCount - 1));
        return Math.min(delay, 21600L);
    }

    /** 提取异常消息，截断到 4000 字符 */
    private String errorMessage(Throwable error) {
        String message = error == null ? "未知错误" : error.getMessage();
        if (!StringUtils.hasText(message) && error != null) {
            message = error.getClass().getName();
        }
        return message == null ? "未知错误" : message.substring(0, Math.min(message.length(), 4000));
    }

    /** 提取异常堆栈字符串，截断到 16000 字符 */
    private String errorStack(Throwable error) {
        if (error == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        String stack = writer.toString();
        return stack.substring(0, Math.min(stack.length(), 16000));
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
