package com.workflow.biz.project.action;

import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.spi.FlowActionHandler;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.biz.project.service.ProjectMemberChangeService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 项目经理节点完成后写入业务检查点，验证 NODE 级自定义动作。
 */
@Component("captureProjectMemberManagerReviewHandler")
public class CaptureProjectMemberManagerReviewHandler
        implements FlowActionHandler {

    private final ProjectMemberChangeService service;

    /**
     * 初始化捕获项目成员{@code manager}{@code review}处理器，保存构造参数供后续方法使用。
     *
     * @param service 服务依赖，保存到当前对象供后续业务方法调用
     */
    public CaptureProjectMemberManagerReviewHandler(
            ProjectMemberChangeService service) {
        this.service = service;
    }

    /**
     * 列出支持的触发条件时机集合；结果供调用方的后续步骤使用。
     *
     * @return 捕获项目成员{@code manager}{@code review}集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of("NODE_COMPLETED");
    }

    /**
     * 列出支持的执行模式集合；结果供调用方的后续步骤使用。
     *
     * @return 捕获项目成员{@code manager}{@code review}集合，供调用方遍历或展示
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
     * 执行捕获项目成员{@code manager}{@code review}，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续捕获项目成员{@code manager}{@code review}步骤传递身份、配置或状态
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Override
    public void execute(FlowActionContext context) {
        Object entityData = context.getEntityData();
        if (!(entityData instanceof EntityDataDTO request)) {
            throw new IllegalStateException(
                    "Project member change data is unavailable.");
        }
        Map<String, Object> result =
                service.captureManagerReview(
                        request, context);
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "MANAGER_REVIEW_CAPTURED",
                "Captured the project manager review checkpoint.",
                result);
    }
}
