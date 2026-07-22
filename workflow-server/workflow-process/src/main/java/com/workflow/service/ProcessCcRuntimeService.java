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

@Service
@RequiredArgsConstructor
public class ProcessCcRuntimeService {
    private final TaskService taskService;
    private final ProcessTaskMapper processTaskMapper;
    private final ProcessOperationLogMapper operationLogMapper;
    private final ProcessCcService ccService;
    private final ProcessCcOutboxService outboxService;
    private final ProcessCcConfigService configService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysGroupMapper groupMapper;
    private final SysUserGroupMapper userGroupMapper;
    private final SysOrganizationMapper organizationMapper;
    private final ObjectMapper objectMapper;
    private final List<CcRecipientResolver> customResolvers;

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

    @Transactional(readOnly = true)
    public boolean isManualCcAllowed(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return false;
        }
        return isManualCcAllowed(task);
    }

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

    private List<SysUser> resolveCustom(JsonNode rule, CcRuntimeContext context) {
        String resolverCode = rule.path("resolverCode").asText();
        CcRecipientResolver resolver = customResolvers.stream()
                .filter(item -> item.code().equalsIgnoreCase(resolverCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未注册知会人员解析器: " + resolverCode));
        Map<String, Object> parameters = objectMapper.convertValue(rule.path("params"), Map.class);
        return resolveDirectUsers(resolver.resolve(context, parameters));
    }

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

    private SysUser findUser(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        SysUser user = userMapper.selectByUsername(value);
        return user != null ? user : userMapper.selectById(value);
    }

    private boolean timingMatches(JsonNode timings, String timing) {
        return stringList(timings).stream().anyMatch(value -> value.equalsIgnoreCase(timing));
    }

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
