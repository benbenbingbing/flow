package com.workflow.process.task.application;

import com.workflow.contracts.entity.model.EntityTaskSummary;
import com.workflow.contracts.entity.port.EntityTaskSummaryPort;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.contracts.process.port.TaskBusinessSummaryPort;
import com.workflow.process.task.infrastructure.persistence.mapper.*;
import com.workflow.process.task.infrastructure.persistence.record.*;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;

/** 本地任务身份与业务摘要的唯一投影写入口；任何异常交给外层事务回滚。 */
@Service
@RequiredArgsConstructor
public class TaskInboxProjectionService implements TaskBusinessSummaryPort {
    private final ProcessTaskMapper tasks;
    private final ProcessTaskCandidateUserMapper users;
    private final ProcessTaskCandidateGroupMapper groups;
    private final TaskInboxProjectionMapper projection;
    private final TaskService engineTasks;
    private final HistoryService history;
    private final IdentityDirectoryPort directory;
    private final EntityTaskSummaryPort summaries;
    private final ObjectProvider<ProcessTaskService> processTasks;
    private final org.flowable.engine.RuntimeService runtime;

    /**
     * 在引擎命令完成、事务提交之前复读最终状态。多次候选增删只产生一次最终投影；
     * 锁住本地主键后替换候选集合，认领/转办和回填不会提交相互覆盖的身份。
     */
    @Transactional(rollbackFor = Exception.class)
    public void synchronizeTask(String taskId) {
        // 与引擎任务写入采用相同锁顺序；回填不能拿着旧的 assignee 快照等待本地锁。
        projection.lockEngineTask(taskId);
        ProcessTask task = tasks.selectByTaskIdForUpdate(taskId);
        if (task == null) {
            Long deletedId = projection.findDeletedTaskId(taskId);
            if (deletedId != null) {
                users.deleteByProcessTaskId(deletedId);
                groups.deleteByProcessTaskId(deletedId);
                return;
            }
        }
        var current = engineTasks.createTaskQuery().taskId(taskId).singleResult();
        if (task == null && current != null) {
            task = processTasks.getObject().createTask(current, runtime.getVariables(current.getProcessInstanceId()));
        }
        if (task == null || "ADD_SIGN".equals(task.getNodeType())) return;
        users.deleteByProcessTaskId(task.getId());
        groups.deleteByProcessTaskId(task.getId());
        if (current != null) {
            Set<String> candidateUsers = new LinkedHashSet<>();
            Set<String> candidateGroups = new LinkedHashSet<>();
            for (var link : engineTasks.getIdentityLinksForTask(taskId)) {
                if (!"candidate".equals(link.getType())) continue;
                if (hasText(link.getUserId())) candidateUsers.add(link.getUserId());
                if (hasText(link.getGroupId())) candidateGroups.add(link.getGroupId());
            }
            int order = 0;
            for (String user : candidateUsers) {
                var row = new ProcessTaskCandidateUser();
                row.setProcessTaskId(task.getId()); row.setUserId(user); row.setSortOrder(order++);
                row.setCreatedAt(LocalDateTime.now()); users.insert(row);
            }
            order = 0;
            for (String group : candidateGroups) {
                var row = new ProcessTaskCandidateGroup();
                row.setProcessTaskId(task.getId()); row.setGroupCode(group); row.setSortOrder(order++);
                row.setCreatedAt(LocalDateTime.now()); groups.insert(row);
            }
            String assignee = hasText(current.getAssignee()) ? current.getAssignee() : null;
            projection.updateIdentity(task.getId(), assignee,
                    assignee == null ? candidateNames(candidateUsers, candidateGroups) : directory.getDisplayName(assignee),
                    assignee == null ? "group" : "user");
        } else {
            // 自动完成、多实例剩余任务取消也会删除引擎任务。仅修复仍标为活跃的本地镜像；
            // 已由业务动作写好的状态/操作结果必须保留，不能覆盖成普通 approve。
            var historic = history.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
            if (("todo".equals(task.getStatus()) || "waiting".equals(task.getStatus()) || "hold".equals(task.getStatus()))
                    && historic != null && historic.getEndTime() != null) {
                task.setStatus(hasText(historic.getDeleteReason()) && !"completed".equals(historic.getDeleteReason())
                        ? ProcessTask.STATUS_SKIP : ProcessTask.STATUS_DONE);
                task.setEndTime(LocalDateTime.ofInstant(historic.getEndTime().toInstant(), ZoneId.systemDefault()));
                task.setDuration(historic.getDurationInMillis());
                tasks.updateById(task);
            }
            String assignee = historic != null && hasText(historic.getAssignee())
                    ? historic.getAssignee() : task.getAssigneeId();
            projection.updateIdentity(task.getId(), assignee, task.getAssigneeName(), task.getAssigneeType());
        }
        // 已就绪任务的业务摘要只由业务写入端维护。认领/候选变更不应拿当前事务的旧读快照
        // 覆盖另一个业务事务刚更新的摘要，也无需重复查询流程发起人。
        if (!Boolean.TRUE.equals(task.getInboxSummaryReady())) initializeSummary(task);
    }

    /** 单条回填独立事务，先复读/锁定身份；删除或已被其他事务填好的记录可安全重跑。 */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void backfill(Long id) {
        ProcessTask task = tasks.selectById(id);
        if (task == null) return;
        if (hasText(task.getTaskId()) && !"ADD_SIGN".equals(task.getNodeType())) {
            synchronizeTask(task.getTaskId());
        } else {
            if (projection.lockTask(id) == null) return;
            task = tasks.selectById(id);
            projection.updateIdentity(task.getId(), task.getAssigneeId(), task.getAssigneeName(), task.getAssigneeType());
            initializeSummary(task);
        }
    }

    /** 同一业务记录只读取一次摘要，按业务索引批量更新；结束任务也保留最新业务显示语义。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshTaskBusinessSummary(String entityCode, String recordId) {
        if (projection.findFirstBusinessTask(entityCode, recordId) == null) return;
        EntityTaskSummary summary = summaries.findTaskSummary(entityCode, recordId);
        projection.updateBusinessSummaries(entityCode, recordId, summary);
    }

    private void initializeSummary(ProcessTask task) {
        EntityTaskSummary summary = task.getEntityCode() == null || task.getEntityDataId() == null
                ? EntityTaskSummary.empty() : summaries.findTaskSummary(task.getEntityCode(), task.getEntityDataId());
        var instance = history.createHistoricProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();
        projection.updateBusinessSummary(task.getId(), summary);
        // 历史清理后仍保留已经确认的发起人身份，不能用缺失的历史行覆盖已有摘要。
        projection.markSummaryReady(task.getId(), instance == null ? task.getStartUserId() : instance.getStartUserId());
    }

    /** 候选身份只在关系表中授权；展示名称合并直接用户与组，避免取消认领后留下原认领人。 */
    private String candidateNames(Set<String> candidateUsers, Set<String> candidateGroups) {
        Set<String> names = new LinkedHashSet<>();
        for (String user : candidateUsers) names.add(directory.getDisplayName(user));
        for (String group : candidateGroups) {
            var entry = directory.findGroup(group);
            if (entry.isEmpty()) {
                names.add(group);
            } else {
                var members = directory.findGroupUsers(entry.get().id());
                if (members.isEmpty()) names.add(entry.get().name());
                else members.forEach(user -> names.add(directory.getDisplayName(user.id())));
            }
        }
        names.remove(null);
        return names.isEmpty() ? null : String.join(",", names);
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
