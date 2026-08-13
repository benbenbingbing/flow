package com.workflow.project.action;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionExecutionMode;
import com.workflow.contracts.action.FlowActionTriggerTiming;
import com.workflow.contracts.action.TypedFlowActionHandler;
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

    @Override
    public Class<Parameters> getParamType() {
        return Parameters.class;
    }

    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of(
                FlowActionTriggerTiming.PROCESS_COMPLETED
                        .name());
    }

    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of(
                FlowActionExecutionMode.AFTER_COMMIT.name());
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
