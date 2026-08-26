package com.workflow.process.coordination.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationPlan;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationService;
import com.workflow.entity.data.application.EntityRelationGraphReadService;
import com.workflow.entity.data.application.model.EntityRelationGraph;
import com.workflow.entity.data.application.model.EntityRelationGraph.Limits;
import com.workflow.entity.data.application.model.EntityRelationGraph.RecordRef;
import com.workflow.entity.definition.application.PublishedRelationPathResolver;
import com.workflow.entity.definition.application.model.PublishedRelationPath;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Command;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Operation;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.ProcessState;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Source;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.TargetImpact;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于已发布关系图生成跨实体流程协同影响预览。
 *
 * <p>服务只从 {@link FlowActionContext} 取宿主实体与记录 ID，
 * 不接受命令中的 recordId、过滤器或数据权限条件。关系图对每一跳
 * 重新鉴权，并且必须完整返回终点；分页或超出预算都会拒绝协同，
 * 避免对部分子流程误判成“全部完成”。</p>
 */
@Service
@RequiredArgsConstructor
public class RelatedProcessCoordinationPlanService {

    static final int MAX_TARGETS = 100;
    private static final Limits GRAPH_LIMITS = new Limits(
            MAX_TARGETS,
            500,
            2000,
            2000,
            1,
            MAX_TARGETS);

    private final PublishedRelationPathResolver pathResolver;
    private final EntityRelationGraphAuthorizationService authorizationService;
    private final EntityRelationGraphReadService graphReadService;
    private final EntityProcessLinkMapper entityProcessLinkMapper;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final ProcessPublishedSnapshotService snapshotService;

    /**
     * 生成并验证一次协同计划。
     *
     * @param context 已发布流程动作的服务端上下文
     * @param command 不含任何记录 ID 的强类型命令
     * @return 已固定目标流程版本的影响预览
     */
    @Transactional(readOnly = true)
    public RelatedProcessCoordinationPlan plan(
            FlowActionContext context,
            Command command) {
        Source source = requireSource(context);
        Command normalized = normalize(command);
        RelatedProcessCoordinationPlan plan = inspect(source, normalized);
        validateOperation(plan, normalized);
        return plan;
    }

    /**
     * Outbox 消费时按原操作人重新生成现状预览。
     *
     * <p>这个入口会重走路径快照校验、专用能力校验和逐跳数据
     * 权限。返回的是现状，由执行器再与原始计划逐项对比。</p>
     */
    @Transactional(readOnly = true)
    public RelatedProcessCoordinationPlan replan(
            Source source,
            Command command) {
        requireAuthenticatedSource(source);
        return inspect(source, normalize(command));
    }

    private RelatedProcessCoordinationPlan inspect(
            Source source,
            Command command) {
        requirePinnedSourceProcess(source);
        PublishedRelationPath path = pathResolver.validate(
                command.publishedRelationPath());
        if (!source.entityCode().equals(path.sourceEntityCode())) {
            throw new BusinessConflictException(
                    "RELATED_PROCESS_SOURCE_MISMATCH",
                    "流程实体与已发布关系路径的来源实体不一致");
        }
        EntityRelationGraphAuthorizationPlan authorization =
                authorizationService.authorizeInternal(
                        path,
                        EntityRelationGraphAuthorizationPlan
                                .InternalPurpose.PROCESS_COORDINATION);
        EntityRelationGraph graph = graphReadService.read(
                path,
                List.of(source.entityRecordId()),
                GRAPH_LIMITS,
                authorization);
        if (graph.truncated()
                || graph.terminalTotal() != graph.terminalRecords().size()) {
            throw conflict(
                    "RELATED_PROCESS_GRAPH_INCOMPLETE",
                    "关联流程数量超出单次安全处理上限 "
                            + MAX_TARGETS + "，未执行任何操作");
        }
        List<RecordRef> terminalRecords = graph.terminalRecords().stream()
                .distinct()
                .sorted(Comparator.comparing(RecordRef::entityCode)
                        .thenComparing(RecordRef::recordId))
                .toList();
        int minimum = command.minimumRelatedRecords() == null
                ? 1 : command.minimumRelatedRecords();
        if (terminalRecords.size() < minimum) {
            throw conflict(
                    "RELATED_PROCESS_TARGETS_INSUFFICIENT",
                    "关联记录数量为 " + terminalRecords.size()
                            + "，少于配置的最小数量 " + minimum);
        }

        List<TargetImpact> impacts = new ArrayList<>();
        for (RecordRef record : terminalRecords) {
            // 终点来自已授权关系图；流程链接只按这个服务端
            // 结果查询，不读取外部传入的 processInstanceId。
            impacts.add(inspectTarget(record));
        }
        String graphFingerprint = graphFingerprint(path, terminalRecords);
        String planId = planId(source, command, graphFingerprint, impacts);
        int active = (int) impacts.stream()
                .filter(item -> item.state() == ProcessState.ACTIVE)
                .count();
        int terminal = (int) impacts.stream()
                .filter(item -> item.state() == ProcessState.COMPLETED
                        || item.state() == ProcessState.TERMINATED)
                .count();
        int none = (int) impacts.stream()
                .filter(item -> item.state() == ProcessState.NONE)
                .count();
        return new RelatedProcessCoordinationPlan(
                planId,
                graphFingerprint,
                command.operation(),
                source,
                path,
                impacts,
                command.allowedStates(),
                trimToNull(command.targetActivityId()),
                trimToNull(command.reason()),
                active,
                terminal,
                none,
                command.operation() == Operation.ROUTE_RELATED_PARENT
                        || command.operation()
                                == Operation.PROPAGATE_TERMINATION);
    }

