package com.workflow.process.status.application;

import com.workflow.process.task.application.operation.NodeOperationConfig;
import com.workflow.process.task.application.operation.NodeOperationConfigReader;
import com.workflow.process.task.application.operation.NodeOperationPolicy;
import com.workflow.process.task.application.operation.NodeOperationPolicyParser;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.springframework.stereotype.Component;
import javax.xml.stream.XMLInputFactory;
import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.Set;

/** 发布和实体状态配置共用的操作依赖分析；条件表达式不会被当作永久关闭开关。 */
@Component
@RequiredArgsConstructor
public class ProcessCancellationRequirements {
    private final NodeOperationConfigReader configReader;
    private final NodeOperationPolicyParser policyParser;

    /** 返回流程可能启用的特殊操作类别，供保存/发布时要求唯一实体目标状态。 */
    public Set<String> requiredCategories(String xml) {
        if (xml == null || xml.isBlank()) return Set.of();
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            var reader = factory.createXMLStreamReader(new StringReader(xml));
            try {
                return requiredCategories(new BpmnXMLConverter().convertToBpmnModel(reader));
            } finally {
                reader.close();
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法确认流程终止/撤回配置，请先修复流程定义", exception);
        }
    }

    /** 从实际部署版本分析，以免新草稿关闭操作后破坏仍在运行的旧实例。 */
    public Set<String> requiredCategories(BpmnModel model) {
        Set<String> result = new LinkedHashSet<>();
        if (model == null) throw new IllegalArgumentException("流程模型不存在");
        for (var process : model.getProcesses()) {
            for (UserTask task : process.findFlowElementsOfType(UserTask.class, true)) {
                var config = configReader.read(task).orElseGet(NodeOperationConfig::allowAll);
                var policy = policyParser.parse(task);
                if (config.allowTerminate() && policy.rule(NodeOperationPolicy.Operation.TERMINATE).enabled()) result.add("TERMINATED");
                if (config.allowWithdraw() && policy.rule(NodeOperationPolicy.Operation.WITHDRAW).enabled()) result.add("WITHDRAWN");
            }
        }
        return Set.copyOf(result);
    }
}
