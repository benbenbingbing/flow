package com.workflow.process.definition.application;

import org.w3c.dom.*;
import java.util.*;

/** 发布时检查并发写状态；互斥网关允许不同结果，汇合后的顺序状态也允许不同。 */
final class EntityTransitionStateValidator {
    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    /**
     * 封装边的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param target 目标，保存在对象中供后续校验、查询或展示
     * @param status 状态标识，决定后续边采用的处理分支
     */
    private record Edge(String target, String status) { }
    /**
     * 初始化实体{@code transition}状态校验器，保存构造参数供后续方法使用。
     */
    private EntityTransitionStateValidator() { }

    /**
     * 校验实体{@code transition}状态；不满足约束时阻止后续处理。
     *
     * @param document 文档，供本方法校验实体{@code transition}状态时使用
     */
    static void validate(Document document) {
        Map<String, Element> nodes = new HashMap<>();
        Map<String, List<Edge>> outgoing = new HashMap<>();
        Map<String, Integer> incoming = new HashMap<>();
        NodeList all = document.getElementsByTagNameNS(BPMN, "*");
        for (int i = 0; i < all.getLength(); i++) {
            Element element = (Element) all.item(i);
            nodes.put(element.getAttribute("id"), element);
            if ("sequenceFlow".equals(element.getLocalName())) {
                String target = element.getAttribute("targetRef");
                outgoing.computeIfAbsent(element.getAttribute("sourceRef"), k -> new ArrayList<>())
                        .add(new Edge(target, status(element)));
                incoming.merge(target, 1, Integer::sum);
            }
        }
        for (var entry : outgoing.entrySet()) {
            Element split = nodes.get(entry.getKey());
            if (entry.getValue().size() < 2 || split == null
                    || Set.of("exclusiveGateway", "eventBasedGateway").contains(split.getLocalName())) continue;
            // 连到所有分支共同可达的同步网关后，状态写入已回到顺序区域。
            // 不能遇到任意 join 就停，否则嵌套并行中会漏检外层竞争。
            Set<String> common = null;
            for (Edge edge : entry.getValue()) {
                Set<String> reachable = reachable(edge.target(), outgoing, Set.of());
                if (common == null) common = reachable; else common.retainAll(reachable);
            }
            Set<String> joins = new HashSet<>();
            if (common != null) for (String id : common) {
                Element node = nodes.get(id);
                if (node != null && incoming.getOrDefault(id, 0) > 1
                        && Set.of("parallelGateway", "inclusiveGateway").contains(node.getLocalName())) joins.add(id);
            }
            List<Set<String>> branchStates = new ArrayList<>();
            for (Edge edge : entry.getValue()) {
                Set<String> states = new HashSet<>();
                if (!edge.status().isBlank()) states.add(edge.status());
                for (String id : reachable(edge.target(), outgoing, joins)) {
                    if (joins.contains(id)) continue;
                    Element node = nodes.get(id);
                    if (node != null && "subProcess".equals(node.getLocalName())) {
                        NodeList innerFlows = node.getElementsByTagNameNS(BPMN, "sequenceFlow");
                        for (int inner = 0; inner < innerFlows.getLength(); inner++) {
                            String value = status((Element) innerFlows.item(inner));
                            if (!value.isBlank()) states.add(value);
                        }
                    }
                    for (Edge downstream : outgoing.getOrDefault(id, List.of())) {
                        if (!downstream.status().isBlank()) states.add(downstream.status());
                    }
                }
                branchStates.add(states);
            }
            for (int i = 0; i < branchStates.size(); i++) for (int j = i + 1; j < branchStates.size(); j++) {
                for (String left : branchStates.get(i)) for (String right : branchStates.get(j)) {
                    if (!left.equals(right)) throw new IllegalArgumentException(
                            "并行分支业务状态冲突: " + entry.getKey() + "，请把不同状态回写移至汇合后的连线");
                }
            }
        }
    }

    /**
     * 有环流程也只访问一次节点；终点和同步网关作为遍历边界。
     *
     * @param start 启动，作为 {@code queue.add} 的输入影响后续处理
     * @param graph 图，供本方法处理{@code reachable}时使用
     * @param stops {@code stops}，供本方法处理{@code reachable}时使用
     * @return 实体{@code transition}状态校验器集合，供调用方遍历或展示
     */
    private static Set<String> reachable(String start, Map<String, List<Edge>> graph, Set<String> stops) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!visited.add(id) || stops.contains(id)) continue;
            for (Edge edge : graph.getOrDefault(id, List.of())) queue.addLast(edge.target());
        }
        return visited;
    }

    /**
     * 生成状态文本，供后续匹配或展示。
     *
     * @param flow 流程，供本方法处理状态时使用
     * @return 处理后的状态文本，供调用方比较或展示
     */
    private static String status(Element flow) {
        NodeList properties = flow.getElementsByTagNameNS("http://flowable.org/bpmn", "property");
        for (int i = 0; i < properties.getLength(); i++) {
            Element property = (Element) properties.item(i);
            if ("entityStatusCode".equals(property.getAttribute("name"))) return property.getAttribute("value").trim();
        }
        return "";
    }
}
