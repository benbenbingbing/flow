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

    /** 验收场景标识，写入动作执行结果并用于日志定位。 */
    private static final String SCENARIO = "scenario";

    /** 可选的日志说明；示例处理器只记录是否配置，不改变动作结果。 */
    private static final String MESSAGE = "message";

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
                        SCENARIO, Map.of(
                                "type", "string",
                                "title", "验证场景"),
                        MESSAGE, Map.of(
                                "type", "string",
                                "title", "日志说明")),
                "required", List.of(SCENARIO));
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
                LogValue.safe(params.get(SCENARIO)),
                params.containsKey(MESSAGE));
        context.setExecutionResult(Map.of(
                "handledBy", getClass().getSimpleName(),
                "scenario", String.valueOf(
                        params.getOrDefault(
                                SCENARIO,
                                "UNSPECIFIED"))));
    }
}