    private TargetImpact inspectTarget(RecordRef record) {
        EntityProcessLink link = entityProcessLinkMapper.selectLatest(
                record.entityCode(), record.recordId());
        if (link == null) {
            return new TargetImpact(
                    record, ProcessState.NONE, null, null, null,
                    null, null, null, null, List.of());
        }
        if (!StringUtils.hasText(link.getProcessInstanceId())) {
            ProcessState state = "PENDING".equals(link.getState())
                    ? ProcessState.STARTING : ProcessState.INCONSISTENT;
            return new TargetImpact(
                    record, state, link.getId(), null, null,
                    null, null, link.getProcessDefinitionKey(),
                    link.getEntityStatus(), List.of());
        }
        ProcessInstance active = runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(link.getProcessInstanceId())
                .singleResult();
        HistoricProcessInstance historic = historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(link.getProcessInstanceId())
                .singleResult();
        String definitionId = active != null
                ? active.getProcessDefinitionId()
                : historic == null ? null
                        : historic.getProcessDefinitionId();
        if (!StringUtils.hasText(definitionId)) {
            return new TargetImpact(
                    record, ProcessState.INCONSISTENT, link.getId(),
                    link.getProcessInstanceId(), null, null, null,
                    link.getProcessDefinitionKey(), link.getEntityStatus(),
                    List.of());
        }
        ProcessDefinition definition = repositoryService
                .getProcessDefinition(definitionId);
        if (definition == null
                || !StringUtils.hasText(definition.getDeploymentId())) {
            throw conflict(
                    "RELATED_PROCESS_VERSION_MISSING",
                    "关联流程的 Flowable 定义已缺失: " + definitionId);
        }
        ProcessVersionHistory version = snapshotService
                .getVersionByProcessDefinitionId(definitionId);
        if (!same(version.getProcessKey(), definition.getKey())
                || !same(version.getProcessKey(),
                        link.getProcessDefinitionKey())) {
            throw conflict(
                    "RELATED_PROCESS_VERSION_MISMATCH",
                    "关联流程实例与平台发布快照不一致: "
                            + link.getProcessInstanceId());
        }
        List<String> activeActivities = active == null
                ? List.of()
                : runtimeService.getActiveActivityIds(
                                link.getProcessInstanceId()).stream()
                        .filter(StringUtils::hasText)
                        .distinct()
                        .sorted()
                        .toList();
        ProcessState state = state(active, historic);
        return new TargetImpact(
                record,
                state,
                link.getId(),
                link.getProcessInstanceId(),
                definitionId,
                version.getId(),
                version.getVersion(),
                version.getProcessKey(),
                link.getEntityStatus(),
                activeActivities);
    }

    /** 宿主动作也必须绑定它触发时的精确流程发布历史。 */
    private void requirePinnedSourceProcess(Source source) {
        ProcessVersionHistory sourceVersion = snapshotService
                .getVersionByProcessDefinitionId(
                        source.processDefinitionId());
        if (sourceVersion == null
                || !same(source.processVersionId(), sourceVersion.getId())) {
            throw conflict(
                    "RELATED_PROCESS_SOURCE_VERSION_DRIFTED",
                    "宿主流程动作与精确发布版本不一致");
        }
    }

