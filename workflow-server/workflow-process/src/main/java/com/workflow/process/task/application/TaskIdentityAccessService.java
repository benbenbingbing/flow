package com.workflow.process.task.application;

import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.identity.IdentityUser;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.core.error.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * 统一任务办理身份校验。Flowable 保存任务候选身份，业务目录保存用户组和角色成员关系；
 * 不能依赖 Flowable IDM 的成员查询，否则业务组成员能看到待办却无法认领。
 */
@Service
@RequiredArgsConstructor
public class TaskIdentityAccessService {

    private final TaskService taskService;
    private final SysGroupMapper groupMapper;
    private final SysRoleMapper roleMapper;
    private final IdentityDirectoryPort identityDirectoryPort;

    /**
     * 校验当前登录人能否查看、认领或办理运行中的任务，不执行认领或其他写操作。
     * 已分配任务仅允许实际办理人；未分配任务允许候选用户或当前有效组/角色成员。
     *
     * @param task 已从引擎查询到的当前任务
     * @throws ForbiddenException 未登录、任务属于他人或当前用户不在候选范围时抛出
     */
    public void requireCurrentUserAccess(Task task) {
        String userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        if (!StringUtils.hasText(userId) && !StringUtils.hasText(username)) {
            throw new ForbiddenException("用户未登录");
        }
        if (StringUtils.hasText(task.getAssignee())) {
            // 候选关系在认领后仍可能保留，不能让原候选成员继续办理别人的任务。
            if (!matchesUser(task.getAssignee(), userId, username)) {
                throw new ForbiddenException("当前任务已分配给其他办理人");
            }
            return;
        }

        if (!isCandidate(task, userId, username)) {
            throw new ForbiddenException("当前用户不是该任务的候选办理人");
        }
    }

    /**
     * 只读判断当前用户是否具有任务办理身份，供流程实例参与者可见性判断使用。
     * 与认领授权使用同一候选规则；未登录返回 false。
     */
    public boolean canCurrentUserAccess(Task task) {
        String userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        if (!StringUtils.hasText(userId) && !StringUtils.hasText(username)) {
            return false;
        }
        return StringUtils.hasText(task.getAssignee())
                ? matchesUser(task.getAssignee(), userId, username)
                : isCandidate(task, userId, username);
    }

    private boolean isCandidate(Task task, String userId, String username) {
        Set<String> candidateGroups = new HashSet<>();
        for (var link : taskService.getIdentityLinksForTask(task.getId())) {
            // owner、participant 等关系不代表办理授权。
            if (!IdentityLinkType.CANDIDATE.equals(link.getType())) {
                continue;
            }
            if (matchesUser(link.getUserId(), userId, username)) {
                return true;
            }
            if (StringUtils.hasText(link.getGroupId())) {
                candidateGroups.add(link.getGroupId());
            }
        }
        return !candidateGroups.isEmpty() && belongsToCandidateGroup(candidateGroups, userId, username);
    }

    /**
     * 按当前业务成员关系匹配组与 ROLE_ 角色身份，兼容历史流程保存的 ID 和编码。
     * Mapper 只返回启用且未删除的组/角色，不将会话中可能过期的角色缓存用于授权。
     */
    private boolean belongsToCandidateGroup(Set<String> candidates, String userId, String username) {
        String directoryUserId = userId;
        if (!StringUtils.hasText(directoryUserId)) {
            directoryUserId = identityDirectoryPort.findUser(username)
                    .map(IdentityUser::id)
                    .orElse(null);
        }
        if (!StringUtils.hasText(directoryUserId)) {
            return false;
        }
        if (candidates.stream().anyMatch(value -> !value.startsWith("ROLE_"))) {
            for (var group : groupMapper.selectGroupsByUserId(directoryUserId)) {
                if (matchesGroupCandidate(candidates, group.getId())
                        || matchesGroupCandidate(candidates, group.getGroupCode())) {
                    return true;
                }
            }
        }
        if (candidates.stream().anyMatch(value -> value.startsWith("ROLE_"))) {
            for (var role : roleMapper.selectRolesByUserId(directoryUserId)) {
                if (candidates.contains("ROLE_" + role.getId())
                        || candidates.contains("ROLE_" + role.getRoleCode())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesGroupCandidate(Set<String> candidates, String groupIdentity) {
        // 混合候选中 ROLE_ 专属于角色，不能被恰好同名的普通用户组匹配。
        return StringUtils.hasText(groupIdentity)
                && !groupIdentity.startsWith("ROLE_") && candidates.contains(groupIdentity);
    }

    private boolean matchesUser(String value, String userId, String username) {
        return StringUtils.hasText(value) && (value.equals(userId) || value.equals(username));
    }
}
