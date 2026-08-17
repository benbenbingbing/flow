package com.workflow.process.task.application.nextapproval;

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
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.core.result.PageResult;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser.LegacyAssignment;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.task.api.request.NextApprovalPreviewRequest;
import com.workflow.process.task.api.request.NextApproverOptionsRequest;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.UserTask;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
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
        return defaults;
    }

    /**
     * 展开目标节点自身的完整办理人集合。
     *
     * <p>DIRECT 的“只取第一人”由调用方在默认值投影时处理；本方法始终返回
     * 完整集合，NODE_ASSIGNMENT 的候选范围和提交重验才能使用同一边界。</p>
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
        if (preferPreparedMultiInstanceSnapshot) {
            List<SysUser> prepared = preparedMultiInstanceUsers(
                    resolution, target);
            if (prepared != null) {
                return prepared;
            }
        }
        boolean multiInstance = "MULTI_INSTANCE".equals(
                target.selectionPolicy().assignmentMode());
        LegacyAssignment legacyAssignment =
                LegacyMultiInstanceAssignmentParser.parse(config);
        boolean sourceUsesLegacyMultiInstance =
                target.assignmentSourceTask()
                        .hasMultiInstanceLoopCharacteristics();
        if ((multiInstance || sourceUsesLegacyMultiInstance)
                && version < 2
                && legacyAssignment.effective()) {
            return resolveLegacyMultiInstanceUsers(
                    resolution, target, legacyAssignment);
        }
        return resolveBaseAssignmentUsers(resolution, target, config);
    }

    /**
     * 已在流程启动时准备的多实例集合是该实例的人员快照，优先于重新展开
     * 可变的组、角色或解析器结果；返回 null 表示变量尚未准备。
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

    private String normalizeAssignmentType(Object value) {
        String type = text(value);
        if (!StringUtils.hasText(type)) {
            return "";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return "interface".equals(normalized)
                ? "resolver" : normalized;
    }

    private String multiInstanceCollectionVariable(UserTask userTask) {
        MultiInstanceLoopCharacteristics loop =
                userTask.getLoopCharacteristics();
        if (loop == null) {
            return null;
        }
        String expression = StringUtils.hasText(loop.getInputDataItem())
                ? loop.getInputDataItem()
                : loop.getCollectionString();
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        String value = expression.trim();
        if ((value.startsWith("${") || value.startsWith("#{"))
                && value.endsWith("}")) {
            value = value.substring(2, value.length() - 1).trim();
        }
        return value.matches("[A-Za-z_][A-Za-z0-9_]*")
                ? value : null;
    }

    /**
     * 返回完整允许集合，供分页接口与完成任务前的权威重验复用。
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

    private List<SysUser> allEnabledUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, SysUser.Status.ENABLED.getValue())
                .eq(SysUser::getDeleted, 0));
    }

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

    private boolean enabled(SysUser user) {
        return user != null
                && StringUtils.hasText(user.getUsername())
                && SysUser.Status.ENABLED.getValue().equals(user.getStatus())
                && !Integer.valueOf(1).equals(user.getDeleted());
    }

    private NextApproverCandidateDTO toDto(SysUser user) {
        return new NextApproverCandidateDTO(
                user.getId(),
                user.getUsername(),
                StringUtils.hasText(user.getNickname())
                        ? user.getNickname()
                        : user.getUsername());
    }

    private boolean matches(
            NextApproverCandidateDTO item,
            String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return normalizeKeyword(item.getUsername()).contains(keyword)
                || normalizeKeyword(item.getDisplayName()).contains(keyword);
    }

    private String normalizeKeyword(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT)
                : "";
    }

    private boolean literal(String value) {
        return StringUtils.hasText(value)
                && !value.contains("${")
                && !value.contains("#{");
    }

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String result = text(value);
            if (StringUtils.hasText(result)) {
                return result.trim();
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
