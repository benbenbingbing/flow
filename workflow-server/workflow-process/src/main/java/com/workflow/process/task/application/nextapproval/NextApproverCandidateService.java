package com.workflow.process.task.application.nextapproval;

import com.workflow.process.task.application.nextapproval.model.NextApprovalResolution;
import com.workflow.process.task.application.nextapproval.model.NextApprovalTarget;
import com.workflow.process.task.application.nextapproval.model.NextApproverSelectionPolicy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.process.assignment.model.PersonResolveRequest;
import com.workflow.contracts.process.assignment.model.PersonResolveUsage;
import com.workflow.core.result.PageResult;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser.LegacyAssignment;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.assignment.domain.EntityUserReferenceFieldConfig;
import com.workflow.process.assignment.domain.RelativeOrgPositionConfig;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.task.api.request.NextApprovalPreviewRequest;
import com.workflow.process.task.api.request.NextApproverOptionsRequest;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import com.workflow.process.task.infrastructure.MultiInstanceVariableNames;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.UserTask;
import org.flowable.task.api.Task;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 解析下一节点可选人员，并统一过滤停用或已删除用户。
 */
@Service
@RequiredArgsConstructor
public class NextApproverCandidateService {

    private final NextApprovalRouteService routeService;
    private final PersonResolverRuntimeService resolverRuntimeService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysGroupMapper groupMapper;
    private final SysUserGroupMapper userGroupMapper;
    private final SysOrganizationMapper organizationMapper;

    /** 仅按任务绑定的部署定义解析稳定流程配置身份。 */
    @Autowired(required = false)
    private RepositoryService repositoryService;

    @Autowired(required = false)
    private ProcessVersionHistoryMapper processVersionMapper;

    /**
     * 处理选项，并将结果传给后续步骤。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param request 本次请求，后续经校验后用于处理选项
     * @return 处理后的选项结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public PageResult<NextApproverCandidateDTO> options(
            String taskId,
            NextApproverOptionsRequest request) {
        requireText(request.getTargetNodeId(), "目标节点不能为空");
        requireText(request.getScopeKey(), "scopeKey 不能为空");
        NextApprovalPreviewRequest preview = new NextApprovalPreviewRequest();
        preview.setAction(request.getAction());
        preview.setActionLabel(request.getActionLabel());
        preview.setComment(request.getComment());
        preview.setFormData(request.getFormData());
        NextApprovalResolution resolution = routeService.resolve(
                taskId, preview);
        if (!resolution.ready()) {
            throw new IllegalArgumentException(
                    resolution.message() == null
                            ? "下一节点尚不能确定"
                            : resolution.message());
        }
        if (!request.getScopeKey().equals(resolution.scopeKey())) {
            throw new IllegalArgumentException(
                    "下一审批路径或人员范围已变化，请刷新后重试");
        }
        NextApprovalTarget target = resolution.targets().stream()
                .filter(item -> request.getTargetNodeId()
                        .equals(item.userTask().getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "目标节点不属于当前命中的下一审批路径"));
        NextApproverSelectionPolicy policy = target.selectionPolicy();
        if (!policy.visible() || !policy.editable()) {
            throw new IllegalArgumentException(
                    "目标节点不允许人工选择下一审批人");
        }
        List<SysUser> users = resolveAllowed(
                resolution, target, PersonResolveUsage.CANDIDATE);
        String keyword = normalizeKeyword(request.getKeyword());
        List<NextApproverCandidateDTO> candidates = users.stream()
                .map(this::toDto)
                .filter(item -> matches(item, keyword))
                .sorted(Comparator
                        .comparing(NextApproverCandidateDTO::getUsername)
                        .thenComparing(NextApproverCandidateDTO::getUserId))
                .toList();
        int pageNum = request.getPageNum() == null
                ? 1 : Math.max(1, request.getPageNum());
        int pageSize = request.getPageSize() == null
                ? 20 : Math.max(1, Math.min(100, request.getPageSize()));
        long requestedOffset = ((long) pageNum - 1L) * pageSize;
        int from = (int) Math.min(candidates.size(), requestedOffset);
        int to = Math.min(candidates.size(), from + pageSize);
        return new PageResult<>(
                candidates.subList(from, to),
                candidates.size(),
                pageNum,
                pageSize);
    }

    /**
     * 整理默认{@code assignees}数据，供调用方遍历或继续处理。
     *
     * @param resolution 解析，作为 {@code resolveNodeAssignmentUsers} 的输入影响后续处理
     * @param target 目标，作为 {@code resolveNodeAssignmentUsers} 的输入影响后续处理
     * @return 下一步审批人候选人集合，供调用方遍历或展示
     */
    public List<NextApproverCandidateDTO> defaultAssignees(
            NextApprovalResolution resolution,
            NextApprovalTarget target) {
        List<NextApproverCandidateDTO> defaults =
                resolveNodeAssignmentUsers(
                        resolution, target, true).stream()
                .map(this::toDto)
                .toList();
        if ("DIRECT".equals(
                target.selectionPolicy().assignmentMode())
                && defaults.size() > 1) {
            return List.of(defaults.get(0));
        }
        // 启动快照可能包含已停用或配置变更后不再允许的人员；默认值只展示
        // 当前仍在允许范围内的人员，避免用户误选后提交被后端拒绝。
        return filterAllowedDefaults(resolution, target, defaults);
    }

