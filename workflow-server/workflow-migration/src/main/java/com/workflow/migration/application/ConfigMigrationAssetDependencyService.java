package com.workflow.migration.application;

import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAssetDependency;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetDependencyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置迁移资产依赖服务。
 *
 * <p>负责迁移资产依赖记录的重建与查询：在保存资产快照时清空并重写其依赖清单，
 * 在导入分析/展示时按资产ID读取依赖列表。</p>
 */
@Service
@RequiredArgsConstructor
public class ConfigMigrationAssetDependencyService {

    private final ConfigMigrationAssetDependencyMapper dependencyMapper;
    private final JsonDocumentCodec codec;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 用新的依赖列表覆盖指定资产的依赖记录。
     *
     * <p>先删除该资产全部依赖，再依次写入新的依赖项；忽略缺少 type 或 key 的条目。</p>
     *
     * @param assetId     资产ID
     * @param dependencies 新的依赖列表(可为空)
     */
    @Transactional(rollbackFor = Exception.class)
    public void replace(String assetId, List<Map<String, Object>> dependencies) {
        Map<String, Object> owner = sourceAsset(assetId);
        dependencyMapper.deleteByAssetId(assetId);
        for (Map<String, Object> source : dependencies == null
                ? List.<Map<String, Object>>of()
                : dependencies) {
            String type = text(source.get("type"));
            String key = text(source.get("key"));
            if (!StringUtils.hasText(type) || !StringUtils.hasText(key)) {
                continue;
            }
            ConfigMigrationAssetDependency dependency =
                    new ConfigMigrationAssetDependency();
            dependency.setAssetId(assetId);
            dependency.setDependencyType(type);
            dependency.setDependencyKey(key);
            dependency.setRequired(!Boolean.FALSE.equals(source.get("required")));
            dependency.setSourceDescription(text(source.get("source")));
            dependency.setSourceAssetType(text(owner.get("assetType")));
            dependency.setSourceBusinessKey(text(owner.get("businessKey")));
            dependency.setSourceVersion(number(owner.get("sourceVersion")));
            dependency.setReferenceLocation(text(source.get("location")));
            dependency.setDependencyStrength(normalized(
                    text(source.get("strength")),
                    dependency.getRequired() ? "HARD" : "SOFT"));
            dependency.setParseStatus(normalized(
                    text(source.get("parseStatus")), "RESOLVED"));
            dependency.setDependencyDocument(codec.write(
                    source, "配置迁移依赖"));
            dependency.setCreatedAt(LocalDateTime.now());
            dependency.setExtractedAt(LocalDateTime.now());
            dependencyMapper.insert(dependency);
        }
    }

    /**
     * 查询指定资产的依赖列表，并以 Map 形式返回。
     *
     * @param assetId 资产ID
     * @return 依赖描述列表
     */
    public List<Map<String, Object>> find(String assetId) {
        return dependencyMapper.findByAssetId(assetId).stream()
                .map(this::toMap)
                .toList();
    }

    /**
     * 将依赖实体转换为依赖描述 Map，合并存储文档与冗余字段。
     */
    private Map<String, Object> toMap(ConfigMigrationAssetDependency dependency) {
        Map<String, Object> result = StringUtils.hasText(dependency.getDependencyDocument())
                ? new LinkedHashMap<>(codec.readObject(
                        dependency.getDependencyDocument(), "配置迁移依赖"))
                : new LinkedHashMap<>();
        result.put("type", dependency.getDependencyType());
        result.put("key", dependency.getDependencyKey());
        result.put("required", dependency.getRequired());
        result.put("source", dependency.getSourceDescription());
        result.put("sourceAssetType", dependency.getSourceAssetType());
        result.put("sourceBusinessKey", dependency.getSourceBusinessKey());
        result.put("sourceVersion", dependency.getSourceVersion());
        result.put("location", dependency.getReferenceLocation());
        result.put("strength", dependency.getDependencyStrength());
        result.put("parseStatus", dependency.getParseStatus());
        result.put("extractedAt", dependency.getExtractedAt());
        return result;
    }

    /** 查询依赖所属资产的稳定标识和版本，确保引用记录不会混淆不同历史版本。 */
    private Map<String, Object> sourceAsset(String assetId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT asset_type AS assetType, business_key AS businessKey,
                       source_version AS sourceVersion
                FROM config_migration_asset
                WHERE id = ? AND deleted = 0
                """, assetId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("配置迁移资产不存在: " + assetId);
        }
        return rows.get(0);
    }

    private Integer number(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String normalized(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : fallback;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
