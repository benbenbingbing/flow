package com.workflow.process.instance.application;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
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

    @Transactional(readOnly = true)
    public void requireSignalAccess(String processInstanceId) {
        requireReadAccess(processInstanceId);
        if (!PermissionUtil.hasPermission(SIGNAL_PERMISSION)
                && !currentUserRoleService.isAdministrator()) {
            throw new ForbiddenException("缺少流程信号触发权限");
        }
    }

    private boolean isTaskParticipant(
            String processInstanceId,
            String userId,
            String username) {
        return identities(userId, username).anyMatch(identity ->
                taskService.createTaskQuery()
                        .processInstanceId(processInstanceId)
                        .taskCandidateOrAssigned(identity)
                        .count() > 0
                || historyService.createHistoricTaskInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .taskAssignee(identity)
                        .count() > 0);
    }

    private Stream<String> identities(String userId, String username) {
        return Stream.of(userId, username)
                .filter(StringUtils::hasText)
                .distinct();
    }

    private boolean matches(String value, String userId, String username) {
        return StringUtils.hasText(value)
                && (value.equals(userId) || value.equals(username));
    }
}
