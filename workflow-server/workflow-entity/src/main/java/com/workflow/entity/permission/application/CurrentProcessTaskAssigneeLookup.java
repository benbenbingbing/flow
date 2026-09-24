package com.workflow.entity.permission.application;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.contracts.process.port.ProcessTaskAccessPort;
import com.workflow.contracts.process.port.ProcessTaskAccessPort.ActionableTaskContext;
import com.workflow.contracts.process.port.ProcessTaskAccessPort.RecordCoordinates;
import com.workflow.contracts.process.port.ProcessTaskAccessPort.TaskCapability;
import com.workflow.entity.data.api.response.EntityDataDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 通过流程访问契约查询业务记录的实际办理人及可审批任务。
 * 会签时实体 current_task_assignee 只保存其中一人，不能作为任务归属依据。
 */
@Component
@RequiredArgsConstructor
public class CurrentProcessTaskAssigneeLookup {

    private final ProcessTaskAccessPort taskAccessPort;
    private final ThreadLocal<DisplayBatch> displayBatch = new ThreadLocal<>();

    /**
     * 只在列表展示循环内开启一次惰性批量查询，嵌套调用结束后恢复旧作用域。
     * 结果不跨请求驻留，精确任务上下文与提交操作不使用此展示快照。
     */
    public DisplayScope openDisplayBatch(java.util.List<EntityDataDTO> rows, SysUser user) {
        DisplayBatch previous = displayBatch.get();
        var coordinates = rows.stream().filter(row -> hasLookupCoordinates(row, user))
                .map(this::coordinates).distinct().toList();
        displayBatch.set(new DisplayBatch(user == null ? null : identity(user), coordinates));
        return () -> { if (previous == null) displayBatch.remove(); else displayBatch.set(previous); };
    }

    public interface DisplayScope extends AutoCloseable { @Override void close(); }

    private TaskCapability displayedCapability(EntityDataDTO row, SysUser user) {
        DisplayBatch batch = displayBatch.get();
        if (batch == null || !java.util.Objects.equals(batch.userId, identity(user))) return null;
        RecordCoordinates key = coordinates(row);
        if (!batch.records.contains(key)) return null;
        if (batch.loaded == null) batch.loaded = taskAccessPort.findCapabilities(batch.userId, batch.orderedRecords);
        return batch.loaded.getOrDefault(key, new TaskCapability(null, null, false));
    }

    private RecordCoordinates coordinates(EntityDataDTO row) {
        boolean entity = StringUtils.hasText(row.getEntityCode()) && StringUtils.hasText(row.getId());
        return new RecordCoordinates(entity ? row.getEntityCode() : null, entity ? row.getId() : null,
                StringUtils.hasText(row.getProcessInstanceId()) ? row.getProcessInstanceId() : null);
    }

    private static final class DisplayBatch {
        final String userId;
        final java.util.List<RecordCoordinates> orderedRecords;
        final java.util.Set<RecordCoordinates> records;
        java.util.Map<RecordCoordinates, TaskCapability> loaded;
        DisplayBatch(String userId, java.util.List<RecordCoordinates> records) {
            this.userId = userId;
            this.orderedRecords = records;
            this.records = java.util.Set.copyOf(records);
        }
    }

    /**
     * 判断用户是否为该记录的当前待办办理人。
     *
     * @param row  业务记录，缺少标识时返回 false
     * @param user 当前用户
     * @return 存在实际指派给当前用户的未完成任务时返回 true；候选人返回 false
     */
    public boolean isCurrentAssignee(EntityDataDTO row, SysUser user) {
        if (!hasLookupCoordinates(row, user)) return false;
        TaskCapability displayed = displayedCapability(row, user);
        if (displayed != null) return displayed.currentAssignee();
        return taskAccessPort.isCurrentAssignee(
                identity(user), row.getEntityCode(), row.getId(), row.getProcessInstanceId());
    }