    /**
     * 使用当前允许范围过滤默认人员，仅移除已不在范围内的项；保留原顺序。
     *
     * <p>仅对 NODE_ASSIGNMENT 来源生效。SCOPE/RESOLVER 的默认值由外部
     * 范围或解析器自身决定，不应再用允许范围二次过滤。</p>
     *
     * <p>对 RESOLVER 来源使用与 options/重验一致的用途，避免解析器用途
     * 漂移导致默认显示与提交校验范围不一致。</p>
     *
     * @param resolution 解析，作为 {@code resolveAllowed} 的输入影响后续处理
     * @param target 目标，作为 {@code equals} 的输入影响后续处理
     * @param defaults {@code defaults}，供本方法处理过滤允许{@code defaults}时使用
     * @return 下一步审批人候选人集合，供调用方遍历或展示
     */
    private List<NextApproverCandidateDTO> filterAllowedDefaults(
            NextApprovalResolution resolution,
            NextApprovalTarget target,
            List<NextApproverCandidateDTO> defaults) {
        if (defaults.isEmpty()
                || target.selectionPolicy().sourceType()
                != NextApproverSelectionPolicy.SourceType.NODE_ASSIGNMENT) {
            return defaults;
        }
        Map<String, Object> config = target.assigneeConfig();
        if (usesEntryTimeDynamicResolver(target)) {
            // 动态默认值本身刚由权威解析器产生，同一预览请求内
            // 再解一次可能恰逢任职变更，反而造成默认显示自相矛盾。
            return defaults;
        }
        PersonResolveUsage usage = "MULTI_INSTANCE".equals(
                target.selectionPolicy().assignmentMode())
                ? PersonResolveUsage.MULTI_INSTANCE
                : PersonResolveUsage.ASSIGNEE;
        Set<String> allowed;
        try {
            allowed = resolveAllowed(resolution, target, usage).stream()
                    .map(SysUser::getUsername)
                    .collect(java.util.stream.Collectors.toSet());
        } catch (RuntimeException exception) {
            // 默认展示不应因允许范围解析失败而完全失效；失败后回退到原默认值。
            return defaults;
        }
        return defaults.stream()
                .filter(dto -> allowed.contains(dto.getUsername()))
                .toList();
    }

