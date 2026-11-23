package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.workflow.admin.setting.application.GlobalSettingService;
import static com.workflow.admin.setting.application.GlobalSettingRegistry.MIGRATION_SIGNING_KEY;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.migration.api.request.ConfigExportRequest;
import com.workflow.migration.infrastructure.persistence.mapper.*;
import com.workflow.migration.infrastructure.persistence.record.*;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.workflow.migration.application.ConfigMigrationAssignmentSupportTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 从真实发布包编码到目标分析/映射的回归，确保不把源环境账号目录作为导出前提。 */
@ExtendWith(MockitoExtension.class)
class ConfigMigrationAssignmentExportImportTest {
    @org.junit.jupiter.api.BeforeEach
    void configureReferenceServices() { MigrationReferenceTestFixture.attachTo(service); }

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    @Mock ConfigMigrationAssetService assetService;
    @Mock ConfigMigrationAssetMapper assetMapper;
    @Mock ConfigExportPackageMapper exportPackageMapper;
    @Mock ConfigExportPackageItemMapper exportItemMapper;
    @Mock ConfigImportPackageMapper importPackageMapper;
    @Mock ConfigImportItemMapper importItemMapper;
    @Mock ConfigAssetBaselineMapper baselineMapper;
    @Mock ConfigEnvironmentMappingMapper environmentMappingMapper;
    @Mock EntityDefinitionMapper entityMapper;
    @Mock ProcessDefinitionConfigMapper processMapper;
    @Mock SysUserMapper userMapper;
    @Mock SysGroupMapper groupMapper;
    @Mock SysRoleMapper roleMapper;
    @Mock SysOrganizationMapper organizationMapper;
    @Mock ConfigMigrationAssignmentTargetValidator assignmentTargetValidator;
    @Spy ConfigMigrationPackageCodec packageCodec = new ConfigMigrationPackageCodec(json, org.mockito.Mockito.mock(GlobalSettingService.class));
    @Spy ConfigMigrationPackageDocumentSupport documents = new ConfigMigrationPackageDocumentSupport(json);
    @InjectMocks ConfigMigrationPackageService service;

