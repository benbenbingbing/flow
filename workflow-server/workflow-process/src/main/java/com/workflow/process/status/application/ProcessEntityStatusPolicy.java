package com.workflow.process.status.application;

import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Component;

/** 实例状态规则绑定部署版本，编辑草稿或再次发布不能改变已有实例。 */
@Component
@RequiredArgsConstructor
public class ProcessEntityStatusPolicy {
    public static final String PROPERTY = "entityStatusPolicy";
    public static final String TRANSITION = "TRANSITION_V1";
    private final RepositoryService repositoryService;

    /**
     * 新部署显式标记连线模式；无标记的存量部署继续兼容原来的起止回写。
     *
     * @param definitionId 定义ID，后续用于处理使用{@code transitions}时定位或关联目标
     * @return 使用{@code transitions}条件成立时为 true，否则为 false
     */
    public boolean usesTransitions(String definitionId) {
        BpmnModel model = repositoryService.getBpmnModel(definitionId);
        if (model == null || model.getMainProcess() == null) {
            throw new IllegalStateException("流程部署模型缺失: " + definitionId);
        }
        return TRANSITION.equals(property(model.getMainProcess(), PROPERTY));
    }

    /**
     * 对账必须保留 BPMN 特殊结束类型，不能仅凭空删除原因把终止结束当成正常结束。
     *
     * @param historic 历史，作为 {@code repositoryService.getBpmnModel} 的输入影响后续处理
     * @return 处理后的结束类别文本，供调用方比较或展示
     */
    public String endCategory(org.flowable.engine.history.HistoricProcessInstance historic) {
        String reason = historic.getDeleteReason();
        if (reason != null && reason.contains("撤回")) return "WITHDRAWN";
        if (reason != null && !reason.isBlank()) return "TERMINATED";
        BpmnModel model = repositoryService.getBpmnModel(historic.getProcessDefinitionId());
        var end = model == null || historic.getEndActivityId() == null ? null
                : model.getFlowElement(historic.getEndActivityId());
        if (end instanceof org.flowable.bpmn.model.EndEvent event && event.getEventDefinitions().stream()
                .anyMatch(definition -> definition instanceof org.flowable.bpmn.model.TerminateEventDefinition
                        || definition instanceof org.flowable.bpmn.model.ErrorEventDefinition
                        || definition instanceof org.flowable.bpmn.model.EscalationEventDefinition)) return "TERMINATED";
        return "COMPLETED";
    }

    /**
     * 读取当前元素自己的属性，不能误读子流程/子节点上的同名配置。
     *
     * @param element 元素，供本方法处理属性时使用
     * @param name 名称，后续用于处理属性时匹配或展示
     * @return 处理后的属性文本，供调用方比较或展示
     */
    public static String property(BaseElement element, String name) {
        if (element == null) return null;
        for (ExtensionElement properties : element.getExtensionElements()
                .getOrDefault("properties", java.util.List.of())) {
            for (ExtensionElement property : properties.getChildElements()
                    .getOrDefault("property", java.util.List.of())) {
                if (name.equals(property.getAttributeValue(null, "name"))) {
                    return property.getAttributeValue(null, "value");
                }
            }
        }
        return null;
    }
}
