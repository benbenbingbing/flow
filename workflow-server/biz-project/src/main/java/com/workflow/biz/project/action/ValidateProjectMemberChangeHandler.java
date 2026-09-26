package com.workflow.biz.project.action;

import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.spi.FlowActionProvider;
import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.biz.project.service.ProjectMemberChangeService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 在 F07 启动事务内执行成员、投入、角色、权限和交接门禁。
 */
@Component("validateProjectMemberChangeHandler")
public class ValidateProjectMemberChangeHandler
        implements FlowActionProvider {

    private final ProjectMemberChangeService service;

    /**
     * 初始化校验项目成员变更处理器，保存构造参数供后续方法使用。
     *
     * @param service 服务依赖，保存到当前对象供后续业务方法调用
     */
    public ValidateProjectMemberChangeHandler(
            ProjectMemberChangeService service) {
        this.service = service;
    }

    /**
     * 列出支持的触发条件时机集合；结果供调用方的后续步骤使用。
     *
     * @return 校验项目成员变更集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of("PROCESS_STARTED");
    }

    /**
     * 列出支持的执行模式集合；结果供调用方的后续步骤使用。
     *
     * @return 校验项目成员变更集合，供调用方遍历或展示
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
     * 执行校验项目成员变更，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续校验项目成员变更步骤传递身份、配置或状态
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Override
    public void execute(FlowActionContext context) {
        EntityRecordData request = context.getEntityData();
        if (request == null) {
            throw new IllegalStateException(
                    "Project member change data is unavailable.");
        }
        Map<String, Object> result =
                service.validateChange(request, context);
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "MEMBER_CHANGE_VALIDATED",
                "Validated member status, allocation, roles, access and handover.",
                result);
    }
}
