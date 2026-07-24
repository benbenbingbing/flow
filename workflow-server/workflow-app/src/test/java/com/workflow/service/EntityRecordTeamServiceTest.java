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

/**
 * 实体记录团队服务测试。
 *
 * <p>被测对象：{@link EntityRecordTeamService}，覆盖团队表创建（仅事件索引无业务唯一索引）、
 * 自动化系统操作跳过记录、已存在事件时跳过回填、团队权限关联记录到外层实体表等场景。
 */
class EntityRecordTeamServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final EntityPhysicalTableResolver tableResolver = mock(EntityPhysicalTableResolver.class);
    private final EntityPublishedSnapshotService snapshotService =
            mock(EntityPublishedSnapshotService.class);
    /** 被测团队服务 */
    private final EntityRecordTeamService service =
            new EntityRecordTeamService(jdbcTemplate, tableResolver, snapshotService);

    /** 清理用户上下文，避免用例间污染 */
    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    /** 测试创建团队表仅使用事件索引且不含业务唯一索引：验证建表 SQL 含 record_id、事件索引、unicode 校对且无 UNIQUE KEY */
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

    /** 测试自动化系统操作跳过记录：验证系统用户触发记录时不与快照服务和 JdbcTemplate 交互 */
    @Test
    void recordSkipsAutomatedSystemOperations() {
        UserContext.setCurrentUser("system", "系统");

        service.record("expense", "record-1", "EDIT", "自动更新", null, null);

        verifyNoInteractions(snapshotService);
        verifyNoInteractions(jdbcTemplate);
    }

    /** 测试团队表已含事件时跳过回填：验证计数大于 0 时不执行 update */
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

    /** 测试团队权限将记录关联到外层实体表：验证权限启用、层级正确且 SQL 条件含 record_id 与 user_id 关联 */
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
