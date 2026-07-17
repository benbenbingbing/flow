package com.workflow.process.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.FlowAction;
import com.workflow.entity.FlowActionExecution;
import com.workflow.service.FlowActionService;
import com.workflow.service.FlowActionExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程动作执行器。
 *
 * <p>统一组装流程动作上下文、解析参数，并调用已发布动作对应的 {@link FlowActionHandler}。
 * 历史顺序流监听器入口继续通过兼容方法复用该执行器。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowActionExecutor {

    private final FlowActionService flowActionService;
    private final ApplicationContext applicationContext;
    private final FlowActionHelper flowActionHelper;
    private final FlowActionExecutionService executionService;
    private final ObjectMapper objectMapper;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    /**
     * 执行指定顺序流上的所有启用动作。
     *
     * @param versionId      流程发布版本 ID
     * @param sequenceFlowId 顺序流 ID
     * @param execution      Flowable 执行上下文
     */
    public void executeActions(String versionId, String sequenceFlowId, DelegateExecution execution) {
        log.info("[FlowActionExecutor] 开始执行顺序流动作, versionId={}, sequenceFlowId={}", versionId, sequenceFlowId);
        List<FlowAction> actions = flowActionService.findPublishedActionsBySequenceFlow(versionId, sequenceFlowId);
        log.info("[FlowActionExecutor] 查询到 {} 个已发布动作", actions == null ? 0 : actions.size());
        if (actions == null || actions.isEmpty()) {
            return;
        }
        for (FlowAction action : actions) {
            if (!Boolean.TRUE.equals(action.getEnabled())) {
                continue;
            }
            try {
                FlowActionTriggerEvent event = fromLegacyExecution(versionId, sequenceFlowId, execution);
                executeAction(action, event, java.util.UUID.randomUUID().toString());
            } catch (Exception e) {
                log.error("[FlowActionExecutor] 执行流程动作失败: actionId={}, actionName={}", action.getId(), action.getActionName(), e);
                throw new RuntimeException("执行流程动作失败: " + action.getActionName(), e);
            }
        }
    }

    public FlowActionContext executeAction(FlowAction action, FlowActionTriggerEvent event, String idempotencyKey) {
        return executeAction(action, event, idempotencyKey, null);
    }

    public FlowActionContext executeAction(
            FlowAction action,
            FlowActionTriggerEvent event,
            String idempotencyKey,
            FlowActionExecution execution) {
        FlowActionContext context = buildContext(action, event, idempotencyKey);
        if (execution != null) {
            executionService.markHandlerStarted(execution, context);
        }
        try {
            invoke(action, context);
            if (context.getExecutionResult() == null) {
                context.setExecutionResult(Map.of(
                        "status", "SUCCESS",
                        "handlerReturnType", "void"));
            }
            context.addExecutionTrace("HANDLER_COMPLETED", "流程动作处理器执行完成");
            if (execution != null) {
                executionService.captureContext(execution, context);
            }
            return context;
        } catch (RuntimeException error) {
            context.addExecutionTrace(
                    "HANDLER_FAILED",
                    "流程动作处理器执行失败",
                    Map.of("error", error.getMessage() == null ? error.getClass().getName() : error.getMessage()));
            if (execution != null) {
                executionService.captureContext(execution, context);
            }
            throw error;
        }
    }

    private FlowActionContext buildContext(
            FlowAction action,
            FlowActionTriggerEvent event,
            String idempotencyKey) {
        FlowActionContext ctx = new FlowActionContext();
        ctx.setHelper(flowActionHelper);
        ctx.setActionId(action.getId());
        ctx.setActionName(action.getActionName());
        ctx.setProcessInstanceId(event.getProcessInstanceId());
        ctx.setProcessDefinitionId(event.getProcessDefinitionId());
        ctx.setEntityCode(event.getEntityCode());
        ctx.setEntityDataId(event.getEntityDataId());
        ctx.setSequenceFlowId(action.getSequenceFlowId());
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
        ctx.setVariablesSnapshot(event.getVariables());
        ctx.setCustomParams(resolveCustomParams(action.getParamsJson(), event.getVariables()));
        return ctx;
    }

    private Map<String, Object> resolveCustomParams(String paramsJson, Map<String, Object> variables) {
        Map<String, Object> params = new HashMap<>();
        if (!StringUtils.hasText(paramsJson)) {
            return params;
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(paramsJson, Map.class);
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    String str = (String) value;
                    if (str.startsWith("${") && str.endsWith("}")) {
                        String expr = str.substring(2, str.length() - 1);
                        if (variables != null && variables.containsKey(expr)) {
                            value = variables.get(expr);
                        } else {
                            StandardEvaluationContext evalCtx = new StandardEvaluationContext(
                                    variables == null ? Map.of() : variables);
                            evalCtx.setVariables(variables == null ? Map.of() : variables);
                            value = spelParser.parseExpression(expr).getValue(evalCtx);
                        }
                    }
                }
                params.put(entry.getKey(), value);
            }
        } catch (Exception e) {
            log.warn("解析 paramsJson 失败: {}", paramsJson, e);
        }
        return params;
    }

    private FlowActionTriggerEvent fromLegacyExecution(
            String versionId,
            String sequenceFlowId,
            DelegateExecution execution) {
        FlowActionTriggerEvent event = new FlowActionTriggerEvent();
        event.setVersionId(versionId);
        event.setProcessDefinitionId(execution.getProcessDefinitionId());
        event.setProcessInstanceId(execution.getProcessInstanceId());
        event.setExecutionId(execution.getId());
        event.setScopeType(FlowActionScopeType.SEQUENCE_FLOW.name());
        event.setElementId(sequenceFlowId);
        event.setTriggerTiming(FlowActionTriggerTiming.TRANSITION_TAKEN.name());
        event.setEntityCode((String) execution.getVariable("entityCode"));
        event.setEntityDataId((String) execution.getVariable("entityDataId"));
        event.setSourceNodeId(defaultString((String) execution.getVariable("_flowActionSourceNodeId_")));
        event.setSourceNodeName(defaultString((String) execution.getVariable("_flowActionSourceNodeName_")));
        event.setTargetNodeId(defaultString((String) execution.getVariable("_flowActionTargetNodeId_")));
        event.setTargetNodeName(defaultString((String) execution.getVariable("_flowActionTargetNodeName_")));
        event.setVariables(new LinkedHashMap<>(execution.getVariables()));
        return event;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private void invoke(FlowAction action, FlowActionContext ctx) {
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

        if (!(bean instanceof FlowActionHandler)) {
            throw new RuntimeException("Bean '" + beanName + "' 未实现 FlowActionHandler 接口");
        }

        ((FlowActionHandler) bean).execute(ctx);
    }
}
