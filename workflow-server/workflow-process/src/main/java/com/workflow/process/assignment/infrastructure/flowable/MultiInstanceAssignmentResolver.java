package com.workflow.process.assignment.infrastructure.flowable;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser.LegacyAssignment;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 从已部署节点配置解析多实例参与人。
 *
 * <p>该组件只负责 assignmentConfig 的 legacy/v2、解析器及组角色展开；
 * 事件时机、覆盖消费和流程变量写入仍由监听器编排，避免安全关键的人员
 * 语义继续膨胀在 Flowable 事件类中。</p>
 */
@Component
@RequiredArgsConstructor
class MultiInstanceAssignmentResolver {

    private final SysGroupMapper groupMapper;
    private final SysUserGroupMapper userGroupMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final PersonResolverRuntimeService personResolverRuntimeService;

    /**
     * 解析目标节点的启用本地用户名，并保持配置或解析器首次出现顺序。
     *
     * @param assignmentVersion 缺省历史配置为 1，统一基础配置为 2
     */
    List<String> resolve(
            String processConfigId,
            String nodeId,
            String nodeName,
            Map<String, Object> config,
            Map<String, Object> variables,
            String processInstanceId,
            String processDefinitionId,
            int assignmentVersion) {
        // v2 显式切换到基础办理人。残留的历史字段不得再次并入，否则
        // 设计器所见配置与实际会签参与人会发生漂移。
        if (assignmentVersion == 2) {
            return resolveBaseAssignment(
                    processConfigId,
                    nodeId,
                    nodeName,
                    config,
                    variables,
                    processInstanceId,
                    processDefinitionId);
        }
        LegacyAssignment legacy =
                LegacyMultiInstanceAssignmentParser.parse(config);
        if (!legacy.effective()) {
            return resolveBaseAssignment(
                    processConfigId,
                    nodeId,
                    nodeName,
                    config,
                    variables,
                    processInstanceId,
                    processDefinitionId);
        }
        if (legacy.resolver()) {
            return resolveWithResolver(
                    processConfigId,
                    nodeId,
                    nodeName,
                    variables,
                    processInstanceId,
                    processDefinitionId,
                    legacy.resolverCode(),
                    legacy.resolverExtraParams());
        }
        return resolveStaticAssignment(
                new LinkedHashSet<>(legacy.userKeys()),
                new LinkedHashSet<>(legacy.groupKeys()),
                new LinkedHashSet<>(legacy.roleKeys()));
    }

    /** v2 普通任务与多人办理共同使用的基础办理人投影。 */
    private List<String> resolveBaseAssignment(
            String processConfigId,
            String nodeId,
            String nodeName,
            Map<String, Object> config,
            Map<String, Object> variables,
            String processInstanceId,
            String processDefinitionId) {
        String type = normalizeAssignmentType(config.get("assigneeType"));
        if ("expression".equals(type)) {
            throw new IllegalArgumentException(
                    "v2 多实例不支持无法安全枚举的表达式办理人: "
                            + nodeId);
        }
        if ("resolver".equals(type)) {
            return resolveWithResolver(
                    processConfigId,
                    nodeId,
                    nodeName,
                    variables,
                    processInstanceId,
                    processDefinitionId,
                    firstText(
                            config.get("resolverCode"),
                            config.get("interfaceName")),
                    mapValue(config.get("extraParams")));
        }
        LinkedHashSet<String> users = new LinkedHashSet<>();
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        if ("user".equals(type) || "candidate".equals(type)) {
            addCsv(users, config.get("assigneeValue"));
            addCsv(users, config.get("candidateUsers"));
        } else if ("group".equals(type)) {
            addCsv(groups, config.get("assigneeValue"));
        } else if ("role".equals(type)) {
            addCsv(roles, config.get("assigneeValue"));
        } else {
            throw new IllegalArgumentException(
                    "v2 多实例缺少可用的基础办理人类型: " + nodeId);
        }
        addCandidateGroups(
                groups, roles, config.get("candidateGroups"));
        return resolveStaticAssignment(users, groups, roles);
    }

