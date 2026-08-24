package com.workflow.process.assignment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 以 REQUIRES_NEW 持久化空办理人事件。
 * BLOCK_PUBLISH 即使回滚任务创建事务，管理员仍能看到阻断证据。
 */
@Service
@RequiredArgsConstructor
public class AssigneeIncidentRecorder {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** 创建事件；相同任务/实例与节点已有开放事件时幂等返回原 ID。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String create(CreateCommand command) {
        String existing = findOpen(command);
        if (existing != null) {
            return existing;
        }
        String incidentId = id();
        try {
            jdbcTemplate.update("""
                    INSERT INTO process_assignee_incident (
                      id, process_config_id, process_definition_id, process_instance_id,
                      task_id, node_id, node_name, policy, status,
                      empty_reason_code, empty_reason_message, resolver_code,
                      resolver_extra_params_json, fallback_user, fallback_group,
                      responsibility_owner, retry_count, max_retries,
                      initial_delay_seconds, backoff_multiplier, next_retry_at,
                      detail_json, create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?,
                              CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, incidentId, command.processConfigId(), command.processDefinitionId(),
                    command.processInstanceId(), command.taskId(), command.nodeId(),
                    command.nodeName(), command.policy(), command.status(), command.reasonCode(),
                    command.reasonMessage(), command.resolverCode(), json(command.extraParams()),
                    command.fallbackUser(), command.fallbackGroup(),
                    command.responsibilityOwner(), command.maxRetries(),
                    command.initialDelaySeconds(), command.backoffMultiplier(),
                    command.nextRetryAt(), json(command.detail()));
            jdbcTemplate.update("""
                    INSERT INTO process_assignee_incident_action (
                      id, incident_id, request_id, action_type, status, operator,
                      request_json, result_json, create_time, finished_at
                    ) VALUES (?, ?, ?, 'INCIDENT_CREATED', 'SUCCESS', 'system', ?, ?,
                              CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, id(), incidentId, "CREATE:" + incidentId,
                    json(Map.of("reasonCode", command.reasonCode(), "policy", command.policy())),
                    json(Map.of("status", command.status())));
            return incidentId;
        } catch (DuplicateKeyException duplicate) {
            String concurrent = findOpen(command);
            if (concurrent != null) {
                return concurrent;
            }
            throw duplicate;
        }
    }

    private String findOpen(CreateCommand command) {
        List<String> ids = jdbcTemplate.query("""
                SELECT id FROM process_assignee_incident
                WHERE node_id = ?
                  AND COALESCE(task_id, '') = COALESCE(?, '')
                  AND COALESCE(process_instance_id, '') = COALESCE(?, '')
                  AND status IN ('OPEN', 'RETRY_SCHEDULED', 'MANUAL_REQUIRED')
                ORDER BY create_time DESC LIMIT 1
                """, (rs, rowNum) -> rs.getString("id"),
                command.nodeId(), command.taskId(), command.processInstanceId());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("空办理人事件 JSON 序列化失败", error);
        }
    }

    private String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record CreateCommand(
            String processConfigId,
            String processDefinitionId,
            String processInstanceId,
            String taskId,
            String nodeId,
            String nodeName,
            String policy,
            String status,
            String reasonCode,
            String reasonMessage,
            String resolverCode,
            Map<String, Object> extraParams,
            String fallbackUser,
            String fallbackGroup,
            String responsibilityOwner,
            int maxRetries,
            int initialDelaySeconds,
            double backoffMultiplier,
            LocalDateTime nextRetryAt,
            Map<String, Object> detail) {
    }
}
