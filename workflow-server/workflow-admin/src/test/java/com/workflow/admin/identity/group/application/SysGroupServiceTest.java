package com.workflow.admin.identity.group.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysUserGroup;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * 用户组管理服务测试，覆盖增删改查、状态切换和成员关系的输入边界。
 */
class SysGroupServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SysGroup.class);
    }

    private final SysGroupMapper groupMapper = mock(SysGroupMapper.class);
    private final SysUserGroupMapper userGroupMapper = mock(SysUserGroupMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysGroupService service =
            new SysGroupService(groupMapper, userGroupMapper, userMapper);

    @Test
    void fillsGroupListMemberIdsInBatches() {
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

        List<SysGroup> result = service.getGroupList();

        assertEquals(1200, result.size());
        assertEquals(List.of("user-group-0"), result.get(0).getUserIds());
        assertEquals(List.of("user-group-500"), result.get(500).getUserIds());
        assertEquals(List.of("user-group-1000"), result.get(1000).getUserIds());
        assertEquals(List.of(), result.get(1).getUserIds());
        assertNull(result.get(0).getUsers());
        verify(groupMapper, times(3)).selectGroupUserIdsByGroupIds(anyList());
        verify(groupMapper, never()).selectGroupMembers(any());
        verify(groupMapper, never()).selectGroupUsers(any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void enabledGroupListAppliesStatusFilterAndStableSort() {
        SysGroup enabled = group("group-1", "finance", "财务组");
        enabled.setStatus("0");
        when(groupMapper.selectList(any())).thenReturn(List.of(enabled));

        List<SysGroup> result = service.getEnabledGroups();

        assertEquals(List.of(enabled), result);
        ArgumentCaptor<LambdaQueryWrapper<SysGroup>> queryCaptor =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(groupMapper).selectList(queryCaptor.capture());
        String sqlSegment = queryCaptor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sqlSegment.contains("status"));
        assertTrue(sqlSegment.contains("order by"));
        assertTrue(queryCaptor.getValue()
                .getParamNameValuePairs().containsValue("0"));
    }

    @Test
    void getsGroupDetailWithDisabledMembersForManagement() {
        SysGroup group = group("group-1", "finance", "财务组");
        SysUser enabled = user("user-1", "0");
        SysUser disabled = user("user-2", "1");
        when(groupMapper.selectById("group-1")).thenReturn(group);
        when(groupMapper.selectGroupMembers("group-1"))
                .thenReturn(List.of(enabled, disabled));

        SysGroup result = service.getById("group-1");

        assertEquals(List.of(enabled, disabled), result.getUsers());
        assertEquals(List.of("user-1", "user-2"), result.getUserIds());
        verify(groupMapper, never()).selectGroupUsers(any());
    }

    @Test
    void createsGroupFromWhitelistedNormalizedFieldsAndDeduplicatesMembers() {
        SysGroup request = group("client-controlled", " finance ", " 财务组 ");
        request.setDescription(" 财务审批人员 ");
        request.setCreateTime(LocalDateTime.of(2000, 1, 1, 0, 0));
        request.setDeleted(1);
        request.setUserIds(List.of(" user-1 ", "user-1", "user-2"));
        when(groupMapper.existsGroupCode("finance", "")).thenReturn(false);
        when(userMapper.selectExistingIdsByIds(List.of("user-1", "user-2")))
                .thenReturn(List.of("user-1", "user-2"));
        when(groupMapper.insert(any(SysGroup.class))).thenAnswer(invocation -> {
            SysGroup inserted = invocation.getArgument(0);
            inserted.setId("group-1");
            return 1;
        });
        when(userGroupMapper.insert(any(SysUserGroup.class))).thenReturn(1);

        SysGroup result = service.createGroup(request);

        assertEquals("group-1", result.getId());
        assertEquals("财务组", result.getGroupName());
        assertEquals("finance", result.getGroupCode());
        assertEquals("财务审批人员", result.getDescription());
        assertEquals(0, result.getSort());
        assertEquals("0", result.getStatus());
        assertNull(result.getDeleted());
        assertEquals(List.of("user-1", "user-2"), result.getUserIds());

        ArgumentCaptor<SysGroup> groupCaptor = ArgumentCaptor.forClass(SysGroup.class);
        verify(groupMapper).insert(groupCaptor.capture());
        assertEquals("group-1", groupCaptor.getValue().getId());
        assertNull(groupCaptor.getValue().getDeleted());

        ArgumentCaptor<SysUserGroup> memberCaptor =
                ArgumentCaptor.forClass(SysUserGroup.class);
        verify(userGroupMapper, times(2)).insert(memberCaptor.capture());
        assertEquals(
                List.of("user-1", "user-2"),
                memberCaptor.getAllValues().stream()
                        .map(SysUserGroup::getUserId)
                        .toList());
    }

    @Test
    void rejectsDuplicateOrInvalidGroupCreationBeforeInsert() {
        SysGroup duplicate = group(null, "finance", "财务组");
        when(groupMapper.existsGroupCode("finance", "")).thenReturn(true);

        IllegalArgumentException duplicateError = assertThrows(
                IllegalArgumentException.class,
                () -> service.createGroup(duplicate));
        assertTrue(duplicateError.getMessage().contains("组编码已存在"));

        SysGroup invalidStatus = group(null, "legal", "法务组");
        invalidStatus.setStatus("2");
        IllegalArgumentException statusError = assertThrows(
                IllegalArgumentException.class,
                () -> service.createGroup(invalidStatus));
        assertTrue(statusError.getMessage().contains("状态只能"));

        SysGroup missingName = group(null, "legal", " ");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createGroup(missingName));
        verify(groupMapper, never()).insert(any(SysGroup.class));
    }

    @Test
    void updatesEditableFieldsButKeepsStableCodeAndSystemFields() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 2, 3, 4);
        SysGroup existing = group("group-1", "finance", "旧名称");
        existing.setStatus("0");
        existing.setSort(3);
        existing.setCreateTime(createdAt);
        when(groupMapper.selectById("group-1")).thenReturn(existing);
        when(groupMapper.updateById(any(SysGroup.class))).thenReturn(1);

        SysGroup request = group("body-id", " finance ", " 新名称 ");
        request.setDescription(" 新描述 ");
        request.setSort(8);
        request.setStatus("1");
        request.setDeleted(1);
        SysGroup result = service.updateGroup(" group-1 ", request);

        assertEquals("group-1", result.getId());
        assertEquals("finance", result.getGroupCode());
        assertEquals("新名称", result.getGroupName());
        assertEquals("新描述", result.getDescription());
        assertEquals(8, result.getSort());
        assertEquals("1", result.getStatus());
        assertEquals(createdAt, result.getCreateTime());
        assertNull(result.getDeleted());
        verify(groupMapper, never()).insert(any(SysGroup.class));
    }

    @Test
    void partialUpdateKeepsFieldsOmittedByOlderClients() {
        SysGroup existing = group("group-1", "finance", "旧名称");
        existing.setDescription("原描述");
        existing.setSort(6);
        existing.setStatus("1");
        when(groupMapper.selectById("group-1")).thenReturn(existing);
        when(groupMapper.updateById(any(SysGroup.class))).thenReturn(1);

        SysGroup request = new SysGroup();
        request.setGroupName("新名称");

        SysGroup result = service.updateGroup("group-1", request);

        assertEquals("新名称", result.getGroupName());
        assertEquals("finance", result.getGroupCode());
        assertEquals("原描述", result.getDescription());
        assertEquals(6, result.getSort());
        assertEquals("1", result.getStatus());
    }

    @Test
    void rejectsUnknownGroupAndCodeChangeOnUpdate() {
        SysGroup request = group(null, "changed", "新名称");
        when(groupMapper.selectById("missing")).thenReturn(null);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateGroup("missing", request));

        SysGroup existing = group("group-1", "stable", "旧名称");
        when(groupMapper.selectById("group-1")).thenReturn(existing);
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateGroup("group-1", request));
        assertTrue(error.getMessage().contains("不可修改"));
        verify(groupMapper, never()).updateById(any(SysGroup.class));
    }

    @Test
    void updatesOnlyValidStatusForExistingGroup() {
        when(groupMapper.selectById("group-1"))
                .thenReturn(group("group-1", "finance", "财务组"));
        when(groupMapper.updateById(any(SysGroup.class))).thenReturn(1);

        service.updateStatus("group-1", " 1 ");

        ArgumentCaptor<SysGroup> captor = ArgumentCaptor.forClass(SysGroup.class);
        verify(groupMapper).updateById(captor.capture());
        assertEquals("group-1", captor.getValue().getId());
        assertEquals("1", captor.getValue().getStatus());
        assertNull(captor.getValue().getGroupName());
    }

    @Test
    void rejectsInvalidStatusAndUnknownGroupWithoutWriting() {
        when(groupMapper.selectById("group-1"))
                .thenReturn(group("group-1", "finance", "财务组"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateStatus("group-1", "enabled"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateStatus("group-1", " "));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateStatus("missing", "0"));
        verify(groupMapper, never()).updateById(any(SysGroup.class));
    }

    @Test
    void deletesMembersBeforeLogicallyDeletingExistingGroup() {
        when(groupMapper.selectById("group-1"))
                .thenReturn(group("group-1", "finance", "财务组"));
        when(groupMapper.deleteById("group-1")).thenReturn(1);

        service.deleteGroup("group-1");

        InOrder order = inOrder(userGroupMapper, groupMapper);
        order.verify(userGroupMapper).deleteByGroupId("group-1");
        order.verify(groupMapper).deleteById("group-1");
    }

    @Test
    void rejectsDeletingUnknownGroupWithoutRemovingMembers() {
        when(groupMapper.selectById("missing")).thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteGroup("missing"));

        verify(userGroupMapper, never()).deleteByGroupId(any());
        verify(groupMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void replacesMembersAfterValidationAndKeepsDisabledUsersEligibleForMembership() {
        when(groupMapper.selectById("group-1"))
                .thenReturn(group("group-1", "finance", "财务组"));
        // 此查询只判断存在/删除状态，user-2 即使已禁用也应保留成员关系。
        when(userMapper.selectExistingIdsByIds(List.of("user-1", "user-2")))
                .thenReturn(List.of("user-1", "user-2"));
        when(userGroupMapper.insert(any(SysUserGroup.class))).thenReturn(1);

        service.saveGroupUsers(
                "group-1",
                List.of(" user-1 ", "user-2", "user-1"));

        InOrder order = inOrder(userGroupMapper);
        order.verify(userGroupMapper).deleteByGroupId("group-1");
        ArgumentCaptor<SysUserGroup> captor =
                ArgumentCaptor.forClass(SysUserGroup.class);
        order.verify(userGroupMapper, times(2)).insert(captor.capture());
        assertEquals(
                List.of("user-1", "user-2"),
                captor.getAllValues().stream().map(SysUserGroup::getUserId).toList());
    }

    @Test
    void clearsMembersOnlyForAnExplicitEmptyArray() {
        when(groupMapper.selectById("group-1"))
                .thenReturn(group("group-1", "finance", "财务组"));

        service.saveGroupUsers("group-1", List.of());

        verify(userGroupMapper).deleteByGroupId("group-1");
        verify(userGroupMapper, never()).insert(any(SysUserGroup.class));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveGroupUsers("group-1", null));
    }

    @Test
    void rejectsBlankOrMissingMemberBeforeDeletingExistingAssignments() {
        when(groupMapper.selectById("group-1"))
                .thenReturn(group("group-1", "finance", "财务组"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveGroupUsers("group-1", List.of(" ")));

        when(userMapper.selectExistingIdsByIds(List.of("user-1", "missing")))
                .thenReturn(List.of("user-1"));
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveGroupUsers(
                        "group-1", List.of("user-1", "missing")));
        assertTrue(error.getMessage().contains("missing"));
        verify(userGroupMapper, never()).deleteByGroupId(any());
        verify(userGroupMapper, never()).insert(any(SysUserGroup.class));
    }

    @Test
    void rejectsZeroAffectedRowsFromCreateOrMemberInsert() {
        SysGroup createRequest = group(null, "finance", "财务组");
        when(groupMapper.existsGroupCode("finance", "")).thenReturn(false);
        when(groupMapper.insert(any(SysGroup.class))).thenReturn(0);
        assertThrows(
                IllegalStateException.class,
                () -> service.createGroup(createRequest));

        when(groupMapper.selectById("group-1"))
                .thenReturn(group("group-1", "finance", "财务组"));
        when(userMapper.selectExistingIdsByIds(List.of("user-1")))
                .thenReturn(List.of("user-1"));
        when(userGroupMapper.insert(any(SysUserGroup.class))).thenReturn(0);
        assertThrows(
                IllegalStateException.class,
                () -> service.saveGroupUsers("group-1", List.of("user-1")));
    }

    private static List<SysGroup> groups(int count) {
        List<SysGroup> groups = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            groups.add(group("group-" + i, "group_" + i, "用户组 " + i));
        }
        return groups;
    }

    private static SysGroup group(String id, String code, String name) {
        SysGroup group = new SysGroup();
        group.setId(id);
        group.setGroupName(name);
        group.setGroupCode(code);
        return group;
    }

    private static SysUser user(String id, String status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        return user;
    }

    private static SysGroupMapper.GroupUserIdRow row(String groupId, String userId) {
        SysGroupMapper.GroupUserIdRow row = new SysGroupMapper.GroupUserIdRow();
        row.setGroupId(groupId);
        row.setUserId(userId);
        return row;
    }
}
