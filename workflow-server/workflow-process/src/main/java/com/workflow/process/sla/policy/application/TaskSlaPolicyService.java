package com.workflow.process.sla.policy.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.migration.ConfigMigrationPublishRequest;
import com.workflow.contracts.migration.MigrationAssetHandler;
import com.workflow.process.sla.policy.api.request.TaskSlaPolicySaveRequest;
import com.workflow.process.sla.policy.api.response.TaskSlaPolicyDTO;
import com.workflow.process.sla.policy.infrastructure.persistence.mapper.TaskSlaEscalationStepMapper;
import com.workflow.process.sla.policy.infrastructure.persistence.mapper.TaskSlaPolicyMapper;
import com.workflow.process.sla.policy.infrastructure.persistence.record.TaskSlaEscalationStep;
import com.workflow.process.sla.policy.infrastructure.persistence.record.TaskSlaPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskSlaPolicyService {

    private static final Set<String> TIME_BASES =
            Set.of("WORKING_TIME", "NATURAL_TIME");
    private static final Set<String> METRICS =
            Set.of("RESPONSE", "COMPLETION");
    private static final Set<String> TRIGGERS =
            Set.of("BEFORE_DUE", "AT_DUE", "AFTER_DUE");
    private static final Set<String> ACTIONS =
            Set.of("NOTIFY", "NOTIFY_MANAGER", "ADD_CC",
                    "TRANSFER", "ADD_SIGN");

    private final TaskSlaPolicyMapper policyMapper;
    private final TaskSlaEscalationStepMapper stepMapper;
    private final ObjectMapper objectMapper;
    private final MigrationAssetHandler migrationAssetHandler;

    @Transactional(readOnly = true)
    public List<TaskSlaPolicy> list() {
        return policyMapper.selectList(
                new LambdaQueryWrapper<TaskSlaPolicy>()
                        .eq(TaskSlaPolicy::getDeleted, 0)
                        .orderByAsc(TaskSlaPolicy::getPolicyCode)
                        .orderByDesc(TaskSlaPolicy::getVersion));
    }

    @Transactional(readOnly = true)
    public List<TaskSlaPolicy> published() {
        return policyMapper.findPublished();
    }

    @Transactional(readOnly = true)
    public TaskSlaPolicyDTO get(String id) {
        TaskSlaPolicy policy = requirePolicy(id);
        return new TaskSlaPolicyDTO(policy, snapshot(policy));
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskSlaPolicyDTO save(
            String id,
            TaskSlaPolicySaveRequest request) {
        validate(request);
        TaskSlaPolicy existing =
                StringUtils.hasText(id) ? requirePolicy(id) : null;
        boolean createVersion = existing == null
                || "PUBLISHED".equals(existing.getStatus())
                || "SUPERSEDED".equals(existing.getStatus());
        TaskSlaPolicy policy =
                createVersion ? new TaskSlaPolicy() : existing;
        String code = request.policyCode().trim();
        policy.setPolicyCode(code);
        policy.setPolicyName(request.policyName().trim());
        policy.setDescription(request.description());
        policy.setVersion(createVersion
                ? policyMapper.findMaxVersion(code) + 1
                : existing.getVersion());
        policy.setResponseTargetMinutes(
                request.responseTargetMinutes());
        policy.setCompletionTargetMinutes(
                request.completionTargetMinutes());
        policy.setResponseTimeBasis(normalizeTimeBasis(
                request.responseTimeBasis()));
        policy.setCompletionTimeBasis(normalizeTimeBasis(
                request.completionTimeBasis()));
        policy.setAllowManualPause(
                Boolean.TRUE.equals(request.allowManualPause()));
        policy.setPauseOnProcessSuspend(
                request.pauseOnProcessSuspend() == null
                        || Boolean.TRUE.equals(
                                request.pauseOnProcessSuspend()));
        policy.setMaxPauseMinutes(request.maxPauseMinutes());
        policy.setStatus("DRAFT");
        policy.setUpdatedBy(currentUser());
        policy.setUpdateTime(now());
        policy.setDeleted(0);
        if (createVersion) {
            policy.setCreatedBy(currentUser());
            policy.setCreateTime(now());
            policyMapper.insert(policy);
        } else {
            policyMapper.updateById(policy);
            stepMapper.deleteByPolicyId(policy.getId());
        }
        saveSteps(policy.getId(), request.escalationSteps());
        return get(policy.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskSlaPolicyDTO publish(String id) {
        return publish(id, new ConfigMigrationPublishRequest());
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskSlaPolicyDTO publish(
            String id,
            ConfigMigrationPublishRequest migrationRequest) {
        TaskSlaPolicy policy = requirePolicy(id);
        if (!"DRAFT".equals(policy.getStatus())) {
            throw new IllegalStateException("仅草稿SLA策略可以发布");
        }
        TaskSlaPolicySnapshot snapshot = snapshot(policy);
        validateSnapshot(snapshot);
        policyMapper.update(
                null,
                new LambdaUpdateWrapper<TaskSlaPolicy>()
                        .set(TaskSlaPolicy::getStatus, "SUPERSEDED")
                        .eq(TaskSlaPolicy::getPolicyCode,
                                policy.getPolicyCode())
                        .eq(TaskSlaPolicy::getStatus, "PUBLISHED")
                        .eq(TaskSlaPolicy::getDeleted, 0));
        policy.setStatus("PUBLISHED");
        policy.setUpdatedBy(currentUser());
        policy.setUpdateTime(now());
        policyMapper.updateById(policy);
        ConfigMigrationPublishRequest effectiveRequest =
                migrationRequest == null
                        ? new ConfigMigrationPublishRequest()
                        : migrationRequest;
        if (!StringUtils.hasText(
                effectiveRequest.getVersionDescription())) {
            effectiveRequest.setVersionDescription(
                    "发布SLA策略 " + policy.getPolicyCode()
                            + " V" + policy.getVersion());
        }
        migrationAssetHandler.recordTaskSlaPolicy(
                policy.getId(),
                effectiveRequest);
        return new TaskSlaPolicyDTO(policy, snapshot);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disableForMigration(String policyCode) {
        TaskSlaPolicy policy =
                policyMapper.findLatestPublished(policyCode);
        if (policy == null) {
            return;
        }
        policy.setStatus("DISABLED");
        policy.setUpdatedBy(currentUser());
        policy.setUpdateTime(now());
        policyMapper.updateById(policy);
    }

    @Transactional(readOnly = true)
    public TaskSlaPolicySnapshot publishedSnapshot(String policyCode) {
        TaskSlaPolicy policy =
                policyMapper.findLatestPublished(policyCode);
        if (policy == null) {
            throw new IllegalArgumentException(
                    "SLA策略未发布: " + policyCode);
        }
        return snapshot(policy);
    }

    @Transactional(readOnly = true)
    public TaskSlaPolicySnapshot snapshot(TaskSlaPolicy policy) {
        List<TaskSlaPolicySnapshot.EscalationStep> steps =
                stepMapper.findEnabledByPolicyId(policy.getId())
                        .stream()
                        .map(step ->
                                new TaskSlaPolicySnapshot.EscalationStep(
                                        step.getId(),
                                        step.getStepName(),
                                        step.getMetricType(),
                                        step.getTriggerType(),
                                        step.getOffsetMinutes(),
                                        step.getRepeatIntervalMinutes(),
                                        step.getMaxExecutions(),
                                        step.getActionType(),
                                        step.getTemplateCode(),
                                        step.getRecipientConfigJson(),
                                        step.getTargetConfigJson(),
                                        step.getSortOrder()))
                        .toList();
        return new TaskSlaPolicySnapshot(
                policy.getPolicyCode(),
                policy.getPolicyName(),
                policy.getVersion(),
                policy.getResponseTargetMinutes(),
                policy.getCompletionTargetMinutes(),
                policy.getResponseTimeBasis(),
                policy.getCompletionTimeBasis(),
                Boolean.TRUE.equals(policy.getAllowManualPause()),
                Boolean.TRUE.equals(policy.getPauseOnProcessSuspend()),
                policy.getMaxPauseMinutes(),
                steps);
    }

    public String writeSnapshot(TaskSlaPolicySnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "SLA策略快照序列化失败",
                    exception);
        }
    }

    public TaskSlaPolicySnapshot readSnapshot(String document) {
        try {
            return objectMapper.readValue(
                    document,
                    TaskSlaPolicySnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "SLA策略快照解析失败",
                    exception);
        }
    }

    private void saveSteps(
            String policyId,
            List<TaskSlaPolicySaveRequest.EscalationStepRequest> values) {
        int sort = 0;
        for (TaskSlaPolicySaveRequest.EscalationStepRequest request :
                values == null
                        ? List.<TaskSlaPolicySaveRequest.EscalationStepRequest>of()
                        : values) {
            TaskSlaEscalationStep step =
                    new TaskSlaEscalationStep();
            step.setPolicyId(policyId);
            step.setStepName(request.stepName().trim());
            step.setMetricType(normalize(
                    request.metricType(), METRICS, "SLA指标"));
            step.setTriggerType(normalize(
                    request.triggerType(), TRIGGERS, "触发类型"));
            step.setOffsetMinutes(
                    request.offsetMinutes() == null
                            ? 0
                            : request.offsetMinutes());
            step.setRepeatIntervalMinutes(
                    request.repeatIntervalMinutes());
            step.setMaxExecutions(
                    request.maxExecutions() == null
                            ? 1
                            : request.maxExecutions());
            step.setActionType(normalize(
                    request.actionType(), ACTIONS, "升级动作"));
            step.setTemplateCode(request.templateCode());
            validateJsonObject(
                    request.recipientConfigJson(),
                    "接收人配置");
            validateJsonObject(
                    request.targetConfigJson(),
                    "动作目标配置");
            step.setRecipientConfigJson(
                    blankToNull(request.recipientConfigJson()));
            step.setTargetConfigJson(
                    blankToNull(request.targetConfigJson()));
            step.setSortOrder(sort++);
            step.setEnabled(true);
            step.setCreateTime(now());
            step.setUpdateTime(now());
            stepMapper.insert(step);
        }
    }

    private void validate(TaskSlaPolicySaveRequest request) {
        if (request == null
                || !StringUtils.hasText(request.policyCode())
                || !StringUtils.hasText(request.policyName())
                || request.completionTargetMinutes() == null
                || request.completionTargetMinutes() <= 0) {
            throw new IllegalArgumentException(
                    "策略编码、名称和办结时限不能为空");
        }
        if (request.responseTargetMinutes() != null
                && request.responseTargetMinutes() <= 0) {
            throw new IllegalArgumentException(
                    "响应时限必须是正整数");
        }
        if (request.maxPauseMinutes() != null
                && request.maxPauseMinutes() <= 0) {
            throw new IllegalArgumentException(
                    "最大暂停分钟数必须是正整数");
        }
        normalizeTimeBasis(request.responseTimeBasis());
        normalizeTimeBasis(request.completionTimeBasis());
        for (TaskSlaPolicySaveRequest.EscalationStepRequest step :
                request.escalationSteps() == null
                        ? List.<TaskSlaPolicySaveRequest.EscalationStepRequest>of()
                        : request.escalationSteps()) {
            if (!StringUtils.hasText(step.stepName())) {
                throw new IllegalArgumentException(
                        "升级步骤名称不能为空");
            }
            normalize(step.metricType(), METRICS, "SLA指标");
            normalize(step.triggerType(), TRIGGERS, "触发类型");
            normalize(step.actionType(), ACTIONS, "升级动作");
            if (step.offsetMinutes() != null
                    && step.offsetMinutes() < 0) {
                throw new IllegalArgumentException(
                        "升级偏移分钟数不能小于0");
            }
            if (step.maxExecutions() != null
                    && step.maxExecutions() <= 0) {
                throw new IllegalArgumentException(
                        "升级最大执行次数必须大于0");
            }
        }
    }

    private void validateSnapshot(TaskSlaPolicySnapshot snapshot) {
        if (snapshot.responseTargetMinutes() != null
                && snapshot.responseTargetMinutes()
                > snapshot.completionTargetMinutes()) {
            throw new IllegalArgumentException(
                    "响应时限不能晚于办结时限");
        }
    }

    private void validateJsonObject(
            String document,
            String fieldName) {
        if (!StringUtils.hasText(document)) {
            return;
        }
        try {
            if (!objectMapper.readTree(document).isObject()) {
                throw new IllegalArgumentException(
                        fieldName + "必须是JSON对象");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    fieldName + "不是有效JSON",
                    exception);
        }
    }

    private String normalizeTimeBasis(String value) {
        return normalize(
                StringUtils.hasText(value)
                        ? value
                        : "WORKING_TIME",
                TIME_BASES,
                "计时方式");
    }

    private String normalize(
            String value,
            Set<String> allowed,
            String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(
                    fieldName + "不能为空");
        }
        String normalized =
                value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(
                    "不支持的" + fieldName + ": " + value);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private TaskSlaPolicy requirePolicy(String id) {
        TaskSlaPolicy policy = policyMapper.selectById(id);
        if (policy == null
                || Integer.valueOf(1).equals(policy.getDeleted())) {
            throw new IllegalArgumentException(
                    "SLA策略不存在: " + id);
        }
        return policy;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String currentUser() {
        String username = UserContext.getUsername();
        return StringUtils.hasText(username) ? username : "system";
    }
}
