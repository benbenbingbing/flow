package com.workflow.biz.project.action;

import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.spi.FlowActionHandler;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.biz.project.service.ProjectGovernanceService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Applies an approved ADD, UPDATE or REMOVE operation to the governed relationship entity.
 */
@Component("applyProjectSystemChangeHandler")
public class ApplyProjectSystemChangeHandler implements FlowActionHandler {

    private final ProjectGovernanceService governanceService;

    /**
     * 初始化应用项目系统变更处理器，保存构造参数供后续方法使用。
     *
     * @param governanceService 治理服务依赖，保存到当前对象供后续业务方法调用
     */
    public ApplyProjectSystemChangeHandler(ProjectGovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    /**
     * 列出支持的触发条件时机集合；结果供调用方的后续步骤使用。
     *
     * @return 应用项目系统变更集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of("PROCESS_COMPLETED");
    }

    /**
     * 列出支持的执行模式集合；结果供调用方的后续步骤使用。
     *
     * @return 应用项目系统变更集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of("AFTER_COMMIT");
    }

    /**
     * 生成推荐执行模式文本，供后续匹配或展示。
     *
     * @return 处理后的推荐执行模式文本，供调用方比较或展示
     */
    @Override
    public String recommendedExecutionMode() {
        return "AFTER_COMMIT";
    }

    /**
     * 执行应用项目系统变更，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续应用项目系统变更步骤传递身份、配置或状态
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Override
    public void execute(FlowActionContext context) {
        if (!"project_system_change_request".equals(context.getEntityCode())
                || !"approve".equals(String.valueOf(context.getVariable("approved")))) {
            context.addExecutionTrace("SKIPPED", "Project-system change was not approved.");
            return;
        }
        Object entityData = context.getEntityData();
        if (!(entityData instanceof EntityDataDTO request)) {
            throw new IllegalStateException("Project-system change data is unavailable.");
        }
        Map<String, Object> result =
                governanceService.applyProjectSystemChange(
                        request,
                        context);
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "RELATIONSHIP_APPLIED",
                "Applied the approved project-system relationship change.",
                result);
    }
}
