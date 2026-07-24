package com.workflow.delegate;

import com.workflow.service.ProcessCcRuntimeService;
import com.workflow.service.cc.CcRuntimeContext;
import com.workflow.service.cc.ProcessCcConfigService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;

/**
 * 抄送通知 JavaDelegate
 * 作为 BPMN 服务任务的委托实现，在节点执行时按节点配置显式触发抄送通知。
 */
@Component("ccNotificationDelegate")
@RequiredArgsConstructor
public class CcNotificationDelegate implements JavaDelegate {
    /** 抄送运行时服务，触发抄送 */
    private final ProcessCcRuntimeService runtimeService;
    /** 抄送配置服务，查询节点抄送配置 */
    private final ProcessCcConfigService configService;
    /** Flowable 仓库服务，查询流程定义 */
    private final RepositoryService repositoryService;

    /**
     * 节点执行回调：查询当前节点的抄送配置，存在则组装上下文显式触发抄送。
     *
     * @param execution Flowable 执行上下文
     */
    @Override
    public void execute(DelegateExecution execution) {
        String config = configService.findConfig(
                execution.getProcessDefinitionId(),
                execution.getCurrentActivityId());
        if (config == null) {
            return;
        }
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(execution.getProcessDefinitionId())
                .singleResult();
        runtimeService.trigger(new CcRuntimeContext(
                execution.getProcessInstanceId(),
                execution.getProcessDefinitionId(),
                definition == null ? null : definition.getKey(),
                definition == null ? null : definition.getName(),
                execution.getProcessInstanceBusinessKey(),
                execution.getCurrentActivityId(),
                execution.getCurrentActivityName(),
                "EXPLICIT",
                null,
                execution.getVariables()), config);
    }
}
