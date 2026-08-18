package com.workflow.service.permission;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.permission.application.CurrentProcessTaskAssigneeLookup;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentProcessTaskAssigneeLookupTest {

    @Test
    void matchesTodoByUsernameWhenEntityFieldStoresAnotherAssignee() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                contains("FROM process_task"),
                eq(Integer.class),
                any(Object[].class)))
                .thenReturn(1);
        CurrentProcessTaskAssigneeLookup lookup =
                new CurrentProcessTaskAssigneeLookup(jdbcTemplate);
        EntityDataDTO row = new EntityDataDTO();
        row.setId("387832ec23b0464baaad021ab0b72bce");
        row.setEntityCode("ZDWREQ");
        row.setCurrentTaskAssignee("verify_user");
        SysUser lisi = new SysUser();
        lisi.setId("2038628006255251457");
        lisi.setUsername("lisi");

        assertTrue(lookup.isCurrentAssignee(row, lisi));
    }

    @Test
    void failsClosedWhenRecordHasNoIdentity() {
        CurrentProcessTaskAssigneeLookup lookup =
                new CurrentProcessTaskAssigneeLookup(mock(JdbcTemplate.class));
        assertFalse(lookup.isCurrentAssignee(new EntityDataDTO(), new SysUser()));
    }
}