    /** 受控解析器在真实多实例语境中始终使用 MULTI_INSTANCE 用途。 */
    private List<String> resolveWithResolver(
            String processConfigId,
            String nodeId,
            String nodeName,
            Map<String, Object> variables,
            String processInstanceId,
            String processDefinitionId,
            String resolverCode,
            Map<String, Object> extraParams) {
        personResolverRuntimeService.requireConfigured(
                resolverCode, PersonResolveUsage.MULTI_INSTANCE);
        return resolveEnabledUsernames(
                personResolverRuntimeService.resolveUsernames(
                        resolverCode,
                        new PersonResolveRequest(
                                1,
                                text(variables.get("traceId")),
                                String.join(
                                        ":",
                                        "MULTI_INSTANCE",
                                        nullSafe(processInstanceId),
                                        nullSafe(nodeId)),
                                PersonResolveUsage.MULTI_INSTANCE,
                                processConfigId,
                                processDefinitionId,
                                processInstanceId,
                                firstText(
                                        variables.get("businessKey"),
                                        variables.get("entityDataId")),
                                nodeId,
                                nodeName,
                                null,
                                text(variables.get("entityCode")),
                                text(variables.get("entityDataId")),
                                firstText(
                                        variables.get("startUserId"),
                                        variables.get("submitterId"),
                                        variables.get("initiator")),
                                null,
                                variables,
                                mapValue(variables.get("entityData")),
                                extraParams)));
    }

    private List<String> resolveStaticAssignment(
            LinkedHashSet<String> users,
            LinkedHashSet<String> groups,
            LinkedHashSet<String> roles) {
        for (String groupCode : groups) {
            SysGroup group = groupMapper.selectByGroupCode(groupCode);
            if (group == null) {
                group = groupMapper.selectById(groupCode);
            }
            if (enabled(group)) {
                addAll(users, userGroupMapper.selectUserIdsByGroupId(
                        group.getId()));
            }
        }
        for (String rawRoleCode : roles) {
            String roleCode = rawRoleCode.startsWith("ROLE_")
                    ? rawRoleCode.substring(5) : rawRoleCode;
            List<SysRole> matches = roleMapper.selectList(
                    new QueryWrapper<SysRole>()
                            .and(wrapper -> wrapper
                                    .eq("id", roleCode)
                                    .or()
                                    .eq("role_code", roleCode))
                            .eq("status", SysRole.Status.ENABLED.getValue())
                            .eq("deleted", 0));
            SysRole role = matches == null
                    ? null
                    : matches.stream()
                    .filter(this::enabled)
                    .findFirst()
                    .orElse(null);
            if (role != null) {
                addAll(users, userRoleMapper.selectUserIdsByRoleId(
                        role.getId()));
            }
        }
        return resolveEnabledUsernames(users);
    }

    private void addCandidateGroups(
            Set<String> groups,
            Set<String> roles,
            Object raw) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        addCsv(values, raw);
        for (String value : values) {
            if (value.startsWith("ROLE_")) {
                roles.add(value.substring(5));
            } else {
                groups.add(value);
            }
        }
    }

    private void addAll(
            Collection<String> target,
            Collection<String> values) {
        if (values != null) {
            target.addAll(values);
        }
    }

    /** 将用户名或用户 ID 统一映射为启用且未删除的本地用户名。 */
    private List<String> resolveEnabledUsernames(
            Collection<String> keys) {
        LinkedHashSet<String> usernames = new LinkedHashSet<>();
        if (keys == null) {
            return List.of();
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String normalized = key.trim();
            SysUser user = userMapper.selectByUsername(normalized);
            if (user == null) {
                user = userMapper.selectById(normalized);
            }
            if (enabled(user)) {
                usernames.add(user.getUsername());
            }
        }
        return new ArrayList<>(usernames);
    }

    private boolean enabled(SysUser user) {
        return user != null
                && SysUser.Status.ENABLED.getValue().equals(user.getStatus())
                && !Integer.valueOf(1).equals(user.getDeleted())
                && StringUtils.hasText(user.getUsername());
    }

    private boolean enabled(SysGroup group) {
        return group != null
                && SysGroup.Status.ENABLED.getValue().equals(group.getStatus())
                && !Integer.valueOf(1).equals(group.getDeleted());
    }

    private boolean enabled(SysRole role) {
        return role != null
                && SysRole.Status.ENABLED.getValue().equals(role.getStatus())
                && !Integer.valueOf(1).equals(role.getDeleted());
    }

    private void addCsv(Set<String> target, Object raw) {
        if (raw instanceof Collection<?> values) {
            values.stream()
                    .map(this::text)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(target::add);
            return;
        }
        String value = text(raw);
        if (!StringUtils.hasText(value)) {
            return;
        }
        for (String item : value.split(",")) {
            if (StringUtils.hasText(item)) {
                target.add(item.trim());
            }
        }
    }

    private String normalizeAssignmentType(Object raw) {
        String type = text(raw);
        if (!StringUtils.hasText(type)) {
            return "";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return "interface".equals(normalized)
                ? "resolver" : normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?>
                ? (Map<String, Object>) value : Map.of();
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
