package com.workflow.process.task.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignUserMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

/** 本地加签任务的只读访问边界，复用待办有效性规则并绑定仍存在的源引擎任务。 */
@Service
@RequiredArgsConstructor
public class LocalAddSignTaskAccessService {

    private final ProcessTaskMapper taskMapper;
    private final ProcessTaskAddSignUserMapper addSignUserMapper;
    private final ProcessTaskAddSignMapper addSignMapper;
    private final TaskService taskService;

    /**
     * 验证当前用户持有有效本地加签任务，并返回实际源节点/部署上下文。
     *
     * <p>任务 ID 前缀及本地 assignee 字段均不能单独授权。共享待办查询同时验证
     * TODO 明细、ACTIVE 父加签、源任务存活及实体/实例关联；后续重新读取时再次校验
     * 关键状态，避免源任务已推进后继续返回旧节点表单。本方法不认领或修改任何任务。</p>
     *
     * @param taskId 本地加签生成的任务 ID
     * @param expectedProcessInstanceId 调用方指定的流程实例，为空时由已授权任务确定
     * @return 当前用户的加签待办与其真实源引擎任务
     * @throws ForbiddenException 非本人、未激活、失效、已处理或实例不匹配时抛出
     */
    @Transactional(readOnly = true)
    public AuthorizedAddSignTask requireCurrentUserAccess(String taskId, String expectedProcessInstanceId) {
        String identity = currentIdentity();
        ProcessTask localTask = StringUtils.hasText(taskId)
                ? taskMapper.selectActionableAddSignTaskByTaskId(identity, taskId) : null;
        if (localTask == null || !"ADD_SIGN".equals(localTask.getNodeType())
                || StringUtils.hasText(expectedProcessInstanceId)
                && !Objects.equals(expectedProcessInstanceId, localTask.getProcessInstanceId())) {
            throw unavailable();
        }
        var child = addSignUserMapper.findByGeneratedTaskId(taskId);
        var addSign = child == null ? null : addSignMapper.selectById(child.getAddSignId());
        if (child == null || !"TODO".equals(child.getStatus())
                || !Objects.equals(taskId, child.getGeneratedTaskId())
                || addSign == null || !"ACTIVE".equals(addSign.getStatus())
                || !Objects.equals(localTask.getProcessInstanceId(), addSign.getProcessInstanceId())
                || !StringUtils.hasText(addSign.getSourceTaskId())) {
            throw unavailable();
        }
        Task sourceTask = taskService.createTaskQuery().taskId(addSign.getSourceTaskId()).singleResult();
        if (sourceTask == null
                || !Objects.equals(localTask.getProcessInstanceId(), sourceTask.getProcessInstanceId())
                || !StringUtils.hasText(sourceTask.getTaskDefinitionKey())
                || !StringUtils.hasText(sourceTask.getProcessDefinitionId())) {
            throw unavailable();
        }
        return new AuthorizedAddSignTask(localTask, sourceTask);
    }

    /** 仅把当前有效的本地加签办理人算作实例参与者，不改变普通任务或历史阅读规则。 */
    @Transactional(readOnly = true)
    public boolean hasCurrentUserTaskInProcess(String processInstanceId) {
        return StringUtils.hasText(processInstanceId)
                && taskMapper.countActionableAddSignTasksInProcess(currentIdentity(), processInstanceId) > 0;
    }

    private String currentIdentity() {
        if (StringUtils.hasText(UserContext.getUserId())) {
            return UserContext.getUserId();
        }
        if (StringUtils.hasText(UserContext.getUsername())) {
            return UserContext.getUsername();
        }
        throw new ForbiddenException("用户未登录");
    }

    private ForbiddenException unavailable() {
        return new ForbiddenException("当前加签任务不存在、未激活、已处理或无办理权限");
    }

    /** localTask 保留实际提交 ID；节点和部署表单必须使用 sourceTask 的运行时身份。 */
    public record AuthorizedAddSignTask(ProcessTask localTask, Task sourceTask) { }
}
