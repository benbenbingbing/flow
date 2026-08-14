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
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.task.api.request.NextApprovalPreviewRequest;
import com.workflow.process.task.api.request.NextApproverOptionsRequest;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.UserTask;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.task.api.Task;
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
        if ("MULTI_INSTANCE".equals(
                target.selectionPolicy().assignmentMode())) {
            String collectionVariable = multiInstanceCollectionVariable(
                    target.userTask());
            if (StringUtils.hasText(collectionVariable)
                    && resolution.variables().containsKey(
                    collectionVariable)) {
                Object value = resolution.variables().get(
                        collectionVariable);
                if (!(value instanceof Collection<?> collection)) {
                    throw new IllegalArgumentException(
                            "多实例集合变量不是人员列表: "
                                    + collectionVariable);
                }
                LinkedHashSet<String> keys = new LinkedHashSet<>();
                collection.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(String::valueOf)
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .forEach(keys::add);
                return resolveUsers(keys).stream()
                        .map(this::toDto)
                        .toList();
            }
        }
        Map<String, Object> config = target.assigneeConfig();
        UserTask userTask = target.userTask();
        String type = text(config.get("assigneeType"));
        String configuredAssignee = text(config.get("assigneeValue"));
        if ("DIRECT".equals(target.selectionPolicy().assignmentMode())) {
            List<SysUser> primary = null;
            if (StringUtils.hasText(userTask.getAssignee())) {
                if (!literal(userTask.getAssignee())) {
                    throw new IllegalArgumentException(
                            "直接办理人使用动态表达式，无法提前解析: "
                                    + userTask.getId());
                }
                primary = resolveUsers(List.of(userTask.getAssignee()));
            } else if ("user".equalsIgnoreCase(type)
                    && StringUtils.hasText(configuredAssignee)) {
                LinkedHashSet<String> configuredAssignees =
                        new LinkedHashSet<>();
                addCsv(configuredAssignees,
                        config.get("assigneeValue"));
                primary = resolveUsers(configuredAssignees);
            } else if ("interface".equalsIgnoreCase(type)
                    || "resolver".equalsIgnoreCase(type)) {
                String resolverCode = firstText(
                        config.get("resolverCode"),
                        config.get("interfaceName"));
                resolverRuntimeService.requireConfigured(
                        resolverCode, PersonResolveUsage.ASSIGNEE);
                primary = resolveUsers(
                        resolverRuntimeService.resolveUsernames(
                                resolverCode,
                                resolverRequest(
                                        resolution,
                                        target,
                                        PersonResolveUsage.ASSIGNEE,
                                        mapValue(config.get("extraParams")))));
            }
            if (primary != null) {
                return primary.stream()
                        .limit(1)
                        .map(this::toDto)
                        .toList();
            }
        }
        LinkedHashSet<String> userKeys = new LinkedHashSet<>();
        addCsv(userKeys, config.get("multiInstanceUsernames"));
        addCsv(userKeys, config.get("candidateUsers"));
        if ("user".equalsIgnoreCase(type)) {
            addCsv(userKeys, config.get("assigneeValue"));
        }

        List<SysUser> result = new ArrayList<>(resolveUsers(userKeys));
        LinkedHashSet<String> groupKeys = new LinkedHashSet<>();
        LinkedHashSet<String> roleKeys = new LinkedHashSet<>();
        addCsv(groupKeys, config.get("multiInstanceGroupCodes"));
        addCsv(roleKeys, config.get("multiInstanceRoleCodes"));
        if ("group".equalsIgnoreCase(type)) {
            addCsv(groupKeys, config.get("assigneeValue"));
        } else if ("role".equalsIgnoreCase(type)) {
            addCsv(roleKeys, config.get("assigneeValue"));
        }

        if (userTask.getCandidateUsers() != null) {
            userTask.getCandidateUsers().stream()
                    .filter(this::literal)
                    .forEach(userKeys::add);
        }
        if (literal(userTask.getAssignee())) {
            userKeys.add(userTask.getAssignee());
        }
        if (userTask.getCandidateGroups() != null) {
            for (String value : userTask.getCandidateGroups()) {
                if (!literal(value)) {
                    continue;
                }
                if (value.startsWith("ROLE_")) {
                    roleKeys.add(value.substring(5));
                } else {
                    groupKeys.add(value);
                }
            }
        }
        result.addAll(resolveUsers(userKeys));
        result.addAll(resolveRoles(roleKeys));
        result.addAll(resolveGroups(groupKeys));

        String collectionSource = text(config.get("collectionSource"));
        boolean collectionResolver = "MULTI_INSTANCE".equals(
                target.selectionPolicy().assignmentMode())
                && ("interface".equalsIgnoreCase(collectionSource)
                || "resolver".equalsIgnoreCase(collectionSource));
        if (collectionResolver) {
            String resolverCode = firstText(
                    config.get("collectionResolverCode"),
                    config.get("collectionInterface"));
            resolverRuntimeService.requireConfigured(
                    resolverCode, PersonResolveUsage.MULTI_INSTANCE);
            List<String> usernames = resolverRuntimeService.resolveUsernames(
                    resolverCode,
                    resolverRequest(
                            resolution,
                            target,
                            PersonResolveUsage.MULTI_INSTANCE,
                            mapValue(config.get("collectionExtraParams"))));
            result.addAll(resolveUsers(usernames));
        } else if ("interface".equalsIgnoreCase(type)
                || "resolver".equalsIgnoreCase(type)) {
            String resolverCode = firstText(
                    config.get("resolverCode"),
                    config.get("interfaceName"));
            resolverRuntimeService.requireConfigured(
                    resolverCode, PersonResolveUsage.ASSIGNEE);
            List<String> usernames = resolverRuntimeService.resolveUsernames(
                    resolverCode,
                    resolverRequest(
                            resolution,
                            target,
                            PersonResolveUsage.ASSIGNEE,
                            mapValue(config.get("extraParams"))));
            result.addAll(resolveUsers(usernames));
        }
        List<NextApproverCandidateDTO> defaults = dedupeEnabled(result).stream()
                .map(this::toDto)
                .toList();
        if ("DIRECT".equals(
                target.selectionPolicy().assignmentMode())
                && defaults.size() > 1) {
            return List.of(defaults.get(0));
        }
        return defaults;
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
                null,
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
