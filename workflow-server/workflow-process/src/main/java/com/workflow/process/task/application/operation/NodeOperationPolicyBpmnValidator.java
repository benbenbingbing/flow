package com.workflow.process.task.application.operation;

import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;

/**
 * 发布前校验 BPMN 中所有用户任务的节点操作配置。
 */
@Component
@RequiredArgsConstructor
public class NodeOperationPolicyBpmnValidator {

    private final NodeOperationPolicyParser policyParser;

    /**
     * 校验新三开关的布尔类型；存量矩阵仍按原规则校验并接受。
     *
     * @param bpmnXml 待发布 BPMN XML
     * @throws IllegalArgumentException BPMN 或节点操作配置不合法
     */
    public void validate(String bpmnXml) {
        try {
            XMLStreamReader reader = XMLInputFactory.newFactory()
                    .createXMLStreamReader(new StringReader(bpmnXml));
            BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(reader);
            for (org.flowable.bpmn.model.Process process : model.getProcesses()) {
                for (UserTask task : process.findFlowElementsOfType(UserTask.class, true)) {
                    try {
                        policyParser.parse(task);
                    } catch (RuntimeException exception) {
                        throw new IllegalArgumentException(
                                "节点操作配置无效 [elementId=" + task.getId() + "]: "
                                        + exception.getMessage(),
                                exception);
                    }
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析节点操作配置所在的 BPMN XML", exception);
        }
    }
}
