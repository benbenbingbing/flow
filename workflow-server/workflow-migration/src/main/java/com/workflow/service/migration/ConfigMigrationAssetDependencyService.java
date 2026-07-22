package com.workflow.service.migration;

import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.entity.migration.ConfigMigrationAssetDependency;
import com.workflow.mapper.migration.ConfigMigrationAssetDependencyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConfigMigrationAssetDependencyService {

    private final ConfigMigrationAssetDependencyMapper dependencyMapper;
    private final JsonDocumentCodec codec;

    @Transactional(rollbackFor = Exception.class)
    public void replace(String assetId, List<Map<String, Object>> dependencies) {
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
            dependency.setDependencyDocument(codec.write(
                    source, "配置迁移依赖"));
            dependency.setCreatedAt(LocalDateTime.now());
            dependencyMapper.insert(dependency);
        }
    }

    public List<Map<String, Object>> find(String assetId) {
        return dependencyMapper.findByAssetId(assetId).stream()
                .map(this::toMap)
                .toList();
    }

    private Map<String, Object> toMap(ConfigMigrationAssetDependency dependency) {
        Map<String, Object> result = StringUtils.hasText(dependency.getDependencyDocument())
                ? new LinkedHashMap<>(codec.readObject(
                        dependency.getDependencyDocument(), "配置迁移依赖"))
                : new LinkedHashMap<>();
        result.put("type", dependency.getDependencyType());
        result.put("key", dependency.getDependencyKey());
        result.put("required", dependency.getRequired());
        result.put("source", dependency.getSourceDescription());
        return result;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
