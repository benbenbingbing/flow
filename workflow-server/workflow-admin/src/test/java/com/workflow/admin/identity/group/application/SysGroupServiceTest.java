package com.workflow.admin.identity.group.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SysGroupServiceTest {

    @Test
    void fillsGroupListMemberIdsInBatches() {
        SysGroupMapper groupMapper = mock(SysGroupMapper.class);
        SysUserGroupMapper userGroupMapper = mock(SysUserGroupMapper.class);
        List<SysGroup> groups = groups(1200);
        when(groupMapper.selectList(any())).thenReturn(groups);
        when(groupMapper.selectGroupUserIdsByGroupIds(anyList()))
                .thenAnswer(invocation -> {
                    List<String> ids = invocation.getArgument(0);
                    List<SysGroupMapper.GroupUserIdRow> rows = new ArrayList<>();
                    if (!ids.isEmpty()) {
                        rows.add(row(ids.get(0), "user-" + ids.get(0)));
                    }
                    return rows;
                });

        List<SysGroup> result =
                new SysGroupService(groupMapper, userGroupMapper).getGroupList();

        assertEquals(1200, result.size());
        assertEquals(List.of("user-group-0"), result.get(0).getUserIds());
        assertEquals(List.of("user-group-500"), result.get(500).getUserIds());
        assertEquals(List.of("user-group-1000"), result.get(1000).getUserIds());
        assertEquals(List.of(), result.get(1).getUserIds());
        assertNull(result.get(0).getUsers());
        verify(groupMapper, times(3)).selectGroupUserIdsByGroupIds(anyList());
        verify(groupMapper, never()).selectGroupUsers(any());
    }

    private static List<SysGroup> groups(int count) {
        List<SysGroup> groups = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SysGroup group = new SysGroup();
            group.setId("group-" + i);
            group.setGroupName("用户组 " + i);
            group.setGroupCode("group_" + i);
            groups.add(group);
        }
        return groups;
    }

    private static SysGroupMapper.GroupUserIdRow row(String groupId, String userId) {
        SysGroupMapper.GroupUserIdRow row = new SysGroupMapper.GroupUserIdRow();
        row.setGroupId(groupId);
        row.setUserId(userId);
        return row;
    }
}
