package com.workflow.biz.project.action;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.model.FlowActionExecutionMode;
import com.workflow.contracts.process.action.model.FlowActionTriggerTiming;
import com.workflow.contracts.process.action.spi.TypedFlowActionProvider;
import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.biz.project.service.ProjectMemberChangeService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 最终顺序流被选中时记录批准决策，验证 SEQUENCE_FLOW 自定义动作。
 */
@Component("recordProjectMemberDecisionHandler")
public class RecordProjectMemberDecisionHandler
        implements TypedFlowActionProvider<
        RecordProjectMemberDecisionHandler.Parameters> {

    private final ProjectMemberChangeService service;

    /**
     * 初始化记录项目成员决策处理器，保存构造参数供后续方法使用。
     *
     * @param service 服务依赖，保存到当前对象供后续业务方法调用
     */
    public RecordProjectMemberDecisionHandler(
            ProjectMemberChangeService service) {
        this.service = service;
    }

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
     * @return 记录项目成员决策集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of(
                FlowActionTriggerTiming.TRANSITION_TAKEN
                        .name());
    }

    /**
     * 列出支持的执行模式集合；结果供调用方的后续步骤使用。
     *
     * @return 记录项目成员决策集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of(
                FlowActionExecutionMode.IN_TRANSACTION
                        .name());
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
                        "decision", Map.of(
                                "type", "string",
                                "title", "决策编码")),
                "required", List.of("decision"));
    }

    /**
     * 执行记录项目成员决策，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续记录项目成员决策步骤传递身份、配置或状态
     * @param parameters 参数集合，作为 {@code service.recordDecision} 的输入影响后续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Override
    public void execute(
            FlowActionContext context,
            Parameters parameters) {
        EntityRecordData request = context.getEntityData();
        if (request == null) {
            throw new IllegalStateException(
                    "Project member change data is unavailable.");
        }
        Map<String, Object> result =
                service.recordDecision(
                        request,
                        context,
                        parameters == null
                                ? null
                                : parameters.decision());
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "FINAL_DECISION_RECORDED",
                "Recorded the selected final approval transition.",
                result);
    }

    /**
     * 项目成员变更决策动作参数。
     *
     * @param decision 被选中审批连线代表的业务决策编码；该值会连同连线、节点和
     *                 操作人信息写入成员变更申请的 {@code decision_trace}，
     *                 未提供时由业务服务记录为 {@code UNKNOWN}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Parameters(String decision) {
    }
}
