package com.workflow.project.custom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionExecutionMode;
import com.workflow.contracts.action.FlowActionTriggerTiming;
import com.workflow.contracts.action.TypedFlowActionHandler;
import com.workflow.core.logging.LogValue;
import com.workflow.project.service.ProjectEntityMutationExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * “项目扩展验收流程”的可见动作处理器。
 *
 * <p>同一个 Bean 可绑定在流程、节点和顺序流作用域。每次触发都会输出结构化
 * {@code log.info}、写入动作执行结果和执行轨迹；当参数 {@code writeBack=true}
 * 时，还会通过统一实体变更端口把阶段摘要写回验收实体的
 * {@code extension_result}、{@code backend_trace}、{@code last_action_scope}
 * 和 {@code last_action_timing} 字段。</p>
 */
@Slf4j
@Component("projectExtensionAcceptanceFlowActionHandler")
@RequiredArgsConstructor
public class ProjectExtensionAcceptanceFlowActionHandler
        implements TypedFlowActionHandler<
        ProjectExtensionAcceptanceFlowActionHandler.Parameters> {

    public static final String ENTITY_CODE =
            "project_extension_acceptance";

    private final ProjectEntityMutationExecutor mutationExecutor;

    @Override
    public Class<Parameters> getParamType() {
        return Parameters.class;
    }

    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of(
                FlowActionTriggerTiming.PROCESS_STARTED.name(),
                FlowActionTriggerTiming.PROCESS_COMPLETED.name(),
                FlowActionTriggerTiming.NODE_ENTERED.name(),
                FlowActionTriggerTiming.NODE_COMPLETED.name(),
                FlowActionTriggerTiming.TASK_CREATED.name(),
                FlowActionTriggerTiming.TASK_ASSIGNED.name(),
                FlowActionTriggerTiming.TASK_COMPLETING.name(),
                FlowActionTriggerTiming.TRANSITION_TAKEN.name(),
                ProjectCustomFlowActionTriggerProvider.TIMING);
    }

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
    public boolean retryable() {
        return true;
    }

    @Override
    public Map<String, Object> extraParamSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "stage", Map.of(
                                "type", "string",
                                "title", "验收阶段编码"),
                        "visibleMessage", Map.of(
                                "type", "string",
                                "title", "写回页面的说明"),
                        "writeBack", Map.of(
                                "type", "boolean",
                                "title", "是否写回验收实体",
                                "default", true)),
                "required", List.of("stage"));
    }

    @Override
    public void execute(
            FlowActionContext context,
            Parameters parameters) {
        String stage = text(
                parameters == null
                        ? null
                        : parameters.stage(),
                text(context.getTriggerTiming(),
                        "UNSPECIFIED"));
        String visibleMessage = text(
                parameters == null
                        ? null
                        : parameters.visibleMessage(),
                "后端流程扩展已执行: " + stage);
        boolean writeBack =
                parameters == null
                        || !Boolean.FALSE.equals(
                        parameters.writeBack());
        LocalDateTime executedAt =
                LocalDateTime.now();

        Map<String, Object> result =
                new LinkedHashMap<>();
        result.put("handledBy",
                getClass().getSimpleName());
        result.put("stage", stage);
        result.put("scopeType",
                context.getScopeType());
        result.put("triggerTiming",
                context.getTriggerTiming());
        result.put("elementId",
                context.getElementId());
        result.put("processInstanceId",
                context.getProcessInstanceId());
        result.put("entityCode",
                context.getEntityCode());
        result.put("entityDataId",
                context.getEntityDataId());
        result.put("writeBack", writeBack);
        result.put("executedAt", executedAt);

        log.info(
                "项目扩展验收流程动作开始: handler={}, actionId={}, actionName={}, stage={}, scopeType={}, triggerTiming={}, executionElementId={}, sequenceFlowId={}, sourceNodeId={}, targetNodeId={}, processInstanceId={}, entityCode={}, entityDataId={}, operatorId={}, writeBack={}",
                getClass().getSimpleName(),
                LogValue.safe(context.getActionId()),
                LogValue.safe(context.getActionName()),
                LogValue.safe(stage),
                LogValue.safe(context.getScopeType()),
                LogValue.safe(context.getTriggerTiming()),
                LogValue.safe(context.getElementId()),
                LogValue.safe(context.getSequenceFlowId()),
                LogValue.safe(context.getSourceNodeId()),
                LogValue.safe(context.getTargetNodeId()),
                LogValue.safe(
                        context.getProcessInstanceId()),
                LogValue.safe(context.getEntityCode()),
                LogValue.safe(context.getEntityDataId()),
                LogValue.safe(context.getOperatorId()),
                writeBack);

        if (writeBack
                && ENTITY_CODE.equals(
                        context.getEntityCode())
                && StringUtils.hasText(
                        context.getEntityDataId())) {
            Map<String, Object> patch =
                    new LinkedHashMap<>();
            patch.put("extension_result",
                    visibleMessage);
            patch.put("backend_trace",
                    stage + " | "
                            + text(context.getScopeType(),
                            "UNKNOWN")
                            + " | "
                            + text(
                            context.getTriggerTiming(),
                            "UNKNOWN")
                            + " | "
                            + executedAt);
            patch.put("last_action_scope",
                    context.getScopeType());
            patch.put("last_action_timing",
                    context.getTriggerTiming());
            patch.put("last_action_element",
                    firstText(
                            context.getElementId(),
                            context.getSequenceFlowId(),
                            context.getTaskId(),
                            "PROCESS"));
            mutationExecutor.inSession(
                    context,
                    "PROJECT_EXTENSION_ACCEPTANCE_TRACE",
                    "记录项目扩展验收流程动作",
                    () -> mutationExecutor.update(
                            ENTITY_CODE,
                            context.getEntityDataId(),
                            Map.of("data", patch)));
            result.put("writeBackFields",
                    List.copyOf(patch.keySet()));
            log.info(
                    "项目扩展验收流程动作写回完成: handler={}, stage={}, entityDataId={}, fieldKeys={}",
                    getClass().getSimpleName(),
                    LogValue.safe(stage),
                    LogValue.safe(
                            context.getEntityDataId()),
                    patch.keySet());
        } else {
            log.info(
                    "项目扩展验收流程动作仅记录日志: handler={}, stage={}, entityCode={}, entityDataId={}, writeBack={}, reason={}",
                    getClass().getSimpleName(),
                    LogValue.safe(stage),
                    LogValue.safe(context.getEntityCode()),
                    LogValue.safe(
                            context.getEntityDataId()),
                    writeBack,
                    LogValue.safe(writeBack
                            ? "ENTITY_NOT_ACCEPTANCE"
                            : "WRITE_BACK_DISABLED"));
        }

        context.setExecutionResult(result);
        context.addExecutionTrace(
                "PROJECT_EXTENSION_ACCEPTANCE_"
                        + stage,
                visibleMessage,
                result);
        log.info(
                "项目扩展验收流程动作结束: handler={}, stage={}, entityDataId={}, resultKeys={}, traceCount={}",
                getClass().getSimpleName(),
                LogValue.safe(stage),
                LogValue.safe(
                        context.getEntityDataId()),
                result.keySet(),
                context.getExecutionTrace().size());
    }

    private String firstText(
            String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String text(
            Object value,
            String fallback) {
        return value == null
                || String.valueOf(value).isBlank()
                ? fallback
                : String.valueOf(value);
    }

    /**
     * 项目扩展验收流程动作参数。
     *
     * @param stage 动作所属业务阶段；参与轨迹阶段名及写回摘要，空值时回退到
     *              当前触发时机
     * @param visibleMessage 展示给页面并写入 {@code extension_result} 的说明；
     *                       空值时根据 {@code stage} 生成默认说明
     * @param writeBack 是否把执行摘要写回验收实体；只有值严格为
     *                  {@link Boolean#FALSE} 时关闭，空值和其他值保持兼容并视为开启
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Parameters(
            String stage,
            String visibleMessage,
            Object writeBack) {
    }
}