    /**
     * 展开目标节点自身的完整办理人集合。
     *
     * <p>DIRECT 的“只取第一人”由调用方在默认值投影时处理；本方法始终返回
     * 完整集合，NODE_ASSIGNMENT 的候选范围和提交重验才能使用同一边界。</p>
     *
     * @param resolution 解析，作为 {@code preparedMultiInstanceUsers} 的输入影响后续处理
     * @param target 目标，作为 {@code preparedMultiInstanceUsers} 的输入影响后续处理
     * @param preferPreparedMultiInstanceSnapshot 优先已准备多实例快照，供本方法解析节点分配用户集合时使用
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> resolveNodeAssignmentUsers(
            NextApprovalResolution resolution,
            NextApprovalTarget target,
            boolean preferPreparedMultiInstanceSnapshot) {
        Map<String, Object> config = target.assigneeConfig();
        int version = assignmentConfigVersion(config);
        if (config.containsKey("assignmentConfigVersion")
                && version != 2) {
            throw new IllegalArgumentException(
                    "不支持的 assignmentConfigVersion: " + version);
        }
        LegacyAssignment legacyAssignment =
                LegacyMultiInstanceAssignmentParser.parse(config);
        if (preferPreparedMultiInstanceSnapshot
                && !usesEntryTimeDynamicResolver(target)) {
            List<SysUser> prepared = preparedMultiInstanceUsers(
                    resolution, target);
            if (prepared != null) {
                return prepared;
            }
        }
        if (LegacyMultiInstanceAssignmentParser
                .usesLegacyMultiInstanceAssignment(
                        config,
                        target.assignmentSourceTask()
                                .hasMultiInstanceLoopCharacteristics())) {
            return resolveLegacyMultiInstanceUsers(
                    resolution, target, legacyAssignment);
        }
        return resolveBaseAssignmentUsers(resolution, target, config);
    }

    /**
     * 节点进入期解析器以当前权威状态为输入，下一审批人预览不能被
     * 上一轮循环遗留的 collection 变量短路。相对职务读取当前任职，
     * 实体用户字段读取任一表单最新保存的实体记录；实际进入节点时
     * 仍由 collection handler 生成当轮稳定集合。
     *
     * @param target 目标，供本方法处理使用入口时间动态解析器时使用
     * @return 使用入口时间动态解析器条件成立时为 true，否则为 false
     */
    private boolean usesEntryTimeDynamicResolver(
            NextApprovalTarget target) {
        Map<String, Object> config = target.assigneeConfig();
        return isEntryTimeDynamicResolver(
                LegacyMultiInstanceAssignmentParser
                        .effectiveResolver(
                                config,
                                target.assignmentSourceTask()
                                        .hasMultiInstanceLoopCharacteristics())
                        .resolverCode());
    }

    /**
     * 判断是否入口时间动态解析器；判断结果决定调用方的后续分支。
     *
     * @param resolverCode 解析器编码，后续用于判断是否入口时间动态解析器时定位或关联目标
     * @return 入口时间动态解析器条件成立时为 true，否则为 false
     */
    private boolean isEntryTimeDynamicResolver(String resolverCode) {
        return RelativeOrgPositionConfig.RESOLVER_CODE.equals(resolverCode)
                || EntityUserReferenceFieldConfig.RESOLVER_CODE.equals(
                resolverCode);
    }

