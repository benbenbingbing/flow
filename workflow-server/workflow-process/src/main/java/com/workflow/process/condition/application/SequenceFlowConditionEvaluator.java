package com.workflow.process.condition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.condition.model.SequenceFlowConditionRequest;
import com.workflow.contracts.process.condition.spi.SequenceFlowConditionProvider;
import com.workflow.process.instance.application.WorkflowReservedVariables;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 发布校验和引擎执行共用的业务条件入口，不接受任意 Bean 名称或方法名。 */
@Component("sequenceFlowConditionEvaluator")
@RequiredArgsConstructor
public class SequenceFlowConditionEvaluator {
    private final List<SequenceFlowConditionProvider> providers;
    private final ObjectMapper objectMapper;

    /** 按稳定业务编码找唯一实现；实现缺失或编码冲突时阻断发布和办理。 */
    public SequenceFlowConditionProvider requireProvider(SequenceFlowConditionConfig config) {
        List<SequenceFlowConditionProvider> matches = providers.stream()
                .filter(provider -> provider.getCode().trim().toLowerCase(Locale.ROOT)
                        .equals(config.providerCode().toLowerCase(Locale.ROOT)))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("业务条件接口不存在或编码重复: " + config.providerCode());
        }
        SequenceFlowConditionProvider provider = matches.get(0);
        provider.validate(config.extraParams());
        return provider;
    }

    /**
     * 由已部署连线的固定表达式调用。配置读取已部署模型，避免运行中使用草稿改动。
     * 异常直接传播给引擎，由外层审批事务统一回滚。
     *
     * @param execution 引擎当前执行上下文，不由客户端传入
     * @param flowId 正在判断的顺序流 ID
     * @return 已注册业务接口返回的布尔值
     */
    public boolean evaluate(DelegateExecution execution, String flowId) {
        var model = org.flowable.engine.impl.util.ProcessDefinitionUtil.getBpmnModel(execution.getProcessDefinitionId());
        var element = model.getMainProcess().getFlowElement(flowId, true);
        if (!(element instanceof SequenceFlow flow)) {
            throw new IllegalArgumentException("业务条件连线不存在: " + flowId);
        }
        String json = property(flow);
        SequenceFlowConditionConfig config = SequenceFlowConditionConfig.parse(json, objectMapper);
        Map<String, Object> variables = new LinkedHashMap<>(execution.getVariables());
        // 平台安全快照不得泄露给业务扩展；审批动作等公开业务上下文仍保留。
        WorkflowReservedVariables.removeInternalVariables(variables);
        return requireProvider(config).evaluate(new SequenceFlowConditionRequest(
                execution.getProcessDefinitionId(), execution.getProcessInstanceId(), execution.getId(),
                flow.getId(), flow.getSourceRef(), flow.getTargetRef(),
                text(variables.get("entityCode")), text(variables.get("entityDataId")), variables, config.extraParams()));
    }

    /** 从 Flowable 已部署扩展属性中读取唯一配置，兼容设计器的 properties 容器。 */
    private String property(SequenceFlow flow) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        flow.getExtensionElements().values().forEach(elements -> elements.forEach(container -> {
            if ("properties".equals(container.getName())) {
                container.getChildElements().getOrDefault("property", List.of()).forEach(property -> {
                    if (SequenceFlowConditionConfig.PROPERTY.equals(property.getAttributeValue(null, "name"))) {
                        values.add(property.getAttributeValue(null, "value"));
                    }
                });
            }
        }));
        if (values.size() != 1) throw new IllegalArgumentException("业务条件接口配置缺失或重复: " + flow.getId());
        return values.get(0);
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value); }
}
