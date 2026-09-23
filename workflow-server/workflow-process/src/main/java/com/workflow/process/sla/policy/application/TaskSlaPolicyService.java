package com.workflow.process.sla.policy.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.migration.model.ConfigMigrationPublishRequest;
import com.workflow.contracts.migration.port.MigrationAssetPort;
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

/**
 * 管理任务 SLA 策略草稿、升级步骤及发布版本。
 * 发布快照固定响应/办结目标和升级动作，供在途任务使用，避免后续编辑改变已开始的计时。
 */
@Service
@RequiredArgsConstructor
public class TaskSlaPolicyService {

    /** 可选值集中校验，保证发布快照与运行时调度器使用同一协议。 */
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
    private final MigrationAssetPort migrationAssetHandler;

    /**
     * 列出未删除的所有策略版本，管理端按编码和版本展示。
     *
     * @return 策略版本列表
     */
    @Transactional(readOnly = true)
    public List<TaskSlaPolicy> list() {
        return policyMapper.selectList(
                new LambdaQueryWrapper<TaskSlaPolicy>()
                        .eq(TaskSlaPolicy::getDeleted, 0)
                        .orderByAsc(TaskSlaPolicy::getPolicyCode)
                        .orderByDesc(TaskSlaPolicy::getVersion));
    }

    /**
     * 读取全部发布中策略，供流程发布时选择和绑定。
     *
     * @return 当前可绑定的发布策略列表
     */
    @Transactional(readOnly = true)
    public List<TaskSlaPolicy> published() {
        return policyMapper.findPublished();
    }

    /**
     * 根据版本 ID 读取策略及升级步骤快照，供管理端详情和编辑回显。
     *
     * @param id 待读取的策略版本 ID
     * @return 策略记录和展开的升级步骤
     * @throws IllegalArgumentException 策略不存在或已删除时抛出
     */
    @Transactional(readOnly = true)
    public TaskSlaPolicyDTO get(String id) {
        TaskSlaPolicy policy = requirePolicy(id);
        return new TaskSlaPolicyDTO(policy, snapshot(policy));
    }

    /**
     * 保存草稿策略及升级步骤；编辑已发布或被替代版本时创建新版本，
     * 避免在途任务引用的目标分钟数和动作列表发生变化。
     *
     * @param id 待编辑的版本 ID；为空时创建新策略
     * @param request 时限、暂停规则和升级步骤的完整草稿配置
     * @return 保存后的草稿和步骤快照
     * @throws IllegalArgumentException 目标时间或升级步骤配置非法时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskSlaPolicyDTO save(
            String id,
            TaskSlaPolicySaveRequest request) {
        validate(request);
        TaskSlaPolicy existing =
                StringUtils.hasText(id) ? requirePolicy(id) : null;
        // 发布快照被流程实例持有，只允许复用尚可编辑的非发布版本。
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

    /**
     * 使用默认迁移说明发布草稿策略。
     *
     * @param id 待发布的策略版本 ID
     * @return 发布后的策略详情
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskSlaPolicyDTO publish(String id) {
        return publish(id, new ConfigMigrationPublishRequest());
    }

    /**
     * 验证完整快照并发布；同编码旧发布版标为 SUPERSEDED，迁移资产记录
     * 本次版本，后续流程发布读取新的稳定快照。
     *
     * @param id 待发布的草稿版本 ID
     * @param migrationRequest 可选的迁移说明，未提供时自动生成
     * @return 发布后的策略及步骤快照
     * @throws IllegalStateException 目标不是草稿时抛出
     */
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

    /**
     * 配置迁移下线时停用该编码的最新发布版；不存在时不产生副作用。
     *
     * @param policyCode 迁移资产对应的策略编码
     */
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

    /**
     * 按编码返回最新发布快照，供流程版本绑定 SLA 策略。
     *
     * @param policyCode 流程节点配置引用的策略编码
     * @return 最新发布版策略快照
     * @throws IllegalArgumentException 编码尚未发布时抛出
     */
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

