package com.workflow.service.permission;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.permission.application.CurrentProcessTaskAssigneeLookup;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrentProcessTaskAssigneeLookupTest {

    @Test
    void matchesTodoByUsernameWhenEntityFieldStoresAnotherAssignee() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                contains("FROM process_task"),
                eq(String.class),
                any(Object[].class)))
                .thenReturn(List.of("task-lisi-sibling"));
        CurrentProcessTaskAssigneeLookup lookup =
                new CurrentProcessTaskAssigneeLookup(jdbcTemplate);
        EntityDataDTO row = new EntityDataDTO();
        row.setId("387832ec23b0464baaad021ab0b72bce");
        row.setEntityCode("ZDWREQ");
        row.setProcessInstanceId("process-1");
        row.setCurrentTaskAssignee("verify_user");
        SysUser lisi = new SysUser();
        lisi.setId("2038628006255251457");
        lisi.setUsername("lisi");

        assertEquals(
                Optional.of("task-lisi-sibling"),
                lookup.findActionableTaskId(row, lisi));
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                eq(String.class),
                argsCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertFalse(sql.contains(" OR "));
        assertTrue(sql.contains(
                "entity_data_id = ? AND entity_code = ? AND process_instance_id = ?"));
        assertEquals(
                List.of(
                        "2038628006255251457",
                        "lisi",
                        "387832ec23b0464baaad021ab0b72bce",
                        "ZDWREQ",
                        "process-1"),
                Arrays.asList(argsCaptor.getValue()));
    }

    @Test
    void failsClosedWhenRecordHasNoIdentity() {
        CurrentProcessTaskAssigneeLookup lookup =
                new CurrentProcessTaskAssigneeLookup(mock(JdbcTemplate.class));
        assertFalse(lookup.isCurrentAssignee(new EntityDataDTO(), new SysUser()));
        assertEquals(
                Optional.empty(),
                lookup.findActionableTaskId(
                        new EntityDataDTO(),
                        new SysUser()));
    }
}
