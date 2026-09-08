package com.workflow.admin.identity.group.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.admin.identity.group.application.SysGroupService;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 用户组 Web 接口委派与请求契约测试。 */
class SysGroupControllerTest {

    private final SysGroupService groupService = mock(SysGroupService.class);
    private final SysUserService userService = mock(SysUserService.class);
    private final SysGroupController controller =
            new SysGroupController(groupService, userService);

    @Test
    void delegatesReadEndpoints() {
        SysGroup group = new SysGroup();
        group.setId("group-1");
        SysUser user = new SysUser();
        user.setId("user-1");
        when(groupService.getGroupList()).thenReturn(List.of(group));
        when(groupService.getEnabledGroups()).thenReturn(List.of(group));
        when(groupService.getById("group-1")).thenReturn(group);
        when(userService.getUserList()).thenReturn(List.of(user));

        assertEquals(List.of(group), controller.list().getData());
        assertEquals(List.of(group), controller.getEnabledGroups().getData());
        assertEquals(group, controller.getById("group-1").getData());
        assertEquals(List.of(user), controller.getUsers().getData());
    }

    @Test
    void keepsCreateAndUpdateContractsSeparate() {
        SysGroup request = new SysGroup();
        request.setId("body-id");
        SysGroup created = new SysGroup();
        created.setId("created-id");
        when(groupService.createGroup(request)).thenReturn(created);
        when(groupService.updateGroup("path-id", request)).thenReturn(request);

        assertEquals(created, controller.save(request).getData());
        assertEquals(request, controller.update("path-id", request).getData());
        verify(groupService).createGroup(request);
        verify(groupService).updateGroup("path-id", request);
        assertEquals("body-id", request.getId());
    }

    @Test
    void delegatesDeleteAndMemberReplacement() {
        List<String> userIds = List.of("user-1", "user-2");

        controller.delete("group-1");
        controller.saveGroupUsers("group-1", userIds);

        verify(groupService).deleteGroup("group-1");
        verify(groupService).saveGroupUsers("group-1", userIds);
    }

    @Test
    void acceptsStatusBodyAndLetsQueryParameterTakePrecedence() {
        controller.updateStatus("group-1", null, Map.of("status", "1"));
        controller.updateStatus("group-1", "0", Map.of("status", "1"));

        verify(groupService).updateStatus("group-1", "1");
        verify(groupService).updateStatus("group-1", "0");
    }

    @Test
    void rejectsMissingStatusBeforeCallingService() {
        assertThrows(
                IllegalArgumentException.class,
                () -> controller.updateStatus("group-1", null, Map.of()));
    }
}
