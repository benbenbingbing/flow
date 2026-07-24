package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.ForbiddenException;
import com.workflow.common.UserContext;
import com.workflow.dto.TaskCcRequest;
import com.workflow.entity.*;
import com.workflow.mapper.*;
import com.workflow.service.cc.CcRecipientResolver;
import com.workflow.service.cc.CcRuntimeContext;
import com.workflow.service.cc.ProcessCcConfigService;
import com.workflow.service.cc.ProcessCcOutboxService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.dao.DuplicateKeyException;
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
    /** 知会发送箱服务 */
    private final ProcessCcOutboxService outboxService;
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
    private final List<CcRecipientResolver> customResolvers;

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

    /** 按规则类型解析知会收件人（USER/ROLE/GROUP/DEPARTMENT/STARTER/HISTORY_APPROVERS 等） */
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

    /** 判断当前任务节点是否允许人工知会（读取节点知会配置 allowManualCc） */
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

    private List<SysUser> resolveGroups(List<String> values) {
        LinkedHashMap<String, SysUser> users = new LinkedHashMap<>();
        for (String value : values) {
            List<SysGroup> groups = groupMapper.selectList(new LambdaQueryWrapper<SysGroup>()
                    .and(wrapper -> wrapper.eq(SysGroup::getId, value).or().eq(SysGroup::getGroupCode, value))
                    .eq(SysGroup::getDeleted, 0));
            for (SysGroup group : groups) {
                resolveDirectUsers(userGroupMapper.selectUserIdsByGroupId(group.getId()))
                        .forEach(user -> users.putIfAbsent(user.getUsername(), user));
            }
        }
        return new ArrayList<>(users.values());
    }

    /** 解析组织/部门收件人，可选包含子组织，仅取启用状态用户 */
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

    /** 调用自定义解析器（按 resolverCode 匹配）解析知会人员 */
    private List<SysUser> resolveCustom(JsonNode rule, CcRuntimeContext context) {
        String resolverCode = rule.path("resolverCode").asText();
        CcRecipientResolver resolver = customResolvers.stream()
                .filter(item -> item.code().equalsIgnoreCase(resolverCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未注册知会人员解析器: " + resolverCode));
        Map<String, Object> parameters = objectMapper.convertValue(rule.path("params"), Map.class);
        return resolveDirectUsers(resolver.resolve(context, parameters));
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
            try {
                ccService.createCcRecord(record);
                outboxService.enqueue(record, channels);
                created++;
            } catch (DuplicateKeyException ignored) {
                // 事件重复投递时保持幂等。
            }
        }
        return created;
    }

    /** 按用户名/ID解析启用状态的用户列表（去重） */
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

    /** 优先按用户名查询，查不到再按ID查询 */
    private SysUser findUser(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        SysUser user = userMapper.selectByUsername(value);
        return user != null ? user : userMapper.selectById(value);
    }

    /** 判断当前时机是否在配置的时机列表中（大小写不敏感） */
    private boolean timingMatches(JsonNode timings, String timing) {
        return stringList(timings).stream().anyMatch(value -> value.equalsIgnoreCase(timing));
    }

    /** 将JSON节点转为字符串列表；字符串值会作为流程变量名解析 */
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("知会审计数据无法序列化", exception);
        }
    }

    /** 从流程变量值解析出字符串列表（支持集合与逗号分隔字符串） */
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

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** 校验并返回当前操作人：需为任务办理人或候选办理人，否则抛出禁止异常 */
    private String requireOperator(Task task) {
        String username = UserContext.getUsername();
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(username)) {
            throw new ForbiddenException("用户未登录");
        }
        if (StringUtils.hasText(task.getAssignee())
                && !task.getAssignee().equals(username)
                && !task.getAssignee().equals(userId)) {
            throw new ForbiddenException("当前任务已分配给其他办理人");
        }
        if (!StringUtils.hasText(task.getAssignee())) {
            boolean candidate = taskService.createTaskQuery().taskId(task.getId()).taskCandidateUser(username).count() > 0;
            if (!candidate) {
                throw new ForbiddenException("当前用户不是该任务候选办理人");
            }
        }
        return username;
    }
}
