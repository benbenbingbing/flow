package com.workflow.process.instance.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysUserGroup;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.process.instance.api.response.ProcessProgressDTO;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import java.util.*;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.util.StringUtils;

/**
 * 单次流程进度读取的批量数据，不跨请求缓存。任务和执行变量分别索引，
 * 防止重复节点、会签兄弟任务互相覆盖；所有查询都限定在已经鉴权的流程实例。
 */
@lombok.extern.slf4j.Slf4j
final class ProcessProgressReadBatch {
    final List<HistoricTaskInstance> historicTasks;
    final List<Task> activeTasks;
    final Map<String, HistoricTaskInstance> historicTaskById = new HashMap<>();
    final Map<String, Task> activeTaskById = new HashMap<>();
    final Map<String, ProcessTask> localTasks = new HashMap<>();
    private final Map<String, List<HistoricVariableInstance>> taskVariables = new HashMap<>();
    private final Map<String, List<HistoricVariableInstance>> executionVariables = new HashMap<>();
    private final Map<String, Comment> comments = new HashMap<>();
    private final Map<String, List<NameToken>> groups = new HashMap<>();
    private final Map<ProcessProgressDTO.AssigneeInfoDTO, List<NameToken>> candidateNames = new IdentityHashMap<>();
    private String rootActionLabel;
    private RuntimeException commentFailure;

    ProcessProgressReadBatch(String processId, HistoryService history, TaskService tasks,
            ProcessTaskMapper localMapper, SysGroupMapper groupMapper, SysUserGroupMapper membershipMapper) {
        historicTasks = history.createHistoricTaskInstanceQuery().processInstanceId(processId).list();
        historicTasks.forEach(task -> historicTaskById.put(task.getId(), task));
        // Flowable 将候选关系随任务一次加载，避免后续为每个活动任务调用 getIdentityLinksForTask。
        activeTasks = tasks.createTaskQuery().processInstanceId(processId).includeIdentityLinks().list();
        activeTasks.forEach(task -> activeTaskById.put(task.getId(), task));
        for (var task : localMapper.selectProgressHistoryByProcessInstanceId(processId)) {
            localTasks.putIfAbsent(task.getTaskId(), task);
        }
        for (var variable : history.createHistoricVariableInstanceQuery().processInstanceId(processId).list()) {
            if (variable.getTaskId() != null) taskVariables.computeIfAbsent(variable.getTaskId(), key -> new ArrayList<>()).add(variable);
            if (variable.getExecutionId() != null) executionVariables.computeIfAbsent(variable.getExecutionId(), key -> new ArrayList<>()).add(variable);
            // 与 Flowable excludeLocalVariables 的流程根作用域条件一致，不能用其他任务的操作名兜底。
            if (variable.getTaskId() == null && processId.equals(variable.getExecutionId())
                    && variable.getSubScopeId() == null && "actionLabel".equals(variable.getVariableName())) {
                rootActionLabel = (String) variable.getValue();
            }
        }
        try {
            // 按流程读取同样的 comment 类型，保留引擎 TIME_ DESC 顺序下每个任务的第一条评论。
            for (var comment : tasks.getProcessInstanceComments(processId, "comment")) {
                if (comment.getTaskId() != null) comments.putIfAbsent(comment.getTaskId(), comment);
            }
        } catch (RuntimeException failure) { commentFailure = failure; }
        try { loadGroups(groupMapper, membershipMapper); }
        catch (RuntimeException failure) {
            // 候选组展示失败沿用组编码兜底，不能让单个目录故障阻断已完成历史的读取。
            log.warn("流程进度候选组名称加载失败，使用组编码展示", failure);
        }
    }

    List<HistoricVariableInstance> nodeVariables(String taskId, String executionId) {
        List<HistoricVariableInstance> values = taskVariables.getOrDefault(taskId, List.of());
        return values.isEmpty() ? executionVariables.getOrDefault(executionId, List.of()) : values;
    }

    String taskValue(String taskId, String name) {
        return taskVariables.getOrDefault(taskId, List.of()).stream()
                .filter(variable -> name.equals(variable.getVariableName()))
                .map(variable -> (String) variable.getValue()).filter(Objects::nonNull).findFirst().orElse(null);
    }

    String actionLabel(String taskId) {
        String local = taskValue(taskId, "actionLabel");
        return local != null ? local : rootActionLabel;
    }

