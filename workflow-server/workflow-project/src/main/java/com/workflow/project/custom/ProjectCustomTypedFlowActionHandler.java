package com.workflow.project.custom;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionExecutionMode;
import com.workflow.contracts.action.TypedFlowActionHandler;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 类型化流程动作示例。
 *
 * <p>平台会把前端保存的 extraParams 转换成 {@link Parameters}，
 * 用于验证带 Java 参数模型的动作扩展。</p>
 */
@Slf4j
@Component("projectCustomTypedFlowActionHandler")
public class ProjectCustomTypedFlowActionHandler
        implements TypedFlowActionHandler<
        ProjectCustomTypedFlowActionHandler.Parameters> {

    @Override
    public Class<Parameters> getParamType() {
        return Parameters.class;
    }

    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of(FlowActionExecutionMode.AFTER_COMMIT.name());
    }

    @Override
    public String recommendedExecutionMode() {
        return FlowActionExecutionMode.AFTER_COMMIT.name();
    }

    @Override
    public boolean retryable() {
        return true;
    }

    @Override
    public Map<String, Object> extraParamSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "message", Map.of(
                                "type", "string",
                                "title", "处理说明"),
                        "priority", Map.of(
                                "type", "integer",
                                "title", "优先级"),
                        "dryRun", Map.of(
                                "type", "boolean",
                                "title", "仅验证不产生副作用")),
                "required", List.of("message"));
    }

    @Override
    public void execute(
            FlowActionContext context,
            Parameters parameters) {
        log.info(
                "项目类型化流程动作执行: processInstanceId={}, entityCode={}, entityDataId={}, priority={}, dryRun={}, messagePresent={}, idempotencyKey={}",
                LogValue.safe(context.getProcessInstanceId()),
                LogValue.safe(context.getEntityCode()),
                LogValue.safe(context.getEntityDataId()),
                parameters == null ? null : parameters.priority(),
                parameters == null ? null : parameters.dryRun(),
                parameters != null
                        && parameters.message() != null
                        && !parameters.message().isBlank(),
                LogValue.safe(context.getIdempotencyKey()));
        context.setExecutionResult(Map.of(
                "handledBy", getClass().getSimpleName(),
                "dryRun", parameters != null
                        && Boolean.TRUE.equals(parameters.dryRun())));
    }

    /**
     * 前端动作参数对应的 Java 类型。
     */
    public record Parameters(
            String message,
            Integer priority,
            Boolean dryRun) {
    }
}