    /**
     * 将策略与启用的升级步骤合成发布快照，后续运行时按 sortOrder 展开事件。
     *
     * @param policy 要固定的策略版本记录
     * @return 包含时限和启用步骤的快照
     */
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

    /**
     * 序列化策略快照，供发布配置和迁移包保存。
     *
     * @param snapshot 待持久化的策略发布内容
     * @return JSON 文档
     * @throws IllegalStateException 序列化失败时抛出
     */
    public String writeSnapshot(TaskSlaPolicySnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "SLA策略快照序列化失败",
                    exception);
        }
    }

    /**
     * 解析历史发布文档，损坏时明确失败以免运行时使用部分策略。
     *
     * @param document 已保存的策略 JSON 文档
     * @return 可供任务初始化的策略快照
     * @throws IllegalStateException 文档无法解析时抛出
     */
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

    /**
     * 按请求顺序重建升级步骤；指标、触发器和动作先归一化供调度器识别。
     *
     * @param policyId 所属草稿策略版本 ID
     * @param values 升级步骤请求，允许为空
     */
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

    /**
     * 校验策略时限及步骤基础约束，防止无效目标进入草稿和发布链路。
     *
     * @param request 待保存的策略草稿
     * @throws IllegalArgumentException 目标分钟数或步骤配置非法时抛出
     */
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

    /**
     * 响应目标不能晚于办结目标，否则运行时可能先触发办结超时。
     *
     * @param snapshot 待发布的完整策略快照
     * @throws IllegalArgumentException 响应时限超过办结时限时抛出
     */
    private void validateSnapshot(TaskSlaPolicySnapshot snapshot) {
        if (snapshot.responseTargetMinutes() != null
                && snapshot.responseTargetMinutes()
                > snapshot.completionTargetMinutes()) {
            throw new IllegalArgumentException(
                    "响应时限不能晚于办结时限");
        }
    }

    /**
     * 收件人和动作目标只能是 JSON 对象，以便后续升级处理器按键读取。
     *
     * @param document 待校验的配置文本，空值表示未配置
     * @param fieldName 异常中显示的业务字段名
     * @throws IllegalArgumentException 文档不是有效 JSON 对象时抛出
     */
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

    /**
     * 未填写计时口径时沿用工作时间，保持旧策略的默认行为。
     *
     * @param value 请求中的计时口径，可为空
     * @return 标准大写口径码
     */
    private String normalizeTimeBasis(String value) {
        return normalize(
                StringUtils.hasText(value)
                        ? value
                        : "WORKING_TIME",
                TIME_BASES,
                "计时方式");
    }

    /**
     * 统一大写并检查协议枚举，fieldName 用于报错定位配置字段。
     *
     * @param value 待规范化的配置值
     * @param allowed 对应字段允许的协议值集合
     * @param fieldName 错误提示使用的业务名称
     * @return 可直接写入发布快照的标准值
     * @throws IllegalArgumentException 值为空或不在允许集合时抛出
     */
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

    /**
     * 空配置不存空字符串，避免运行时把它误认为可解析的 JSON。
     *
     * @param value 请求中的可选文本
     * @return 去空白文本，空值返回 null
     */
    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 按版本 ID 读取未软删除策略，供保存和发布入口共用存在性校验。
     *
     * @param id 目标策略版本 ID
     * @return 可操作的策略记录
     * @throws IllegalArgumentException 记录不存在或已删除时抛出
     */
    private TaskSlaPolicy requirePolicy(String id) {
        TaskSlaPolicy policy = policyMapper.selectById(id);
        if (policy == null
                || Integer.valueOf(1).equals(policy.getDeleted())) {
            throw new IllegalArgumentException(
                    "SLA策略不存在: " + id);
        }
        return policy;
    }

    /**
     * 使用 UTC 记录跨时区一致的配置修改时间。
     *
     * @return 当前 UTC 本地时间，供创建和更新审计字段使用
     */
    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 无交互身份的迁移操作以 system 记审计人。
     *
     * @return 当前用户名或 system
     */
    private String currentUser() {
        String username = UserContext.getUsername();
        return StringUtils.hasText(username) ? username : "system";
    }
}
