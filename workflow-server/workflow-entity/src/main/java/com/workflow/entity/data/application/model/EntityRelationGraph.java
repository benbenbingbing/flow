package com.workflow.entity.data.application.model;

import java.util.List;

/**
 * 权限过滤后的有界关系图结果。
 *
 * <p>只返回实体编码和记录 ID，不携带原始业务字段。目标表单/列表仍需通过
 * 自己的发布版本和字段权限读取内容，图解析本身不能成为绕过字段权限的接口。</p>
 */
public record EntityRelationGraph(
        List<Node> nodes,
        List<Edge> edges,
        List<RecordRef> terminalRecords,
        int depth,
        boolean truncated,
        String truncatedReason,
        long terminalTotal,
        long terminalPageNum,
        long terminalPageSize) {

    public EntityRelationGraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        terminalRecords = terminalRecords == null
                ? List.of() : List.copyOf(terminalRecords);
    }

    /** 实体记录的安全引用，不包含业务字段。 */
    public record RecordRef(String entityCode, String recordId) {
    }

    /** 图节点及其首次出现的深度。 */
    public record Node(RecordRef record, int depth) {
    }

    /** 一条由已发布路径步骤产生的边。 */
    public record Edge(
            int hop,
            String stepType,
            String stepCode,
            RecordRef source,
            RecordRef target) {
    }

    /**
     * 有界读取参数。中间层超出 maxRowsPerHop 会失败；只有最终层允许分页。
     */
    public record Limits(
            int maxRowsPerHop,
            int maxTotalNodes,
            int maxEdges,
            int maxPathStates,
            long terminalPageNum,
            long terminalPageSize) {

        public static Limits defaults() {
            return new Limits(200, 1000, 4000, 4000, 1, 50);
        }
    }
}
