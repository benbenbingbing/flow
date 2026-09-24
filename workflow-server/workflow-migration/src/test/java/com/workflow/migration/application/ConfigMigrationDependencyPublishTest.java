package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetDependencyMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAssetDependency;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 覆盖发布时真实的引用转换、资产快照和依赖写入链路，模拟数据库唯一约束拒绝重复行。 */
class ConfigMigrationDependencyPublishTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void publishingRepeatedAdminReferencesKeepsOneDependencyAndAllLocations() throws Exception {
        var references = new ConfigMigrationReferenceService(null, null, null, null, null, null);
        var subForms = new ConfigMigrationSubFormReferences(null, null, null, references, null, json);
        var assets = mock(ConfigMigrationAssetService.class, CALLS_REAL_METHODS);
        var assetMapper = mock(ConfigMigrationAssetMapper.class);
        var dependencyMapper = mock(ConfigMigrationAssetDependencyMapper.class);
        var jdbc = mock(JdbcTemplate.class);
        var rows = new LinkedHashMap<List<String>, ConfigMigrationAssetDependency>();
        when(jdbc.queryForList(anyString(), eq("asset-v24"))).thenReturn(List.of(Map.of(
                "assetType", "PROCESS", "businessKey", "all_flow", "sourceVersion", 24)));
        when(assetMapper.insert(any(ConfigMigrationAsset.class))).thenAnswer(call -> {
            call.getArgument(0, ConfigMigrationAsset.class).setId("asset-v24");
            return 1;
        });
        when(dependencyMapper.insert(any(ConfigMigrationAssetDependency.class))).thenAnswer(call -> {
            var row = call.getArgument(0, ConfigMigrationAssetDependency.class);
            var key = List.of(row.getAssetId(), row.getDependencyType(), row.getDependencyKey());
            if (rows.putIfAbsent(key, row) != null) throw new DuplicateKeyException("uk_config_asset_dependency: " + key);
            return 1;
        });
        ReflectionTestUtils.setField(assets, "objectMapper", json);
        ReflectionTestUtils.setField(assets, "assetMapper", assetMapper);
        ReflectionTestUtils.setField(assets, "referenceService", references);
        ReflectionTestUtils.setField(assets, "subFormReferences", subForms);
        ReflectionTestUtils.setField(assets, "assetDependencyService", new ConfigMigrationAssetDependencyService(
                dependencyMapper, new JsonDocumentCodec(json), jdbc));

        String admin = ConfigMigrationReferenceSupport.reference("USER", "admin");
        String assignment = json.writeValueAsString(Map.of("multiInstanceUserIds", List.of(admin))).replace("\"", "&quot;");
        String bpmn = ConfigMigrationAssignmentSupportTest.bpmn("<bpmn:userTask id=\"review\"><bpmn:extensionElements>"
                + "<flowable:properties><flowable:property name=\"assigneeConfig\" value=\"" + assignment
                + "\"/></flowable:properties></bpmn:extensionElements></bpmn:userTask>");
        Map<String, Object> snapshot = Map.of("assetType", "PROCESS", "businessKey", "all_flow", "bpmnXml", bpmn,
                "dependencies", List.of(Map.of("type", "USER", "key", "admin", "required", true,
                        "source", "原始办理人", "references", List.of(Map.of("nodeId", "start", "location", "assignee")))),
                "nodes", List.of(Map.of("nodeId", "notify", "configJson", json.writeValueAsString(Map.of(
                        "ccConfig", Map.of("recipientRules", List.of(Map.of("type", "USER", "values", List.of(admin)))))))));

        ConfigMigrationAsset asset = ReflectionTestUtils.invokeMethod(assets, "saveAsset", "PROCESS", "all_flow",
                "全流程验收", "history-v24", 24, "测试重复引用", "REL-TEST", true, "COMPLETE", snapshot,
                List.of(), LocalDateTime.now(), "admin");

        assertNotNull(asset);
        assertEquals(1, asset.getDependencyCount());
        assertEquals(1, rows.size());
        var savedSnapshot = json.readValue(asset.getSnapshotJson(), Map.class);
        var dependencies = ConfigMigrationReferenceService.maps(savedSnapshot.get("dependencies"));
        assertEquals(dependencies, json.readValue(asset.getDependenciesJson(), List.class));
        var stored = json.readValue(new ArrayList<>(rows.values()).get(0).getDependencyDocument(), Map.class);
        assertEquals(dependencies.get(0), stored);
        Set<String> nodes = ConfigMigrationReferenceService.maps(stored.get("references")).stream()
                .map(value -> String.valueOf(value.get("nodeId"))).collect(Collectors.toSet());
        assertEquals(Set.of("start", "review", "notify"), nodes);
        // 导出副本及最终写库会重复经过规范化，合并结果必须稳定，不能改变快照哈希输入。
        assertEquals(dependencies, ConfigMigrationAssignmentSupport.mergeDependencies(dependencies));
        assertEquals(savedSnapshot, subForms.exportReferences(references.exportReferences(savedSnapshot)));
    }
}