    private ProcessState state(
            ProcessInstance active,
            HistoricProcessInstance historic) {
        if (active != null) {
            return ProcessState.ACTIVE;
        }
        if (historic == null || historic.getEndTime() == null) {
            return ProcessState.INCONSISTENT;
        }
        return StringUtils.hasText(historic.getDeleteReason())
                ? ProcessState.TERMINATED : ProcessState.COMPLETED;
    }

    private void validateOperation(
            RelatedProcessCoordinationPlan plan,
            Command command) {
        switch (command.operation()) {
            case ASSERT_RELATED_STATE -> requireAllowedStates(plan, false);
            case WAIT_RELATED_PROCESSES -> requireAllowedStates(plan, true);
            case ROUTE_RELATED_PARENT -> validateRoute(plan);
            case PROPAGATE_TERMINATION -> validateTermination(plan);
        }
    }

    private void requireAllowedStates(
            RelatedProcessCoordinationPlan plan,
            boolean wait) {
        Set<ProcessState> allowed = plan.allowedStates();
        List<TargetImpact> rejected = plan.targets().stream()
                .filter(item -> !allowed.contains(item.state()))
                .toList();
        if (rejected.isEmpty()) {
            return;
        }
        String ids = rejected.stream()
                .limit(5)
                .map(item -> item.record().entityCode() + ":"
                        + item.record().recordId() + "=" + item.state())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        throw conflict(
                wait ? "RELATED_PROCESSES_PENDING"
                        : "RELATED_PROCESS_STATE_ASSERTION_FAILED",
                wait ? "仍有关联流程未达到可继续状态: " + ids
                        : "关联流程状态不符合要求: " + ids);
    }

    private void validateRoute(RelatedProcessCoordinationPlan plan) {
        if (plan.targets().size() != 1) {
            throw conflict(
                    "RELATED_PARENT_CARDINALITY_INVALID",
                    "路由关联上级要求关系路径精确命中一条记录");
        }
        TargetImpact target = plan.targets().get(0);
        if (target.state() != ProcessState.ACTIVE) {
            throw conflict(
                    "RELATED_PARENT_PROCESS_NOT_ACTIVE",
                    "关联上级流程不在运行中，无法路由");
        }
        requireUserTask(
                target.processDefinitionId(),
                plan.targetActivityId());
    }

    private void validateTermination(RelatedProcessCoordinationPlan plan) {
        List<TargetImpact> unsafe = plan.targets().stream()
                .filter(item -> item.state() == ProcessState.STARTING
                        || item.state() == ProcessState.INCONSISTENT)
                .toList();
        if (!unsafe.isEmpty()) {
            throw conflict(
                    "RELATED_PROCESS_STATE_UNCERTAIN",
                    "部分关联流程正在发起或链接异常，未下发终止操作");
        }
    }

    private void requireUserTask(
            String processDefinitionId,
            String targetActivityId) {
        if (!StringUtils.hasText(targetActivityId)) {
            throw new IllegalArgumentException("路由关联上级必须选择目标节点");
        }
        BpmnModel model = repositoryService.getBpmnModel(
                processDefinitionId);
        if (model == null || model.getMainProcess() == null
                || !(model.getMainProcess().getFlowElement(
                        targetActivityId, true) instanceof UserTask)) {
            throw conflict(
                    "RELATED_PARENT_TARGET_NODE_INVALID",
                    "目标节点不是该精确流程版本中的用户任务: "
                            + targetActivityId);
        }
    }

    private Source requireSource(FlowActionContext context) {
        if (context == null
                || !StringUtils.hasText(context.getEntityCode())
                || !StringUtils.hasText(context.getEntityDataId())
                || !StringUtils.hasText(context.getActionId())
                || !StringUtils.hasText(context.getIdempotencyKey())
                || !StringUtils.hasText(context.getProcessVersionId())
                || !StringUtils.hasText(context.getProcessDefinitionId())
                || !StringUtils.hasText(context.getProcessInstanceId())) {
            throw new IllegalArgumentException(
                    "跨实体流程协同缺少已发布动作上下文");
        }
        String operatorId = UserContext.getUserId();
        if (!StringUtils.hasText(operatorId)
                || !operatorId.equals(context.getOperatorId())) {
            throw new ForbiddenException(
                    "跨实体流程协同的操作人与当前登录人不一致");
        }
        return new Source(
                context.getActionId(),
                context.getIdempotencyKey(),
                context.getProcessVersionId(),
                context.getProcessDefinitionId(),
                context.getProcessInstanceId(),
                context.getEntityCode().trim(),
                context.getEntityDataId().trim(),
                operatorId,
                StringUtils.hasText(UserContext.getUsername())
                        ? UserContext.getUsername() : operatorId);
    }

