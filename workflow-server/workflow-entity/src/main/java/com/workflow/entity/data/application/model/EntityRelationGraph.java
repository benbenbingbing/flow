package com.workflow.entity.data.application.model;

import java.util.List;

/**
 * 权限过滤后的有界关系图结果。
 *
 * <p>只返回实体编码和记录 ID，不携带原始业务字段。目标表单/列表仍需通过
 * 自己的发布版本和字段权限读取内容，图解析本身不能成为绕过字段权限的接口。</p>
 *
 * @param nodes 节点集合，保存在对象中供后续校验、查询或展示
 * @param edges {@code edges}，保存在对象中供后续校验、查询或展示
 * @param terminalRecords 终态记录集合，保存在对象中供后续校验、查询或展示
 * @param depth 深度，保存在对象中供后续校验、查询或展示
 * @param truncated {@code truncated}，保存在对象中供后续校验、查询或展示
 * @param truncatedReason {@code truncated}原因，保存在对象中供后续校验、查询或展示
 * @param terminalTotal 终态总数，保存在对象中供后续校验、查询或展示
 * @param terminalPageNum 终态分页数量，保存在对象中供后续校验、查询或展示
 * @param terminalPageSize 终态分页大小，保存在对象中供后续校验、查询或展示
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

    /**
     * 初始化实体关系图，保存构造参数供后续方法使用。
     *
     * @param nodes 节点集合，保存在对象中供后续校验、查询或展示
     * @param edges {@code edges}，保存在对象中供后续校验、查询或展示
     * @param terminalRecords 终态记录集合，保存在对象中供后续校验、查询或展示
     * @param depth 深度，保存在对象中供后续校验、查询或展示
     * @param truncated {@code truncated}，保存在对象中供后续校验、查询或展示
     * @param truncatedReason {@code truncated}原因，保存在对象中供后续校验、查询或展示
     * @param terminalTotal 终态总数，保存在对象中供后续校验、查询或展示
     * @param terminalPageNum 终态分页数量，保存在对象中供后续校验、查询或展示
     * @param terminalPageSize 终态分页大小，保存在对象中供后续校验、查询或展示
     */
    public EntityRelationGraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        terminalRecords = terminalRecords == null
                ? List.of() : List.copyOf(terminalRecords);
    }

    /**
     * 实体记录的安全引用，不包含业务字段。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    public record RecordRef(String entityCode, String recordId) {
    }

    /**
     * 图节点及其首次出现的深度。
     *
     * @param record 记录，保存在对象中供后续校验、查询或展示
     * @param depth 深度，保存在对象中供后续校验、查询或展示
     */
    public record Node(RecordRef record, int depth) {
    }

    /**
     * 一条由已发布路径步骤产生的边。
     *
     * @param hop 跳，保存在对象中供后续校验、查询或展示
     * @param stepType 步骤类型标识，决定后续边采用的处理分支
     * @param stepCode 步骤编码，后续用于处理边时定位或关联目标
     * @param source 待处理边的原始输入，结果供调用方继续使用
     * @param target 目标，保存在对象中供后续校验、查询或展示
     */
    public record Edge(
            int hop,
            String stepType,
            String stepCode,
            RecordRef source,
            RecordRef target) {
    }

    /**
     * 有界读取参数。中间层超出 maxRowsPerHop 会失败；只有最终层允许分页。
     *
     * @param maxRowsPerHop 最大行每跳，保存在对象中供后续校验、查询或展示
     * @param maxTotalNodes 最大总数节点集合，保存在对象中供后续校验、查询或展示
     * @param maxEdges 最大{@code edges}，保存在对象中供后续校验、查询或展示
     * @param maxPathStates 最大路径{@code states}，保存在对象中供后续校验、查询或展示
     * @param terminalPageNum 终态分页数量，保存在对象中供后续校验、查询或展示
     * @param terminalPageSize 终态分页大小，保存在对象中供后续校验、查询或展示
     */
    public record Limits(
            int maxRowsPerHop,
            int maxTotalNodes,
            int maxEdges,
            int maxPathStates,
            long terminalPageNum,
            long terminalPageSize) {

        /**
         * 处理{@code defaults}，并将结果传给后续步骤。
         *
         * @return 处理后的{@code defaults}结果，供调用方继续处理
         */
        public static Limits defaults() {
            return new Limits(200, 1000, 4000, 4000, 1, 50);
        }
    }
}
