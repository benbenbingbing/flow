package com.workflow.migration.reference.application;

import com.workflow.migration.reference.application.ConfigurationReferenceModels.ImpactReport;
import com.workflow.migration.reference.application.ConfigurationReferenceModels.ReferenceEdge;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** 复用配置迁移依赖表提供正反向引用、版本过滤和路径影响查询。 */
@Service
@RequiredArgsConstructor
public class ConfigurationReferenceQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final ConfigReferenceGraphAnalyzer graphAnalyzer;

    public List<ReferenceEdge> forward(String type, String key, Integer version) {
        StringBuilder sql = new StringBuilder(BASE_SQL)
                .append(" WHERE COALESCE(d.source_asset_type, a.asset_type) = ?")
                .append(" AND COALESCE(d.source_business_key, a.business_key) = ?");
        List<Object> arguments = new ArrayList<>(List.of(type, key));
        if (version != null) {
            sql.append(" AND COALESCE(d.source_version, a.source_version) = ?");
            arguments.add(version);
        }
        sql.append(" ORDER BY source_version DESC, d.dependency_type, d.dependency_key");
        return jdbcTemplate.query(sql.toString(), this::map, arguments.toArray());
    }

    public List<ReferenceEdge> reverse(String type, String key) {
        return jdbcTemplate.query(BASE_SQL + """
                 WHERE d.dependency_type = ? AND d.dependency_key = ?
                 ORDER BY source_version DESC, source_type, source_key
                """, this::map, type, key);
    }

    public ImpactReport impact(
            String type,
            String key,
            String direction,
            Integer maxDepth) {
        List<ReferenceEdge> edges = jdbcTemplate.query(
                BASE_SQL + " ORDER BY a.update_time DESC LIMIT 10000", this::map);
        return graphAnalyzer.analyze(type, key, direction,
                maxDepth == null ? 8 : maxDepth, edges);
    }

    private ReferenceEdge map(ResultSet rs, int rowNum) throws SQLException {
        return new ReferenceEdge(
                rs.getString("id"), rs.getString("source_type"), rs.getString("source_key"),
                (Integer) rs.getObject("source_version"), rs.getString("target_type"),
                rs.getString("target_key"), rs.getBoolean("required"),
                normalized(rs.getString("dependency_strength"), rs.getBoolean("required") ? "HARD" : "SOFT"),
                rs.getString("reference_location"), normalized(rs.getString("parse_status"), "RESOLVED"));
    }

    private String normalized(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static final String BASE_SQL = """
            SELECT d.id,
                   COALESCE(d.source_asset_type, a.asset_type) source_type,
                   COALESCE(d.source_business_key, a.business_key) source_key,
                   COALESCE(d.source_version, a.source_version) source_version,
                   d.dependency_type target_type, d.dependency_key target_key,
                   d.required, d.dependency_strength, d.reference_location, d.parse_status
            FROM config_migration_asset_dependency d
            JOIN config_migration_asset a ON a.id = d.asset_id AND a.deleted = 0
            """;
}
