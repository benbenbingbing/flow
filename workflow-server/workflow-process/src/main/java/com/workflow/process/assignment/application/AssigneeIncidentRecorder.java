package com.workflow.process.assignment.application;

import com.workflow.core.database.JdbcWriteAttempt;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
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
    // 只渲染读守卫语法；开放槽身份及筛选条件仍由本服务决定。
    private final DatabaseQueryDialect queryDialect;
    private final JdbcWriteAttempt writeAttempt;

    /**
     * 创建事件；相同任务/实例与节点已有开放事件时幂等返回原 ID。
     *
     * @param command 本次命令，后续经校验后用于创建办理人异常事件{@code recorder}
     * @return 创建后的办理人异常事件{@code recorder}文本，供调用方比较或展示
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String create(CreateCommand command) {
        String existing = findOpen(command, false);
        if (existing != null) {
            return existing;
        }
        String incidentId = id();
        try {
            writeAttempt.execute(() -> jdbcTemplate.update("""
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
                    command.nextRetryAt(), json(command.detail())));
        } catch (DuplicateKeyException duplicate) {
            String concurrent = findOpen(command, true);
            if (concurrent != null) {
                return concurrent;
            }
            throw duplicate;
        }
        // 创建审计失败必须回滚本次新事件，不能把审计的唯一冲突误认成事件已存在。
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
    }

    /**
     * 下次进入同一多实例节点已成功解析时，闭环之前 taskId 为空的节点事件。
     *
     * <p>成功状态必须加入 Flowable 节点进入事务；后续实例创建失败时
     * 与外层一起回滚，避免实际未恢复却提前关闭 incident。失败创建仍由
     * {@link #create(CreateCommand)} 使用 REQUIRES_NEW 留存证据。</p>
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param nodeId 节点ID，后续用于解析打开节点入口时定位或关联目标
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void resolveOpenNodeEntry(
            String processInstanceId,
            String nodeId) {
        if (processInstanceId == null || processInstanceId.isBlank()
                || nodeId == null || nodeId.isBlank()) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE process_assignee_incident
                SET status = 'RESOLVED', resolution_action = 'NODE_ENTRY_RECOVERED',
                    resolved_by = 'system', resolved_at = CURRENT_TIMESTAMP,
                    next_retry_at = NULL, update_time = CURRENT_TIMESTAMP
                WHERE process_instance_id = ? AND node_id = ? AND task_id IS NULL
                  AND status IN ('OPEN', 'RETRY_SCHEDULED', 'MANUAL_REQUIRED')
                """, processInstanceId, nodeId);
    }

    /**
     * 开放槽唯一索引保证至多一行；冲突后用当前读避开 MySQL 的旧快照。
     *
     * @param command 本次命令，后续经校验后用于查询打开
     * @param currentRead 当前读取，供本方法查询打开时使用
     * @return 查询后的打开文本，供调用方比较或展示
     */
    private String findOpen(CreateCommand command, boolean currentRead) {
        var parameters = new ArrayList<Object>(List.of(openSlot(command), command.nodeId()));
        String task = emptyToNull(command.taskId());
        String instance = emptyToNull(command.processInstanceId());
        // 空值分支直接生成 IS NULL，避免 Oracle 的空串规则和 PostgreSQL 的未知类型空参数。
        String taskCondition = task == null ? "(task_id IS NULL OR task_id = '')" : "task_id = ?";
        String instanceCondition = instance == null ? "(process_instance_id IS NULL OR process_instance_id = '')" : "process_instance_id = ?";
        if (task != null) parameters.add(task);
        if (instance != null) parameters.add(instance);
        List<String> ids = jdbcTemplate.query("SELECT id FROM process_assignee_incident WHERE open_slot = ? AND node_id = ? AND "
                + taskCondition + " AND " + instanceCondition
                + " AND status IN ('OPEN', 'RETRY_SCHEDULED', 'MANUAL_REQUIRED')"
                + (currentRead ? queryDialect.readGuardClause() : ""), (rs, rowNum) -> rs.getString("id"), parameters.toArray());
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * 生成空截止空值文本，供后续匹配或展示。
     *
     * @param value 待处理空截止空值的原始输入，结果供调用方继续使用
     * @return 处理后的空截止空值文本，供调用方比较或展示
     */
    private static String emptyToNull(String value) { return value == null || value.isEmpty() ? null : value; }

    /**
     * 与既有开放槽约束一致：有任务取任务，否则取实例，均缺失时保留固定命名空间。
     *
     * @param command 本次命令，后续经校验后用于处理打开{@code slot}
     * @return 处理后的打开{@code slot}文本，供调用方比较或展示
     */
    private static String openSlot(CreateCommand command) {
        String owner = command.taskId() != null ? command.taskId()
                : command.processInstanceId() != null ? command.processInstanceId() : "NO_INSTANCE";
        return owner + ":" + command.nodeId();
    }

    /**
     * 生成JSON文本，供后续匹配或展示。
     *
     * @param value 待处理JSON的原始输入，结果供调用方继续使用
     * @return 处理后的JSON文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 生成ID文本，供后续匹配或展示。
     *
     * @return 处理后的ID文本，供调用方比较或展示
     */
    private String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 封装创建的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param processConfigId 流程配置 ID，后续定位已发布的节点配置
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param nodeId 节点ID，后续用于处理创建命令时定位或关联目标
     * @param nodeName 节点名称，后续用于处理创建命令时匹配或展示
     * @param policy 策略内容，决定后续创建命令的处理规则
     * @param status 状态标识，决定后续创建命令采用的处理分支
     * @param reasonCode 原因编码，后续用于处理创建命令时定位或关联目标
     * @param reasonMessage 原因消息，保存在对象中供后续校验、查询或展示
     * @param resolverCode 解析器编码，后续用于处理创建命令时定位或关联目标
     * @param extraParams 附加参数，后续传给解析器或执行器
     * @param fallbackUser 兜底用户，主值不可用时供后续处理兜底
     * @param fallbackGroup 兜底分组，主值不可用时供后续处理兜底
     * @param responsibilityOwner {@code responsibility}归属方，保存在对象中供后续校验、查询或展示
     * @param maxRetries 最大{@code retries}，保存在对象中供后续校验、查询或展示
     * @param initialDelaySeconds 初始{@code delay}秒数，保存在对象中供后续校验、查询或展示
     * @param backoffMultiplier {@code backoff}{@code multiplier}，保存在对象中供后续校验、查询或展示
     * @param nextRetryAt 下一步重试时间，后续用于判断有效期或展示该事件的发生时间
     * @param detail 详情，保存在对象中供后续校验、查询或展示
     */
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
