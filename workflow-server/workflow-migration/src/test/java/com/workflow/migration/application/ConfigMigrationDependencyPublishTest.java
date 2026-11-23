package com.workflow.migration.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.port.migration.model.ConfigMigrationPublishRequest;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFlowStatusMappingMapper;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetDependencyMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAssetDependency;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.ProcessNodeApprovalMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.form.infrastructure.persistence.mapper.ProcessNodeFormMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DuplicateKeyException;
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
        var rows = new LinkedHashMap<List<String>, ConfigMigrationAssetDependency>();
        when(dependencyMapper.findSourceAsset("asset-v24")).thenReturn(Map.of(
                "assetType", "PROCESS", "businessKey", "all_flow", "sourceVersion", 24));
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
                dependencyMapper, new JsonDocumentCodec(json)));

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

    @ParameterizedTest(name = "实体绑定={0}，标记导出={1}")
    @CsvSource({"PUBLISHED, true", "PUBLISHED, false", "DRAFT_ONLY, true", "UNBOUND, true", "AMBIGUOUS, true"})
    void publishingUsesOnlyConfirmedEntityFieldTypesForGatewayVariables(Binding binding, boolean markForExport) throws Exception {
        var assets = mock(ConfigMigrationAssetService.class, CALLS_REAL_METHODS);
        var assetMapper = mock(ConfigMigrationAssetMapper.class);
        var entities = mock(EntityDefinitionMapper.class);
        var publications = mock(EntityPublishedSnapshotService.class);
        var users = mock(SysUserMapper.class);
        var references = new ConfigMigrationReferenceService(users, null, null, null, null, null);
        when(users.selectById("1")).thenReturn(new SysUser());
        var entity = new EntityDefinition();
        entity.setEntityCode("expense");
        var fields = new EntityPublishedSnapshot();
        var memo = new EntityField();
        memo.setFieldCode("memo");
        memo.setFieldType(EntityField.FieldType.STRING);
        var reviewer = new EntityField();
        reviewer.setFieldCode("reviewer");
        reviewer.setFieldType(EntityField.FieldType.USER);
        fields.setFields(List.of(memo, reviewer));
        when(entities.findAllByProcessDefinitionId("42")).thenReturn(switch (binding) {
            case PUBLISHED, DRAFT_ONLY -> List.of(entity);
            case UNBOUND -> List.of();
            case AMBIGUOUS -> List.of(entity, new EntityDefinition());
        });
        when(publications.findLatestByEntityCode("expense")).thenReturn(binding == Binding.PUBLISHED ? fields : null);
        ReflectionTestUtils.setField(assets, "objectMapper", json);
        ReflectionTestUtils.setField(assets, "assetMapper", assetMapper);
        ReflectionTestUtils.setField(assets, "referenceService", references);
        ReflectionTestUtils.setField(assets, "subFormReferences",
                new ConfigMigrationSubFormReferences(null, null, null, references, null, json));
        ReflectionTestUtils.setField(assets, "assetDependencyService", mock(ConfigMigrationAssetDependencyService.class));
        ReflectionTestUtils.setField(assets, "entityMapper", entities);
        ReflectionTestUtils.setField(assets, "entityPublishedSnapshots", publications);
        ReflectionTestUtils.setField(assets, "nodeFormMapper", mock(ProcessNodeFormMapper.class));
        ReflectionTestUtils.setField(assets, "nodeConfigMapper", mock(NodeConfigMapper.class));
        ReflectionTestUtils.setField(assets, "nodeApprovalMapper", mock(ProcessNodeApprovalMapper.class));
        ReflectionTestUtils.setField(assets, "flowActionMapper", mock(FlowActionMapper.class));
        ReflectionTestUtils.setField(assets, "statusMappingMapper", mock(EntityFlowStatusMappingMapper.class));
        var config = new ProcessDefinitionConfig();
        config.setId("42");
        config.setProcessKey("expense_review");
        var history = new ProcessVersionHistory();
        history.setId("history-v1");
        history.setVersion(1);
        history.setBpmnXml(ConfigMigrationAssignmentSupportTest.bpmn("""
                <bpmn:sequenceFlow id="route" sourceRef="start" targetRef="end">
                  <bpmn:conditionExpression>${memo == '1'}</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
                """));
        var request = new ConfigMigrationPublishRequest();
        request.setMarkForExport(markForExport);
        var asset = assets.recordProcess(config, history, request);
        verify(assetMapper).insert(asset);
        assertEquals(markForExport, asset.getMarkForExport());
        verifyNoInteractions(users);
        var captured = json.readValue(asset.getSnapshotJson(), new TypeReference<Map<String, Object>>() { });
        var fieldTypes = ConfigMigrationReferenceSupport.object(captured.get(ConfigMigrationValueReferences.FIELD_TYPES));
        if (binding != Binding.PUBLISHED) {
            assertFalse(fieldTypes.containsKey("memo"));
            if (binding == Binding.DRAFT_ONLY) verify(publications).findLatestByEntityCode("expense");
            else verifyNoInteractions(publications);
            // 缺少可靠字段类型时仍可发布。导出遇到目录撞号时保留人工确认，避免从草稿猜测类型。
            var error = assertThrows(IllegalArgumentException.class, () -> references.exportReferences(captured));
            assertTrue(error.getMessage().contains("固定 USER ID"));
            verify(users).selectById("1");
            return;
        }
        assertEquals("VALUE", fieldTypes.get("memo"));
        assertEquals("USER", fieldTypes.get("reviewer"));
        assertEquals(history.getBpmnXml(), references.exportReferences(captured).get("bpmnXml"));

        captured.put("bpmnXml", history.getBpmnXml().replace("memo", "reviewer"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> references.exportReferences(captured))
                .getMessage().contains("固定 USER ID"));
        verifyNoInteractions(users);
    }

    private enum Binding { PUBLISHED, DRAFT_ONLY, UNBOUND, AMBIGUOUS }
}