    /**
     * 查询当前用户针对该记录可办理的未完成任务 ID。
     *
     * <p>多实例审批会为同一流程节点生成多个兄弟任务，而实体表上的
     * {@code current_task_id} 只能保存其中一个任务。本方法以当前认证用户、
     * 记录身份和流程实例为联合约束回查 {@code process_task}，返回的始终是
     * 当前用户实际持有或作为真实候选人可审批的 Flowable taskId，不能使用
     * 实体字段中的兄弟任务 ID 代替。候选审批权不影响 isCurrentAssignee 的语义。</p>
     *
     * @param row  业务记录，缺少标识时返回空
     * @param user 当前认证 Flow 用户
     * @return 当前用户可办理的任务 ID；没有未完成待办时返回空
     */
    public Optional<String> findActionableTaskId(EntityDataDTO row, SysUser user) {
        if (!hasLookupCoordinates(row, user)) {
            return Optional.empty();
        }
        TaskCapability displayed = displayedCapability(row, user);
        if (displayed != null) return Optional.ofNullable(displayed.taskId());
        return taskAccessPort.findActionableTaskId(
                identity(user), row.getEntityCode(), row.getId(), row.getProcessInstanceId());
    }

    /**
     * 使用已选中的可办理 taskId 回查任务名称，使列表文案与实际提交目标一致。
     * 查询仍限制在当前用户、业务记录和流程实例内，不能信任实体行的任务摘要。
     */
    public Optional<String> findActionableTaskName(
            EntityDataDTO row, SysUser user, String taskId) {
        if (!hasLookupCoordinates(row, user)
                || !StringUtils.hasText(taskId)
                || !StringUtils.hasText(row.getEntityCode())
                || !StringUtils.hasText(row.getId())
                || !StringUtils.hasText(row.getProcessInstanceId())) {
            return Optional.empty();
        }
        TaskCapability displayed = displayedCapability(row, user);
        if (displayed != null) return taskId.equals(displayed.taskId())
                ? Optional.ofNullable(displayed.taskName()).filter(StringUtils::hasText) : Optional.empty();
        return taskAccessPort.findActionableTaskName(
                identity(user), taskId, row.getEntityCode(),
                row.getId(), row.getProcessInstanceId());
    }

    /**
     * 将未受信 taskId 重新绑定到当前用户和已鉴权业务记录的真实活动待办。
     *
     * @param row 行，供本方法查询可执行任务上下文时使用
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 匹配的可执行任务上下文；未找到时为空
     */
    public Optional<ActionableTaskContext> findActionableTaskContext(
            EntityDataDTO row,
            SysUser user,
            String taskId) {
        if (!hasLookupCoordinates(row, user)
                || !StringUtils.hasText(taskId)
                || !StringUtils.hasText(row.getEntityCode())
                || !StringUtils.hasText(row.getId())
                || !StringUtils.hasText(row.getProcessInstanceId())) {
            return Optional.empty();
        }
        return taskAccessPort.findActionableTaskContext(
                identity(user), taskId, row.getEntityCode(),
                row.getId(), row.getProcessInstanceId());
    }

    /**
     * 优先使用认证用户 ID；流程端口负责统一匹配 ID 与用户名别名。
     *
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 处理后的身份文本，供调用方比较或展示
     */
    private String identity(SysUser user) {
        return StringUtils.hasText(user.getId()) ? user.getId() : user.getUsername();
    }

    /**
     * 缺少认证用户或记录坐标时不查询，避免把用户在其他记录上的任务误当本行能力。
     *
     * @param row 行，供本方法判断是否具有查找{@code coordinates}时使用
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 查找{@code coordinates}条件成立时为 true，否则为 false
     */
    private boolean hasLookupCoordinates(EntityDataDTO row, SysUser user) {
        return row != null && user != null && StringUtils.hasText(identity(user))
                && (StringUtils.hasText(row.getProcessInstanceId())
                || StringUtils.hasText(row.getEntityCode()) && StringUtils.hasText(row.getId()));
    }
}