    private void requireAuthenticatedSource(Source source) {
        if (source == null
                || !StringUtils.hasText(source.operatorId())
                || !source.operatorId().equals(UserContext.getUserId())
                || !StringUtils.hasText(source.processVersionId())
                || !StringUtils.hasText(source.processDefinitionId())
                || !StringUtils.hasText(source.entityCode())
                || !StringUtils.hasText(source.entityRecordId())) {
            throw new ForbiddenException(
                    "协同重试缺少与操作人绑定的服务端来源上下文");
        }
    }

    private Command normalize(Command command) {
        if (command == null || command.operation() == null
                || command.publishedRelationPath() == null) {
            throw new IllegalArgumentException(
                    "跨实体流程协同命令不完整");
        }
        int minimum = command.minimumRelatedRecords() == null
                ? 1 : command.minimumRelatedRecords();
        if (minimum < 0 || minimum > MAX_TARGETS) {
            throw new IllegalArgumentException(
                    "最小关联记录数必须在 0 到 " + MAX_TARGETS + " 之间");
        }
        Set<ProcessState> allowed = new LinkedHashSet<>(
                command.allowedStates() == null
                        ? Set.of() : command.allowedStates());
        if (command.operation() == Operation.WAIT_RELATED_PROCESSES
                && allowed.isEmpty()) {
            allowed.add(ProcessState.COMPLETED);
        }
        if (command.operation() == Operation.ASSERT_RELATED_STATE
                && allowed.isEmpty()) {
            throw new IllegalArgumentException(
                    "检查关联流程状态必须明确选择允许的状态");
        }
        if ((command.operation() == Operation.ASSERT_RELATED_STATE
                || command.operation()
                        == Operation.WAIT_RELATED_PROCESSES)
                && (allowed.contains(ProcessState.ACTIVE)
                        || allowed.contains(ProcessState.STARTING)
                        || allowed.contains(ProcessState.INCONSISTENT))) {
            throw new IllegalArgumentException(
                    "前置校验不允许把运行中、发起中或异常状态视为完成");
        }
        String reason = trimToNull(command.reason());
        if (reason != null && reason.length() > 500) {
            throw new IllegalArgumentException("协同原因不能超过 500 个字符");
        }
        return new Command(
                command.operation(),
                command.publishedRelationPath(),
                allowed,
                minimum,
                trimToNull(command.targetActivityId()),
                reason);
    }

    private String graphFingerprint(
            PublishedRelationPath path,
            List<RecordRef> records) {
        StringBuilder value = new StringBuilder()
                .append(path.sourceHistoryId()).append('\n')
                .append(path.sourceSchemaHash()).append('\n');
        path.hops().forEach(hop -> value
                .append(hop.index()).append(':')
                .append(hop.type()).append(':')
                .append(hop.code()).append(':')
                .append(hop.targetHistoryId()).append(':')
                .append(hop.targetSchemaHash()).append('\n'));
        records.forEach(record -> value
                .append(record.entityCode()).append(':')
                .append(record.recordId()).append('\n'));
        return sha256(value.toString());
    }

    private String planId(
            Source source,
            Command command,
            String graphFingerprint,
            List<TargetImpact> impacts) {
        StringBuilder value = new StringBuilder()
                .append(source.actionId()).append('\n')
                .append(source.actionExecutionId()).append('\n')
                .append(source.processDefinitionId()).append('\n')
                .append(source.processInstanceId()).append('\n')
                .append(source.entityCode()).append(':')
                .append(source.entityRecordId()).append('\n')
                .append(command.operation()).append('\n')
                .append(graphFingerprint).append('\n')
                .append(trimToEmpty(command.targetActivityId())).append('\n');
        impacts.forEach(item -> value
                .append(item.record().entityCode()).append(':')
                .append(item.record().recordId()).append(':')
                .append(trimToEmpty(item.processInstanceId())).append(':')
                .append(trimToEmpty(item.processDefinitionId())).append(':')
                .append(trimToEmpty(item.processVersionHistoryId())).append(':')
                .append(item.state()).append(':')
                .append(String.join(",", item.activeActivityIds()))
                .append('\n'));
        return sha256(value.toString());
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private BusinessConflictException conflict(
            String code,
            String message) {
        return new BusinessConflictException(code, message);
    }

    private boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private String trimToNull(String value) {
        return !StringUtils.hasText(value) ? null : value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
