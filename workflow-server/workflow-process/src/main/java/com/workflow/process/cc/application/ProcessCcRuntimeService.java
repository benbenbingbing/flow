package com.workflow.process.cc.application;

import com.workflow.contracts.process.cc.model.CcRuntimeContext;

import com.workflow.contracts.process.cc.spi.CcRecipientProvider;

import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import com.workflow.process.task.application.TaskIdentityAccessService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.workflow.core.error.ForbiddenException;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.contracts.process.assignment.model.PersonResolveRequest;
import com.workflow.contracts.process.assignment.model.PersonResolveUsage;
import com.workflow.process.cc.api.request.TaskCcRequest;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 流程知会运行时服务。
 *
 * <p>负责知会（抄送）的运行时处理：包括人工知会、基于配置的自动知会触发，
 * 以及知会人员的多维度解析（用户、角色、组、部门、历史审批人、实体字段、自定义解析器等）。
 * 解析结果写入知会记录并入队发送箱。</p>
 */
@Service
@RequiredArgsConstructor
public class ProcessCcRuntimeService {
    private final TaskService taskService;
    private final ProcessTaskMapper processTaskMapper;
    private final ProcessOperationLogMapper operationLogMapper;
    private final ProcessCcService ccService;
    /** 知会通知发布器 */
    private final ProcessCcNotificationPublisher notificationPublisher;
    /** 知会配置服务（读取节点知会配置） */
    private final ProcessCcConfigService configService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysGroupMapper groupMapper;
    private final SysUserGroupMapper userGroupMapper;
    private final SysOrganizationMapper organizationMapper;
    private final ObjectMapper objectMapper;
    /** 自定义知会人员解析器列表 */
    private final List<CcRecipientProvider> customResolvers;
    /** 统一人员解析器运行时 */
    private final PersonResolverRuntimeService personResolverRuntimeService;
    /** 人工知会与任务认领使用一致的业务身份、候选用户及候选组校验。 */
    private final TaskIdentityAccessService taskIdentityAccessService;

