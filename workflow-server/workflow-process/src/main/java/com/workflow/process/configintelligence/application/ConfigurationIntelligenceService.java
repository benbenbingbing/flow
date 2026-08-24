package com.workflow.process.configintelligence.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.AssetRef;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.Blueprint;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.BlueprintInstance;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.BlueprintSaveRequest;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.DependencyEdge;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.DependencySaveRequest;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.ImpactReport;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.ImpactRequest;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.PackageVerification;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.PortablePackage;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.QualityAnalyzeRequest;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.QualityReport;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** P2 配置蓝图、依赖图、质量分析和便携包的统一应用服务。 */
@Service
@RequiredArgsConstructor
public class ConfigurationIntelligenceService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final BlueprintTemplateEngine templateEngine;
    private final DependencyImpactAnalyzer impactAnalyzer;
    private final ConfigurationQualityAnalyzer qualityAnalyzer;

    public List<Blueprint> blueprints(String category, String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM config_blueprint WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(category)) {
            sql.append(" AND category = ?");
            args.add(category.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(status)) {
            sql.append(" AND status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY update_time DESC, version DESC");
        return jdbcTemplate.query(sql.toString(), this::mapBlueprint, args.toArray());
    }

    public Blueprint blueprint(String id) {
        List<Blueprint> rows = jdbcTemplate.query(
                "SELECT * FROM config_blueprint WHERE id = ?",
                this::mapBlueprint,
                id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("配置蓝图不存在: " + id);
        }
        return rows.get(0);
    }

    /** 新建版本或更新尚未发布的草稿，已发布版本保持不可变。 */
    @Transactional
    public Blueprint saveBlueprint(BlueprintSaveRequest request) {
        validateBlueprint(request);
        String checksum = checksum(Map.of(
                "schema", safeMap(request.parameterSchema()),
                "bundle", safeMap(request.bundle())));
        Instant now = Instant.now();
        String actor = currentActor();
        if (StringUtils.hasText(request.id())) {
            Blueprint current = blueprint(request.id());
            if (!"DRAFT".equals(current.status())) {
                throw new IllegalStateException("已发布蓝图不可修改，请创建新版本");
            }
            jdbcTemplate.update("""
                    UPDATE config_blueprint
                    SET name = ?, category = ?, description = ?, parameter_schema_json = ?,
                        bundle_json = ?, checksum = ?, updated_by = ?, update_time = ?
                    WHERE id = ? AND status = 'DRAFT'
                    """, request.name().trim(), normalize(request.category()), request.description(),
                    json(request.parameterSchema()), json(request.bundle()), checksum, actor,
                    Timestamp.from(now), request.id());
            return blueprint(request.id());
        }
        int version = request.version() == null
                ? nextVersion(request.blueprintKey())
                : request.version();
        String id = id();
        jdbcTemplate.update("""
                INSERT INTO config_blueprint
                (id, blueprint_key, name, category, description, version, status,
                 parameter_schema_json, bundle_json, checksum, download_count,
                 created_by, updated_by, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, 0, ?, ?, ?, ?)
                """, id, request.blueprintKey().trim(), request.name().trim(), normalize(request.category()),
                request.description(), version, json(request.parameterSchema()), json(request.bundle()),
                checksum, actor, actor, Timestamp.from(now), Timestamp.from(now));
        return blueprint(id);
    }

    @Transactional
    public Blueprint publishBlueprint(String id) {
        Blueprint blueprint = blueprint(id);
        // 发布前用默认参数执行一次实例化；无默认值的必填参数由引擎验证 schema 本身。
        validateSchemaReferences(blueprint);
        jdbcTemplate.update("""
                UPDATE config_blueprint SET status = 'PUBLISHED', updated_by = ?, update_time = ?
                WHERE id = ? AND status = 'DRAFT'
                """, currentActor(), Timestamp.from(Instant.now()), id);
        return blueprint(id);
    }

    public BlueprintInstance instantiate(String id, Map<String, Object> parameters) {
        Blueprint blueprint = blueprint(id);
        BlueprintTemplateEngine.RenderResult rendered = templateEngine.render(
                blueprint.parameterSchema(), blueprint.bundle(), parameters);
        String instanceChecksum = checksum(Map.of(
                "blueprint", blueprint.checksum(),
                "parameters", rendered.effectiveParameters(),
                "bundle", rendered.renderedBundle()));
        if ("PUBLISHED".equals(blueprint.status())) {
            jdbcTemplate.update("UPDATE config_blueprint SET download_count = download_count + 1 WHERE id = ?", id);
        }
        return new BlueprintInstance(
                blueprint.id(), blueprint.blueprintKey(), blueprint.version(),
                rendered.effectiveParameters(), rendered.renderedBundle(), instanceChecksum);
    }

    public PortablePackage portablePackage(String id, Map<String, Object> parameters) {
        BlueprintInstance instance = instantiate(id, parameters);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", "WORKFLOW_CONFIG_PACKAGE");
        manifest.put("formatVersion", 1);
        manifest.put("blueprintKey", instance.blueprintKey());
        manifest.put("blueprintVersion", instance.blueprintVersion());
        manifest.put("generatedAt", Instant.now().toString());
        manifest.put("payloadChecksum", instance.checksum());
        Map<String, Object> payload = Map.of(
                "parameters", instance.effectiveParameters(),
                "bundle", instance.renderedBundle());
        return new PortablePackage(manifest, payload, checksum(Map.of("manifest", manifest, "payload", payload)));
    }

    public PackageVerification verifyPackage(PortablePackage configPackage) {
        String actual = checksum(Map.of(
                "manifest", configPackage.manifest(),
                "payload", configPackage.payload()));
        return new PackageVerification(
                actual.equalsIgnoreCase(configPackage.packageChecksum()),
                configPackage.packageChecksum(),
                actual);
    }

    /** 原子替换资产的出边，避免保存一半导致影响图不一致。 */
    @Transactional
    public List<DependencyEdge> replaceDependencies(
            String sourceType,
            String sourceId,
            List<DependencySaveRequest> dependencies) {
        String normalizedType = normalize(sourceType);
        requireText(sourceId, "sourceId");
        jdbcTemplate.update(
                "DELETE FROM config_asset_dependency WHERE source_type = ? AND source_id = ?",
                normalizedType, sourceId);
        for (DependencySaveRequest dependency : dependencies == null ? List.<DependencySaveRequest>of() : dependencies) {
            jdbcTemplate.update("""
                    INSERT INTO config_asset_dependency
                    (id, source_type, source_id, target_type, target_id, relation_type,
                     required_flag, metadata_json, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id(), normalizedType, sourceId, normalize(dependency.targetType()),
                    requireText(dependency.targetId(), "targetId"), normalize(dependency.relationType()),
                    dependency.required() == null || dependency.required(), json(dependency.metadata()),
                    Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        }
        return dependencies(normalizedType, sourceId);
    }

    public List<DependencyEdge> dependencies(String sourceType, String sourceId) {
        return jdbcTemplate.query("""
                SELECT * FROM config_asset_dependency
                WHERE source_type = ? AND source_id = ? ORDER BY relation_type, target_type, target_id
                """, this::mapDependency, normalize(sourceType), sourceId);
    }

    public ImpactReport impact(ImpactRequest request) {
        List<DependencyEdge> allEdges = jdbcTemplate.query(
                "SELECT * FROM config_asset_dependency ORDER BY id",
                this::mapDependency);
        return impactAnalyzer.analyze(
                new AssetRef(normalize(request.assetType()), requireText(request.assetId(), "assetId")),
                allEdges,
                request.direction(),
                request.maxDepth() == null ? 6 : request.maxDepth());
    }

    @Transactional
    public QualityReport analyzeQuality(QualityAnalyzeRequest request) {
        QualityReport report = qualityAnalyzer.analyze(
                normalize(request.assetType()),
                requireText(request.assetId(), "assetId"),
                safeMap(request.configuration()));
        jdbcTemplate.update("""
                INSERT INTO config_quality_snapshot
                (id, asset_type, asset_id, config_fingerprint, score, grade, blocker_count,
                 warning_count, findings_json, generated_by, generated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id(), report.assetType(), report.assetId(), report.fingerprint(), report.score(),
                report.grade(), report.blockerCount(), report.warningCount(), json(report.findings()),
                currentActor(), Timestamp.from(report.generatedAt()));
        return report;
    }

    public List<QualityReport> qualityHistory(String assetType, String assetId) {
        return jdbcTemplate.query("""
                SELECT * FROM config_quality_snapshot
                WHERE asset_type = ? AND asset_id = ? ORDER BY generated_at DESC LIMIT 50
                """, (rs, row) -> new QualityReport(
                rs.getString("asset_type"), rs.getString("asset_id"),
                rs.getString("config_fingerprint"), rs.getInt("score"), rs.getString("grade"),
                rs.getInt("blocker_count"), rs.getInt("warning_count"),
                list(rs.getString("findings_json"), ConfigurationIntelligenceModels.QualityFinding.class),
                List.of(), rs.getTimestamp("generated_at").toInstant()),
                normalize(assetType), assetId);
    }

    private Blueprint mapBlueprint(ResultSet rs, int row) throws SQLException {
        return new Blueprint(
                rs.getString("id"), rs.getString("blueprint_key"), rs.getString("name"),
                rs.getString("category"), rs.getString("description"), rs.getInt("version"),
                rs.getString("status"), map(rs.getString("parameter_schema_json")),
                map(rs.getString("bundle_json")), rs.getString("checksum"),
                rs.getLong("download_count"), rs.getTimestamp("create_time").toInstant(),
                rs.getTimestamp("update_time").toInstant());
    }

    private DependencyEdge mapDependency(ResultSet rs, int row) throws SQLException {
        return new DependencyEdge(
                rs.getString("id"),
                new AssetRef(rs.getString("source_type"), rs.getString("source_id")),
                new AssetRef(rs.getString("target_type"), rs.getString("target_id")),
                rs.getString("relation_type"), rs.getBoolean("required_flag"),
                map(rs.getString("metadata_json")));
    }

    private void validateBlueprint(BlueprintSaveRequest request) {
        requireText(request.blueprintKey(), "blueprintKey");
        requireText(request.name(), "蓝图名称");
        requireText(request.category(), "蓝图分类");
        if (request.bundle() == null || request.bundle().isEmpty()) {
            throw new IllegalArgumentException("蓝图内容不能为空");
        }
        // 使用 schema 默认值进行尽可能完整的静态引用检查。
        try {
            templateEngine.render(request.parameterSchema(), request.bundle(), Map.of());
        } catch (IllegalArgumentException exception) {
            if (!exception.getMessage().startsWith("缺少蓝图必填参数")) {
                throw exception;
            }
        }
    }

    private void validateSchemaReferences(Blueprint blueprint) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        objectMapper.valueToTree(blueprint.parameterSchema()).path("parameters")
                .fields().forEachRemaining(entry -> {
                    if (entry.getValue().has("default")) {
                        defaults.put(entry.getKey(), objectMapper.convertValue(
                                entry.getValue().get("default"), Object.class));
                    }
                });
        try {
            templateEngine.render(blueprint.parameterSchema(), blueprint.bundle(), defaults);
        } catch (IllegalArgumentException exception) {
            if (!exception.getMessage().startsWith("缺少蓝图必填参数")) {
                throw exception;
            }
        }
    }

    private int nextVersion(String key) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version), 0) + 1 FROM config_blueprint WHERE blueprint_key = ?",
                Integer.class,
                key);
        return value == null ? 1 : value;
    }

    private String checksum(Object value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] json = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成配置包校验和", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("配置资产无法序列化", exception);
        }
    }

    private Map<String, Object> map(String json) {
        if (!StringUtils.hasText(json)) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("持久化配置资产 JSON 已损坏", exception);
        }
    }

    private <T> List<T> list(String json, Class<T> type) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception exception) {
            throw new IllegalStateException("持久化质量结果 JSON 已损坏", exception);
        }
    }

    private String normalize(String value) {
        return requireText(value, "类型").toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private String currentActor() {
        return StringUtils.hasText(UserContext.getUserId()) ? UserContext.getUserId() : UserContext.getUsername();
    }

    private String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