    @BeforeEach
    void configureCodec() {
        org.mockito.Mockito.lenient().when(((GlobalSettingService) ReflectionTestUtils.getField(packageCodec, "globalSettings"))
                .readSystemValue(MIGRATION_SIGNING_KEY)).thenReturn(TextNode.valueOf("assignment-export-import-test-key-2026"));
        ReflectionTestUtils.setField(packageCodec, "environmentName", "source");
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "GROUP", "ROLE", "DEPT", "PERSON_RESOLVER", "POSITION", "ORG_BUSINESS_LEVEL"})
    void exportsExternalReferencesWithoutConsultingSourceDirectory(String type) throws Exception {
        ConfigMigrationAsset asset = asset(List.of(dependency(type, "source-code")));
        when(assetService.getRequired(asset.getId())).thenReturn(asset);
        ConfigExportRequest request = new ConfigExportRequest();
        request.setAssetIds(List.of(asset.getId()));
        request.setMigrationTag("REL-PEOPLE");
        // 即使调用方保留旧的“只校验”选项，人员引用仍按目标环境校验规则导出。
        request.setValidateOnlyDependencies(Set.of(type + ":source-code"));
        service.exportPackage(request);
        ArgumentCaptor<ConfigExportPackage> captured = ArgumentCaptor.forClass(ConfigExportPackage.class);
        verify(exportPackageMapper).insert(captured.capture());
        var decoded = packageCodec.decode(captured.getValue().getPackageData());
        assertEquals(1, decoded.assets().size());
        var reference = decoded.assets().get(0).dependencies().get(0);
        assertEquals(type, reference.get("type"));
        assertEquals("source-code", reference.get("key"));
        assertEquals(true, reference.get("required"));
        verifyNoInteractions(userMapper, groupMapper, roleMapper, organizationMapper, assignmentTargetValidator);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "GROUP", "ROLE", "DEPT"})
    void targetMissingRemainsBlockedEvenWithMappingRecord(String type) throws Exception {
        ConfigImportItem item = prepareImport(List.of(dependency(type, "source-code")));
        ConfigEnvironmentMapping mapping = new ConfigEnvironmentMapping();
        mapping.setTargetKey("nonexistent-target");
        when(environmentMappingMapper.selectOne(any())).thenReturn(mapping);
        Map<String, Object> report = service.analyze("import-people");
        assertEquals(true, report.get("blocked"));
        assertEquals("UNRESOLVED", item.getMappingStatus());
        verify(environmentMappingMapper, never()).selectCount(any());
        assertThrows(IllegalStateException.class, () -> service.requireResolvedDependencies(List.of(item)));
    }

    @Test
    void targetLoginMappingIsValidatedAndBpmnUsesLoginInsteadOfLocalUserId() throws Exception {
        ConfigImportItem item = prepareImport(List.of(dependency("USER", "alice")));
        ConfigEnvironmentMapping mapping = new ConfigEnvironmentMapping();
        mapping.setTargetKey("alice.prod");
        when(environmentMappingMapper.selectOne(any())).thenReturn(mapping);
        SysUser target = new SysUser();
        target.setId("target-db-id-77");
        target.setUsername("alice.prod");
        when(userMapper.selectByUsername("alice.prod")).thenReturn(target);
        assertEquals(false, service.analyze("import-people").get("blocked"));
        assertEquals("RESOLVED", item.getMappingStatus());

        ConfigMigrationImportApplyService importer = mock(ConfigMigrationImportApplyService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(importer, "userMapper", userMapper);
        ReflectionTestUtils.setField(importer, "environmentMappingMapper", environmentMappingMapper);
        String source = bpmn("<bpmn:userTask id=\"review\" flowable:assignee=\"alice\"/>"
                + task("review2", Map.of("assigneeType", "user", "assigneeValue", "alice"), false));
        String restored = ReflectionTestUtils.invokeMethod(importer, "resolvePortableBpmn", source, Map.of());
        assertTrue(restored.contains("alice.prod"));
        assertFalse(restored.contains("target-db-id-77"));
        assertFalse(restored.contains("wf-user://"));
        verify(userMapper, never()).selectById(any());

        when(userMapper.selectByUsername("alice.prod")).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service.requireResolvedDependencies(List.of(item)));
    }

    @ParameterizedTest(name = "旧发布快照={0}，目标登录名={1}")
    @CsvSource({"true, alice", "true, alice.prod", "false, alice", "false, alice.prod"})
    void packagedConditionsRouteAndSkipWithTargetAccount(boolean legacySnapshot, String targetLogin) throws Exception {
        var asset = asset(List.of(dependency("USER", "alice")));
        var snapshot = documents.readMap(asset.getSnapshotJson());
        // 对照故障流程的两个连线及原始表达式，给每个分支加一个实际办理任务。
        String source = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  targetNamespace="http://workflow.test">
                  <process id="approval" isExecutable="true">
                    <startEvent id="start"/>
                    <userTask id="initial" flowable:assignee="alice" flowable:skipExpression="${status == '1'}"/>
                    <exclusiveGateway id="route"/>
                    <userTask id="equal" flowable:assignee="alice"/>
                    <userTask id="different" flowable:assignee="alice"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="to-initial" sourceRef="start" targetRef="initial"/>
                    <sequenceFlow id="to-route" sourceRef="initial" targetRef="route"/>
                    <sequenceFlow id="Flow_1ump0qa" sourceRef="route" targetRef="equal">
                      <conditionExpression xsi:type="tFormalExpression">${name == '1'}</conditionExpression>
                    </sequenceFlow>
                    <sequenceFlow id="Flow_0g4twn6" sourceRef="route" targetRef="different">
                      <conditionExpression xsi:type="tFormalExpression">${name != '1'}</conditionExpression>
                    </sequenceFlow>
                    <sequenceFlow id="equal-to-end" sourceRef="equal" targetRef="end"/>
                    <sequenceFlow id="different-to-end" sourceRef="different" targetRef="end"/>
                  </process>
                </definitions>
                """;
        snapshot.put("bpmnXml", source);
        var references = (ConfigMigrationReferenceService) ReflectionTestUtils.getField(service, "referenceService");
        asset.setSnapshotJson(json.writeValueAsString(legacySnapshot ? snapshot : references.captureReferences(snapshot)));
        asset.setContentHash(packageCodec.hashSnapshot(documents.readMap(asset.getSnapshotJson())));
        when(assetService.getRequired(asset.getId())).thenReturn(asset);
        lenient().when(userMapper.selectById("1")).thenReturn(user("1", "alice"));
        ConfigExportRequest request = new ConfigExportRequest();
        request.setAssetIds(List.of(asset.getId()));
        service.exportPackage(request);
        var captured = ArgumentCaptor.forClass(ConfigExportPackage.class);
        verify(exportPackageMapper).insert(captured.capture());
        var portable = packageCodec.decode(captured.getValue().getPackageData()).assets().get(0).snapshot();
        String packagedBpmn = (String) portable.get("bpmnXml");
        assertEquals(source, packagedBpmn);
        verifyNoInteractions(userMapper);

        // 目标环境的旧主键已分配给另一个账号，办理人只能由登录名或显式映射解析。
        lenient().when(userMapper.selectById("1")).thenReturn(user("1", "different.account"));
        when(userMapper.selectByUsername(targetLogin)).thenReturn(user("target-id-77", targetLogin));
        if (!"alice".equals(targetLogin)) {
            ConfigEnvironmentMapping mapping = new ConfigEnvironmentMapping();
            mapping.setTargetKey(targetLogin);
            when(environmentMappingMapper.selectOne(any())).thenReturn(mapping);
        }
        references.validateAssignments(portable, (type, key) -> "USER".equals(type) && "alice".equals(key) ? targetLogin : key);
        var importer = mock(ConfigMigrationImportApplyService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(importer, "userMapper", userMapper);
        ReflectionTestUtils.setField(importer, "environmentMappingMapper", environmentMappingMapper);
        String targetXml = ReflectionTestUtils.invokeMethod(importer, "resolvePortableBpmn", packagedBpmn, portable);
        var engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:portable_assignee_" + UUID.randomUUID())
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
                .setAsyncExecutorActivate(false)
                .buildProcessEngine();
        try {
            engine.getRepositoryService().createDeployment().addString("approval.bpmn20.xml", targetXml).deploy();
            // 两条连线各自覆盖跳过与办理首个节点。即使条件为真，未启用跳过开关时仍生成首个待办。
            verifyTaskSequence(engine, targetLogin, Map.of("name", "1", "status", "1", "_FLOWABLE_SKIP_EXPRESSION_ENABLED", true), "equal");
            verifyTaskSequence(engine, targetLogin, Map.of("name", "2", "status", "1", "_FLOWABLE_SKIP_EXPRESSION_ENABLED", true), "different");
            verifyTaskSequence(engine, targetLogin, Map.of("name", "2", "status", "0", "_FLOWABLE_SKIP_EXPRESSION_ENABLED", true), "initial", "different");
            verifyTaskSequence(engine, targetLogin, Map.of("name", "1", "status", "1"), "initial", "equal");
        } finally {
            engine.close();
        }
        verify(userMapper, never()).selectById(any());
    }

    @ParameterizedTest(name = "目标登录名缺失，存在账号映射={0}")
    @ValueSource(booleans = {false, true})
    void targetCannotRouteToAccountThatReusesTheSourceId(boolean hasMapping) throws Exception {
        ConfigImportItem item = prepareImport(List.of(dependency("USER", "alice")));
        var snapshot = documents.readMap(item.getSnapshotJson());
        snapshot.put("bpmnXml", bpmn("<bpmn:userTask id=\"review\" flowable:assignee=\"alice\"/>"));
        item.setSnapshotJson(json.writeValueAsString(snapshot));
        lenient().when(userMapper.selectById("1")).thenReturn(user("1", "different.account"));
        if (hasMapping) {
            ConfigEnvironmentMapping mapping = new ConfigEnvironmentMapping();
            mapping.setTargetKey("alice.prod");
            when(environmentMappingMapper.selectOne(any())).thenReturn(mapping);
        }

        assertEquals(true, service.analyze("import-people").get("blocked"));
        assertEquals("UNRESOLVED", item.getMappingStatus());
        assertThrows(IllegalStateException.class, () -> service.requireResolvedDependencies(List.of(item)));
        verify(userMapper, atLeastOnce()).selectByUsername(hasMapping ? "alice.prod" : "alice");
        verify(userMapper, never()).selectById(any());
    }

    private void verifyTaskSequence(ProcessEngine engine, String login, Map<String, Object> variables, String... nodes) {
        var instance = engine.getRuntimeService().startProcessInstanceByKey("approval", variables);
        for (String node : nodes) {
            var task = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
            assertNotNull(task, node);
            assertEquals(node, task.getTaskDefinitionKey());
            assertEquals(login, task.getAssignee());
            engine.getTaskService().complete(task.getId());
        }
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instance.getId()).singleResult());
    }

    private SysUser user(String id, String username) {
        var user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    @Test
    void scopedPackageRetainsCompositeResolverDependenciesWhenBpmnIsSelected() throws Exception {
        Map<String, Object> ref = Map.of("nodeId", "review", "section", "bpmnXml");
        Map<String, Object> dependency = Map.of("type", "ENTITY_USER_FIELD", "key", "expense/reviewer",
                "required", true, "targetOnly", true, "references", List.of(ref));
        Map<String, Object> snapshot = documents.readMap(asset(List.of(dependency)).getSnapshotJson());
        snapshot.put("bpmnXml", bpmn(task("review", resolver("entityUserReferenceField",
                Map.of("entityCode", "expense", "fieldCode", "reviewer")), false)));
        var selected = packageCodec.selectSnapshot(snapshot,
                Map.of("full", false, "sections", List.of("bpmnXml")));
        assertTrue(ConfigMigrationAssignmentSupport.maps(selected.get("dependencies")).stream()
                .anyMatch(value -> "ENTITY_USER_FIELD".equals(value.get("type"))));
        var unrelated = packageCodec.selectSnapshot(snapshot,
                Map.of("full", false, "sections", List.of("definition")));
        assertFalse(ConfigMigrationAssignmentSupport.maps(unrelated.get("dependencies")).stream()
                .anyMatch(value -> "ENTITY_USER_FIELD".equals(value.get("type"))));
    }

    private ConfigImportItem prepareImport(List<Map<String, Object>> dependencies) throws Exception {
        ConfigImportPackage batch = new ConfigImportPackage();
        batch.setId("import-people");
        ConfigImportItem item = new ConfigImportItem();
        item.setId("item-people");
        item.setImportPackageId(batch.getId());
        item.setAssetType("PROCESS");
        item.setBusinessKey("approval");
        item.setSnapshotJson(asset(dependencies).getSnapshotJson());
        item.setDependenciesJson(json.writeValueAsString(dependencies));
        when(importPackageMapper.selectForUpdate(batch.getId())).thenReturn(batch);
        when(importItemMapper.selectList(any())).thenReturn(List.of(item));
        return item;
    }

    private ConfigMigrationAsset asset(List<Map<String, Object>> dependencies) throws Exception {
        ConfigMigrationAsset asset = new ConfigMigrationAsset();
        asset.setId("asset-people");
        asset.setAssetType("PROCESS");
        asset.setBusinessKey("approval");
        asset.setSourceVersion(1);
        asset.setSnapshotSchemaVersion(1);
        asset.setSnapshotCompleteness("COMPLETE");
        Map<String, Object> snapshot = Map.of("schemaVersion", 1, "assetType", "PROCESS",
                "businessKey", "approval", "definition", Map.of("processKey", "approval"),
                "bpmnXml", bpmn("<bpmn:userTask id=\"review\" flowable:assignee=\"source-code\"/>"),
                "dependencies", dependencies);
        asset.setSnapshotJson(json.writeValueAsString(snapshot));
        asset.setContentHash(packageCodec.hashSnapshot(snapshot));
        return asset;
    }

    private Map<String, Object> dependency(String type, String key) {
        return Map.of("type", type, "key", key, "required", true, "targetOnly", true,
                "source", "流程 approval / 节点 review");
    }
}
