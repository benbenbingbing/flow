package com.workflow.process.assignment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.identity.resolver.PersonPrincipal;
import com.workflow.contracts.identity.resolver.PersonPrincipalType;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.process.assignment.api.request.AssigneeIncidentHandleRequest;
import com.workflow.process.assignment.domain.AssigneeResolutionResult;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 管理空办理人事件、幂等重试、兜底转派、终止和告警指标。 */
@Service
@RequiredArgsConstructor
public class AssigneeIncidentService {

    private static final Set<String> ACTIONS = Set.of(
            "RETRY_RESOLVER", "ASSIGN_USER", "FALLBACK_GROUP", "TERMINATE_INSTANCE");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final AssigneeResolutionService resolutionService;

    /** 查询事件和责任人、重试时间等管理字段。 */
    public List<Map<String, Object>> list(String status) {
        String normalized = trimToNull(status);
        String sql = """
                SELECT id, process_config_id, process_definition_id, process_instance_id,
                       task_id, node_id, node_name, policy, status,
                       empty_reason_code, empty_reason_message, resolver_code,
                       fallback_user, fallback_group, responsibility_owner,
                       retry_count, max_retries, next_retry_at, resolution_action,
                       resolved_by, resolved_at, create_time, update_time
                FROM process_assignee_incident
                """ + (normalized == null ? "" : " WHERE status = ?")
                + " ORDER BY FIELD(status, 'OPEN', 'RETRY_SCHEDULED', 'MANUAL_REQUIRED', 'RESOLVED', 'TERMINATED'), update_time DESC LIMIT 500";
        return normalized == null
                ? jdbcTemplate.query(sql, (rs, rowNum) -> incidentView(rs))
                : jdbcTemplate.query(sql, (rs, rowNum) -> incidentView(rs), normalized);
    }

    /** 查询事件详情及完整处置审计链。 */
    public Map<String, Object> detail(String id) {
        Incident incident = required(id);
        Map<String, Object> result = incidentMap(incident);
        result.put("actions", jdbcTemplate.query("""
                SELECT request_id, action_type, status, operator, request_json,
                       result_json, error_message, create_time, finished_at
                FROM process_assignee_incident_action
                WHERE incident_id = ? ORDER BY create_time, id
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("requestId", rs.getString("request_id"));
            row.put("actionType", rs.getString("action_type"));
            row.put("status", rs.getString("status"));
            row.put("operator", rs.getString("operator"));
            row.put("request", readJson(rs.getString("request_json")));
            row.put("result", readJson(rs.getString("result_json")));
            row.put("errorMessage", rs.getString("error_message"));
            row.put("createdAt", rs.getObject("create_time"));
            row.put("finishedAt", rs.getObject("finished_at"));
            return row;
        }, id));
        return result;
    }

    /** 汇总开放事件、待重试、人工恢复和策略分布，供告警接入。 */
    public Map<String, Object> metrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusCounts", jdbcTemplate.queryForList("""
                SELECT status, COUNT(*) AS count
                FROM process_assignee_incident GROUP BY status ORDER BY status
                """));
        result.put("policyCounts", jdbcTemplate.queryForList("""
                SELECT policy, COUNT(*) AS count
                FROM process_assignee_incident
                WHERE create_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)
                GROUP BY policy ORDER BY count DESC
                """));
        Long overdue = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM process_assignee_incident
                WHERE status = 'RETRY_SCHEDULED' AND next_retry_at <= CURRENT_TIMESTAMP
                """, Long.class);
        result.put("overdueRetries", overdue == null ? 0 : overdue);
        return result;
    }

