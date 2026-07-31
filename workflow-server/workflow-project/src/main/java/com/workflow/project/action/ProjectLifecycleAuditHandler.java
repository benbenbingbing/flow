package com.workflow.project.action;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionHandler;
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
        implements FlowActionHandler {

    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of("PROCESS_COMPLETED");
    }

    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of("AFTER_COMMIT");
    }

    @Override
    public String recommendedExecutionMode() {
        return "AFTER_COMMIT";
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
    public void execute(FlowActionContext context) {
        Map<String, Object> params =
                context.getExtraParams() == null
                        ? Map.of()
                        : context.getExtraParams();
        Map<String, Object> result =
                new LinkedHashMap<>();
        result.put(
                "auditCode",
                params.getOrDefault(
                        "auditCode",
                        "PROJECT_LIFECYCLE"));
        result.put(
                "businessStage",
                params.getOrDefault(
                        "businessStage",
                        "UNKNOWN"));
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
}