    /**
     * 已在流程启动时准备的多实例集合是该实例的人员快照，优先于重新展开
     * 可变的组、角色或解析器结果；返回 null 表示变量尚未准备。
     *
     * @param resolution 解析，供本方法处理已准备多实例用户集合时使用
     * @param target 目标，作为 {@code multiInstanceCollectionVariable} 的输入影响后续处理
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> preparedMultiInstanceUsers(
            NextApprovalResolution resolution,
            NextApprovalTarget target) {
        if (!"MULTI_INSTANCE".equals(
                target.selectionPolicy().assignmentMode())) {
            return null;
        }
        String variable = multiInstanceCollectionVariable(
                target.userTask());
        if (!StringUtils.hasText(variable)
                || !resolution.variables().containsKey(variable)) {
            return null;
        }
        Object value = resolution.variables().get(variable);
        if (!(value instanceof Collection<?> collection)) {
            throw new IllegalArgumentException(
                    "多实例集合变量不是人员列表: " + variable);
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        collection.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(keys::add);
        return resolveUsers(keys);
    }

    /**
     * 历史多实例部署以独立 multiInstance 与 collection 来源字段为准。
     * 解析器来源即使返回空集合也不能回退到可能陈旧的基础配置；静态旧字段
     * 全为空时保留历史行为，继续尝试基础 user 配置。
     *
     * @param resolution 解析，作为 {@code resolveWithResolver} 的输入影响后续处理
     * @param target 目标，作为 {@code equals} 的输入影响后续处理
     * @param legacy 旧版，作为 {@code resolveWithResolver} 的输入影响后续处理
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> resolveLegacyMultiInstanceUsers(
            NextApprovalResolution resolution,
            NextApprovalTarget target,
            LegacyAssignment legacy) {
        if (legacy.resolver()) {
            PersonResolveUsage usage = "MULTI_INSTANCE".equals(
                    target.selectionPolicy().assignmentMode())
                    ? PersonResolveUsage.MULTI_INSTANCE
                    : PersonResolveUsage.ASSIGNEE;
            return resolveWithResolver(
                    resolution,
                    target,
                    legacy.resolverCode(),
                    usage,
                    legacy.resolverExtraParams());
        }
        List<SysUser> result = new ArrayList<>(
                resolveUsers(legacy.userKeys()));
        result.addAll(resolveGroups(legacy.groupKeys()));
        result.addAll(resolveRoles(legacy.roleKeys()));
        return dedupeEnabled(result);
    }

    /**
     * v2 普通任务和多实例共同使用的基础办理人解析。
     *
     * @param resolution 解析，作为 {@code resolveWithResolver} 的输入影响后续处理
     * @param target 目标，作为 {@code equals} 的输入影响后续处理
     * @param config 配置内容，决定后续基础分配用户集合的处理规则
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> resolveBaseAssignmentUsers(
            NextApprovalResolution resolution,
            NextApprovalTarget target,
            Map<String, Object> config) {
        UserTask task = target.assignmentSourceTask();
        String type = normalizeAssignmentType(
                config.get("assigneeType"));
        boolean multiInstance = "MULTI_INSTANCE".equals(
                target.selectionPolicy().assignmentMode());
        if ("expression".equals(type)) {
            throw new IllegalArgumentException(
                    "表达式办理人无法安全枚举: "
                            + target.userTask().getId());
        }
        if ("node_reference".equals(type)
                || "nodereference".equals(type)) {
            throw new IllegalArgumentException(
                    "审批人节点引用未在部署模型中解析: "
                            + target.userTask().getId());
        }
        if ("resolver".equals(type)) {
            PersonResolveUsage usage = multiInstance
                    ? PersonResolveUsage.MULTI_INSTANCE
                    : PersonResolveUsage.ASSIGNEE;
            return resolveWithResolver(
                    resolution,
                    target,
                    firstText(
                            config.get("resolverCode"),
                            config.get("interfaceName")),
                    usage,
                    mapValue(config.get("extraParams")));
        }

        LinkedHashSet<String> users = new LinkedHashSet<>();
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        // 实际 BPMN assignee 决定 DIRECT 的第一默认人员，必须保持最高顺序。
        if (literal(task.getAssignee())) {
            users.add(task.getAssignee().trim());
        } else if (!multiInstance
                && StringUtils.hasText(task.getAssignee())) {
            throw new IllegalArgumentException(
                    "直接办理人使用动态表达式，无法提前解析: "
                            + target.userTask().getId());
        }
        if ("user".equals(type) || "candidate".equals(type)) {
            addCsv(users, config.get("assigneeValue"));
            addCsv(users, config.get("candidateUsers"));
        } else if ("group".equals(type)) {
            addCsv(groups, config.get("assigneeValue"));
        } else if ("role".equals(type)) {
            addCsv(roles, config.get("assigneeValue"));
        }
        addLiteralCandidateUsers(task, users);
        addLiteralCandidateGroups(task, groups, roles);

        List<SysUser> result = new ArrayList<>(resolveUsers(users));
        result.addAll(resolveGroups(groups));
        result.addAll(resolveRoles(roles));
        return dedupeEnabled(result);
    }

    /**
     * 解析解析器；输出作为后续校验或处理的输入。
     *
     * @param resolution 解析，作为 {@code resolveUsers} 的输入影响后续处理
     * @param target 目标，供本方法解析解析器时使用
     * @param resolverCode 解析器编码，后续用于解析解析器时定位或关联目标
     * @param usage 使用场景，作为 {@code resolverRuntimeService.requireConfigured} 的输入影响后续处理
     * @param extraParams 附加参数，供本方法解析解析器时使用
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> resolveWithResolver(
            NextApprovalResolution resolution,
            NextApprovalTarget target,
            String resolverCode,
            PersonResolveUsage usage,
            Map<String, Object> extraParams) {
        resolverRuntimeService.requireConfigured(resolverCode, usage);
        return resolveUsers(resolverRuntimeService.resolveUsernames(
                resolverCode,
                resolverRequest(
                        resolution,
                        target,
                        usage,
                        extraParams)));
    }

    /**
     * 添加字面值候选人用户集合；结果供后续流程传递或持久化。
     *
     * @param task 任务，供本方法添加字面值候选人用户集合时使用
     * @param users 用户集合，供本方法添加字面值候选人用户集合时使用
     */
    private void addLiteralCandidateUsers(
            UserTask task,
            Set<String> users) {
        if (task.getCandidateUsers() != null) {
            task.getCandidateUsers().stream()
                    .filter(this::literal)
                    .map(String::trim)
                    .forEach(users::add);
        }
    }

