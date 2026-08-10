package com.workflow.project.custom;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionExecutionMode;
import com.workflow.contracts.action.FlowActionHandler;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 无类型参数的流程动作示例。
 *
 * <p>前端在流程动作中选择 Bean
 * {@code projectCustomFlowActionHandler} 后，运行时会记录动作、流程、
 * 节点和实体定位信息。适合验证流程级、节点级及连线级动作扩展。</p>
 */
@Slf4j
@Component("projectCustomFlowActionHandler")
public class ProjectCustomFlowActionHandler
        implements FlowActionHandler {

    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of(
                FlowActionExecutionMode.IN_TRANSACTION.name(),
                FlowActionExecutionMode.AFTER_COMMIT.name());
    }

    @Override
    public String recommendedExecutionMode() {
        return FlowActionExecutionMode.IN_TRANSACTION.name();
    }

    @Override
    public Map<String, Object> extraParamSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "scenario", Map.of(
                                "type", "string",
                                "title", "验证场景"),
                        "message", Map.of(
                                "type", "string",
                                "title", "日志说明")),
                "required", List.of("scenario"));
    }

    @Override
    public void execute(FlowActionContext context) {
        Map<String, Object> params = context.getExtraParams() == null
                ? Map.of() : context.getExtraParams();
        log.info(
                "项目自定义流程动作执行: actionId={}, actionName={}, scopeType={}, triggerTiming={}, processInstanceId={}, sourceNodeId={}, targetNodeId={}, sequenceFlowId={}, entityCode={}, entityDataId={}, scenario={}, messagePresent={}",
                LogValue.safe(context.getActionId()),
                LogValue.safe(context.getActionName()),
                LogValue.safe(context.getScopeType()),
                LogValue.safe(context.getTriggerTiming()),
                LogValue.safe(context.getProcessInstanceId()),
                LogValue.safe(context.getSourceNodeId()),
                LogValue.safe(context.getTargetNodeId()),
                LogValue.safe(context.getSequenceFlowId()),
                LogValue.safe(context.getEntityCode()),
                LogValue.safe(context.getEntityDataId()),
                LogValue.safe(params.get("scenario")),
                params.containsKey("message"));
        context.setExecutionResult(Map.of(
                "handledBy", getClass().getSimpleName(),
                "scenario", String.valueOf(
                        params.getOrDefault("scenario", "UNSPECIFIED"))));
    }
}
