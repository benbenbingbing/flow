package com.workflow.service;

import com.workflow.common.UserContext;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.publish.EntityPublishedSnapshot;
import com.workflow.entity.publish.EntityPublishedSnapshotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntityRecordTeamServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final EntityPhysicalTableResolver tableResolver = mock(EntityPhysicalTableResolver.class);
    private final EntityPublishedSnapshotService snapshotService =
            mock(EntityPublishedSnapshotService.class);
    private final EntityRecordTeamService service =
            new EntityRecordTeamService(jdbcTemplate, tableResolver, snapshotService);

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void ensureTeamTableUsesEventIndexesWithoutBusinessUniqueIndex() {
        EntityDefinition definition = new EntityDefinition();
        definition.setTeamVisibilityEnabled(false);
        when(tableResolver.resolve(definition)).thenReturn("wf_expense");

        service.ensureTeamTable(definition);

        verify(jdbcTemplate).execute(argThat((String sql) ->
                sql.contains("CREATE TABLE IF NOT EXISTS `wf_expense_team`")
                        && sql.contains("`record_id`")
                        && sql.contains("idx_team_user_record")
                        && sql.contains("COLLATE=utf8mb4_unicode_ci")
                        && !sql.toUpperCase().contains("UNIQUE KEY")));
    }

    @Test
    void recordSkipsAutomatedSystemOperations() {
        UserContext.setCurrentUser("system", "系统");

        service.record("expense", "record-1", "EDIT", "自动更新", null, null);

        verifyNoInteractions(snapshotService);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void backfillSkipsWhenTeamTableAlreadyContainsEvents() {
        EntityDefinition definition = new EntityDefinition();
        when(tableResolver.resolve(definition)).thenReturn("wf_expense");
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `wf_expense_team`", Long.class))
                .thenReturn(2L);

        service.backfillIfEmpty(definition);

        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    void teamPermissionCorrelatesRecordToOuterEntityTable() {
        EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
        snapshot.setTeamVisibilityEnabled(true);
        snapshot.setTeamVisibilityLevel(EntityDefinition.TeamVisibilityLevel.OVERRIDE_SCOPE);
        when(snapshotService.getLatestByEntityCode("expense")).thenReturn(snapshot);
        when(tableResolver.resolve("expense")).thenReturn("wf_expense");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("wf_expense_team")))
                .thenReturn(1);

        EntityRecordTeamService.TeamPermission permission =
                service.teamPermission("expense", "user-1");

        assertTrue(permission.enabled());
        assertEquals(EntityDefinition.TeamVisibilityLevel.OVERRIDE_SCOPE, permission.level());
        assertTrue(permission.sqlCondition().contains("team.record_id = `wf_expense`.id"));
        assertTrue(permission.sqlCondition().contains("team.user_id = 'user-1'"));
    }
}
