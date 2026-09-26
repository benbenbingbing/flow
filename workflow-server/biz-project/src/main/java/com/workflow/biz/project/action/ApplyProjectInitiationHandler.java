package com.workflow.biz.project.action;

import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.spi.FlowActionProvider;
import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.biz.project.service.ProjectGovernanceService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Creates governed members and role assignments and activates initial links after approval.
 */
@Component("applyProjectInitiationHandler")
public class ApplyProjectInitiationHandler implements FlowActionProvider {

    private final ProjectGovernanceService governanceService;

    /**
     * 初始化应用项目发起处理器，保存构造参数供后续方法使用。
     *
     * @param governanceService 治理服务依赖，保存到当前对象供后续业务方法调用
     */
    public ApplyProjectInitiationHandler(ProjectGovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    /**
     * 列出支持的触发条件时机集合；结果供调用方的后续步骤使用。
     *
     * @return 应用项目发起集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of("PROCESS_COMPLETED");
    }

    /**
     * 列出支持的执行模式集合；结果供调用方的后续步骤使用。
     *
     * @return 应用项目发起集合，供调用方遍历或展示
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
     * 执行应用项目发起，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续应用项目发起步骤传递身份、配置或状态
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Override
    public void execute(FlowActionContext context) {
        if (!"project".equals(context.getEntityCode())
                || !"approve".equals(String.valueOf(context.getVariable("approved")))) {
            context.addExecutionTrace("SKIPPED", "Project initiation was not approved.");
            return;
        }
        EntityRecordData project = context.getEntityData();
        if (project == null) {
            throw new IllegalStateException("Project initiation data is unavailable.");
        }
        Map<String, Object> result =
                governanceService.applyProjectInitiation(
                        project,
                        context);
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "PROJECT_INITIALIZED",
                "Activated initial links and created governed members and roles.",
                result);
    }
}
