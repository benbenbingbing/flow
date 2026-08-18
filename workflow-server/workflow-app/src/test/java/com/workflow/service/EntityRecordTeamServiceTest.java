package com.workflow.service;

import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.entity.data.application.EntityRecordTeamService;
import com.workflow.entity.data.application.SchemaDdlExecutor;

import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
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
 * 自动化系统操作跳过记录、团队权限关联记录到外层实体表等场景。
 */
class EntityRecordTeamServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final EntityPhysicalTableResolver tableResolver = mock(EntityPhysicalTableResolver.class);
    private final EntityPublishedSnapshotService snapshotService =
            mock(EntityPublishedSnapshotService.class);
    private final SchemaDdlExecutor schemaDdlExecutor = mock(SchemaDdlExecutor.class);
    /** 被测团队服务 */
    private final EntityRecordTeamService service =
            new EntityRecordTeamService(
                    jdbcTemplate, tableResolver, snapshotService, schemaDdlExecutor);

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

        verify(schemaDdlExecutor).execute(argThat((String sql) ->
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
        assertTrue(permission.sqlCondition().contains("team.user_id = #{permissionParameters.teamUserId}"));
        assertEquals("user-1", permission.sqlParameters().get("teamUserId"));
    }

    @Test
    void relatedPeopleSqlUsesTeamTableAndEscapesUserId() {
        when(tableResolver.resolve("expense")).thenReturn("wf_expense");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("wf_expense_team")))
                .thenReturn(1);

        String sql = service.relatedPeopleSql("expense", "u'1", "li'si");

        assertTrue(sql.contains("`wf_expense_team`"));
        assertTrue(sql.contains("team.record_id = `wf_expense`.id"));
        assertTrue(sql.contains("team.user_id IN ('u''1','li''si')"));
        assertFalse(sql.contains("current_task_assignee"));
    }

    @Test
    void recordWritesWithoutPublishedSnapshot() {
        UserContext.setCurrentUser("2038628006255251457", "lisi");
        when(tableResolver.resolve("expense")).thenReturn("wf_expense");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("wf_expense_team")))
                .thenReturn(1);
        when(snapshotService.getLatestByEntityCode("expense"))
                .thenThrow(new RuntimeException("实体未发布: expense"));

        service.record("expense", "record-1", "APPROVE", "通过", "pi-1", "task-1");

        verify(jdbcTemplate).update(
                contains("INSERT INTO `wf_expense_team`"),
                any(),
                eq("record-1"),
                eq("2038628006255251457"),
                eq("APPROVE"),
                eq("通过"),
                eq("pi-1"),
                eq("task-1"));
        verify(snapshotService, never()).getLatestByEntityCode(anyString());
    }

    @Test
    void relatedPeopleSqlFailsClosedWhenTeamTableMissing() {
        when(tableResolver.resolve("expense")).thenReturn("wf_expense");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("wf_expense_team")))
                .thenReturn(0);

        assertEquals("1=0", service.relatedPeopleSql("expense", "user-1"));
    }
}
