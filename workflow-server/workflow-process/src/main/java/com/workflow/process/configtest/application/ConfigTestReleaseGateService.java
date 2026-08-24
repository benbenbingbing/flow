package com.workflow.process.configtest.application;

import com.workflow.core.error.ForbiddenException;
import com.workflow.process.configtest.application.ConfigTestModels.GateStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 配置发布门禁：仅在显式启用 release_gate 的套件存在时生效。 */
@Service
@RequiredArgsConstructor
public class ConfigTestReleaseGateService {

    private final JdbcTemplate jdbcTemplate;

    public GateStatus status(String scopeType, String scopeId) {
        List<GateRow> rows = jdbcTemplate.query("""
                SELECT s.id, s.name, s.update_time,
                       (SELECT r.status FROM config_test_run r
                        WHERE r.suite_id = s.id AND r.status <> 'RUNNING'
                        ORDER BY r.started_at DESC LIMIT 1) AS latest_status,
                       (SELECT r.started_at FROM config_test_run r
                        WHERE r.suite_id = s.id AND r.status <> 'RUNNING'
                        ORDER BY r.started_at DESC LIMIT 1) AS latest_started_at,
                       (SELECT MAX(c.update_time) FROM config_test_case c
                        WHERE c.suite_id = s.id) AS case_updated_at
                FROM config_test_suite s
                WHERE s.scope_type = ? AND s.scope_id = ?
                  AND s.enabled = 1 AND s.release_gate = 1 AND s.deleted = 0
                """, (rs, row) -> new GateRow(
                rs.getString("id"), rs.getString("name"), rs.getTimestamp("update_time"),
                rs.getString("latest_status"), rs.getTimestamp("latest_started_at"),
                rs.getTimestamp("case_updated_at")), scopeType.toUpperCase(), scopeId);
        List<String> reasons = new ArrayList<>();
        for (GateRow row : rows) {
            if (!"PASS".equals(row.latestStatus)) {
                reasons.add(row.name + " 尚无通过的最新运行");
                continue;
            }
            if (row.latestStartedAt == null
                    || row.latestStartedAt.before(row.suiteUpdatedAt)
                    || (row.caseUpdatedAt != null && row.latestStartedAt.before(row.caseUpdatedAt))) {
                reasons.add(row.name + " 的通过结果已过期");
            }
        }
        return new GateStatus(scopeType.toUpperCase(), scopeId, !rows.isEmpty(), reasons.isEmpty(), List.copyOf(reasons));
    }

    public void requirePassed(String scopeType, String scopeId) {
        GateStatus status = status(scopeType, scopeId);
        if (status.required() && !status.passed()) {
            throw new ForbiddenException("配置测试发布门禁未通过: " + String.join("；", status.reasons()));
        }
    }

    private record GateRow(
            String id,
            String name,
            java.sql.Timestamp suiteUpdatedAt,
            String latestStatus,
            java.sql.Timestamp latestStartedAt,
            java.sql.Timestamp caseUpdatedAt) {
    }
}
