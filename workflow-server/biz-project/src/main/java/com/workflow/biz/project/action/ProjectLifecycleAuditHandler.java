package com.workflow.biz.project.action;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.model.FlowActionExecutionMode;
import com.workflow.contracts.process.action.model.FlowActionTriggerTiming;
import com.workflow.contracts.process.action.spi.TypedFlowActionHandler;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 可被全部实体流程选择的项目生命周期审计动作。
 *
 * <p>该动作不修改业务实体，只将标准审计摘要写入动作执行结果和轨迹，
 * 用于验证 GLOBAL 动作目录、自定义参数和提交后执行。</p>
 */
@Component("projectLifecycleAuditHandler")
public class ProjectLifecycleAuditHandler
        implements TypedFlowActionHandler<
        ProjectLifecycleAuditHandler.Parameters> {

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
     * 列出支持的触发条件时机集合；结果供调用方的后续步骤使用。
     *
     * @return 项目生命周期审计集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of(
                FlowActionTriggerTiming.PROCESS_COMPLETED
                        .name());
    }

    /**
     * 列出支持的执行模式集合；结果供调用方的后续步骤使用。
     *
     * @return 项目生命周期审计集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of(
                FlowActionExecutionMode.AFTER_COMMIT.name());
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
                        "auditCode", Map.of(
                                "type", "string",
                                "title", "审计场景编码"),
                        "businessStage", Map.of(
                                "type", "string",
                                "title", "业务阶段")),
                "required", List.of(
                        "auditCode",
                        "businessStage"));
    }

    /**
     * 执行项目生命周期审计，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续项目生命周期审计步骤传递身份、配置或状态
     * @param parameters 参数集合，供本方法执行项目生命周期审计时使用
     */
    @Override
    public void execute(
            FlowActionContext context,
            Parameters parameters) {
        String auditCode = parameters == null
                || parameters.auditCode() == null
                ? "PROJECT_LIFECYCLE"
                : parameters.auditCode();
        String businessStage = parameters == null
                || parameters.businessStage() == null
                ? "UNKNOWN"
                : parameters.businessStage();
        Map<String, Object> result =
                new LinkedHashMap<>();
        result.put("auditCode", auditCode);
        result.put("businessStage", businessStage);
        result.put(
                "entityCode",
                context.getEntityCode());
        result.put(
                "entityDataId",
                context.getEntityDataId());
        result.put(
                "processInstanceId",
                context.getProcessInstanceId());
        result.put(
                "approved",
                context.getVariable("approved"));
        result.put(
                "operatorId",
                context.getOperatorId());
        result.put(
                "recordedAt",
                LocalDateTime.now());
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "PROJECT_LIFECYCLE_AUDITED",
                "Recorded a reusable project lifecycle audit summary.",
                result);
    }

    /**
     * 项目生命周期审计动作参数。
     *
     * @param auditCode 审计场景标识，写入执行结果，未提供时使用
     *                  {@code PROJECT_LIFECYCLE}
     * @param businessStage 当前业务阶段，写入执行结果，未提供时使用
     *                      {@code UNKNOWN}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Parameters(
            String auditCode,
            String businessStage) {
    }
}
