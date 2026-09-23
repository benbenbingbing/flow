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
import com.workflow.contracts.process.assignment.model.PersonResolveRequest;
import com.workflow.contracts.process.assignment.model.PersonResolveUsage;
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
     * @param processConfigId 流程配置ID，后续用于解析多实例分配解析器时定位或关联目标
     * @param nodeId 节点ID，后续用于解析多实例分配解析器时定位或关联目标
     * @param nodeName 节点名称，后续用于解析多实例分配解析器时匹配或展示
     * @param config 配置内容，决定后续多实例分配解析器的处理规则
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param assignmentVersion 缺省历史配置为 1，统一基础配置为 2
     * @param multiInstanceSource 生效规则源是否真实为多实例 UserTask
     * @return 多实例分配解析器集合，供调用方遍历或展示
     */
    List<String> resolve(
            String processConfigId,
            String nodeId,
            String nodeName,
            Map<String, Object> config,
            Map<String, Object> variables,
            String processInstanceId,
            String processDefinitionId,
            int assignmentVersion,
            boolean multiInstanceSource) {
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
        if (!LegacyMultiInstanceAssignmentParser
                .usesLegacyMultiInstanceAssignment(
                        config, multiInstanceSource)) {
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

    /**
     * v2 普通任务与多人办理共同使用的基础办理人投影。
     *
     * @param processConfigId 流程配置ID，后续用于解析基础分配时定位或关联目标
     * @param nodeId 节点ID，后续用于解析基础分配时定位或关联目标
     * @param nodeName 节点名称，后续用于解析基础分配时匹配或展示
     * @param config 配置内容，决定后续基础分配的处理规则
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @return 多实例分配解析器集合，供调用方遍历或展示
     */
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

    /**
     * 受控解析器在真实多实例语境中始终使用 MULTI_INSTANCE 用途。
     *
     * @param processConfigId 流程配置ID，后续用于解析解析器时定位或关联目标
     * @param nodeId 节点ID，后续用于解析解析器时定位或关联目标
     * @param nodeName 节点名称，后续用于解析解析器时匹配或展示
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param resolverCode 解析器编码，后续用于解析解析器时定位或关联目标
     * @param extraParams 附加参数，供本方法解析解析器时使用
     * @return 多实例分配解析器集合，供调用方遍历或展示
     */
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

    /**
     * 解析{@code static}分配；输出作为后续校验或处理的输入。
     *
     * @param users 用户集合，作为 {@code addAll} 的输入影响后续处理
     * @param groups 分组集合，供本方法解析{@code static}分配时使用
     * @param roles 角色集合，供本方法解析{@code static}分配时使用
     * @return 多实例分配解析器集合，供调用方遍历或展示
     */
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

    /**
     * 添加候选人分组集合；结果供后续流程传递或持久化。
     *
     * @param groups 分组集合，供本方法添加候选人分组集合时使用
     * @param roles 角色集合，供本方法添加候选人分组集合时使用
     * @param raw 待添加候选人分组集合的原始输入，结果供调用方继续使用
     */
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

    /**
     * 添加多实例分配解析器全部；结果供后续流程传递或持久化。
     *
     * @param target 目标，供本方法添加多实例分配解析器全部时使用
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     */
    private void addAll(
            Collection<String> target,
            Collection<String> values) {
        if (values != null) {
            target.addAll(values);
        }
    }

    /**
     * 将用户名或用户 ID 统一映射为启用且未删除的本地用户名。
     *
     * @param keys 键集合，供本方法解析启用{@code usernames}时使用
     * @return 多实例分配解析器集合，供调用方遍历或展示
     */
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

    /**
     * 判断启用条件是否成立，供调用方选择后续分支。
     *
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 启用条件成立时为 true，否则为 false
     */
    private boolean enabled(SysUser user) {
        return user != null
                && SysUser.Status.ENABLED.getValue().equals(user.getStatus())
                && !Integer.valueOf(1).equals(user.getDeleted())
                && StringUtils.hasText(user.getUsername());
    }

    /**
     * 判断启用条件是否成立，供调用方选择后续分支。
     *
     * @param group 分组，供本方法处理启用时使用
     * @return 启用条件成立时为 true，否则为 false
     */
    private boolean enabled(SysGroup group) {
        return group != null
                && SysGroup.Status.ENABLED.getValue().equals(group.getStatus())
                && !Integer.valueOf(1).equals(group.getDeleted());
    }

    /**
     * 判断启用条件是否成立，供调用方选择后续分支。
     *
     * @param role 角色，供本方法处理启用时使用
     * @return 启用条件成立时为 true，否则为 false
     */
    private boolean enabled(SysRole role) {
        return role != null
                && SysRole.Status.ENABLED.getValue().equals(role.getStatus())
                && !Integer.valueOf(1).equals(role.getDeleted());
    }

    /**
     * 添加CSV；结果供后续流程传递或持久化。
     *
     * @param target 目标，供本方法添加CSV时使用
     * @param raw 待添加CSV的原始输入，结果供调用方继续使用
     */
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

    /**
     * 规范化分配类型；输出作为后续校验或处理的输入。
     *
     * @param raw 待规范化分配类型的原始输入，结果供调用方继续使用
     * @return 规范化后的分配类型文本，供调用方比较或展示
     */
    private String normalizeAssignmentType(Object raw) {
        String type = text(raw);
        if (!StringUtils.hasText(type)) {
            return "";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return "interface".equals(normalized)
                ? "resolver" : normalized;
    }

    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param value 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?>
                ? (Map<String, Object>) value : Map.of();
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
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
     * 生成空值安全文本，供后续匹配或展示。
     *
     * @param value 待处理空值安全的原始输入，结果供调用方继续使用
     * @return 处理后的空值安全文本，供调用方比较或展示
     */
    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
