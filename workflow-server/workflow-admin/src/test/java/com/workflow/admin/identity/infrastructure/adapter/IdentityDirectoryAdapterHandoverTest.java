package com.workflow.admin.identity.infrastructure.adapter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentityDirectoryAdapterHandoverTest {
    private final SysUserService userService = mock(SysUserService.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final IdentityDirectoryAdapter adapter = new IdentityDirectoryAdapter(
            userService, mock(SysGroupMapper.class), userMapper);

    @Test
    void findsDisabledAndDeletedSourceByExactIdWithoutOrdinaryDirectoryFallback() {
        SysUser source = user("retired-id", "1", 1);
        when(userMapper.selectHandoverUser("retired-id")).thenReturn(source);

        var found = adapter.findHandoverUser("retired-id").orElseThrow();

        assertEquals("retired-id", found.id());
        assertEquals("1", found.status());
        assertTrue(found.deleted());
        verifyNoInteractions(userService);
    }

    @Test
    void boundsSearchAndPreservesSourceOrTargetSelection() {
        doAnswer(invocation -> {
            Page<SysUser> page = invocation.getArgument(0);
            assertEquals(1, page.getCurrent());
            assertEquals(200, page.getSize());
            assertFalse(page.searchCount());
            return page.setRecords(List.of(user("active-id", "0", 0)));
        }).when(userMapper).selectHandoverUsers(any(), eq("alice"), eq(true));

        var found = adapter.searchHandoverUsers("  alice  ", true);

        assertEquals(1, found.size());
        assertFalse(found.get(0).deleted());
        verify(userMapper).selectHandoverUsers(any(), eq("alice"), eq(true));
    }

    @Test
    void treatsBlankIdsAsMissingAndDoesNotLockOrQueryEntireDirectory() {
        assertTrue(adapter.findHandoverUser(" ").isEmpty());
        assertTrue(adapter.lockHandoverUser(null).isEmpty());
        verifyNoInteractions(userMapper);
    }

    @Test
    void lockingReadsLatestStateAndRequiresExistingHandoverTransaction() throws Exception {
        when(userMapper.selectHandoverUserForUpdate("active-id")).thenReturn(user("active-id", "0", 0));
        assertEquals("active-id", adapter.lockHandoverUser("active-id").orElseThrow().id());
        var transaction = IdentityDirectoryAdapter.class.getMethod("lockHandoverUser", String.class)
                .getAnnotation(Transactional.class);
        assertEquals(Propagation.MANDATORY, transaction.propagation());
    }

    private SysUser user(String id, String status, int deleted) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("alice");
        user.setNickname("张三");
        user.setStatus(status);
        user.setDeleted(deleted);
        return user;
    }
}
