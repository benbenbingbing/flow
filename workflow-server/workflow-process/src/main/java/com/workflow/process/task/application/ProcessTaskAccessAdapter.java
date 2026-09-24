package com.workflow.process.task.application;

import com.workflow.contracts.process.port.ProcessTaskAccessPort;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/** 实体审批入口复用待办查询的权威身份范围，不依赖实体摘要中的办理人或任务 ID。 */
@Component
@RequiredArgsConstructor
public class ProcessTaskAccessAdapter implements ProcessTaskAccessPort {

    private final ProcessTaskMapper taskMapper;
    private final ProcessPublishedSnapshotService publishedSnapshotService;

    /** 批量入口只读取当前页所需字段，沿用权威身份 SQL，不改变列表或提交时的授权口径。 */
    @Override
    public java.util.Map<RecordCoordinates, TaskCapability> findCapabilities(String userId, List<RecordCoordinates> records) {
        if (!StringUtils.hasText(userId) || records == null || records.isEmpty()) return java.util.Map.of();
        List<RecordCoordinates> requested = records.stream().filter(java.util.Objects::nonNull)
                .filter(record -> StringUtils.hasText(record.processInstanceId())
                        || StringUtils.hasText(record.entityCode()) && StringUtils.hasText(record.entityDataId()))
                .distinct().toList();
        var result = new java.util.LinkedHashMap<RecordCoordinates, TaskCapability>();
        for (int start = 0; start < requested.size(); start += 100) {
            List<RecordCoordinates> batch = requested.subList(start, Math.min(start + 100, requested.size()));
            // 与单条查询一致：不完整实体坐标不参与约束；有效流程坐标仍需联合匹配。
            var queryRecords = batch.stream().map(record -> {
                boolean entity = StringUtils.hasText(record.entityCode()) && StringUtils.hasText(record.entityDataId());
                return new RecordCoordinates(entity ? record.entityCode() : null, entity ? record.entityDataId() : null,
                        StringUtils.hasText(record.processInstanceId()) ? record.processInstanceId() : null);
            }).distinct().toList();
            var tasks = taskMapper.selectActionableTaskSummaries(userId, queryRecords);
            for (RecordCoordinates record : batch) {
                String taskId = null;
                String taskName = null;
                boolean assigned = false;
                for (var task : tasks) {
                    boolean entityMatches = !StringUtils.hasText(record.entityCode()) || !StringUtils.hasText(record.entityDataId())
                            || java.util.Objects.equals(record.entityCode(), task.getEntityCode())
                                && java.util.Objects.equals(record.entityDataId(), task.getEntityDataId());
                    boolean processMatches = !StringUtils.hasText(record.processInstanceId())
                            || java.util.Objects.equals(record.processInstanceId(), task.getProcessInstanceId());
                    if (!entityMatches || !processMatches) continue;
                    // 名称与 ID 始终取同一条最新可办理任务；办理人标记则取该记录所有匹配任务的并集。
                    if (taskId == null && StringUtils.hasText(task.getTaskId())) {
                        taskId = task.getTaskId();
                        taskName = task.getNodeName();
                    }
                    assigned |= Integer.valueOf(1).equals(task.getAssignedFlag());
                }
                result.put(record, new TaskCapability(taskId, taskName, assigned));
            }
        }
        return java.util.Map.copyOf(result);
    }

    /**
     * 查询可执行任务ID；查询结果供调用方展示或继续处理。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 匹配的可执行任务ID；未找到时为空
     */
    @Override
    public Optional<String> findActionableTaskId(
            String userId, String entityCode, String entityDataId, String processInstanceId) {
        return findTask(userId, entityCode, entityDataId, processInstanceId, false);
    }

