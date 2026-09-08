package com.workflow.process.definition.application;

import java.util.Collection;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ReceiveTask;
import org.flowable.bpmn.model.ScriptTask;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;
import org.springframework.util.StringUtils;

/** 历史部署启用 skipExpression 前的只读安全检查。 */
public final class DeployedSkipExpressionSafety {

    private DeployedSkipExpressionSafety() {
    }

    /**
     * 返回首个不符合当前发布白名单的 skipExpression 节点 ID。
     *
     * <p>启动开关是进程级变量，因此必须检查部署中的全部受支持任务，而非只检查
     * 当前用户任务；否则历史服务任务上的表达式也会被意外激活。返回 null 表示安全。</p>
     *
     * @param model 不可变的已部署 BPMN 模型
     * @return 首个不安全节点 ID；模型不可用时返回 {@code <deployment-model>}
     */
    public static String firstUnsafeElementId(BpmnModel model) {
        if (model == null || model.getProcesses() == null
                || model.getProcesses().isEmpty()) {
            return "<deployment-model>";
        }
        for (org.flowable.bpmn.model.Process process
                : model.getProcesses()) {
            String unsafe = firstUnsafeElementId(
                    process.getFlowElements());
            if (unsafe != null) {
                return unsafe;
            }
        }
        return null;
    }

    private static String firstUnsafeElementId(
            Collection<FlowElement> elements) {
        if (elements == null) {
            return null;
        }
        for (FlowElement element : elements) {
            String expression = skipExpression(element);
            if (StringUtils.hasText(expression)
                    && !BpmnExecutableContentValidator
                            .isSafeDataExpression(expression.trim())) {
                return StringUtils.hasText(element.getId())
                        ? element.getId() : "<unknown>";
            }
            if (element instanceof SubProcess subProcess) {
                String unsafe = firstUnsafeElementId(
                        subProcess.getFlowElements());
                if (unsafe != null) {
                    return unsafe;
                }
            }
        }
        return null;
    }

    private static String skipExpression(FlowElement element) {
        if (element instanceof UserTask task) {
            return task.getSkipExpression();
        }
        if (element instanceof ServiceTask task) {
            return task.getSkipExpression();
        }
        if (element instanceof ScriptTask task) {
            return task.getSkipExpression();
        }
        if (element instanceof ReceiveTask task) {
            return task.getSkipExpression();
        }
        return null;
    }
}
