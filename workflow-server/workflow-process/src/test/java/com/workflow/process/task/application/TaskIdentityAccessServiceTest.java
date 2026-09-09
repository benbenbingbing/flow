package com.workflow.process.task.application;

import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.identity.IdentityUser;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.core.error.ForbiddenException;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证任务身份与业务成员目录一致，并拒绝非候选关系及已被他人认领的任务。 */
@ExtendWith(MockitoExtension.class)
class TaskIdentityAccessServiceTest {

    @Mock private TaskService taskService;
    @Mock private SysGroupMapper groupMapper;
    @Mock private SysRoleMapper roleMapper;
    @Mock private IdentityDirectoryPort identityDirectoryPort;
    @Mock private Task task;
    private TaskIdentityAccessService service;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("user-1", "alice");
        service = new TaskIdentityAccessService(taskService, groupMapper, roleMapper, identityDirectoryPort);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"user-1", "alice"})
    void assignedUserCanAccessByIdOrUsername(String assignee) {
        when(task.getAssignee()).thenReturn(assignee);

        assertDoesNotThrow(() -> service.requireCurrentUserAccess(task));

        verifyNoInteractions(taskService, groupMapper, roleMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user-1", "alice"})
    void candidateUserCanAccessByIdOrUsername(String candidate) {
        candidateLinks(link("candidate", candidate, null));

        assertDoesNotThrow(() -> service.requireCurrentUserAccess(task));

        verifyNoInteractions(groupMapper, roleMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"group-1", "reviewers"})
    void businessGroupMemberCanAccessWithoutFlowableIdmMembership(String candidateGroup) {
        candidateLinks(link("candidate", null, candidateGroup));
        when(groupMapper.selectGroupsByUserId("user-1")).thenReturn(List.of(group()));

        assertDoesNotThrow(() -> service.requireCurrentUserAccess(task));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_role-1", "ROLE_manager"})
    void businessRoleMemberCanAccessByRoleIdOrCode(String candidateRole) {
        candidateLinks(link("candidate", null, candidateRole));
        SysRole role = new SysRole();
        role.setId("role-1");
        role.setRoleCode("manager");
        when(roleMapper.selectRolesByUserId("user-1")).thenReturn(List.of(role));

        assertDoesNotThrow(() -> service.requireCurrentUserAccess(task));

        verifyNoInteractions(groupMapper);
    }

    @Test
    void membershipIsRecheckedAfterRemovalOrGroupDisabled() {
        candidateLinks(link("candidate", null, "reviewers"));
        when(groupMapper.selectGroupsByUserId("user-1"))
                .thenReturn(List.of(group()), List.of());

        assertDoesNotThrow(() -> service.requireCurrentUserAccess(task));
        assertThrows(ForbiddenException.class, () -> service.requireCurrentUserAccess(task));
    }

    @Test
    void mixedCandidatesAllowDirectCandidateDespiteNoGroupMembership() {
        candidateLinks(link("candidate", null, "reviewers"), link("candidate", "alice", null));

        assertDoesNotThrow(() -> service.requireCurrentUserAccess(task));
    }

    @Test
    void groupNamedLikeRoleCannotGrantRoleAccessInMixedCandidates() {
        candidateLinks(link("candidate", null, "unrelated-group"), link("candidate", null, "ROLE_manager"));
        SysGroup group = group();
        group.setGroupCode("ROLE_manager");
        when(groupMapper.selectGroupsByUserId("user-1")).thenReturn(List.of(group));

        assertThrows(ForbiddenException.class, () -> service.requireCurrentUserAccess(task));
    }

    @Test
    void usernameOnlyContextResolvesBusinessIdForMembership() {
        UserContext.setCurrentUser(null, "alice");
        candidateLinks(link("candidate", null, "reviewers"));
        when(identityDirectoryPort.findUser("alice")).thenReturn(Optional.of(
                new IdentityUser("user-1", "alice", "Alice", null, null)));
        when(groupMapper.selectGroupsByUserId("user-1")).thenReturn(List.of(group()));

        assertDoesNotThrow(() -> service.requireCurrentUserAccess(task));
    }

    @Test
    void userIdOnlyContextCanAccessCandidateGroup() {
        UserContext.setCurrentUser("user-1", null);
        candidateLinks(link("candidate", null, "reviewers"));
        when(groupMapper.selectGroupsByUserId("user-1")).thenReturn(List.of(group()));

        assertDoesNotThrow(() -> service.requireCurrentUserAccess(task));
    }

    @Test
    void otherAssigneeTakesPrecedenceOverCandidateMembership() {
        when(task.getAssignee()).thenReturn("bob");

        assertThrows(ForbiddenException.class, () -> service.requireCurrentUserAccess(task));

        verifyNoInteractions(taskService, groupMapper, roleMapper);
    }

    @Test
    void nonMemberAndUnassignedTaskWithoutCandidatesAreDenied() {
        candidateLinks(link("candidate", "bob", "reviewers"));

        assertThrows(ForbiddenException.class, () -> service.requireCurrentUserAccess(task));
        when(taskService.getIdentityLinksForTask("task-1")).thenReturn(List.of());
        assertThrows(ForbiddenException.class, () -> service.requireCurrentUserAccess(task));
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "participant", "assignee"})
    void nonCandidateIdentityDoesNotAuthorizeUserOrGroup(String type) {
        IdentityLink link = mock(IdentityLink.class);
        when(link.getType()).thenReturn(type);
        candidateLinks(link);

        assertThrows(ForbiddenException.class, () -> service.requireCurrentUserAccess(task));

        verifyNoInteractions(groupMapper, roleMapper);
    }

    @Test
    void missingLoginIsDeniedBeforeReadingTask() {
        UserContext.clear();

        assertThrows(ForbiddenException.class, () -> service.requireCurrentUserAccess(task));

        verifyNoInteractions(task, taskService, groupMapper, roleMapper);
    }

    private void candidateLinks(IdentityLink... links) {
        when(task.getId()).thenReturn("task-1");
        when(taskService.getIdentityLinksForTask("task-1")).thenReturn(List.of(links));
    }

    private IdentityLink link(String type, String userId, String groupId) {
        IdentityLink link = mock(IdentityLink.class);
        when(link.getType()).thenReturn(type);
        when(link.getUserId()).thenReturn(userId);
        if (groupId != null) {
            when(link.getGroupId()).thenReturn(groupId);
        }
        return link;
    }

    private SysGroup group() {
        SysGroup group = new SysGroup();
        group.setId("group-1");
        group.setGroupCode("reviewers");
        return group;
    }
}
