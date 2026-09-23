package com.workflow.process.sla.runtime.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.core.error.ForbiddenException;
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
import com.workflow.process.task.application.operation.NodeOperationCapabilityService;
import com.workflow.process.task.application.operation.NodeOperationPolicy;
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

/**
 * 执行调度器已认领的 SLA 到期与升级事件。
 * 通过 ownerId/leaseToken 限定写回权限，通知、转办和加签结果最终记录到事件行，
 * 失败事件按次数退避重试，避免重复调度造成并发副作用。
 */
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
    private final NodeOperationCapabilityService nodeOperationCapabilityService;
    private final ProcessCcService ccService;
    private final ProcessCcNotificationPublisher notificationPublisher;
    private final ProcessCcRecordMapper ccRecordMapper;
    private final SysUserMapper userMapper;
    private final SysOrganizationMapper organizationMapper;
    private final IdentityDirectoryPort identityDirectory;
    private final ObjectMapper objectMapper;

    /**
     * 在独立事务中处理单个已认领事件；租约不匹配时不执行任何动作。
     * eventId 定位事件，ownerId 与 leaseToken 用于确认当前调度器仍持有处理权。
     *
     * @param eventId 已认领事件 ID，用于读取事件和关联 SLA
     * @param ownerId 工作器租约身份，写回结果时再次校验处理权
     * @param leaseToken 本次认领的租约版本，阻止过期工作器覆盖后续结果
     */
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
            // 节点开关拒绝属于确定性策略结果，重试不会改变权限，直接结束事件以免反复告警。
            boolean dead = exception instanceof ForbiddenException
                    || attempts + 1 >= maxRetries;
            // 临时失败指数退避并封顶；确定性的节点权限拒绝直接 DEAD，避免无效重试。
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

    /**
     * 根据发布快照中的 actionType 分发动作，返回结果供事件成功记录和排障。
     *
     * @param event 含动作类型和冻结配置的已认领事件
     * @param sla 事件所属 SLA，提供当前任务、指标和办理人信息
     * @return 供事件持久化的动作结果
     */
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

    /**
     * 为每个收件人创建或复用抄送记录，再按配置渠道入通知队列。
     * eventIdempotencyKey 与用户 ID 组合，保证重试不新增重复抄送。
     *
     * @param event 升级事件，其幂等键和配置决定抄送记录及通知渠道
     * @param sla 所属 SLA，提供任务与节点信息以生成通知内容
     * @param recipients 去重后的目标用户 ID，逐个创建或复用抄送记录
     * @return 已创建数量、用户和渠道等执行摘要
     */
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

    /**
     * 自动转办前复用节点转办开关校验，Flowable 办理人与本地待办同步更新。
     *
     * @param event 转办事件，提供目标用户配置和任务 ID
     * @param sla 所属 SLA，转办成功后同步其当前办理人
     * @return 实际转办目标等执行摘要
     * @throws IllegalArgumentException 没有可用的转办目标
     */
    private Map<String, Object> transfer(
            ProcessTaskSlaEvent event,
            ProcessTaskSla sla) {
        String target = firstTarget(event, sla);
        if (!StringUtils.hasText(target)) {
            throw new IllegalArgumentException("SLA转办目标不能为空");
        }
        Task task = requireActiveTask(event.getTaskId());
        // SLA 自动升级同样属于转办，必须在修改 Flowable 办理人之前通过节点总开关。
        nodeOperationCapabilityService.requireConfiguredAllowed(
                task.getId(),
                NodeOperationPolicy.Operation.TRANSFER);
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

    /**
     * 将升级目标转为加签请求；自动动作以当前任务办理人身份执行既有加签流程，
     * finally 必须恢复原请求上下文，避免调度线程后续事件继承错误用户。
     *
     * @param event 加签事件，提供目标配置及当前任务 ID
     * @param sla 所属 SLA，提供自动动作所需的当前办理人身份
     * @return 加签类型和目标等执行摘要
     */
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
        // 加签服务依赖当前办理人做任务权限检查，自动升级后务必恢复线程上下文。
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

    /**
     * 合并收件人和目标配置、当前办理人、主管及已有关注人并去重。
     * defaultAssignee 控制未显式配置时是否通知办理人，targetFirst 决定优先读取目标名单。
     *
     * @param event 升级事件，提供发布时冻结的收件人与目标配置
     * @param sla 所属 SLA，提供当前办理人、主管和关注人关联
     * @param defaultAssignee 未显式配置收件人时是否纳入当前办理人
     * @param targetFirst 是否先读取目标配置中的用户列表
     * @return 去重且过滤空值后的用户 ID 列表
     */
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

    /**
     * 根据当前办理人的部门或组织找到负责人，供主管通知和主管目标动作使用。
     *
     * @param sla 所属 SLA，通过当前办理人定位其部门或组织负责人
     * @return 可用于主管通知或目标动作的负责人 ID 列表
     */
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

    /**
     * 自动转办只接受单一目标；优先显式 userId，再取列表首位或主管。
     *
     * @param event 转办事件，提供显式目标或候选用户列表
     * @param sla 所属 SLA，主管目标需要据当前办理人解析
     * @return 首个可用目标 ID；未配置时为 null
     */
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

    /**
     * 未配置渠道时使用站内通知，保证升级事件至少有可见提醒。
     *
     * @param event 升级事件，读取其固定收件人配置中的渠道
     * @return 通知渠道；未配置时包含 IN_APP
     */
    private List<String> channels(ProcessTaskSlaEvent event) {
        List<String> channels = stringList(
                nested(event, "recipientConfigJson").get("channels"));
        return channels.isEmpty() ? List.of("IN_APP") : channels;
    }

    /**
     * 从事件固定的动作快照读取内嵌 JSON 配置；不回查当前可变策略，
     * 避免重试时收件人或目标随草稿编辑变化。
     *
     * @param event 含动作配置快照的事件，重试时不回查可变策略
     * @param field 快照内要提取的嵌套 JSON 字段名
     * @return 解析后的嵌套配置；缺失时为空 Map
     */
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

    /**
     * SLA 已结束、暂停或对应指标已解决时跳过旧事件，不再执行升级副作用。
     *
     * @param sla 事件所属 SLA，可为空或已结束
     * @param event 待处理事件，其指标状态决定是否仍需要动作
     * @return 旧事件不应再执行时为 true
     */
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

    /**
     * 为跳过结果提供稳定原因码，写入事件结果供排障。
     *
     * @param sla 事件所属 SLA，用于判断缺失、暂停或完成状态
     * @param event 待跳过事件，用于生成指标已解决原因码
     * @return 写入事件结果的稳定原因码
     */
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

    /**
     * 转办和加签前重新确认 Flowable 任务仍活动，避免对已完成任务执行动作。
     *
     * @param taskId Flowable 任务 ID，执行转办或加签前重新核实活动状态
     * @return 仍活动的 Flowable 任务
     * @throws IllegalStateException 任务不存在或已不再活动
     */
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

    /**
     * 按原租约写入执行结果；失去租约的工作器不能覆盖新工作器状态。
     *
     * @param event 已执行事件，提供本次事件 ID
     * @param ownerId 工作器租约身份，写回成功状态时校验
     * @param leaseToken 本次认领的租约版本，防止覆盖后续认领
     * @param result 动作执行摘要，序列化为事件结果供排障
     */
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

    /**
     * 为抄送记录生成包含节点和指标的提示文本，供收件人辨认升级原因。
     *
     * @param event 升级事件，提供响应或办结指标类型
     * @param sla 所属 SLA，提供节点名称作为通知上下文
     * @return 显示给收件人的升级原因文本
     */
    private String message(
            ProcessTaskSlaEvent event,
            ProcessTaskSla sla) {
        return String.format(
                "任务“%s”的%sSLA已触发升级动作",
                defaultText(sla.getNodeName(), sla.getNodeId()),
                "RESPONSE".equals(event.getMetricType())
                        ? "响应" : "办结");
    }

    /**
     * 仅接受列表配置并过滤空值，避免空用户 ID 进入抄送或转办请求。
     *
     * @param value 配置中的候选列表；非列表、空项均被过滤
     * @return 可用于用户或渠道配置的非空字符串列表
     */
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

    /**
     * 兼容 JSON 标量配置转字符串，null 保留为未配置。
     *
     * @param value 配置中的 JSON 标量，可为空
     * @return 字符串形式；输入为空时为 null
     */
    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 缺失开关采用动作默认值，显式配置再按布尔字符串解析。
     *
     * @param value 配置中显式填写的布尔值或字符串
     * @param fallback 配置缺失时动作应采用的默认开关
     * @return 解析后的开关值
     */
    private boolean booleanValue(Object value, boolean fallback) {
        return value == null
                ? fallback
                : Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * 配置文本缺失时取业务默认值，供加签类型和通知内容使用。
     *
     * @param value 业务配置中的首选文本，可为空白
     * @param fallback 首选文本缺失时使用的提示或类型
     * @return 非空首选文本或回退文本
     */
    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    /**
     * 限制持久化的失败信息长度，防止异常内容超过事件列容量。
     *
     * @param message 异常信息，写入事件失败结果前限制字段长度
     * @return 不超过持久化字段容量的失败说明
     */
    private String abbreviate(String message) {
        if (message == null) {
            return "UNKNOWN";
        }
        return message.length() <= 4000
                ? message
                : message.substring(0, 4000);
    }
}