    /** 幂等处置事件。相同 incidentId/requestId 只执行一次外部流程引擎动作。 */
    public Map<String, Object> handle(
            String incidentId,
            AssigneeIncidentHandleRequest request) {
        Incident incident = required(incidentId);
        String action = request.getAction().trim().toUpperCase(java.util.Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            throw new IllegalArgumentException("不支持的处置动作：" + action);
        }
        if (Set.of("RESOLVED", "TERMINATED").contains(incident.status())) {
            return detail(incidentId);
        }
        String actionId = id();
        String actor = actor();
        try {
            jdbcTemplate.update("""
                    INSERT INTO process_assignee_incident_action (
                      id, incident_id, request_id, action_type, status, operator,
                      request_json, create_time
                    ) VALUES (?, ?, ?, ?, 'RUNNING', ?, ?, CURRENT_TIMESTAMP)
                    """, actionId, incidentId, request.getRequestId().trim(), action,
                    actor, writeJson(request));
        } catch (DuplicateKeyException duplicate) {
            return detail(incidentId);
        }
        try {
            Map<String, Object> result = switch (action) {
                case "RETRY_RESOLVER" -> retryResolver(incident);
                case "ASSIGN_USER" -> assignUser(incident, request.getUserId());
                case "FALLBACK_GROUP" -> assignGroup(incident, request.getGroupCode());
                case "TERMINATE_INSTANCE" -> terminate(incident, request.getReason());
                default -> throw new IllegalArgumentException("不支持的处置动作：" + action);
            };
            jdbcTemplate.update("""
                    UPDATE process_assignee_incident_action
                    SET status = 'SUCCESS', result_json = ?, finished_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, writeJson(result), actionId);
        } catch (RuntimeException error) {
            jdbcTemplate.update("""
                    UPDATE process_assignee_incident_action
                    SET status = 'FAILED', error_message = ?, finished_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, safeError(error), actionId);
            throw error;
        }
        return detail(incidentId);
    }

    /** 系统重启后继续扫描持久化的到期重试事件。 */
    @Scheduled(fixedDelayString = "${workflow.assignee-incident.retry-scan-ms:30000}")
    public void retryDueIncidents() {
        List<String> ids = jdbcTemplate.query("""
                SELECT id FROM process_assignee_incident
                WHERE status = 'RETRY_SCHEDULED' AND next_retry_at <= CURRENT_TIMESTAMP
                ORDER BY next_retry_at LIMIT 50
                """, (rs, rowNum) -> rs.getString("id"));
        for (String incidentId : ids) {
            Incident incident = required(incidentId);
            AssigneeIncidentHandleRequest request = new AssigneeIncidentHandleRequest();
            request.setRequestId("AUTO:" + incidentId + ":" + (incident.retryCount() + 1));
            request.setAction("RETRY_RESOLVER");
            request.setReason("系统按退避策略自动重试空办理人解析");
            try {
                handle(incidentId, request);
            } catch (RuntimeException ignored) {
                // 失败动作已写入审计；下一轮按事件状态继续或等待人工处理。
            }
        }
    }

