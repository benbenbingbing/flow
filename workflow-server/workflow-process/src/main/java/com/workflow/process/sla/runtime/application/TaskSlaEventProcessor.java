package com.workflow.process.sla.runtime.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.identity.IdentityDirectoryPort;
import com.workflow.process.cc.application.ProcessCcNotificationPublisher;
import com.workflow.process.cc.application.ProcessCcService;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaEventMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSla;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSlaEvent;
import com.workflow.process.task.api.request.TaskAddSignRequest;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.TaskAddSignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskSlaEventProcessor {

    private final ProcessTaskSlaEventMapper eventMapper;
    private final ProcessTaskSlaMapper slaMapper;
    private final TaskSlaRuntimeService slaRuntimeService;
    private final org.flowable.engine.TaskService flowableTaskService;
    private final ProcessTaskService processTaskService;
    private final TaskAddSignService taskAddSignService;
    private final ProcessCcService ccService;
    private final ProcessCcNotificationPublisher notificationPublisher;
    private final ProcessCcRecordMapper ccRecordMapper;
    private final SysUserMapper userMapper;
    private final SysOrganizationMapper organizationMapper;
    private final IdentityDirectoryPort identityDirectory;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(
            String eventId,
            String ownerId,
            long leaseToken) {
        ProcessTaskSlaEvent event =
                eventMapper.selectClaimed(eventId, ownerId);
        if (event == null
                || event.getLeaseToken() == null
                || event.getLeaseToken() != leaseToken) {
            return;
        }
        try {
            ProcessTaskSla sla = slaMapper.findByTaskId(event.getTaskId());
            if (skip(sla, event)) {
                success(event, ownerId, leaseToken, Map.of(
                        "skipped", true,
                        "reason", skipReason(sla, event)));
                return;
            }
            Map<String, Object> result = execute(event, sla);
            success(event, ownerId, leaseToken, result);
        } catch (Exception exception) {
            int attempts = event.getAttempts() == null
                    ? 0 : event.getAttempts();
            int maxRetries = event.getMaxRetries() == null
                    ? 5 : event.getMaxRetries();
            boolean dead = attempts + 1 >= maxRetries;
            long retrySeconds = Math.min(
                    1800L,
                    15L * (1L << Math.min(6, attempts)));
            eventMapper.markFailure(
                    eventId,
                    ownerId,
                    leaseToken,
                    dead ? "DEAD" : "FAILED",
                    retrySeconds,
                    abbreviate(exception.getMessage()));
            log.warn(
                    "SLA事件执行失败: eventId={}, action={}, dead={}",
                    eventId,
                    event.getActionType(),
                    dead,
                    exception);
        }
    }

    private Map<String, Object> execute(
            ProcessTaskSlaEvent event,
            ProcessTaskSla sla) {
        String action = event.getActionType().toUpperCase();
        return switch (action) {
            case "MARK_BREACH" -> {
                slaRuntimeService.markBreach(
                        event.getTaskId(),
                        event.getMetricType());
                yield Map.of("breached", event.getMetricType());
            }
            case "NOTIFY" -> notifyUsers(
                    event,
                    sla,
                    recipients(event, sla, true, false));
            case "NOTIFY_MANAGER" -> notifyUsers(
                    event,
                    sla,
                    managerRecipients(sla));
            case "ADD_CC" -> notifyUsers(
                    event,
                    sla,
                    recipients(event, sla, false, true));
            case "TRANSFER" -> transfer(event, sla);
            case "ADD_SIGN" -> addSign(event, sla);
            default -> throw new IllegalArgumentException(
                    "不支持的SLA升级动作: " + action);
        };
    }

    private Map<String, Object> notifyUsers(
            ProcessTaskSlaEvent event,
            ProcessTaskSla sla,
            List<String> recipients) {
        int created = 0;
        List<String> channels = channels(event);
        for (String userId : recipients) {
            String uniqueKey =
                    event.getIdempotencyKey() + ":" + userId;
            ProcessCcRecord record =
                    ccRecordMapper.findByUniqueKey(uniqueKey);
            if (record == null) {
                record = new ProcessCcRecord();
                record.setProcessInstanceId(sla.getProcessInstanceId());
                record.setProcessDefinitionId(
                        sla.getProcessDefinitionId());
                record.setProcessKey(sla.getProcessKey());
                record.setProcessName(sla.getProcessKey());
                record.setBusinessKey(sla.getBusinessKey());
                record.setNodeId(sla.getNodeId());
                record.setNodeName(sla.getNodeName());
                record.setCcUserId(userId);
                record.setCcUserName(
                        identityDirectory.getDisplayName(userId));
                record.setCcType("AUTO");
                record.setCcTiming("SLA");
                record.setOperatorId("system");
                record.setOperatorName("SLA自动升级");
                record.setComment(message(event, sla));
                record.setSourceTaskId(sla.getTaskId());
                record.setSourceType("SLA_ESCALATION");
                record.setRecipientRuleSnapshot(
                        event.getActionConfigSnapshot());
                record.setUniqueKey(uniqueKey);
                record = ccService.createCcRecord(record);
                created++;
            }
            notificationPublisher.enqueue(record, channels);
        }
        return Map.of(
                "recipients", recipients,
                "created", created,
                "channels", channels);
    }

    private Map<String, Object> transfer(
            ProcessTaskSlaEvent event,
            ProcessTaskSla sla) {
        String target = firstTarget(event, sla);
        if (!StringUtils.hasText(target)) {
            throw new IllegalArgumentException("SLA转办目标不能为空");
        }
        Task task = requireActiveTask(event.getTaskId());
        flowableTaskService.setAssignee(task.getId(), target);
        processTaskService.transferTask(
                task.getId(),
                target,
                "SLA升级自动转办");
        return Map.of(
                "transferredTo", target,
                "displayName",
                identityDirectory.getDisplayName(target));
    }

    private Map<String, Object> addSign(
            ProcessTaskSlaEvent event,
            ProcessTaskSla sla) {
        Task task = requireActiveTask(event.getTaskId());
        Map<String, Object> target = nested(
                event,
                "targetConfigJson");
        List<String> userIds = stringList(target.get("userIds"));
        if (userIds.isEmpty()
                && "MANAGER".equalsIgnoreCase(
                        string(target.get("targetType")))) {
            userIds = managerRecipients(sla);
        }
        if (userIds.isEmpty()) {
            throw new IllegalArgumentException("SLA加签人员不能为空");
        }
        TaskAddSignRequest request = new TaskAddSignRequest();
        request.setType(defaultText(
                string(target.get("type")),
                "PARALLEL"));
        request.setUserIds(userIds);
        request.setComment("SLA升级自动加签");
        request.setCompletionPolicy("ALL");
        String previousUserId = UserContext.getUserId();
        String previousUsername = UserContext.getUsername();
        try {
            String operator = task.getAssignee();
            UserContext.clear();
            UserContext.setCurrentUser(operator, operator);
            return taskAddSignService.addSign(task.getId(), request);
        } finally {
            UserContext.clear();
            if (StringUtils.hasText(previousUserId)) {
                UserContext.setCurrentUser(
                        previousUserId,
                        defaultText(previousUsername, previousUserId));
            }
        }
    }

    private List<String> recipients(
            ProcessTaskSlaEvent event,
            ProcessTaskSla sla,
            boolean defaultAssignee,
            boolean targetFirst) {
        Map<String, Object> recipient =
                nested(event, "recipientConfigJson");
        Map<String, Object> target =
                nested(event, "targetConfigJson");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.addAll(stringList(
                (targetFirst ? target : recipient).get("userIds")));
        result.addAll(stringList(
                (targetFirst ? recipient : target).get("userIds")));
        boolean includeAssignee = booleanValue(
                recipient.get("includeAssignee"),
                defaultAssignee);
        if (includeAssignee
                && StringUtils.hasText(sla.getCurrentAssigneeId())) {
            result.add(sla.getCurrentAssigneeId());
        }
        if (booleanValue(recipient.get("includeManager"), false)) {
            result.addAll(managerRecipients(sla));
        }
        if (booleanValue(recipient.get("includeFollowers"), false)) {
            ccRecordMapper.findByProcessInstanceId(
                            sla.getProcessInstanceId())
                    .stream()
                    .map(ProcessCcRecord::getCcUserId)
                    .filter(StringUtils::hasText)
                    .forEach(result::add);
        }
        return result.stream().filter(StringUtils::hasText).toList();
    }

    private List<String> managerRecipients(ProcessTaskSla sla) {
        if (!StringUtils.hasText(sla.getCurrentAssigneeId())) {
            return List.of();
        }
        SysUser user = userMapper.selectById(
                sla.getCurrentAssigneeId());
        if (user == null) {
            user = userMapper.selectByUsername(
                    sla.getCurrentAssigneeId());
        }
        if (user == null) {
            return List.of();
        }
        String organizationId = StringUtils.hasText(user.getDeptId())
                ? user.getDeptId() : user.getOrgId();
        SysOrganization organization = StringUtils.hasText(organizationId)
                ? organizationMapper.selectById(organizationId)
                : null;
        return organization == null
                || !StringUtils.hasText(organization.getLeaderId())
                ? List.of()
                : List.of(organization.getLeaderId());
    }

    private String firstTarget(
            ProcessTaskSlaEvent event,
            ProcessTaskSla sla) {
        Map<String, Object> target =
                nested(event, "targetConfigJson");
        String userId = string(target.get("userId"));
        if (StringUtils.hasText(userId)) {
            return userId;
        }
        List<String> users = stringList(target.get("userIds"));
        if (!users.isEmpty()) {
            return users.get(0);
        }
        if ("MANAGER".equalsIgnoreCase(
                string(target.get("targetType")))) {
            List<String> managers = managerRecipients(sla);
            return managers.isEmpty() ? null : managers.get(0);
        }
        return null;
    }

    private List<String> channels(ProcessTaskSlaEvent event) {
        List<String> channels = stringList(
                nested(event, "recipientConfigJson").get("channels"));
        return channels.isEmpty() ? List.of("IN_APP") : channels;
    }

    private Map<String, Object> nested(
            ProcessTaskSlaEvent event,
            String field) {
        if (!StringUtils.hasText(event.getActionConfigSnapshot())) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(
                    event.getActionConfigSnapshot());
            String document = root.path(field).asText("");
            if (!StringUtils.hasText(document)) {
                return Map.of();
            }
            return objectMapper.readValue(
                    document,
                    new TypeReference<>() {
                    });
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "SLA升级动作配置无法解析: " + field,
                    exception);
        }
    }

    private boolean skip(
            ProcessTaskSla sla,
            ProcessTaskSlaEvent event) {
        if (sla == null
                || "COMPLETED".equals(sla.getOverallStatus())
                || "PAUSED".equals(sla.getOverallStatus())) {
            return true;
        }
        String status = "RESPONSE".equals(event.getMetricType())
                ? sla.getResponseStatus()
                : sla.getCompletionStatus();
        return "MET".equals(status)
                || "NOT_APPLICABLE".equals(status);
    }

    private String skipReason(
            ProcessTaskSla sla,
            ProcessTaskSlaEvent event) {
        if (sla == null) {
            return "SLA_NOT_FOUND";
        }
        if ("PAUSED".equals(sla.getOverallStatus())) {
            return "SLA_PAUSED";
        }
        if ("COMPLETED".equals(sla.getOverallStatus())) {
            return "TASK_COMPLETED";
        }
        return event.getMetricType() + "_RESOLVED";
    }

    private Task requireActiveTask(String taskId) {
        Task task = flowableTaskService.createTaskQuery()
                .taskId(taskId)
                .active()
                .singleResult();
        if (task == null) {
            throw new IllegalStateException("Flowable任务已结束");
        }
        return task;
    }

    private void success(
            ProcessTaskSlaEvent event,
            String ownerId,
            long leaseToken,
            Map<String, Object> result) {
        try {
            eventMapper.markSuccess(
                    event.getId(),
                    ownerId,
                    leaseToken,
                    objectMapper.writeValueAsString(result));
        } catch (Exception exception) {
            throw new IllegalStateException("SLA事件结果序列化失败", exception);
        }
    }

    private String message(
            ProcessTaskSlaEvent event,
            ProcessTaskSla sla) {
        return String.format(
                "任务“%s”的%sSLA已触发升级动作",
                defaultText(sla.getNodeName(), sla.getNodeId()),
                "RESPONSE".equals(event.getMetricType())
                        ? "响应" : "办结");
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            String text = string(item);
            if (StringUtils.hasText(text)) {
                result.add(text);
            }
        }
        return result;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean booleanValue(Object value, boolean fallback) {
        return value == null
                ? fallback
                : Boolean.parseBoolean(String.valueOf(value));
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String abbreviate(String message) {
        if (message == null) {
            return "UNKNOWN";
        }
        return message.length() <= 4000
                ? message
                : message.substring(0, 4000);
    }
}
