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

    @Override
    public Optional<String> findActionableTaskId(
            String userId, String entityCode, String entityDataId, String processInstanceId) {
        return findTask(userId, entityCode, entityDataId, processInstanceId, false);
    }

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

    @Override
    public boolean isCurrentAssignee(
            String userId, String entityCode, String entityDataId, String processInstanceId) {
        return findTask(userId, entityCode, entityDataId, processInstanceId, true).isPresent();
    }

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
