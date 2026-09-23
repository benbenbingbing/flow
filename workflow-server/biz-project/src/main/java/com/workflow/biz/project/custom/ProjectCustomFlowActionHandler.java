package com.workflow.biz.project.custom;

import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.model.FlowActionExecutionMode;
import com.workflow.contracts.process.action.spi.FlowActionHandler;
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

    /**
     * 列出支持的执行模式集合；结果供调用方的后续步骤使用。
     *
     * @return 项目自定义流程动作集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of(
                FlowActionExecutionMode.IN_TRANSACTION.name(),
                FlowActionExecutionMode.AFTER_COMMIT.name());
    }

    /**
     * 生成推荐执行模式文本，供后续匹配或展示。
     *
     * @return 处理后的推荐执行模式文本，供调用方比较或展示
     */
    @Override
    public String recommendedExecutionMode() {
        return FlowActionExecutionMode.IN_TRANSACTION.name();
    }

    /**
     * 整理附加参数结构数据，供调用方遍历或继续处理。
     *
     * @return 附加参数结构键值结果，供调用方继续处理
     */
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

    /**
     * 执行项目自定义流程动作，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续项目自定义流程动作步骤传递身份、配置或状态
     */
    @Override
    public void execute(FlowActionContext context) {
        Map<String, Object> params = context.getExtraParams() == null
                ? Map.of() : context.getExtraParams();
        log.info(
                "项目自定义流程动作执行: actionId={}, actionName={}, scopeType={}, triggerTiming={}, processInstanceId={}, sourceNodeId={}, targetNodeId={}, elementId={}, entityCode={}, entityDataId={}, scenario={}, messagePresent={}",
                LogValue.safe(context.getActionId()),
                LogValue.safe(context.getActionName()),
                LogValue.safe(context.getScopeType()),
                LogValue.safe(context.getTriggerTiming()),
                LogValue.safe(context.getProcessInstanceId()),
                LogValue.safe(context.getSourceNodeId()),
                LogValue.safe(context.getTargetNodeId()),
                LogValue.safe(context.getElementId()),
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
