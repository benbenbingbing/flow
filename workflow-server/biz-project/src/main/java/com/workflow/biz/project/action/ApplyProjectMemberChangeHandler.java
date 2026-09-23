package com.workflow.biz.project.action;

import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.spi.FlowActionHandler;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.biz.project.service.ProjectMemberChangeService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * F07 批准后生效成员、投入、权限和角色交接结果。
 */
@Component("applyProjectMemberChangeHandler")
public class ApplyProjectMemberChangeHandler
        implements FlowActionHandler {

    private final ProjectMemberChangeService service;

    /**
     * 初始化应用项目成员变更处理器，保存构造参数供后续方法使用。
     *
     * @param service 服务依赖，保存到当前对象供后续业务方法调用
     */
    public ApplyProjectMemberChangeHandler(
            ProjectMemberChangeService service) {
        this.service = service;
    }

    /**
     * 列出支持的触发条件时机集合；结果供调用方的后续步骤使用。
     *
     * @return 应用项目成员变更集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of("PROCESS_COMPLETED");
    }

    /**
     * 列出支持的执行模式集合；结果供调用方的后续步骤使用。
     *
     * @return 应用项目成员变更集合，供调用方遍历或展示
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
     * 判断可重试条件是否成立，供调用方选择后续分支。
     *
     * @return 可重试条件成立时为 true，否则为 false
     */
    @Override
    public boolean retryable() {
        return true;
    }

    /**
     * 执行应用项目成员变更，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续应用项目成员变更步骤传递身份、配置或状态
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Override
    public void execute(FlowActionContext context) {
        if (!"project_member_change_request".equals(
                context.getEntityCode())
                || !"approve".equals(String.valueOf(
                context.getVariable("approved")))) {
            context.addExecutionTrace(
                    "SKIPPED",
                    "Project member change was not approved.");
            return;
        }
        Object entityData = context.getEntityData();
        if (!(entityData instanceof EntityDataDTO request)) {
            throw new IllegalStateException(
                    "Project member change data is unavailable.");
        }
        Map<String, Object> result =
                service.applyChange(request, context);
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "MEMBER_CHANGE_APPLIED",
                "Applied member, access and role handover changes.",
                result);
    }
}