    String latestComment(String taskId) {
        if (commentFailure != null) throw commentFailure;
        Comment comment = comments.get(taskId);
        return comment == null ? null : comment.getFullMessage();
    }

    /** 候选组优先的展示规则保持不变；这里只收集身份，所有名字在最终组装时统一解析。 */
    void prepareCandidateNames(Task task, ProcessProgressDTO.AssigneeInfoDTO info) {
        List<NameToken> groupNames = new ArrayList<>();
        List<NameToken> userNames = new ArrayList<>();
        if (task.getIdentityLinks() != null) for (var link : task.getIdentityLinks()) {
            if (link.getGroupId() != null) groupNames.addAll(groups.getOrDefault(link.getGroupId(),
                    List.of(new NameToken(link.getGroupId(), false))));
            if (link.getUserId() != null) userNames.add(new NameToken(link.getUserId(), true));
        }
        candidateNames.put(info, !groupNames.isEmpty() ? groupNames : userNames);
        info.setAssigneeId(null);
        info.setAssigneeName("未分配");
    }

    private void loadGroups(SysGroupMapper mapper, SysUserGroupMapper memberships) {
        List<String> codes = activeTasks.stream().filter(task -> !StringUtils.hasText(task.getAssignee()))
                .flatMap(task -> task.getIdentityLinks() == null ? java.util.stream.Stream.empty() : task.getIdentityLinks().stream())
                .map(link -> link.getGroupId()).filter(StringUtils::hasText).distinct().toList();
        for (int start = 0; start < codes.size(); start += 200) {
            var batch = codes.subList(start, Math.min(start + 200, codes.size()));
            var found = mapper.selectDisplayGroupsByCodes(batch);
            if (found.isEmpty()) continue;
            Map<String, List<NameToken>> byGroup = new HashMap<>();
            var ids = found.stream().map(SysGroupMapper.GroupDisplayRow::getId).distinct().toList();
            for (var member : memberships.selectList(Wrappers.<SysUserGroup>query()
                    .select("group_id", "user_id").in("group_id", ids))) {
                byGroup.computeIfAbsent(member.getGroupId(), key -> new ArrayList<>()).add(new NameToken(member.getUserId(), true));
            }
            for (var group : found) groups.put(group.getLookupCode(), byGroup.getOrDefault(group.getId(),
                    List.of(new NameToken(group.getGroupName(), false))));
        }
    }

    /** 汇集所有时间线、当前任务和悬浮详情的用户，批量解析一次后统一回填。 */
    void fillDisplayNames(ProcessProgressDTO progress, SysUserService users) {
        Set<String> ids = new LinkedHashSet<>();
        progress.getNodeHistory().forEach(item -> ids.add(item.getAssignee()));
        if (progress.getTasks() != null) progress.getTasks().forEach(item -> ids.add(item.getAssignee()));
        progress.getNodeAssigneesMap().values().forEach(items -> items.forEach(item -> ids.add(item.getAssigneeId())));
        candidateNames.values().forEach(tokens -> tokens.stream().filter(NameToken::user).forEach(token -> ids.add(token.value())));
        ids.removeIf(value -> !StringUtils.hasText(value) || value.startsWith("${"));
        Map<String, String> names = users.getDisplayNameMap(ids);
        for (var item : progress.getNodeHistory()) {
            String id = item.getAssignee();
            String name = displayName(names, id);
            if (id != null && !id.equals(name)) item.setAssigneeName(name);
        }
        if (progress.getTasks() != null) progress.getTasks().forEach(item -> item.setAssigneeName(displayName(names, item.getAssignee())));
        progress.getNodeAssigneesMap().values().forEach(items -> items.forEach(item -> {
            if (item.getAssigneeId() != null) item.setAssigneeName(displayName(names, item.getAssigneeId()));
        }));
        candidateNames.forEach((info, tokens) -> {
            String display = tokens.stream().map(token -> token.user() ? displayName(names, token.value()) : token.value())
                    .filter(StringUtils::hasText).distinct().collect(java.util.stream.Collectors.joining(","));
            info.setAssigneeName(display.isEmpty() ? "未分配" : display);
        });
    }

    private static String displayName(Map<String, String> names, String id) {
        return id == null ? null : names.getOrDefault(id, id);
    }

    private record NameToken(String value, boolean user) {}
}
