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

@Component("ccNotificationDelegate")
@RequiredArgsConstructor
public class CcNotificationDelegate implements JavaDelegate {
    private final ProcessCcRuntimeService runtimeService;
    private final ProcessCcConfigService configService;
    private final RepositoryService repositoryService;

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
