package com.workflow.process.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.FlowAction;
import com.workflow.service.FlowActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程动作执行器。
 *
 * <p>根据版本 ID 和顺序流 ID 查询已发布的流程动作，并依次调用对应的 {@link FlowActionHandler}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowActionExecutor {

    private final FlowActionService flowActionService;
    private final ApplicationContext applicationContext;
    private final FlowActionHelper flowActionHelper;
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
                FlowActionContext ctx = buildContext(action, execution);
                invoke(action, ctx);
            } catch (Exception e) {
                log.error("[FlowActionExecutor] 执行流程动作失败: actionId={}, actionName={}", action.getId(), action.getActionName(), e);
                throw new RuntimeException("执行流程动作失败: " + action.getActionName(), e);
            }
        }
    }

    private FlowActionContext buildContext(FlowAction action, DelegateExecution execution) {
        FlowActionContext ctx = new FlowActionContext();
        ctx.setHelper(flowActionHelper);
        ctx.setActionId(action.getId());
        ctx.setActionName(action.getActionName());
        ctx.setProcessInstanceId(execution.getProcessInstanceId());
        ctx.setEntityCode((String) execution.getVariable("entityCode"));
        ctx.setEntityDataId((String) execution.getVariable("entityDataId"));
        ctx.setSequenceFlowId(action.getSequenceFlowId());
        ctx.setSourceNodeId(defaultString((String) execution.getVariable("_flowActionSourceNodeId_")));
        ctx.setSourceNodeName(defaultString((String) execution.getVariable("_flowActionSourceNodeName_")));
        ctx.setTargetNodeId(defaultString((String) execution.getVariable("_flowActionTargetNodeId_")));
        ctx.setTargetNodeName(defaultString((String) execution.getVariable("_flowActionTargetNodeName_")));
        ctx.setCustomParams(resolveCustomParams(action.getParamsJson(), execution));
        return ctx;
    }

    private Map<String, Object> resolveCustomParams(String paramsJson, DelegateExecution execution) {
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
                        StandardEvaluationContext evalCtx = new StandardEvaluationContext();
                        evalCtx.setVariables(execution.getVariables());
                        value = spelParser.parseExpression(expr).getValue(evalCtx);
                    }
                }
                params.put(entry.getKey(), value);
            }
        } catch (Exception e) {
            log.warn("解析 paramsJson 失败: {}", paramsJson, e);
        }
        return params;
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