    /**
     * 人工知会：办理人手动添加知会人员。
     *
     * <p>校验任务与权限 -> 构建运行时上下文 -> 解析直接用户 -> 创建知会记录并入队 -> 记录操作日志。</p>
     *
     * @param taskId  任务ID
     * @param request 知会请求（含知会人员与备注）
     * @return 创建的知会记录数
     * @throws IllegalArgumentException 任务不存在时抛出
     * @throws ForbiddenException       当前节点未开放人工知会或用户无权限时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.CC,
            operation = "手工知会流程任务",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "PROCESS_TASK",
            targetIdArg = 0)
    public int manualCc(String taskId, TaskCcRequest request) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或已处理: " + taskId);
        }
        String operator = requireOperator(task);
        if (!isManualCcAllowed(task)) {
            throw new ForbiddenException("当前节点未开放人工知会");
        }
        ProcessTask mirror = processTaskMapper.selectByTaskId(taskId);
        if (mirror == null) {
            throw new IllegalStateException("任务镜像不存在，请先同步流程任务");
        }
        CcRuntimeContext context = new CcRuntimeContext(
                task.getProcessInstanceId(),
                task.getProcessDefinitionId(),
                mirror.getProcessKey(),
                mirror.getProcessName(),
                mirror.getBusinessKey(),
                task.getTaskDefinitionKey(),
                task.getName(),
                "MANUAL",
                operator,
                Map.of());
        List<SysUser> recipients = resolveDirectUsers(request.getUserIds());
        int created = createRecords(context, recipients, "MANUAL", request.getComment(), null, List.of("IN_APP"), taskId);
        if (created > 0) {
            ProcessOperationLog log = new ProcessOperationLog();
            log.setProcessInstanceId(task.getProcessInstanceId());
            log.setTaskId(taskId);
            log.setOperationType("CC");
            log.setOperatorId(operator);
            log.setOperatorName(operator);
            log.setOperationTime(java.time.LocalDateTime.now());
            log.setOperationComment(request.getComment());
            log.setNewValue(writeJson(Map.of(
                    "recipients", recipients.stream().map(SysUser::getUsername).toList(),
                    "created", created)));
            log.setNewValueFormat("JSON");
            log.setCreatedAt(java.time.LocalDateTime.now());
            operationLogMapper.insert(log);
        }
        return created;
    }

    /**
     * 判断当前任务是否允许人工知会。
     *
     * <p>未配置时默认允许；配置 allowManualCc 为 false 时禁止。</p>
     *
     * @param taskId 任务ID
     * @return true 表示允许人工知会
     */
    @Transactional(readOnly = true)
    public boolean isManualCcAllowed(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return false;
        }
        return isManualCcAllowed(task);
    }

    /**
     * 基于知会配置自动触发知会。
     *
     * <p>解析配置JSON：校验启用与时机匹配 -> 按规则解析收件人 -> 排除操作人（可选） ->
     * 创建知会记录并入队指定渠道。</p>
     *
     * @param context    知会运行时上下文
     * @param configJson 知会配置JSON
     * @return 创建的知会记录数（配置为空或未启用/时机不匹配时返回0）
     * @throws IllegalArgumentException 配置解析失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public int trigger(CcRuntimeContext context, String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return 0;
        }
        try {
            JsonNode config = objectMapper.readTree(configJson);
            if (!config.path("enabled").asBoolean(false) || !timingMatches(config.path("timings"), context.timing())) {
                return 0;
            }
            LinkedHashMap<String, SysUser> recipients = new LinkedHashMap<>();
            JsonNode rules = config.path("recipientRules");
            if (rules.isArray()) {
                rules.forEach(rule -> resolveRule(rule, context).forEach(user -> recipients.putIfAbsent(user.getUsername(), user)));
            }
            if (!config.path("includeOperator").asBoolean(false) && StringUtils.hasText(context.operatorId())) {
                SysUser operator = findUser(context.operatorId());
                if (operator != null) {
                    recipients.remove(operator.getUsername());
                }
            }
            List<String> channels = stringList(config.path("channels"));
            String summary = config.path("summary").asText(null);
            return createRecords(
                    context,
                    new ArrayList<>(recipients.values()),
                    "AUTO",
                    summary,
                    configJson,
                    channels,
                    context.nodeId());
        } catch (Exception exception) {
            throw new IllegalArgumentException("知会配置解析失败: " + exception.getMessage(), exception);
        }
    }

    /**
     * 按规则类型解析知会收件人（USER/ROLE/GROUP/DEPARTMENT/STARTER/HISTORY_APPROVERS 等）
     *
     * @param rule 规则，作为 {@code stringList} 的输入影响后续处理
     * @param context 执行上下文，向后续规则步骤传递身份、配置或状态
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> resolveRule(JsonNode rule, CcRuntimeContext context) {
        String type = rule.path("type").asText("").toUpperCase(Locale.ROOT);
        List<String> values = stringList(rule.path("values"));
        return switch (type) {
            case "USER" -> resolveDirectUsers(values);
            case "ROLE" -> resolveRoles(values);
            case "GROUP" -> resolveGroups(values);
            case "DEPARTMENT", "DEPT", "ORGANIZATION", "ORG" ->
                    resolveOrganizations(values, rule.path("includeChildren").asBoolean(false));
            case "STARTER" -> resolveDirectUsers(List.of(firstText(
                    context.variables().get("startUserId"),
                    context.variables().get("submitterId"),
                    context.variables().get("initiator"))));
            case "CURRENT_ASSIGNEE" -> resolveDirectUsers(List.of(context.operatorId()));
            case "HISTORY_APPROVERS" -> processTaskMapper.selectByProcessInstance(context.processInstanceId()).stream()
                    .filter(task -> ProcessTask.STATUS_DONE.equals(task.getStatus()))
                    .map(ProcessTask::getAssigneeId)
                    .filter(StringUtils::hasText)
                    .flatMap(value -> resolveDirectUsers(List.of(value)).stream())
                    .toList();
            case "ENTITY_FIELD" -> resolveDirectUsers(valuesFromVariable(
                    context.variables().get(rule.path("fieldCode").asText())));
            case "RESOLVER" -> resolveCustom(rule, context);
            default -> List.of();
        };
    }

    /**
     * 判断当前任务节点是否允许人工知会（读取节点知会配置 allowManualCc）
     *
     * @param task 任务，作为 {@code configService.findConfig} 的输入影响后续处理
     * @return 人工抄送允许条件成立时为 true，否则为 false
     */
    private boolean isManualCcAllowed(Task task) {
        String configJson = configService.findConfig(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
        if (!StringUtils.hasText(configJson)) {
            return true;
        }
        try {
            return objectMapper.readTree(configJson).path("allowManualCc").asBoolean(true);
        } catch (Exception exception) {
            throw new IllegalArgumentException("知会配置解析失败: " + exception.getMessage(), exception);
        }
    }

    /**
     * 解析角色集合；输出作为后续校验或处理的输入。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> resolveRoles(List<String> values) {
        LinkedHashMap<String, SysUser> users = new LinkedHashMap<>();
        for (String value : values) {
            List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                    .and(wrapper -> wrapper.eq(SysRole::getId, value).or().eq(SysRole::getRoleCode, value))
                    .eq(SysRole::getDeleted, 0));
            for (SysRole role : roles) {
                resolveDirectUsers(userRoleMapper.selectUserIdsByRoleId(role.getId()))
                        .forEach(user -> users.putIfAbsent(user.getUsername(), user));
            }
        }
        return new ArrayList<>(users.values());
    }

    /**
     * 解析分组集合；输出作为后续校验或处理的输入。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> resolveGroups(List<String> values) {
        LinkedHashMap<String, SysUser> users = new LinkedHashMap<>();
        for (String value : values) {
            List<SysGroup> groups = groupMapper.selectList(new LambdaQueryWrapper<SysGroup>()
                    .and(wrapper -> wrapper.eq(SysGroup::getId, value).or().eq(SysGroup::getGroupCode, value))
                    .eq(SysGroup::getStatus, SysGroup.Status.ENABLED.getValue())
                    .eq(SysGroup::getDeleted, 0));
            for (SysGroup group : groups) {
                // 运行时再做一次状态校验，避免异常数据或替代 Mapper 绕过查询条件。
                if (group == null
                        || !SysGroup.Status.ENABLED.getValue().equals(group.getStatus())
                        || Integer.valueOf(1).equals(group.getDeleted())) {
                    continue;
                }
                resolveDirectUsers(userGroupMapper.selectUserIdsByGroupId(group.getId()))
                        .forEach(user -> users.putIfAbsent(user.getUsername(), user));
            }
        }
        return new ArrayList<>(users.values());
    }

    /**
     * 解析组织/部门收件人，可选包含子组织，仅取启用状态用户
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param includeChildren {@code include}子节点，供本方法解析{@code organizations}时使用
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> resolveOrganizations(List<String> values, boolean includeChildren) {
        LinkedHashSet<String> organizationIds = new LinkedHashSet<>();
        for (String value : values) {
            SysOrganization organization = organizationMapper.selectById(value);
            if (organization == null) {
                organization = organizationMapper.selectByCode(value);
            }
            if (organization == null || !"0".equals(organization.getStatus())) {
                continue;
            }
            organizationIds.add(organization.getId());
            if (includeChildren && StringUtils.hasText(organization.getPath())) {
                organizationMapper.selectAllChildrenByPath(organization.getPath()).stream()
                        .map(SysOrganization::getId)
                        .forEach(organizationIds::add);
            }
        }
        if (organizationIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, SysUser.Status.ENABLED.getValue())
                .eq(SysUser::getDeleted, 0)
                .and(wrapper -> wrapper.in(SysUser::getDeptId, organizationIds)
                        .or().in(SysUser::getOrgId, organizationIds)));
    }

    /**
     * 调用自定义解析器（按 resolverCode 匹配）解析知会人员
     *
     * @param rule 规则，作为 {@code readResolverExtraParams} 的输入影响后续处理
     * @param context 执行上下文，向后续自定义步骤传递身份、配置或状态
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> resolveCustom(JsonNode rule, CcRuntimeContext context) {
        String resolverCode = rule.path("resolverCode").asText();
        if (personResolverRuntimeService.supports(
                resolverCode, PersonResolveUsage.CC)) {
            Map<String, Object> extraParams = readResolverExtraParams(rule);
            return resolveDirectUsers(
                    personResolverRuntimeService.resolveUsernames(
                            resolverCode,
                            new PersonResolveRequest(
                            1,
                            textVariable(context.variables(), "traceId"),
                            String.join(":",
                                    "CC",
                                    nullSafe(context.processInstanceId()),
                                    nullSafe(context.nodeId()),
                                    nullSafe(context.timing()),
                                    resolverCode),
                            PersonResolveUsage.CC,
                            textVariable(context.variables(), "processConfigId"),
                            context.processDefinitionId(),
                            context.processInstanceId(),
                            context.businessKey(),
                            context.nodeId(),
                            context.nodeName(),
                            textVariable(context.variables(), "taskId"),
                            textVariable(context.variables(), "entityCode"),
                            firstText(
                                    context.variables().get("entityDataId"),
                                    context.businessKey()),
                            firstText(
                                    context.variables().get("startUserId"),
                                    context.variables().get("submitterId"),
                                    context.variables().get("initiator")),
                            context.operatorId(),
                            context.variables(),
                            mapVariable(context.variables().get("entityData")),
                            extraParams)));
        }

        CcRecipientProvider resolver = customResolvers.stream()
                .filter(item -> item.code().equalsIgnoreCase(resolverCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未注册知会人员解析器: " + resolverCode));
        Map<String, Object> parameters = readResolverExtraParams(rule);
        return resolveDirectUsers(resolver.resolve(context, parameters));
    }

    /**
     * 读取解析器附加参数；查询结果供调用方展示或继续处理。
     *
     * @param rule 规则，供本方法读取解析器附加参数时使用
     * @return 解析器附加参数键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readResolverExtraParams(JsonNode rule) {
        JsonNode extraParams = rule.path("extraParams");
        if (!extraParams.isObject()) {
            extraParams = rule.path("params");
        }
        if (!extraParams.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(extraParams, Map.class);
    }

    /**
     * 整理映射变量数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射变量的原始输入，结果供调用方继续使用
     * @return 映射变量键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mapVariable(Object value) {
        return value instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : Map.of();
    }

    /**
     * 生成文本变量文本，供后续匹配或展示。
     *
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 处理后的文本变量文本，供调用方比较或展示
     */
    private String textVariable(Map<String, Object> variables, String key) {
        return firstText(variables.get(key));
    }

    /**
     * 批量创建知会记录并入队发送箱。
     *
     * <p>通过 uniqueKey 保证幂等，重复投递时跳过。</p>
     *
     * @param context       运行时上下文
     * @param recipients    知会收件人列表
     * @param ccType        知会类型（MANUAL/AUTO）
     * @param comment       备注
     * @param ruleSnapshot  规则快照JSON
     * @param channels      通知渠道列表
     * @param sourceTaskId  来源任务ID
     * @return 实际创建的记录数
     */
    private int createRecords(
            CcRuntimeContext context,
            List<SysUser> recipients,
            String ccType,
            String comment,
            String ruleSnapshot,
            List<String> channels,
            String sourceTaskId) {
        int created = 0;
        for (SysUser user : recipients) {
            ProcessCcRecord record = new ProcessCcRecord();
            record.setProcessInstanceId(context.processInstanceId());
            record.setProcessDefinitionId(context.processDefinitionId());
            record.setProcessKey(context.processKey());
            record.setProcessName(context.processName());
            record.setBusinessKey(context.businessKey());
            record.setNodeId(context.nodeId());
            record.setNodeName(context.nodeName());
            record.setCcUserId(user.getUsername());
            record.setCcUserName(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
            record.setCcType(ccType);
            record.setCcTiming(context.timing());
            record.setOperatorId(context.operatorId());
            record.setOperatorName(context.operatorId());
            record.setComment(comment);
            record.setSourceTaskId(sourceTaskId);
            record.setSourceType("MANUAL".equals(ccType) ? "TASK" : "PROCESS_EVENT");
            record.setRecipientRuleSnapshot(ruleSnapshot);
            record.setUniqueKey(String.join(":",
                    ccType,
                    nullSafe(context.processInstanceId()),
                    nullSafe(context.nodeId()),
                    nullSafe(context.timing()),
                    user.getUsername()));
            if (ccService.createCcRecordIfAbsent(record)) {
                // 只有首次创建才通知；通知持久化失败必须穿透，不能被当成抄送重复而吞掉。
                notificationPublisher.enqueue(record, channels);
                created++;
            }
        }
        return created;
    }

    /**
     * 按用户名/ID解析启用状态的用户列表（去重）
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 系统用户集合，供调用方遍历或展示
     */
    private List<SysUser> resolveDirectUsers(List<String> values) {
        LinkedHashMap<String, SysUser> users = new LinkedHashMap<>();
        if (values == null) {
            return List.of();
        }
        for (String value : values) {
            SysUser user = findUser(value);
            if (user != null && SysUser.Status.ENABLED.getValue().equals(user.getStatus())
                    && !Integer.valueOf(1).equals(user.getDeleted())) {
                users.putIfAbsent(user.getUsername(), user);
            }
        }
        return new ArrayList<>(users.values());
    }

    /**
     * 优先按用户名查询，查不到再按ID查询
     *
     * @param value 待查询用户的原始输入，结果供调用方继续使用
     * @return 符合条件的系统用户结果，供调用方继续处理
     */
    private SysUser findUser(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        SysUser user = userMapper.selectByUsername(value);
        return user != null ? user : userMapper.selectById(value);
    }

    /**
     * 判断当前时机是否在配置的时机列表中（大小写不敏感）
     *
     * @param timings 时机集合，作为 {@code stringList} 的输入影响后续处理
     * @param timing 时机，供本方法处理时机匹配时使用
     * @return 时机匹配条件成立时为 true，否则为 false
     */
    private boolean timingMatches(JsonNode timings, String timing) {
        return stringList(timings).stream().anyMatch(value -> value.equalsIgnoreCase(timing));
    }

    /**
     * 将JSON节点转为字符串列表；字符串值会作为流程变量名解析
     *
     * @param node 节点，作为 {@code valuesFromVariable} 的输入影响后续处理
     * @return 流程抄送集合，供调用方遍历或展示
     */
    private List<String> stringList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(item -> {
                if (StringUtils.hasText(item.asText())) {
                    values.add(item.asText());
                }
            });
            return values;
        }
        return valuesFromVariable(node.asText());
    }

    /**
     * 写入JSON；后续读取或执行将使用更新后的状态。
     *
     * @param value 待写入JSON的原始输入，结果供调用方继续使用
     * @return 写入后的JSON文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("知会审计数据无法序列化", exception);
        }
    }

    /**
     * 从流程变量值解析出字符串列表（支持集合与逗号分隔字符串）
     *
     * @param value 待处理值集合起始变量的原始输入，结果供调用方继续使用
     * @return 流程抄送集合，供调用方遍历或展示
     */
    private List<String> valuesFromVariable(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).filter(StringUtils::hasText).toList();
        }
        return Arrays.stream(String.valueOf(value).split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "";
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

    /**
     * 校验并返回当前操作人：需为任务办理人或候选办理人，否则抛出禁止异常
     *
     * @param task 任务，作为 {@code taskIdentityAccessService.requireCurrentUserAccess} 的输入影响后续处理
     * @return 校验并获取后的操作人文本，供调用方比较或展示
     */
    private String requireOperator(Task task) {
        taskIdentityAccessService.requireCurrentUserAccess(task);
        String username = UserContext.getUsername();
        if (!StringUtils.hasText(username)) {
            throw new ForbiddenException("用户未登录");
        }
        return username;
    }
}