    /**
     * 添加字面值候选人分组集合；结果供后续流程传递或持久化。
     *
     * @param task 任务，供本方法添加字面值候选人分组集合时使用
     * @param groups 分组集合，供本方法添加字面值候选人分组集合时使用
     * @param roles 角色集合，供本方法添加字面值候选人分组集合时使用
     */
    private void addLiteralCandidateGroups(
            UserTask task,
            Set<String> groups,
            Set<String> roles) {
        if (task.getCandidateGroups() == null) {
            return;
        }
        for (String value : task.getCandidateGroups()) {
            if (!literal(value)) {
                continue;
            }
            String normalized = value.trim();
            if (normalized.startsWith("ROLE_")) {
                roles.add(normalized.substring(5));
            } else {
                groups.add(normalized);
            }
        }
    }

    /**
     * 处理分配配置版本，并将结果传给后续步骤。
     *
     * @param config 配置内容，决定后续分配配置版本的处理规则
     * @return 处理后的分配配置版本结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private int assignmentConfigVersion(Map<String, Object> config) {
        Object raw = config.get("assignmentConfigVersion");
        if (raw == null) {
            return 1;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "assignmentConfigVersion 必须是整数", exception);
        }
    }

    /**
     * 规范化分配类型；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化分配类型的原始输入，结果供调用方继续使用
     * @return 规范化后的分配类型文本，供调用方比较或展示
     */
    private String normalizeAssignmentType(Object value) {
        String type = text(value);
        if (!StringUtils.hasText(type)) {
            return "";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return "interface".equals(normalized)
                ? "resolver" : normalized;
    }

    /**
     * 生成多实例集合变量文本，供后续匹配或展示。
     *
     * @param userTask 用户任务，作为 {@code MultiInstanceVariableNames.resolveCollectionVariable} 的输入影响后续处理
     * @return 处理后的多实例集合变量文本，供调用方比较或展示
     */
    private String multiInstanceCollectionVariable(UserTask userTask) {
        return MultiInstanceVariableNames.resolveCollectionVariable(
                userTask);
    }

    /**
     * 返回完整允许集合，供分页接口与完成任务前的权威重验复用。
     *
     * @param resolution 解析，作为 {@code resolveNodeAssignmentUsers} 的输入影响后续处理
     * @param target 目标，作为 {@code resolveNodeAssignmentUsers} 的输入影响后续处理
     * @param usage 使用场景，供本方法解析允许时使用
     * @return 系统用户集合，供调用方遍历或展示
     */
    public List<SysUser> resolveAllowed(
            NextApprovalResolution resolution,
            NextApprovalTarget target,
            PersonResolveUsage usage) {
        NextApproverSelectionPolicy policy = target.selectionPolicy();
        if (policy.sourceType()
                == NextApproverSelectionPolicy.SourceType.NODE_ASSIGNMENT) {
            // 调用方为独立 RESOLVER 传入 CANDIDATE；NODE_ASSIGNMENT 必须忽略
            // 该 usage，并按目标真实分配模式复用 ASSIGNEE/MULTI_INSTANCE。
            // options 与完成任务重验必须按当前组、角色或解析器范围重算；
            // 启动时的 collection 仅是默认展示快照，不能授权旧人员继续被选。
            return resolveNodeAssignmentUsers(
                    resolution, target, false);
        }
        if (policy.sourceType()
                == NextApproverSelectionPolicy.SourceType.RESOLVER) {
            resolverRuntimeService.requireConfigured(
                    policy.resolverCode(), usage);
            return resolveUsers(resolverRuntimeService.resolveUsernames(
                    policy.resolverCode(),
                    resolverRequest(
                            resolution,
                            target,
                            usage,
                            policy.extraParams())));
        }
        if (policy.sourceType()
                != NextApproverSelectionPolicy.SourceType.SCOPE) {
            throw new IllegalArgumentException(
                    "目标节点未配置可选择的人员数据源");
        }
        List<SysUser> result = new ArrayList<>();
        for (NextApproverSelectionPolicy.Scope scope : policy.scopes()) {
            switch (scope.type()) {
                case ALL_USERS -> result.addAll(allEnabledUsers());
                case USER -> result.addAll(resolveUsers(scope.values()));
                case ROLE -> result.addAll(resolveRoles(scope.values()));
                case GROUP -> result.addAll(resolveGroups(scope.values()));
                case ORGANIZATION -> result.addAll(resolveOrganizations(
                        scope.values(), scope.includeChildren()));
            }
        }
        return dedupeEnabled(result);
    }

    /**
     * 处理解析器请求，并将结果传给后续步骤。
     *
     * @param resolution 解析，供本方法处理解析器请求时使用
     * @param target 目标，供本方法处理解析器请求时使用
     * @param usage 使用场景，供本方法处理解析器请求时使用
     * @param extraParams 附加参数，供本方法处理解析器请求时使用
     * @return 处理后的解析器请求结果，供调用方继续处理
     */
    private PersonResolveRequest resolverRequest(
            NextApprovalResolution resolution,
            NextApprovalTarget target,
            PersonResolveUsage usage,
            Map<String, Object> extraParams) {
        Task task = resolution.task();
        Map<String, Object> variables = resolution.variables();
        return new PersonResolveRequest(
                1,
                text(variables.get("traceId")),
                String.join(
                        ":",
                        "NEXT_APPROVER",
                        usage.name(),
                        task.getId(),
                        target.userTask().getId()),
                usage,
                publishedProcessConfigId(task.getProcessDefinitionId()),
                task.getProcessDefinitionId(),
                task.getProcessInstanceId(),
                firstText(
                        variables.get("businessKey"),
                        variables.get("entityDataId")),
                target.userTask().getId(),
                target.userTask().getName(),
                task.getId(),
                text(variables.get("entityCode")),
                text(variables.get("entityDataId")),
                firstText(
                        variables.get("startUserId"),
                        variables.get("submitterId"),
                        variables.get("initiator")),
                firstText(UserContext.getUserId(), UserContext.getUsername()),
                variables,
                mapValue(variables.get("entityData")),
                extraParams);
    }

    /**
     * 生成已发布流程配置ID文本，供后续匹配或展示。
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @return 处理后的已发布流程配置ID文本，供调用方比较或展示
     */
    private String publishedProcessConfigId(String processDefinitionId) {
        if (repositoryService == null
                || processVersionMapper == null
                || !StringUtils.hasText(processDefinitionId)) {
            return null;
        }
        ProcessDefinition definition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        if (definition == null
                || !StringUtils.hasText(definition.getDeploymentId())) {
            return null;
        }
        return processVersionMapper
                .findByDeploymentId(definition.getDeploymentId())
                .map(history -> history.getProcessConfigId())
                .orElse(null);
    }

    /**
     * 整理全部启用用户集合数据，供调用方遍历或继续处理。
     *
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> allEnabledUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, SysUser.Status.ENABLED.getValue())
                .eq(SysUser::getDeleted, 0));
    }

    /**
     * 解析用户集合；输出作为后续校验或处理的输入。
     *
     * @param keys 键集合，供本方法解析用户集合时使用
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> resolveUsers(Collection<String> keys) {
        List<SysUser> result = new ArrayList<>();
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            SysUser user = userMapper.selectByUsername(key.trim());
            if (user == null) {
                user = userMapper.selectById(key.trim());
            }
            if (enabled(user)) {
                result.add(user);
            }
        }
        return dedupeEnabled(result);
    }

    /**
     * 解析角色集合；输出作为后续校验或处理的输入。
     *
     * @param keys 键集合，供本方法解析角色集合时使用
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> resolveRoles(Collection<String> keys) {
        List<SysUser> result = new ArrayList<>();
        for (String raw : keys) {
            String key = raw != null && raw.startsWith("ROLE_")
                    ? raw.substring(5) : raw;
            if (!StringUtils.hasText(key)) {
                continue;
            }
            List<SysRole> roles = roleMapper.selectList(
                    new LambdaQueryWrapper<SysRole>()
                            .and(wrapper -> wrapper
                                    .eq(SysRole::getId, key)
                                    .or()
                                    .eq(SysRole::getRoleCode, key))
                            .eq(SysRole::getStatus,
                                    SysRole.Status.ENABLED.getValue())
                            .eq(SysRole::getDeleted, 0));
            for (SysRole role : roles) {
                if (role == null
                        || !SysRole.Status.ENABLED.getValue()
                        .equals(role.getStatus())
                        || Integer.valueOf(1).equals(role.getDeleted())) {
                    continue;
                }
                result.addAll(resolveUsers(
                        userRoleMapper.selectUserIdsByRoleId(role.getId())));
            }
        }
        return dedupeEnabled(result);
    }

    /**
     * 解析分组集合；输出作为后续校验或处理的输入。
     *
     * @param keys 键集合，供本方法解析分组集合时使用
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> resolveGroups(Collection<String> keys) {
        List<SysUser> result = new ArrayList<>();
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            List<SysGroup> groups = groupMapper.selectList(
                    new LambdaQueryWrapper<SysGroup>()
                            .and(wrapper -> wrapper
                                    .eq(SysGroup::getId, key)
                                    .or()
                                    .eq(SysGroup::getGroupCode, key))
                            .eq(SysGroup::getStatus,
                                    SysGroup.Status.ENABLED.getValue())
                            .eq(SysGroup::getDeleted, 0));
            for (SysGroup group : groups) {
                if (group == null
                        || !SysGroup.Status.ENABLED.getValue()
                        .equals(group.getStatus())
                        || Integer.valueOf(1).equals(group.getDeleted())) {
                    continue;
                }
                result.addAll(resolveUsers(
                        userGroupMapper.selectUserIdsByGroupId(group.getId())));
            }
        }
        return dedupeEnabled(result);
    }

    /**
     * 解析{@code organizations}；输出作为后续校验或处理的输入。
     *
     * @param keys 键集合，供本方法解析{@code organizations}时使用
     * @param includeChildren {@code include}子节点，供本方法解析{@code organizations}时使用
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> resolveOrganizations(
            Collection<String> keys,
            boolean includeChildren) {
        Set<String> organizationIds = new LinkedHashSet<>();
        for (String key : keys) {
            SysOrganization organization = organizationMapper.selectById(key);
            if (organization == null) {
                organization = organizationMapper.selectByCode(key);
            }
            if (organization == null
                    || !"0".equals(organization.getStatus())) {
                continue;
            }
            organizationIds.add(organization.getId());
            if (includeChildren && StringUtils.hasText(organization.getPath())) {
                organizationMapper.selectAllChildrenByPath(
                                organization.getPath())
                        .stream()
                        .filter(item -> "0".equals(item.getStatus()))
                        .map(SysOrganization::getId)
                        .forEach(organizationIds::add);
            }
        }
        if (organizationIds.isEmpty()) {
            return List.of();
        }
        return dedupeEnabled(userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus,
                                SysUser.Status.ENABLED.getValue())
                        .eq(SysUser::getDeleted, 0)
                        .and(wrapper -> wrapper
                                .in(SysUser::getOrgId, organizationIds)
                                .or()
                                .in(SysUser::getDeptId, organizationIds))));
    }

    /**
     * 整理{@code dedupe}启用数据，供调用方遍历或继续处理。
     *
     * @param source 待处理{@code dedupe}启用的原始输入，结果供调用方继续使用
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> dedupeEnabled(Collection<SysUser> source) {
        LinkedHashMap<String, SysUser> result = new LinkedHashMap<>();
        if (source != null) {
            source.stream()
                    .filter(this::enabled)
                    .forEach(user -> result.putIfAbsent(
                            user.getUsername(), user));
        }
        return new ArrayList<>(result.values());
    }

    /**
     * 判断启用条件是否成立，供调用方选择后续分支。
     *
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 启用条件成立时为 true，否则为 false
     */
    private boolean enabled(SysUser user) {
        return user != null
                && StringUtils.hasText(user.getUsername())
                && SysUser.Status.ENABLED.getValue().equals(user.getStatus())
                && !Integer.valueOf(1).equals(user.getDeleted());
    }

    /**
     * 转换为DTO；输出作为后续校验或处理的输入。
     *
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 转换为后的DTO结果，供调用方继续处理
     */
    private NextApproverCandidateDTO toDto(SysUser user) {
        return new NextApproverCandidateDTO(
                user.getId(),
                user.getUsername(),
                StringUtils.hasText(user.getNickname())
                        ? user.getNickname()
                        : user.getUsername());
    }

    /**
     * 判断是否匹配下一步审批人候选人；判断结果决定调用方的后续分支。
     *
     * @param item 条目，作为 {@code normalizeKeyword} 的输入影响后续处理
     * @param keyword 关键字，供本方法判断是否匹配下一步审批人候选人时使用
     * @return 下一步审批人候选人条件成立时为 true，否则为 false
     */
    private boolean matches(
            NextApproverCandidateDTO item,
            String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return normalizeKeyword(item.getUsername()).contains(keyword)
                || normalizeKeyword(item.getDisplayName()).contains(keyword);
    }

    /**
     * 规范化关键字；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化关键字的原始输入，结果供调用方继续使用
     * @return 规范化后的关键字文本，供调用方比较或展示
     */
    private String normalizeKeyword(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT)
                : "";
    }

    /**
     * 判断字面值条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理字面值的原始输入，结果供调用方继续使用
     * @return 字面值条件成立时为 true，否则为 false
     */
    private boolean literal(String value) {
        return StringUtils.hasText(value)
                && !value.contains("${")
                && !value.contains("#{");
    }

    /**
     * 添加CSV；结果供后续流程传递或持久化。
     *
     * @param target 目标，供本方法添加CSV时使用
     * @param value 待添加CSV的原始输入，结果供调用方继续使用
     */
    private void addCsv(Set<String> target, Object value) {
        if (value instanceof Collection<?> collection) {
            collection.stream()
                    .map(this::text)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(target::add);
            return;
        }
        String text = text(value);
        if (StringUtils.hasText(text)) {
            for (String item : text.split(",")) {
                if (StringUtils.hasText(item)) {
                    target.add(item.trim());
                }
            }
        }
    }

    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param value 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            String result = text(value);
            if (StringUtils.hasText(result)) {
                return result.trim();
            }
        }
        return null;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 校验并获取文本；不满足约束时阻止后续处理。
     *
     * @param value 待校验并获取文本的原始输入，结果供调用方继续使用
     * @param message 消息，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
