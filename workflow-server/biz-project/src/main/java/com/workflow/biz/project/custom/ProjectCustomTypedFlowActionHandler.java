package com.workflow.biz.project.custom;

import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.model.FlowActionExecutionMode;
import com.workflow.contracts.process.action.spi.TypedFlowActionProvider;
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
        implements TypedFlowActionProvider<
        ProjectCustomTypedFlowActionHandler.Parameters> {

    /**
     * 读取参数类型；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的{@code class<parameters>}结果，供调用方继续处理
     */
    @Override
    public Class<Parameters> getParamType() {
        return Parameters.class;
    }

    /**
     * 列出支持的执行模式集合；结果供调用方的后续步骤使用。
     *
     * @return 项目自定义{@code typed}流程动作集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of(FlowActionExecutionMode.AFTER_COMMIT.name());
    }

    /**
     * 生成推荐执行模式文本，供后续匹配或展示。
     *
     * @return 处理后的推荐执行模式文本，供调用方比较或展示
     */
    @Override
    public String recommendedExecutionMode() {
        return FlowActionExecutionMode.AFTER_COMMIT.name();
    }

    /**
     * 判断可重试条件是否成立，供调用方选择后续分支。
     *
     * @return 可重试条件成立时为 true，否则为 false
     */
    @Override
    public boolean retryable() {
        return true;
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

    /**
     * 执行项目自定义{@code typed}流程动作，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续项目自定义{@code typed}流程动作步骤传递身份、配置或状态
     * @param parameters 参数集合，供本方法执行项目自定义{@code typed}流程动作时使用
     */
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
     *
     * @param message 消息，保存在对象中供后续校验、查询或展示
     * @param priority 优先级，保存在对象中供后续校验、查询或展示
     * @param dryRun {@code dry}{@code run}，保存在对象中供后续校验、查询或展示
     */
    public record Parameters(
            String message,
            Integer priority,
            Boolean dryRun) {
    }
}
