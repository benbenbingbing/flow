package com.workflow.biz.project.action;

import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.spi.FlowActionHandler;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.biz.project.service.ProjectGovernanceService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Blocks process start when project cross-entity prerequisites are not satisfied.
 */
@Component("validateProjectInitiationHandler")
public class ValidateProjectInitiationHandler implements FlowActionHandler {

    private final ProjectGovernanceService governanceService;

    /**
     * 初始化校验项目发起处理器，保存构造参数供后续方法使用。
     *
     * @param governanceService 治理服务依赖，保存到当前对象供后续业务方法调用
     */
    public ValidateProjectInitiationHandler(ProjectGovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    /**
     * 列出支持的触发条件时机集合；结果供调用方的后续步骤使用。
     *
     * @return 校验项目发起集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of("PROCESS_STARTED");
    }

    /**
     * 列出支持的执行模式集合；结果供调用方的后续步骤使用。
     *
     * @return 校验项目发起集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of("IN_TRANSACTION");
    }

    /**
     * 生成推荐执行模式文本，供后续匹配或展示。
     *
     * @return 处理后的推荐执行模式文本，供调用方比较或展示
     */
    @Override
    public String recommendedExecutionMode() {
        return "IN_TRANSACTION";
    }

    /**
     * 执行校验项目发起，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续校验项目发起步骤传递身份、配置或状态
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Override
    public void execute(FlowActionContext context) {
        Object entityData = context.getEntityData();
        if (!(entityData instanceof EntityDataDTO project)) {
            throw new IllegalStateException("Project initiation data is unavailable.");
        }
        Map<String, Object> result =
                governanceService.validateProjectInitiation(
                        project,
                        context);
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "CROSS_ENTITY_VALIDATED",
                "Validated requirement allocation, system status and key project data.",
                result);
    }
}
