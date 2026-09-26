package com.workflow.entity.data.application;

import com.workflow.entity.data.application.port.EntityRelationProjectionReadPort;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.list.model.DataScopePlan;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationPlan.Grant;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationPlan.AccessMode;
import com.workflow.entity.data.application.port.EntityRelationProjectionReadPort.PredicateType;
import com.workflow.entity.data.application.port.EntityRelationProjectionReadPort.ProjectionPage;
import com.workflow.entity.data.application.port.EntityRelationProjectionReadPort.ProjectionQuery;
import com.workflow.entity.data.application.port.EntityRelationProjectionReadPort.ProjectionRow;
import com.workflow.entity.data.application.model.EntityRelationGraph;
import com.workflow.entity.data.application.model.EntityRelationGraph.Edge;
import com.workflow.entity.data.application.model.EntityRelationGraph.Limits;
import com.workflow.entity.data.application.model.EntityRelationGraph.Node;
import com.workflow.entity.data.application.model.EntityRelationGraph.RecordRef;
import com.workflow.entity.definition.application.PublishedRelationPathResolver;
import com.workflow.entity.definition.application.model.PublishedRelationPath;
import com.workflow.entity.definition.application.model.PublishedRelationPath.Hop;
import com.workflow.entity.definition.application.model.PublishedRelationPath.LinkField;
import com.workflow.entity.definition.application.model.PublishedRelationPath.LinkValueType;
import com.workflow.entity.definition.application.model.PublishedRelationPath.StepType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按精确发布路径和服务端逐跳授权计划批量读取跨实体关系图。
 *
 * <p>遍历只通过 {@link EntityRelationProjectionReadPort} 读取记录 ID 与钉定
 * 链接字段。服务不接受 listKey、权限 SQL 或完整业务数据，也不会读取当前
 * 字段定义。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityRelationGraphReadService {

    private static final int HARD_MAX_ROWS_PER_HOP = 200;
    private static final int HARD_MAX_TOTAL_NODES = 2000;
    private static final int HARD_MAX_EDGES = 10000;
    private static final int HARD_MAX_PATH_STATES = 10000;

    private final PublishedRelationPathResolver pathResolver;
    private final EntityRelationProjectionReadPort projectionPort;

    /**
     * 执行有界关系图遍历。
     *
     * <p>中间层必须完整加载，最终层允许分页；ONE_TO_ONE 会先完整读取本层
     * 并核验每个来源最多一个目标，再应用最终页。所有节点、边和 lineage
     * 状态均在分配前检查硬预算。</p>
     *
     * @param path          发布时编译并固定的关系路径
     * @param sourceRecordIds 来源记录 ID
     * @param requestedLimits 图读取预算
     * @param authorization 只能由服务端授权服务签发的逐跳计划
     * @return 读取后的实体关系图读取结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public EntityRelationGraph read(
            PublishedRelationPath path,
            Collection<String> sourceRecordIds,
            Limits requestedLimits,
            EntityRelationGraphAuthorizationPlan authorization) {
        PublishedRelationPath verified = pathResolver.validate(path);
        List<Grant> grants = validateAuthorization(
                verified, authorization);
        Limits limits = normalizeLimits(requestedLimits);
        List<String> sourceIds = normalizeIds(
                sourceRecordIds,
                limits.maxRowsPerHop(),
                "来源记录数量超过单层上限 "
                        + limits.maxRowsPerHop());
        if (sourceIds.isEmpty()) {
            throw new IllegalArgumentException("关系图来源记录不能为空");
        }
        if (sourceIds.size() > limits.maxRowsPerHop()) {
            throw limit("来源记录数量超过单层上限 "
                    + limits.maxRowsPerHop());
        }

        List<TraversalRow> currentRows = requireAccessibleSources(
                verified,
                sourceIds,
                grants.get(0).dataScopePlan(),
                projectedFieldsAtDepth(verified, 0),
                limits);
        Map<RecordRef, Node> nodes = new LinkedHashMap<>();
        List<Edge> edges = new ArrayList<>();
        Map<RecordRef, List<Set<RecordRef>>> ancestry =
                new LinkedHashMap<>();
        if (currentRows.size() > limits.maxPathStates()) {
            throw limit("来源路径状态超过上限 "
                    + limits.maxPathStates());
        }
        for (TraversalRow row : currentRows) {
            RecordRef ref = ref(verified.sourceEntityCode(), row.id());
            putNodeWithinBudget(nodes, ref, 0, limits);
            ancestry.put(ref, List.of(new LinkedHashSet<>(Set.of(ref))));
        }

        boolean truncated = false;
        String truncatedReason = null;
        long terminalTotal = currentRows.size();
        long terminalPageNum = 1;
        long terminalPageSize = currentRows.size();
        MutableCounter pathStateCount = new MutableCounter(currentRows.size());
        for (int index = 0; index < verified.hops().size(); index++) {
            Hop hop = verified.hops().get(index);
            boolean terminal = index == verified.hops().size() - 1;
            HopRows loaded = loadHop(
                    verified,
                    index,
                    hop,
                    currentRows,
                    terminal,
                    grants.get(index + 1).dataScopePlan(),
                    limits,
                    edges.size());
            terminalTotal = loaded.total();
            terminalPageNum = loaded.pageNum();
            terminalPageSize = loaded.pageSize();
            if (!terminal && loaded.total() > limits.maxRowsPerHop()) {
                throw limit("关系路径第 " + hop.index()
                        + " 层记录数 " + loaded.total()
                        + " 超过完整遍历上限 " + limits.maxRowsPerHop());
            }
            if (terminal && (loaded.pageNum() > 1
                    || loaded.total() > loaded.rows().size())) {
                truncated = true;
                truncatedReason = "最终层记录较多，仅返回第 "
                        + loaded.pageNum() + " 页";
            }

            Map<String, TraversalRow> targetsById = indexById(loaded.rows());
            Map<RecordRef, List<Set<RecordRef>>> nextAncestry =
                    new LinkedHashMap<>();
            Set<RecordRef> cardinalitySources = new LinkedHashSet<>();
            Map<RecordRef, List<Set<RecordRef>>> currentAncestry = ancestry;
            EdgeConsumer consumer = (source, target) -> {
                if (!hop.multiple() && !cardinalitySources.add(source)) {
                    throw cardinality(hop, source);
                }
                if (edges.size() >= limits.maxEdges()) {
                    throw limit("关系图边数量超过上限 "
                            + limits.maxEdges());
                }
                List<Set<RecordRef>> parentPaths = currentAncestry.get(source);
                if (parentPaths == null || parentPaths.isEmpty()) {
                    return;
                }
                for (Set<RecordRef> parentPath : parentPaths) {
                    if (parentPath.contains(target)) {
                        throw new BusinessConflictException(
                                "ENTITY_RELATION_GRAPH_CYCLE",
                                "关联路径在第 " + hop.index()
                                        + " 层形成记录循环: "
                                        + target.recordId());
                    }
                    if (pathStateCount.value() >= limits.maxPathStates()) {
                        throw limit("关系图可达路径数量超过上限 "
                                + limits.maxPathStates());
                    }
                    // 先检查预算，再复制 lineage，避免稠密图在发现超限前
                    // 已经分配大量 Set。
                    Set<RecordRef> childPath = new LinkedHashSet<>(parentPath);
                    childPath.add(target);
                    nextAncestry.computeIfAbsent(
                                    target, ignored -> new ArrayList<>())
                            .add(childPath);
                    pathStateCount.increment();
                }
                edges.add(edge(hop, source, target));
                putNodeWithinBudget(nodes, target, hop.index(), limits);
            };
            emitEdges(hop, currentRows, targetsById, consumer);
            currentRows = loaded.rows().stream()
                    .filter(row -> nextAncestry.containsKey(ref(
                            hop.targetEntityCode(), row.id())))
                    .toList();
            ancestry = nextAncestry;
        }

        List<RecordRef> terminalRecords = currentRows.stream()
                .map(row -> ref(row.entityCode(), row.id()))
                .distinct()
                .toList();
        List<Node> orderedNodes = nodes.values().stream()
                .sorted(Comparator.comparingInt(Node::depth)
                        .thenComparing(node -> node.record().entityCode())
                        .thenComparing(node -> node.record().recordId()))
                .toList();
        return new EntityRelationGraph(
                orderedNodes,
                edges,
                terminalRecords,
                verified.hops().size(),
                truncated,
                truncatedReason,
                terminalTotal,
                terminalPageNum,
                terminalPageSize);
    }

    /**
     * 校验授权；不满足约束时阻止后续处理。
     *
     * @param path 路径，供本方法校验授权时使用
     * @param authorization 授权，作为 {@code validateGrant} 的输入影响后续处理
     * @return 授权集合，供调用方遍历或展示
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    private List<Grant> validateAuthorization(
            PublishedRelationPath path,
            EntityRelationGraphAuthorizationPlan authorization) {
        if (!(authorization
                instanceof IssuedEntityRelationGraphAuthorizationPlan)) {
            throw new ForbiddenException("关系图读取缺少服务端可信授权计划");
        }
        if (!StringUtils.hasText(UserContext.getUserId())
                || !UserContext.getUserId().equals(
                        authorization.subjectUserId())) {
            throw new ForbiddenException("关系图授权计划不属于当前用户");
        }
        if (authorization.purpose() == null
                || authorization.source() == null
                || authorization.hops() == null
                || authorization.hops().size() != path.hops().size()) {
            throw new ForbiddenException("关系图授权计划结构无效");
        }
        List<Grant> result = new ArrayList<>();
        validateGrant(
                authorization.source(),
                0,
                path.sourceEntityCode(),
                path.sourceHistoryId(),
                path.sourceSchemaHash());
        result.add(authorization.source());
        for (int index = 0; index < path.hops().size(); index++) {
            Hop hop = path.hops().get(index);
            Grant grant = authorization.hops().get(index);
            validateGrant(
                    grant,
                    hop.index(),
                    hop.targetEntityCode(),
                    hop.targetHistoryId(),
                    hop.targetSchemaHash());
            result.add(grant);
        }
        return result;
    }

    /**
     * 校验授权；不满足约束时阻止后续处理。
     *
     * @param grant 授权，作为 {@code equals} 的输入影响后续处理
     * @param expectedIndex 预期索引，供本方法校验授权时使用
     * @param expectedEntityCode 预期实体编码，后续用于校验授权时定位或关联目标
     * @param expectedHistoryId 预期历史ID，后续用于校验授权时定位或关联目标
     * @param expectedSchemaHash 预期结构哈希，供本方法校验授权时使用
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    private void validateGrant(
            Grant grant,
            int expectedIndex,
            String expectedEntityCode,
            String expectedHistoryId,
            String expectedSchemaHash) {
        if (grant == null
                || grant.hopIndex() != expectedIndex
                || !same(grant.entityCode(), expectedEntityCode)
                || !same(grant.entityHistoryId(), expectedHistoryId)
                || !same(grant.entitySchemaHash(), expectedSchemaHash)
                || grant.accessMode() != AccessMode.INTERNAL_SAFE
                || !EntityRelationGraphAuthorizationService.INTERNAL_SCOPE_KEY
                        .equals(grant.listKey())
                || StringUtils.hasText(grant.publishedIdentity())) {
            throw new ForbiddenException("关系图逐跳授权与钉定发布路径不一致");
        }
        DataScopePlan scope = grant.dataScopePlan();
        if (scope == null || !StringUtils.hasText(scope.sqlFragment())
                || (scope.requiredJoins() != null
                        && !scope.requiredJoins().isEmpty())
                || (!scope.allowed()
                        && !"1=0".equals(scope.sqlFragment().trim()))) {
            throw new ForbiddenException("关系图逐跳数据范围计划无效");
        }
    }

    /**
     * 加载跳；查询结果供调用方展示或继续处理。
     *
     * @param path 路径，作为 {@code projectedFieldsAtDepth} 的输入影响后续处理
     * @param hopOffset 跳偏移，作为 {@code projectedFieldsAtDepth} 的输入影响后续处理
     * @param hop 跳，作为 {@code collectLinkIds} 的输入影响后续处理
     * @param sources {@code sources}，作为 {@code collectLinkIds} 的输入影响后续处理
     * @param terminal 终态，供本方法加载跳时使用
     * @param scope 作用域，供本方法加载跳时使用
     * @param limits 限制集合，作为 {@code remainingMultiBudget} 的输入影响后续处理
     * @param allocatedEdges {@code allocated}{@code edges}，作为 {@code remainingMultiBudget} 的输入影响后续处理
     * @return 符合条件的跳行结果，供调用方继续处理
     */
    private HopRows loadHop(
            PublishedRelationPath path,
            int hopOffset,
            Hop hop,
            List<TraversalRow> sources,
            boolean terminal,
            DataScopePlan scope,
            Limits limits,
            int allocatedEdges) {
        if (sources.isEmpty()) {
            return new HopRows(List.of(), 0,
                    terminal ? limits.terminalPageNum() : 1,
                    terminal ? limits.terminalPageSize()
                            : limits.maxRowsPerHop());
        }
        PredicateType predicateType;
        LinkField predicateField = null;
        List<String> predicateValues;
        if (hop.type() == StepType.REFERENCE_FIELD) {
            predicateType = PredicateType.ID_IN;
            predicateValues = collectLinkIds(
                    sources,
                    hop.sourceLinkField(),
                    remainingMultiBudget(limits, allocatedEdges));
        } else {
            predicateType = PredicateType.LINK_IN;
            predicateField = requiredLink(hop.targetLinkField(), hop);
            predicateValues = sources.stream()
                    .map(TraversalRow::id)
                    .distinct()
                    .toList();
        }
        if (predicateValues.isEmpty()) {
            return new HopRows(List.of(), 0,
                    terminal ? limits.terminalPageNum() : 1,
                    terminal ? limits.terminalPageSize()
                            : limits.maxRowsPerHop());
        }
        List<LinkField> projected = projectedFieldsAtDepth(
                path, hopOffset + 1);

        // ONE_TO_ONE 必须先完整读取本层才能验证每个来源的真实基数，不能只
        // 检查用户请求的最终页。
        boolean completeForCardinality = !hop.multiple();
        long pageNum = terminal && !completeForCardinality
                ? limits.terminalPageNum() : 1;
        long pageSize = terminal && !completeForCardinality
                ? limits.terminalPageSize() : limits.maxRowsPerHop();
        ProjectionPage page = projectionPort.readPage(new ProjectionQuery(
                hop.targetEntityCode(),
                projected,
                predicateType,
                predicateField,
                predicateValues,
                scope,
                pageNum,
                pageSize,
                remainingMultiBudget(limits, allocatedEdges)));
        List<TraversalRow> rows = traversalRows(
                hop.targetEntityCode(), page.rows());
        if (completeForCardinality) {
            if (page.total() > limits.maxRowsPerHop()) {
                throw limit("ONE_TO_ONE 关系返回记录数超过完整校验上限");
            }
            validateOneToOne(hop, sources, rows);
            if (terminal) {
                rows = page(rows,
                        limits.terminalPageNum(),
                        limits.terminalPageSize());
                return new HopRows(
                        rows,
                        page.total(),
                        limits.terminalPageNum(),
                        limits.terminalPageSize());
            }
        }
        return new HopRows(
                rows,
                page.total(),
                page.pageNum(),
                page.pageSize());
    }

    /**
     * 校验{@code one}截止{@code one}；不满足约束时阻止后续处理。
     *
     * @param hop 跳，作为 {@code emitEdges} 的输入影响后续处理
     * @param sources {@code sources}，作为 {@code emitEdges} 的输入影响后续处理
     * @param targets 目标集合，作为 {@code indexById} 的输入影响后续处理
     */
    private void validateOneToOne(
            Hop hop,
            List<TraversalRow> sources,
            List<TraversalRow> targets) {
        Map<String, TraversalRow> targetsById = indexById(targets);
        Set<RecordRef> seen = new LinkedHashSet<>();
        emitEdges(hop, sources, targetsById, (source, target) -> {
            if (!seen.add(source)) {
                throw cardinality(hop, source);
            }
        });
    }

    /**
     * 处理{@code emit}{@code edges}，并将结果传给后续步骤。
     *
     * @param hop 跳，作为 {@code ref} 的输入影响后续处理
     * @param sources {@code sources}，供本方法处理{@code emit}{@code edges}时使用
     * @param targetsById 目标集合ID，后续用于处理{@code emit}{@code edges}时定位或关联目标
     * @param consumer {@code consumer}，供本方法处理{@code emit}{@code edges}时使用
     */
    private void emitEdges(
            Hop hop,
            List<TraversalRow> sources,
            Map<String, TraversalRow> targetsById,
            EdgeConsumer consumer) {
        if (hop.type() == StepType.REFERENCE_FIELD) {
            for (TraversalRow source : sources) {
                RecordRef sourceRef = ref(
                        hop.sourceEntityCode(), source.id());
                for (String targetId : linkIds(
                        source, requiredLink(hop.sourceLinkField(), hop))) {
                    if (targetsById.containsKey(targetId)) {
                        consumer.accept(sourceRef, ref(
                                hop.targetEntityCode(), targetId));
                    }
                }
            }
            return;
        }
        Set<String> sourceIds = sources.stream()
                .map(TraversalRow::id)
                .collect(java.util.stream.Collectors.toSet());
        LinkField targetField = requiredLink(
                hop.targetLinkField(), hop);
        for (TraversalRow target : targetsById.values()) {
            for (String sourceId : linkIds(target, targetField)) {
                if (sourceIds.contains(sourceId)) {
                    consumer.accept(
                            ref(hop.sourceEntityCode(), sourceId),
                            ref(hop.targetEntityCode(), target.id()));
                }
            }
        }
    }

    /**
     * 校验并获取可访问{@code sources}；不满足约束时阻止后续处理。
     *
     * @param path 路径，作为 {@code projectionPort.readPage} 的输入影响后续处理
     * @param ids ID 集合，供本方法校验并获取可访问{@code sources}时使用
     * @param scope 作用域，供本方法校验并获取可访问{@code sources}时使用
     * @param projectedFields {@code projected}字段，供本方法校验并获取可访问{@code sources}时使用
     * @param limits 限制集合，供本方法校验并获取可访问{@code sources}时使用
     * @return 遍历行集合，供调用方遍历或展示
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    private List<TraversalRow> requireAccessibleSources(
            PublishedRelationPath path,
            List<String> ids,
            DataScopePlan scope,
            List<LinkField> projectedFields,
            Limits limits) {
        ProjectionPage page = projectionPort.readPage(new ProjectionQuery(
                path.sourceEntityCode(),
                projectedFields,
                PredicateType.ID_IN,
                null,
                ids,
                scope,
                1,
                ids.size(),
                limits.maxEdges()));
        List<TraversalRow> rows = traversalRows(
                path.sourceEntityCode(), page.rows());
        Set<String> actual = rows.stream()
                .map(TraversalRow::id)
                .collect(java.util.stream.Collectors.toSet());
        if (page.total() != ids.size()
                || actual.size() != ids.size()
                || !actual.containsAll(ids)) {
            throw new ForbiddenException("来源记录不存在或不在当前数据权限范围内");
        }
        return rows;
    }

    /**
     * 只选择当前 hop 建边所需字段和下一 hop 的来源引用字段。所有描述均已
     * 由 PublishedRelationPathResolver 对 exact snapshot 复验。
     *
     * @param path 路径，供本方法处理{@code projected}字段时间深度时使用
     * @param depth 深度，供本方法处理{@code projected}字段时间深度时使用
     * @return 链接字段集合，供调用方遍历或展示
     */
    private List<LinkField> projectedFieldsAtDepth(
            PublishedRelationPath path,
            int depth) {
        Map<String, LinkField> fields = new LinkedHashMap<>();
        if (depth > 0) {
            LinkField incoming = path.hops().get(depth - 1)
                    .targetLinkField();
            addField(fields, incoming);
        }
        if (depth < path.hops().size()) {
            Hop next = path.hops().get(depth);
            if (next.type() == StepType.REFERENCE_FIELD) {
                addField(fields, next.sourceLinkField());
            }
        }
        return List.copyOf(fields.values());
    }

    /**
     * 添加字段；结果供后续流程传递或持久化。
     *
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param field 字段，作为 {@code fields.putIfAbsent} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void addField(
            Map<String, LinkField> fields,
            LinkField field) {
        if (field == null) {
            return;
        }
        LinkField previous = fields.putIfAbsent(field.fieldCode(), field);
        if (previous != null && !previous.equals(field)) {
            throw new IllegalArgumentException("关联路径包含冲突的钉定链接字段");
        }
    }

    /**
     * 整理链接ID 集合数据，供调用方遍历或继续处理。
     *
     * @param row 行，供本方法处理链接ID 集合时使用
     * @param field 字段，作为 {@code requiredLink} 的输入影响后续处理
     * @return 实体关系图读取集合，供调用方遍历或展示
     */
    private List<String> linkIds(
            TraversalRow row,
            LinkField field) {
        LinkField required = requiredLink(field, null);
        Object raw = row.linkValues().get(required.fieldCode());
        if (raw == null) {
            return List.of();
        }
        if (required.valueType() == LinkValueType.SCALAR_REFERENCE) {
            if (!(raw instanceof String) && !(raw instanceof Number)) {
                throw invalidReference("单值实体引用包含非标量记录ID");
            }
            return normalizeIds(
                    List.of(raw), HARD_MAX_EDGES,
                    "实体引用值数量超过硬上限");
        }
        if (!(raw instanceof Collection<?> values)) {
            throw invalidReference("多值实体引用未按钉定多值类型返回");
        }
        return normalizeIds(
                values, HARD_MAX_EDGES,
                "多值实体引用数量超过硬上限 " + HARD_MAX_EDGES);
    }

    /**
     * 在分配目标 ID 集合前执行剩余边预算，避免多行多值引用乘积放大。
     *
     * @param rows 行，供本方法收集链接ID 集合时使用
     * @param field 字段，供本方法收集链接ID 集合时使用
     * @param maxValues 最大值集合，作为 {@code limit} 的输入影响后续处理
     * @return 实体关系图读取集合，供调用方遍历或展示
     */
    private List<String> collectLinkIds(
            List<TraversalRow> rows,
            LinkField field,
            int maxValues) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (TraversalRow row : rows) {
            for (String value : linkIds(row, field)) {
                if (!result.contains(value) && result.size() >= maxValues) {
                    throw limit("关系图链接目标数量超过剩余边预算 "
                            + maxValues);
                }
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 处理必填链接，并将结果传给后续步骤。
     *
     * @param field 字段，供本方法处理必填链接时使用
     * @param hop 跳，供本方法处理必填链接时使用
     * @return 处理后的必填链接结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private LinkField requiredLink(LinkField field, Hop hop) {
        if (field == null || field.valueType() == null
                || !StringUtils.hasText(field.fieldCode())) {
            String label = hop == null ? "" : "第 " + hop.index() + " 步";
            throw new IllegalArgumentException(label + "缺少钉定链接字段");
        }
        return field;
    }

    /**
     * 整理遍历行数据，供调用方遍历或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param rows 行，供本方法处理遍历行时使用
     * @return 遍历行集合，供调用方遍历或展示
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private List<TraversalRow> traversalRows(
            String entityCode,
            List<ProjectionRow> rows) {
        List<TraversalRow> result = new ArrayList<>();
        for (ProjectionRow row : safe(rows)) {
            if (row == null || !StringUtils.hasText(row.recordId())) {
                throw new BusinessConflictException(
                        "ENTITY_RELATION_GRAPH_RECORD_INVALID",
                        "关系图投影包含缺少记录ID的数据");
            }
            result.add(new TraversalRow(
                    entityCode,
                    row.recordId().trim(),
                    row.linkValues()));
        }
        return result;
    }

    /**
     * 整理索引ID数据，供调用方遍历或继续处理。
     *
     * @param rows 行，供本方法处理索引ID时使用
     * @return 索引ID键值结果，供调用方继续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private Map<String, TraversalRow> indexById(
            List<TraversalRow> rows) {
        Map<String, TraversalRow> result = new LinkedHashMap<>();
        for (TraversalRow row : rows) {
            TraversalRow previous = result.putIfAbsent(row.id(), row);
            if (previous != null) {
                throw new BusinessConflictException(
                        "ENTITY_RELATION_PROJECTION_INVALID",
                        "关系图投影返回重复记录ID: " + row.id());
            }
        }
        return result;
    }

    /**
     * 分页查询实体关系图读取；查询结果供调用方展示或继续处理。
     *
     * @param rows 行，作为 {@code Math.min} 的输入影响后续处理
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 遍历行集合，供调用方遍历或展示
     */
    private List<TraversalRow> page(
            List<TraversalRow> rows,
            long pageNum,
            long pageSize) {
        long offset = (pageNum - 1) * pageSize;
        if (offset >= rows.size()) {
            return List.of();
        }
        int from = Math.toIntExact(offset);
        int to = (int) Math.min(rows.size(), offset + pageSize);
        return List.copyOf(rows.subList(from, to));
    }

    /**
     * 处理{@code remaining}多实例{@code budget}，并将结果传给后续步骤。
     *
     * @param limits 限制集合，作为 {@code limit} 的输入影响后续处理
     * @param allocatedEdges {@code allocated}{@code edges}，供本方法处理{@code remaining}多实例{@code budget}时使用
     * @return 处理后的{@code remaining}多实例{@code budget}结果，供调用方继续处理
     */
    private int remainingMultiBudget(Limits limits, int allocatedEdges) {
        int remaining = limits.maxEdges() - allocatedEdges;
        if (remaining < 1) {
            throw limit("关系图边数量已达到上限 " + limits.maxEdges());
        }
        return remaining;
    }

    /**
     * 规范化限制集合；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化限制集合的原始输入，结果供调用方继续使用
     * @return 规范化后的限制集合结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private Limits normalizeLimits(Limits value) {
        Limits requested = value == null ? Limits.defaults() : value;
        int perHop = requested.maxRowsPerHop();
        int total = requested.maxTotalNodes();
        int edges = requested.maxEdges();
        int pathStates = requested.maxPathStates();
        long pageNum = requested.terminalPageNum();
        long pageSize = requested.terminalPageSize();
        if (perHop < 1 || perHop > HARD_MAX_ROWS_PER_HOP) {
            throw new IllegalArgumentException("单层关系记录上限必须在 1 至 "
                    + HARD_MAX_ROWS_PER_HOP + " 之间");
        }
        if (total < perHop || total > HARD_MAX_TOTAL_NODES) {
            throw new IllegalArgumentException("关系图总节点上限必须在单层上限至 "
                    + HARD_MAX_TOTAL_NODES + " 之间");
        }
        if (edges < 1 || edges > HARD_MAX_EDGES) {
            throw new IllegalArgumentException("关系图边上限必须在 1 至 "
                    + HARD_MAX_EDGES + " 之间");
        }
        if (pathStates < perHop || pathStates > HARD_MAX_PATH_STATES) {
            throw new IllegalArgumentException("关系图路径状态上限必须在单层上限至 "
                    + HARD_MAX_PATH_STATES + " 之间");
        }
        if (pageNum < 1 || pageSize < 1 || pageSize > perHop) {
            throw new IllegalArgumentException("最终层页码或每页数量无效");
        }
        return new Limits(
                perHop, total, edges, pathStates, pageNum, pageSize);
    }

    /**
     * 写入节点{@code within}{@code budget}；后续读取或执行将使用更新后的状态。
     *
     * @param nodes 节点集合，供本方法写入节点{@code within}{@code budget}时使用
     * @param ref 引用，作为 {@code nodes.put} 的输入影响后续处理
     * @param depth 深度，作为 {@code nodes.put} 的输入影响后续处理
     * @param limits 限制集合，作为 {@code limit} 的输入影响后续处理
     */
    private void putNodeWithinBudget(
            Map<RecordRef, Node> nodes,
            RecordRef ref,
            int depth,
            Limits limits) {
        if (!nodes.containsKey(ref)
                && nodes.size() >= limits.maxTotalNodes()) {
            throw limit("关系图节点总数超过上限 "
                    + limits.maxTotalNodes());
        }
        if (!nodes.containsKey(ref)) {
            nodes.put(ref, new Node(ref, depth));
        }
    }

    /**
     * 规范化ID 集合；输出作为后续校验或处理的输入。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param maxValues 最大值集合，供本方法规范化ID 集合时使用
     * @param overflowMessage {@code overflow}消息，作为 {@code limit} 的输入影响后续处理
     * @return 实体关系图读取集合，供调用方遍历或展示
     */
    private List<String> normalizeIds(
            Collection<?> values,
            int maxValues,
            String overflowMessage) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (!(value instanceof String)
                    && !(value instanceof Number)) {
                throw invalidReference("实体引用字段只能包含记录ID");
            }
            String text = String.valueOf(value).trim();
            if (StringUtils.hasText(text)) {
                if (!result.contains(text) && result.size() >= maxValues) {
                    throw limit(overflowMessage);
                }
                result.add(text);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 处理引用，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 处理后的引用结果，供调用方继续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private RecordRef ref(String entityCode, String recordId) {
        if (!StringUtils.hasText(entityCode)
                || !StringUtils.hasText(recordId)) {
            throw new BusinessConflictException(
                    "ENTITY_RELATION_GRAPH_RECORD_INVALID",
                    "关系图中存在缺少实体编码或记录ID的数据");
        }
        return new RecordRef(entityCode.trim(), recordId.trim());
    }

    /**
     * 处理边，并将结果传给后续步骤。
     *
     * @param hop 跳，作为 {@code Edge} 的输入影响后续处理
     * @param source 待处理边的原始输入，结果供调用方继续使用
     * @param target 目标，供本方法处理边时使用
     * @return 处理后的边结果，供调用方继续处理
     */
    private Edge edge(Hop hop, RecordRef source, RecordRef target) {
        return new Edge(
                hop.index(),
                hop.type().name(),
                hop.code(),
                source,
                target);
    }

    /**
     * 判断相同条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，供本方法处理相同时使用
     * @param right 右侧，作为 {@code left.equals} 的输入影响后续处理
     * @return 相同条件成立时为 true，否则为 false
     */
    private boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    /**
     * 整理安全数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 实体关系图读取集合，供调用方遍历或展示
     */
    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 构造无效引用异常，供调用方区分失败原因。
     *
     * @param message 消息，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @return 处理后的无效引用结果，供调用方继续处理
     */
    private BusinessConflictException invalidReference(String message) {
        return new BusinessConflictException(
                "ENTITY_RELATION_REFERENCE_INVALID", message);
    }

    /**
     * 构造{@code cardinality}异常，供调用方区分失败原因。
     *
     * @param hop 跳，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @param source 待处理{@code cardinality}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code cardinality}结果，供调用方继续处理
     */
    private BusinessConflictException cardinality(
            Hop hop,
            RecordRef source) {
        return new BusinessConflictException(
                "ENTITY_RELATION_CARDINALITY_VIOLATION",
                "ONE_TO_ONE 关系 " + hop.code()
                        + " 对来源记录 " + source.recordId()
                        + " 返回了多条目标记录");
    }

    /**
     * 构造上限异常，供调用方区分失败原因。
     *
     * @param message 消息，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @return 处理后的上限结果，供调用方继续处理
     */
    private BusinessConflictException limit(String message) {
        return new BusinessConflictException(
                "ENTITY_RELATION_GRAPH_LIMIT_EXCEEDED", message);
    }

    /**
     * 封装遍历行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 对象标识，供后续引用、更新或关联
     * @param linkValues 链接值集合，保存在对象中供后续校验、查询或展示
     */
    private record TraversalRow(
            String entityCode,
            String id,
            Map<String, Object> linkValues) {
    }

    /**
     * 封装跳行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param rows 行，保存在对象中供后续校验、查询或展示
     * @param total 总数，保存在对象中供后续校验、查询或展示
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     */
    private record HopRows(
            List<TraversalRow> rows,
            long total,
            long pageNum,
            long pageSize) {
    }

    /**
     * 负责可变计数器的业务处理；协调校验、状态变化及后续结果传递。
     */
    private static final class MutableCounter {
        private int value;

        /**
         * 初始化可变计数器，保存构造参数供后续方法使用。
         *
         * @param value 值依赖，保存到当前对象供后续业务方法调用
         */
        private MutableCounter(int value) {
            this.value = value;
        }

        /**
         * 读取或规范化输入值，供后续计算与比较使用。
         *
         * @return 处理后的值结果，供调用方继续处理
         */
        private int value() {
            return value;
        }

        /**
         * 处理{@code increment}，并将结果传给后续步骤。
         */
        private void increment() {
            value++;
        }
    }

    /**
     * 定义边{@code consumer}的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
     */
    @FunctionalInterface
    private interface EdgeConsumer {
        /**
         * 处理{@code accept}，并将结果传给后续步骤。
         *
         * @param source 待处理{@code accept}的原始输入，结果供调用方继续使用
         * @param target 目标，供本方法处理{@code accept}时使用
         */
        void accept(RecordRef source, RecordRef target);
    }
}