    /** 列表中的可办理任务名称必须来自与提交目标同一条受限待办查询。 */
    @Override
    public Optional<String> findActionableTaskName(
            String userId,
            String taskId,
            String entityCode,
            String entityDataId,
            String processInstanceId) {
        if (!StringUtils.hasText(userId)
                || !StringUtils.hasText(taskId)
                || !StringUtils.hasText(entityCode)
                || !StringUtils.hasText(entityDataId)
                || !StringUtils.hasText(processInstanceId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(taskMapper.selectActionableTaskName(
                        userId, taskId, entityCode, entityDataId, processInstanceId))
                .filter(StringUtils::hasText);
    }

    /**
     * 查询可执行任务上下文；查询结果供调用方展示或继续处理。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 匹配的可执行任务上下文；未找到时为空
     */
    @Override
    public Optional<ActionableTaskContext> findActionableTaskContext(
            String userId,
            String taskId,
            String entityCode,
            String entityDataId,
            String processInstanceId) {
        if (!StringUtils.hasText(userId)
                || !StringUtils.hasText(taskId)
                || !StringUtils.hasText(entityCode)
                || !StringUtils.hasText(entityDataId)
                || !StringUtils.hasText(processInstanceId)) {
            return Optional.empty();
        }
        ProcessTask task = taskMapper.selectActionableTaskContext(
                userId, taskId, entityCode, entityDataId,
                processInstanceId);
        if (task == null
                || !StringUtils.hasText(task.getProcessDefinitionId())
                || !StringUtils.hasText(task.getNodeId())) {
            return Optional.empty();
        }
        // 流程定义 ID 先解析到不可变发布历史，避免拿当前流程配置版本做比较。
        // 定义或历史在并发清理中失效时按无可办理上下文处理，不能把底层发布
        // 查询细节泄露到审批按钮响应，也不能回退使用当前可变流程版本。
        ProcessVersionHistory history;
        try {
            history = publishedSnapshotService
                    .getVersionByProcessDefinitionId(
                            task.getProcessDefinitionId());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        String historyId = history == null ? null : history.getId();
        if (!StringUtils.hasText(historyId)) {
            return Optional.empty();
        }
        return Optional.of(new ActionableTaskContext(
                task.getTaskId(),
                task.getProcessInstanceId(),
                task.getProcessDefinitionId(),
                historyId,
                task.getNodeId(),
                task.getEntityCode(),
                task.getEntityDataId()));
    }

    /**
     * 判断是否当前办理人；判断结果决定调用方的后续分支。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 当前办理人条件成立时为 true，否则为 false
     */
    @Override
    public boolean isCurrentAssignee(
            String userId, String entityCode, String entityDataId, String processInstanceId) {
        return findTask(userId, entityCode, entityDataId, processInstanceId, true).isPresent();
    }

    /**
     * 查询可执行实体数据ID 集合；查询结果供调用方展示或继续处理。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 流程任务访问集合，供调用方遍历或展示
     */
    @Override
    public List<String> findActionableEntityDataIds(String userId, String entityCode) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(entityCode)) {
            return List.of();
        }
        return taskMapper.selectActionableEntityDataIds(userId, entityCode).stream()
                .filter(StringUtils::hasText).distinct().toList();
    }

    /**
     * 统一坐标校验；assignedOnly 用于普通办理人权限，防止候选审批权扩散到编辑等动作。
     * 缺少用户身份或完整记录坐标时失败关闭，不允许退化成查询当前用户任意任务。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param assignedOnly {@code assigned}仅，供本方法查询任务时使用
     * @return 匹配的任务；未找到时为空
     */
    private Optional<String> findTask(
            String userId, String entityCode, String entityDataId, String processInstanceId, boolean assignedOnly) {
        boolean hasEntityCoordinates = StringUtils.hasText(entityCode) && StringUtils.hasText(entityDataId);
        boolean hasProcessCoordinate = StringUtils.hasText(processInstanceId);
        if (!StringUtils.hasText(userId) || !hasEntityCoordinates && !hasProcessCoordinate) {
            return Optional.empty();
        }
        return Optional.ofNullable(taskMapper.selectActionableTaskId(
                        userId,
                        hasEntityCoordinates ? entityCode : null,
                        hasEntityCoordinates ? entityDataId : null,
                        hasProcessCoordinate ? processInstanceId : null,
                        assignedOnly))
                .filter(StringUtils::hasText);
    }
}
