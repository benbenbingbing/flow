package com.workflow.process.action.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.spi.FlowActionProvider;
import com.workflow.contracts.audit.AuditEventIds;
import com.workflow.contracts.audit.model.AuditSourcePointer;
import com.workflow.contracts.audit.context.OperationContext;
import com.workflow.contracts.audit.context.OperationContextHolder;
import com.workflow.process.action.domain.FlowActionTriggerEvent;
import com.workflow.process.action.infrastructure.flowable.FlowActionRuntimeAdapter;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import com.workflow.process.action.infrastructure.persistence.record.FlowActionExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 流程动作执行器。
 *
 * <p>统一组装流程动作上下文、解析参数，并调用已发布动作对应的 {@link FlowActionProvider}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowActionExecutor {

    private final ApplicationContext applicationContext;
    private final FlowActionRuntimeAdapter flowActionHelper;
    private final FlowActionExecutionService executionService;
    private final ObjectMapper objectMapper;

    /**
     * 执行单个流程动作（无既有执行记录）。
     *
     * @param action         动作配置
     * @param event          触发事件
     * @param idempotencyKey 幂等键
     * @return 流程动作执行上下文（含执行结果与步骤轨迹）
     */
    public FlowActionContext executeAction(FlowAction action, FlowActionTriggerEvent event, String idempotencyKey) {
        return executeAction(action, event, idempotencyKey, null);
    }

    /**
     * 执行单个流程动作并同步回写执行记录。
     *
     * @param action         动作配置
     * @param event          触发事件
     * @param idempotencyKey 幂等键
     * @param execution      既有执行记录；为 null 表示不持久化中间状态
     * @return 流程动作执行上下文（含执行结果与步骤轨迹）
     */
    public FlowActionContext executeAction(
            FlowAction action,
            FlowActionTriggerEvent event,
            String idempotencyKey,
            FlowActionExecution execution) {
        OperationContext operationContext = operationContext(
                action, event, idempotencyKey, execution);
        // 提交后动作运行在线程池中，ThreadLocal 不会自动继承。这里从已持久化
        // 的触发事件恢复作用域，使处理器发起的实体变更仍归属于原业务操作。
        try (OperationContextHolder.Scope ignored =
                     OperationContextHolder.open(operationContext)) {
            FlowActionContext context = buildContext(
                    action, event, idempotencyKey);
            if (execution != null) {
                executionService.markHandlerStarted(execution, context);
            }
            try {
                invoke(action, context);
                // 处理器未显式写入结果时，补充默认成功标记
                if (context.getExecutionResult() == null) {
                    context.setExecutionResult(Map.of(
                            "status", "SUCCESS",
                            "handlerReturnType", "void"));
                }
                context.addExecutionTrace(
                        "HANDLER_COMPLETED", "流程动作处理器执行完成");
                if (execution != null) {
                    executionService.captureContext(execution, context);
                }
                return context;
            } catch (RuntimeException error) {
                // 捕获失败上下文后重新抛出，交由上层决定是否回滚或重试
                context.addExecutionTrace(
                        "HANDLER_FAILED",
                        "流程动作处理器执行失败",
                        Map.of("error", error.getMessage() == null
                                ? error.getClass().getName()
                                : error.getMessage()));
                if (execution != null) {
                    executionService.captureContext(execution, context);
                }
                throw error;
            }
        }
    }

    /**
     * 从持久化触发事件恢复业务操作上下文。旧执行记录缺少 operationId 时，
     * 以动作和幂等键生成独立操作，绝不使用 traceId 猜测归并。
     *
     * @param action 动作标识，决定后续操作上下文采用的处理分支
     * @param event 事件，作为 {@code firstNonBlank} 的输入影响后续处理
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @param execution 执行，供本方法处理操作上下文时使用
     * @return 处理后的操作上下文结果，供调用方继续处理
     */
    private OperationContext operationContext(
            FlowAction action,
            FlowActionTriggerEvent event,
            String idempotencyKey,
            FlowActionExecution execution) {
        OperationContext inherited =
                OperationContextHolder.current().orElse(null);
        String operationId = firstNonBlank(
                event.getOperationId(),
                inherited == null ? null : inherited.operationId());
        if (!StringUtils.hasText(operationId)) {
            operationId = "flow_action_" + AuditEventIds.stable(
                    "flow-action-operation",
                    action.getId(),
                    idempotencyKey);
        }
        String traceId = firstNonBlank(
                event.getTraceId(),
                inherited == null ? null : inherited.traceId());
        String parentOperationId = event.getParentOperationId();
        if (!StringUtils.hasText(parentOperationId)
                && inherited != null
                && !operationId.equals(inherited.operationId())) {
            parentOperationId = inherited.operationId();
        }
        String sourceId = execution == null
                ? action.getId()
                : execution.getId();
        return new OperationContext(
                operationId,
                traceId,
                parentOperationId,
                new AuditSourcePointer(
                        "PROCESS_ACTION",
                        execution == null
                                ? "FLOW_ACTION"
                                : "FLOW_ACTION_EXECUTION",
                        sourceId,
                        idempotencyKey));
    }

    /**
     * 组装流程动作执行上下文：填充触发事件相关标识并解析业务参数。
     *
     * @param action         动作配置
     * @param event          触发事件
     * @param idempotencyKey 幂等键
     * @return 已填充字段的执行上下文
     */
    private FlowActionContext buildContext(
            FlowAction action,
            FlowActionTriggerEvent event,
            String idempotencyKey) {
        FlowActionContext ctx = new FlowActionContext();
        ctx.setRuntimeAccess(flowActionHelper);
        ctx.setActionId(action.getId());
        ctx.setActionName(action.getActionName());
        ctx.setProcessInstanceId(event.getProcessInstanceId());
        ctx.setProcessDefinitionId(event.getProcessDefinitionId());
        ctx.setEntityCode(event.getEntityCode());
        ctx.setEntityDataId(event.getEntityDataId());
        ctx.setSourceNodeId(defaultString(event.getSourceNodeId()));
        ctx.setSourceNodeName(defaultString(event.getSourceNodeName()));
        ctx.setTargetNodeId(defaultString(event.getTargetNodeId()));
        ctx.setTargetNodeName(defaultString(event.getTargetNodeName()));
        ctx.setTriggerTiming(event.getTriggerTiming());
        ctx.setScopeType(event.getScopeType());
        ctx.setElementId(event.getElementId());
        ctx.setElementName(event.getElementName());
        ctx.setElementType(event.getElementType());
        ctx.setExecutionId(event.getExecutionId());
        ctx.setTaskId(event.getTaskId());
        ctx.setTaskName(event.getTaskName());
        ctx.setTaskAssignee(event.getTaskAssignee());
        ctx.setOperatorId(event.getOperatorId());
        ctx.setApprovalAction(event.getApprovalAction());
        ctx.setEndReason(event.getEndReason());
        ctx.setIdempotencyKey(idempotencyKey);
        // 需要阻断主流程的处理器必须能复核执行方式与失败策略，
        // 避免把“前置校验”错配成提交后通知而实际未阻断主操作。
        ctx.setProcessVersionId(event.getVersionId());
        ctx.setExecutionMode(action.getExecutionMode());
        ctx.setFailurePolicy(action.getFailurePolicy());
        ctx.setVariablesSnapshot(event.getVariables());
        ctx.setExtraParams(
                resolveExtraParams(action.getParamsJson(), event.getVariables()));
        return ctx;
    }

    /**
     * 解析 paramsJson，仅支持以 ${变量名} 形式引用流程变量。
     *
     * @param paramsJson 参数 JSON 字符串
     * @param variables  流程变量集合
     * @return 解析后的参数 map；未配置参数时返回空 map
     * @throws IllegalArgumentException JSON 无法解析，或参数包含不支持的变量引用表达式
     */
    private Map<String, Object> resolveExtraParams(String paramsJson, Map<String, Object> variables) {
        Map<String, Object> params = new HashMap<>();
        if (!StringUtils.hasText(paramsJson)) {
            return params;
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(paramsJson, Map.class);
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                Object value = entry.getValue();
                // 仅允许精确变量引用。表达式求值会扩大为服务器代码执行能力。
                if (value instanceof String) {
                    String str = (String) value;
                    if (str.startsWith("${") && str.endsWith("}")) {
                        String variableName = str.substring(2, str.length() - 1);
                        if (!variableName.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
                            throw new IllegalArgumentException(
                                    "流程动作参数仅支持 ${变量名} 引用");
                        }
                        value = variables == null ? null : variables.get(variableName);
                    }
                }
                params.put(entry.getKey(), value);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("流程动作参数配置非法", e);
        }
        return params;
    }

    /**
     * 生成默认字符串文本，供后续匹配或展示。
     *
     * @param value 待处理默认字符串的原始输入，结果供调用方继续使用
     * @return 处理后的默认字符串文本，供调用方比较或展示
     */
    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    /**
     * 按候选顺序取首个非空白值，供后续处理使用。
     *
     * @param first 首个，供本方法处理首个非空白时使用
     * @param second {@code second}，供本方法处理首个非空白时使用
     * @return 处理后的首个非空白文本，供调用方比较或展示
     */
    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    /**
     * 从 Spring 容器查找并调用动作处理器 Bean。
     *
     * @param action 动作配置
     * @param ctx    执行上下文
     * @throws RuntimeException 处理器 Bean 不存在或未实现 FlowActionProvider 接口时抛出
     */
    private void invoke(FlowAction action, FlowActionContext ctx) {
        resolveHandler(action).execute(ctx);
    }

    /**
     * 判断可重试条件是否成立，供调用方选择后续分支。
     *
     * @param action 动作标识，决定后续可重试采用的处理分支
     * @return 可重试条件成立时为 true，否则为 false
     */
    public boolean retryable(FlowAction action) {
        return resolveHandler(action).retryable();
    }

    /**
     * 解析处理器；输出作为后续校验或处理的输入。
     *
     * @param action 动作标识，决定后续处理器采用的处理分支
     * @return 解析后的处理器结果，供调用方继续处理
     */
    private FlowActionProvider resolveHandler(FlowAction action) {
        String beanName = action.getInterfaceName();
        if (!StringUtils.hasText(beanName)) {
            throw new RuntimeException("流程动作未配置接口名称: " + action.getActionName());
        }

        Object bean;
        try {
            bean = applicationContext.getBean(beanName);
        } catch (Exception e) {
            throw new RuntimeException("未找到流程动作对应的 Bean: " + beanName, e);
        }

        if (!(bean instanceof FlowActionProvider handler)) {
            throw new RuntimeException("Bean '" + beanName + "' 未实现 FlowActionProvider 接口");
        }
        return handler;
    }
}