    private Map<String, Object> retryResolver(Incident incident) {
        if (!StringUtils.hasText(incident.resolverCode())) {
            // 历史部署可能为固定人员选择了自动重试。必须退出定时扫描，
            // 否则相同请求号的失败审计会让事件永远停留在 RETRY_SCHEDULED。
            jdbcTemplate.update("""
                    UPDATE process_assignee_incident
                    SET status = 'MANUAL_REQUIRED', next_retry_at = NULL,
                        empty_reason_code = 'RESOLVER_CODE_MISSING',
                        empty_reason_message = '事件没有可重试的人员接口，请人工补充办理人或转派用户组',
                        resolution_action = 'RETRY_UNAVAILABLE', update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, incident.id());
            return Map.of("requiresManualHandling", true);
        }
        if (!StringUtils.hasText(incident.taskId())) {
            return retryMultiInstanceNodeEntry(incident);
        }
        Task task = requiredTask(incident);
        Map<String, Object> variables = runtimeService.getVariables(incident.processInstanceId());
        AssigneeResolutionResult resolution = resolutionService.resolveConfigured(
                incident.resolverCode(),
                new PersonResolveRequest(
                        1, text(variables.get("traceId")), "ASSIGNEE_RETRY:" + incident.id(),
                        PersonResolveUsage.ASSIGNEE, incident.processConfigId(),
                        incident.processDefinitionId(), incident.processInstanceId(),
                        firstText(variables.get("businessKey"), variables.get("entityDataId")),
                        incident.nodeId(), incident.nodeName(), incident.taskId(),
                        text(variables.get("entityCode")), text(variables.get("entityDataId")),
                        firstText(variables.get("startUserId"), variables.get("submitterId"),
                                variables.get("initiator")),
                        null, variables, mapValue(variables.get("entityData")),
                        readMap(incident.resolverExtraParamsJson())));
        if (resolution.resolved()) {
            applyUsers(task, resolution.usernames());
            resolveIncident(incident.id(), "RETRY_RESOLVER", actor());
            return Map.of("resolvedUsers", resolution.usernames());
        }
        int retryCount = incident.retryCount() + 1;
        if (retryCount >= incident.maxRetries()) {
            jdbcTemplate.update("""
                    UPDATE process_assignee_incident
                    SET status = 'OPEN', retry_count = ?, next_retry_at = NULL,
                        empty_reason_code = ?, empty_reason_message = ?,
                        resolution_action = 'RETRY_EXHAUSTED', update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, retryCount, resolution.reasonCode(), resolution.reasonMessage(), incident.id());
            return Map.of("retryCount", retryCount, "exhausted", true);
        }
        long delay = Math.max(5L, Math.round(
                incident.initialDelaySeconds()
                        * Math.pow(incident.backoffMultiplier(), retryCount)));
        jdbcTemplate.update("""
                UPDATE process_assignee_incident
                SET status = 'RETRY_SCHEDULED', retry_count = ?,
                    next_retry_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND),
                    empty_reason_code = ?, empty_reason_message = ?, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, retryCount, delay, resolution.reasonCode(), resolution.reasonMessage(), incident.id());
        return Map.of("retryCount", retryCount, "nextDelaySeconds", delay);
    }

    /**
     * 多实例节点进入失败时目标 Task 尚未存在。管理端重试只执行权威人员
     * 重验；重验成功后转为 MANUAL_REQUIRED，由原办理人重新提交仍活跃的源任务。
     * 直接代替用户完成源任务会跳过表单校验和业务副作用，因此明确禁止。
     */
    private Map<String, Object> retryMultiInstanceNodeEntry(
            Incident incident) {
        Map<String, Object> variables = runtimeService.getVariables(
                incident.processInstanceId());
        AssigneeResolutionResult resolution =
                resolutionService.resolveConfigured(
                        incident.resolverCode(),
                        new PersonResolveRequest(
                                1,
                                text(variables.get("traceId")),
                                "MULTI_INSTANCE_RETRY:" + incident.id(),
                                PersonResolveUsage.MULTI_INSTANCE,
                                incident.processConfigId(),
                                incident.processDefinitionId(),
                                incident.processInstanceId(),
                                firstText(
                                        variables.get("businessKey"),
                                        variables.get("entityDataId")),
                                incident.nodeId(),
                                incident.nodeName(),
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
                                readMap(incident.resolverExtraParamsJson())));
        if (resolution.resolved()) {
            jdbcTemplate.update("""
                    UPDATE process_assignee_incident
                    SET status = 'MANUAL_REQUIRED', next_retry_at = NULL,
                        empty_reason_code = 'NODE_ENTRY_RETRY_READY',
                        empty_reason_message = '人员已恢复，请重新提交前序任务',
                        resolution_action = 'WAITING_SOURCE_TASK_RETRY',
                        update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, incident.id());
            return Map.of(
                    "resolvedUsers", resolution.usernames(),
                    "requiresSourceTaskRetry", true);
        }
        int retryCount = incident.retryCount() + 1;
        if (retryCount >= incident.maxRetries()) {
            jdbcTemplate.update("""
                    UPDATE process_assignee_incident
                    SET status = 'OPEN', retry_count = ?, next_retry_at = NULL,
                        empty_reason_code = ?, empty_reason_message = ?,
                        resolution_action = 'RETRY_EXHAUSTED', update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, retryCount, resolution.reasonCode(),
                    resolution.reasonMessage(), incident.id());
            return Map.of("retryCount", retryCount, "exhausted", true);
        }
        long delay = Math.max(5L, Math.round(
                incident.initialDelaySeconds()
                        * Math.pow(
                        incident.backoffMultiplier(), retryCount)));
        jdbcTemplate.update("""
                UPDATE process_assignee_incident
                SET status = 'RETRY_SCHEDULED', retry_count = ?,
                    next_retry_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND),
                    empty_reason_code = ?, empty_reason_message = ?,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, retryCount, delay, resolution.reasonCode(),
                resolution.reasonMessage(), incident.id());
        return Map.of("retryCount", retryCount, "nextDelaySeconds", delay);
    }

    private Map<String, Object> assignUser(Incident incident, String userId) {
        String user = requiredText(userId, "必须选择补充办理人");
        AssigneeResolutionResult result = resolutionService.resolvePrincipals(
                List.of(PersonPrincipal.user(user)), "MANUAL_USER_INVALID");
        if (!result.resolved()) {
            throw new IllegalArgumentException(result.reasonMessage());
        }
        Task task = requiredTask(incident);
        applyUsers(task, result.usernames());
        resolveIncident(incident.id(), "ASSIGN_USER", actor());
        return Map.of("resolvedUsers", result.usernames());
    }

    private Map<String, Object> assignGroup(Incident incident, String groupCode) {
        String group = requiredText(
                firstText(groupCode, incident.fallbackGroup()), "必须选择兜底用户组");
        AssigneeResolutionResult result = resolutionService.resolvePrincipals(
                List.of(new PersonPrincipal(PersonPrincipalType.GROUP, group)),
                "MANUAL_GROUP_INVALID");
        if (!result.resolved()) {
            throw new IllegalArgumentException(result.reasonMessage());
        }
        Task task = requiredTask(incident);
        taskService.addCandidateGroup(task.getId(), group);
        resolveIncident(incident.id(), "FALLBACK_GROUP", actor());
        return Map.of("fallbackGroup", group, "resolvedUsers", result.usernames());
    }

    private Map<String, Object> terminate(Incident incident, String reason) {
        // 这是受管理员权限保护的故障恢复通道，不是办理人可用的“终止”操作；
        // 必须保留三开关豁免，否则关闭终止的空办理人流程将无法由管理员清理。
        if (StringUtils.hasText(incident.processInstanceId())
                && runtimeService.createProcessInstanceQuery()
                .processInstanceId(incident.processInstanceId()).singleResult() != null) {
            runtimeService.deleteProcessInstance(incident.processInstanceId(), reason);
        }
        jdbcTemplate.update("""
                UPDATE process_assignee_incident
                SET status = 'TERMINATED', resolution_action = 'TERMINATE_INSTANCE',
                    resolved_by = ?, resolved_at = CURRENT_TIMESTAMP,
                    next_retry_at = NULL, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, actor(), incident.id());
        return Map.of("terminated", true);
    }

    private void applyUsers(Task task, List<String> users) {
        taskService.setAssignee(task.getId(), users.get(0));
        users.stream().skip(1).forEach(user -> taskService.addCandidateUser(task.getId(), user));
        taskService.setVariableLocal(task.getId(), "wfAssigneeIncidentStatus", "RESOLVED");
    }

    private void resolveIncident(String incidentId, String action, String actor) {
        jdbcTemplate.update("""
                UPDATE process_assignee_incident
                SET status = 'RESOLVED', resolution_action = ?, resolved_by = ?,
                    resolved_at = CURRENT_TIMESTAMP, next_retry_at = NULL,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, action, actor, incidentId);
    }

    private Task requiredTask(Incident incident) {
        Task task = StringUtils.hasText(incident.taskId())
                ? taskService.createTaskQuery().taskId(incident.taskId()).singleResult()
                : null;
        if (task == null) {
            jdbcTemplate.update("""
                    UPDATE process_assignee_incident
                    SET status = 'MANUAL_REQUIRED', next_retry_at = NULL,
                        resolution_action = 'TASK_NOT_FOUND', update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, incident.id());
            throw new IllegalStateException("原流程任务不存在，需要人工核对流程实例");
        }
        return task;
    }

    private Incident required(String id) {
        List<Incident> rows = jdbcTemplate.query("""
                SELECT id, process_config_id, process_definition_id, process_instance_id,
                       task_id, node_id, node_name, policy, status, empty_reason_code,
                       empty_reason_message, resolver_code, resolver_extra_params_json,
                       fallback_user, fallback_group, responsibility_owner,
                       retry_count, max_retries, initial_delay_seconds,
                       backoff_multiplier, next_retry_at, resolution_action,
                       resolved_by, resolved_at, create_time, update_time
                FROM process_assignee_incident WHERE id = ?
                """, (rs, rowNum) -> new Incident(
                rs.getString("id"), rs.getString("process_config_id"),
                rs.getString("process_definition_id"), rs.getString("process_instance_id"),
                rs.getString("task_id"), rs.getString("node_id"), rs.getString("node_name"),
                rs.getString("policy"), rs.getString("status"),
                rs.getString("empty_reason_code"), rs.getString("empty_reason_message"),
                rs.getString("resolver_code"), rs.getString("resolver_extra_params_json"),
                rs.getString("fallback_user"), rs.getString("fallback_group"),
                rs.getString("responsibility_owner"), rs.getInt("retry_count"),
                rs.getInt("max_retries"), rs.getInt("initial_delay_seconds"),
                rs.getDouble("backoff_multiplier"),
                rs.getObject("next_retry_at", LocalDateTime.class),
                rs.getString("resolution_action"), rs.getString("resolved_by"),
                rs.getObject("resolved_at"), rs.getObject("create_time"),
                rs.getObject("update_time")), id);
        if (rows.size() != 1) {
            throw new IllegalArgumentException("空办理人事件不存在：" + id);
        }
        return rows.get(0);
    }

    private Map<String, Object> incidentView(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("processConfigId", rs.getString("process_config_id"));
        row.put("processDefinitionId", rs.getString("process_definition_id"));
        row.put("processInstanceId", rs.getString("process_instance_id"));
        row.put("taskId", rs.getString("task_id"));
        row.put("nodeId", rs.getString("node_id"));
        row.put("nodeName", rs.getString("node_name"));
        row.put("policy", rs.getString("policy"));
        row.put("status", rs.getString("status"));
        row.put("emptyReasonCode", rs.getString("empty_reason_code"));
        row.put("emptyReasonMessage", rs.getString("empty_reason_message"));
        row.put("resolverCode", rs.getString("resolver_code"));
        row.put("fallbackUser", rs.getString("fallback_user"));
        row.put("fallbackGroup", rs.getString("fallback_group"));
        row.put("responsibilityOwner", rs.getString("responsibility_owner"));
        row.put("retryCount", rs.getInt("retry_count"));
        row.put("maxRetries", rs.getInt("max_retries"));
        row.put("nextRetryAt", rs.getObject("next_retry_at"));
        row.put("resolutionAction", rs.getString("resolution_action"));
        row.put("resolvedBy", rs.getString("resolved_by"));
        row.put("resolvedAt", rs.getObject("resolved_at"));
        row.put("createdAt", rs.getObject("create_time"));
        row.put("updatedAt", rs.getObject("update_time"));
        return row;
    }

    private Map<String, Object> incidentMap(Incident incident) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", incident.id());
        row.put("processConfigId", incident.processConfigId());
        row.put("processDefinitionId", incident.processDefinitionId());
        row.put("processInstanceId", incident.processInstanceId());
        row.put("taskId", incident.taskId());
        row.put("nodeId", incident.nodeId());
        row.put("nodeName", incident.nodeName());
        row.put("policy", incident.policy());
        row.put("status", incident.status());
        row.put("emptyReasonCode", incident.emptyReasonCode());
        row.put("emptyReasonMessage", incident.emptyReasonMessage());
        row.put("resolverCode", incident.resolverCode());
        row.put("fallbackUser", incident.fallbackUser());
        row.put("fallbackGroup", incident.fallbackGroup());
        row.put("responsibilityOwner", incident.responsibilityOwner());
        row.put("retryCount", incident.retryCount());
        row.put("maxRetries", incident.maxRetries());
        row.put("nextRetryAt", incident.nextRetryAt());
        row.put("resolutionAction", incident.resolutionAction());
        row.put("resolvedBy", incident.resolvedBy());
        row.put("resolvedAt", incident.resolvedAt());
        row.put("createdAt", incident.createdAt());
        row.put("updatedAt", incident.updatedAt());
        return row;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("事件处置 JSON 序列化失败", error);
        }
    }

    private Object readJson(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception error) {
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String value) {
        Object parsed = readJson(value);
        return parsed instanceof Map<?, ?> ? (Map<String, Object>) parsed : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private String actor() {
        return firstText(UserContext.getUsername(), UserContext.getUserId(), "system");
    }

    private String requiredText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String trimToNull(String value) {
        return !StringUtils.hasText(value) ? null : value.trim();
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) return text;
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String safeError(Throwable error) {
        String message = error.getMessage();
        return StringUtils.hasText(message) ? message : error.getClass().getSimpleName();
    }

    private String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record Incident(
            String id, String processConfigId, String processDefinitionId,
            String processInstanceId, String taskId, String nodeId, String nodeName,
            String policy, String status, String emptyReasonCode, String emptyReasonMessage,
            String resolverCode, String resolverExtraParamsJson, String fallbackUser,
            String fallbackGroup, String responsibilityOwner, int retryCount,
            int maxRetries, int initialDelaySeconds, double backoffMultiplier,
            LocalDateTime nextRetryAt, String resolutionAction, String resolvedBy,
            Object resolvedAt, Object createdAt, Object updatedAt) {
    }
}
