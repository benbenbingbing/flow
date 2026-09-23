package com.workflow.process.instance.application;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import com.workflow.process.task.application.TaskIdentityAccessService;
import com.workflow.process.task.application.LocalAddSignTaskAccessService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.stream.Stream;

/**
 * Central object-level authorization for process instances.
 */
@Service
@RequiredArgsConstructor
public class ProcessInstanceAccessService {

    private static final String SIGNAL_PERMISSION = "process:instance:signal";

    private final HistoryService historyService;
    private final TaskService taskService;
    private final ProcessCcRecordMapper ccRecordMapper;
    private final CurrentUserRoleService currentUserRoleService;
    private final TaskIdentityAccessService taskIdentityAccessService;
    private final LocalAddSignTaskAccessService localAddSignTaskAccessService;

    /**
     * 校验并获取读取访问；不满足约束时阻止后续处理。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Transactional(readOnly = true)
    public void requireReadAccess(String processInstanceId) {
        String userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        if (!StringUtils.hasText(userId) && !StringUtils.hasText(username)) {
            throw new ForbiddenException("用户未登录");
        }
        HistoricProcessInstance instance = historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) {
            throw new IllegalArgumentException("流程实例不存在");
        }
        if (currentUserRoleService.isAdministrator()
                || matches(instance.getStartUserId(), userId, username)
                || isTaskParticipant(processInstanceId, userId, username)
                || ccRecordMapper.existsForUser(processInstanceId, userId, username) > 0) {
            return;
        }
        throw new ForbiddenException("无权访问该流程实例");
    }

    /**
     * 校验并获取{@code signal}访问；不满足约束时阻止后续处理。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    @Transactional(readOnly = true)
    public void requireSignalAccess(String processInstanceId) {
        requireReadAccess(processInstanceId);
        if (!PermissionUtil.hasPermission(SIGNAL_PERMISSION)
                && !currentUserRoleService.isAdministrator()) {
            throw new ForbiddenException("缺少流程信号触发权限");
        }
    }

    /**
     * 判断是否任务{@code participant}；判断结果决定调用方的后续分支。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @return 任务{@code participant}条件成立时为 true，否则为 false
     */
    private boolean isTaskParticipant(
            String processInstanceId,
            String userId,
            String username) {
        // 组和角色成员由业务目录维护，必须与认领授权共用规则，才能在认领前查看流程。
        boolean currentParticipant = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list().stream()
                .anyMatch(taskIdentityAccessService::canCurrentUserAccess);
        return currentParticipant
                || localAddSignTaskAccessService.hasCurrentUserTaskInProcess(processInstanceId)
                || identities(userId, username).anyMatch(identity ->
                historyService.createHistoricTaskInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .taskAssignee(identity)
                        .count() > 0);
    }

    /**
     * 处理{@code identities}，并将结果传给后续步骤。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @return 流程实例访问集合，供调用方遍历或展示
     */
    private Stream<String> identities(String userId, String username) {
        return Stream.of(userId, username)
                .filter(StringUtils::hasText)
                .distinct();
    }

    /**
     * 判断是否匹配流程实例访问；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否匹配流程实例访问的原始输入，结果供调用方继续使用
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @return 流程实例访问条件成立时为 true，否则为 false
     */
    private boolean matches(String value, String userId, String username) {
        return StringUtils.hasText(value)
                && (value.equals(userId) || value.equals(username));
    }
}
