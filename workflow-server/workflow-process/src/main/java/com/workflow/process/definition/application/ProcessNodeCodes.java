package com.workflow.process.definition.application;

import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** 从设计 BPMN 读取供表单规则使用的节点编码。 */
public final class ProcessNodeCodes {
    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String FLOWABLE = "http://flowable.org/bpmn";
    private static final String PROPERTY_NAME = "nodeCode";
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final Set<String> ACTIVITY_TYPES = Set.of(
            "task", "subProcess", "adHocSubProcess", "transaction", "callActivity");

    private ProcessNodeCodes() { }

    /**
     * 按入口或待办所属发布版本取业务编码，适用于显式绑定和使用实体默认表单的节点。
     *
     * @param history 服务端依据当前入口定位的发布版本
     * @param nodeId BPMN 节点 ID，用于匹配固定版本的节点扩展属性
     * @return 已设置的编码，版本或节点缺失时返回 null
     */
    public static String forPublishedNode(ProcessVersionHistory history, String nodeId) {
        return history == null || nodeId == null ? null : read(history.getBpmnXml()).get(nodeId);
    }

    /**
     * 读取 BPMN 节点 ID 到业务编码的映射，供设计校验和表单运行时共用。
     * 空编码省略；格式错误、同一节点重复配置或流程内编码重复时抛出可定位节点的错误。
     *
     * @param bpmnXml 当前草稿或待发布的设计 BPMN
     * @return 已配置业务编码的节点映射
     */
    static Map<String, String> read(String bpmnXml) {
        if (bpmnXml == null || !bpmnXml.contains(PROPERTY_NAME)) return Map.of();
        Document document;
        try {
            document = ProcessBpmnSynchronousExecutionNormalizer.parse(bpmnXml);
        } catch (Exception exception) {
            throw new IllegalArgumentException("NODE_CODE_BPMN_INVALID: 无法读取 BPMN 节点编码", exception);
        }

        Map<String, String> codesByNodeId = new HashMap<>();
        Map<String, String> nodeIdsByCode = new HashMap<>();
        var properties = document.getElementsByTagNameNS(FLOWABLE, "property");
        for (int index = 0; index < properties.getLength(); index++) {
            Element property = (Element) properties.item(index);
            if (!PROPERTY_NAME.equals(property.getAttribute("name"))) continue;
            String nodeId = ownerNodeId(property);
            String code = property.getAttribute("value").trim();
            if (nodeId.isEmpty() || code.isEmpty()) continue;
            if (!CODE_PATTERN.matcher(code).matches()) {
                throw new IllegalArgumentException("NODE_CODE_INVALID: 节点编码须以英文字母开头，仅包含英文字母、数字和下划线，最长 64 个字符"
                        + "，节点 " + nodeId + " element=" + nodeId);
            }
            if (codesByNodeId.putIfAbsent(nodeId, code) != null) {
                throw new IllegalArgumentException("NODE_CODE_INVALID: 节点 " + nodeId + " 配置了多个编码 element=" + nodeId);
            }
            String firstNodeId = nodeIdsByCode.putIfAbsent(code, nodeId);
            if (firstNodeId != null) {
                throw new IllegalArgumentException("NODE_CODE_DUPLICATE: 节点编码「" + code + "」已由节点 "
                        + firstNodeId + " 使用 element=" + nodeId);
            }
        }
        return Map.copyOf(codesByNodeId);
    }

    /** 只读取节点直接声明的扩展，避免把子流程内部节点的编码归到外层节点。 */
    private static String ownerNodeId(Element property) {
        if (!(property.getParentNode() instanceof Element container)
                || !FLOWABLE.equals(container.getNamespaceURI()) || !"properties".equals(container.getLocalName())
                || !(container.getParentNode() instanceof Element extensions)
                || !BPMN.equals(extensions.getNamespaceURI()) || !"extensionElements".equals(extensions.getLocalName())
                || !(extensions.getParentNode() instanceof Element owner) || !BPMN.equals(owner.getNamespaceURI())) {
            return "";
        }
        String type = owner.getLocalName();
        return ACTIVITY_TYPES.contains(type) || type.endsWith("Task") || type.endsWith("Event") || type.endsWith("Gateway")
                ? owner.getAttribute("id") : "";
    }
}
