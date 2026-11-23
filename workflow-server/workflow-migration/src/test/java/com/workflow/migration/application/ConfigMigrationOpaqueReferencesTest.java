package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.extension.action.infrastructure.persistence.mapper.FlowActionDefinitionMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.contracts.process.action.port.FlowActionCatalogPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static com.workflow.migration.application.ConfigMigrationReferenceSupport.object;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 扩展契约、代码阻断和旧包校验必须在 ID 撞号时保持同样行为，不能猜测目标主键。 */
class ConfigMigrationOpaqueReferencesTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final ConfigMigrationReferenceService references = new ConfigMigrationReferenceService(users,
            mock(SysRoleMapper.class), mock(SysGroupMapper.class), mock(SysOrganizationMapper.class),
            mock(FlowActionDefinitionMapper.class), mock(FlowActionCatalogPort.class));

    @Test
    void declaredNestedParametersResolveOnTargetWithoutInstallingTheSourceProvider() throws Exception {
        sourceUser();
        references.configureParameterReferences(List.of((configuration, name) ->
                "notify".equals(configuration.get("actionCode"))
                        ? Map.of("/recipients", "USER", "/businessText", "VALUE") : Map.of()));
        Map<String, Object> source = Map.of("actionConfigDocument", json.writeValueAsString(Map.of(
                "actionCode", "notify", "paramsJson", "{\"recipients\":[\"source-u\"],\"businessText\":\"source-u\"}")));
        Map<String, Object> captured = references.captureReferences(source);
        // 后续导出使用发布时已冻结的编码和声明，源账号或扩展适配器撤下后仍可导出。
        references.configureParameterReferences(List.of());
        reset(users);
        Map<String, Object> portable = references.exportReferences(captured);
        assertEquals(captured, portable);
        assertTrue(portable.toString().contains("wf-ref://USER/alice"));
        assertTrue(portable.toString().contains(ConfigMigrationOpaqueReferences.PARAMETER_TYPES));
        assertFalse(source.toString().contains("wf-ref://"));
        verifyNoInteractions(users);

        when(users.selectByUsername("alice")).thenReturn(user("target-u", "alice"));
        Map<String, Object> target = references.importReferences(portable, (type, code) -> code);
        Map<String, Object> action = object(json.readValue((String) target.get("actionConfigDocument"), Map.class));
        Map<String, Object> params = object(json.readValue((String) action.get("paramsJson"), Map.class));
        assertEquals(List.of("target-u"), params.get("recipients"));
        assertEquals("source-u", params.get("businessText"));
        assertFalse(action.containsKey(ConfigMigrationOpaqueReferences.PARAMETER_TYPES));
        verify(users, never()).selectById(any());
    }

    @Test
    void undeclaredSourceDirectoryIdHasAnExactPointerAndActionableAdvice() {
        sourceUser();
        var error = assertThrows(ConfigMigrationValueReferences.ReferenceException.class,
                () -> references.exportReferences(Map.of("businessKey", "expense", "paramsJson",
                        "{\"nested\":{\"reviewers\":[\"source-u\"]}}")));
        assertTrue(error.getMessage().contains("paramsJson/nested/reviewers/0"));
        assertTrue(error.getMessage().contains("ConfigMigrationParameterReferenceProvider"));
        assertTrue(error.getMessage().contains("VALUE"));
        assertEquals("BLOCKING", error.risk().get("level"));
        assertFalse(error.risk().get("suggestion").toString().isBlank());
    }

    @Test
    void legacyStringNodeDocumentsAlsoInspectResolverParameters() throws Exception {
        sourceUser();
        String assignment = json.writeValueAsString(Map.of("resolverCode", "customResolver",
                "paramsJson", "{\"owner\":\"source-u\"}"));
        Map<String, Object> snapshot = Map.of("nodes", List.of(Map.of("nodeId", "review", "configJson",
                json.writeValueAsString(Map.of("assigneeConfig", assignment)))));
        var error = assertThrows(IllegalArgumentException.class, () -> references.exportReferences(snapshot));
        assertTrue(error.getMessage().contains("assigneeConfig.paramsJson/owner"));
        assertTrue(error.getMessage().contains("建议"));
    }

    @Test
    void sqlAndBpmnExpressionsBlockLiteralIdsWithoutChangingTheCode() {
        sourceUser();
        Map<String, Object> sql = Map.of("interfaceExtensions", List.of(Map.of("extensionKey", "owners", "sql",
                "select * from entity_expense where submitter_id = 'source-u'")));
        var error = assertThrows(IllegalArgumentException.class, () -> references.exportReferences(sql));
        assertTrue(error.getMessage().contains("interfaceExtensions[0].sql"));
        assertTrue(error.getMessage().contains("#{userId}"));
        String bpmn = ConfigMigrationAssignmentSupportTest.bpmn("<bpmn:sequenceFlow id=\"route\" sourceRef=\"start\" targetRef=\"end\">"
                + "<bpmn:conditionExpression>${owner == 'source-u'}</bpmn:conditionExpression></bpmn:sequenceFlow>");
        error = assertThrows(IllegalArgumentException.class, () -> references.exportReferences(Map.of("bpmnXml", bpmn)));
        assertTrue(error.getMessage().contains("[route].conditionExpression"));
        assertTrue(error.getMessage().contains("运行时参数"));
        assertTrue(bpmn.contains("'source-u'"));
    }

    @Test
    void ordinaryBpmnComparisonsPreserveCollidingValuesAcrossCaptureExportAndImport() {
        when(users.selectById("1")).thenReturn(user("1", "admin"));
        String bpmn = ConfigMigrationAssignmentSupportTest.bpmn("""
                <bpmn:sequenceFlow id="Flow_1ump0qa" sourceRef="start" targetRef="end">
                  <bpmn:conditionExpression>${name == '1'}</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
                <bpmn:sequenceFlow id="Flow_0g4twn6" sourceRef="start" targetRef="end">
                  <bpmn:conditionExpression>${'1' != (name) &amp;&amp; (code == 1 || status == "1")}</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
                <bpmn:userTask id="skip" flowable:skipExpression="${name == '1'}"/>
                """);
        Map<String, Object> draft = Map.of("bpmnXml", bpmn, "businessKey", "FLOWZDW_FLOW");

        Map<String, Object> captured = references.captureReferences(draft);
        Map<String, Object> exported = references.exportReferences(captured);
        Map<String, Object> imported = references.importReferences(exported, (type, code) -> code);

        assertEquals(bpmn, captured.get("bpmnXml"));
        assertEquals(bpmn, exported.get("bpmnXml"));
        assertEquals(bpmn, imported.get("bpmnXml"));
        verifyNoInteractions(users);
    }

    @ParameterizedTest(name = "业务字段条件 {0}")
    @ValueSource(strings = {"amount == 1", "amount == 1.5", "amount == -1", "(-1.5) <= (amount)",
            "amount != 9223372036854775807", "empty amount || amount == 0"})
    void customNumericConditionsKeepTheirBusinessValues(String condition) {
        for (String id : List.of("0", "1", "1.5", "-1", "-1.5", "9223372036854775807")) {
            when(users.selectById(id)).thenReturn(user(id, "account"));
        }
        String bpmn = conditionBpmn(condition);
        var captured = references.captureReferences(Map.of("bpmnXml", bpmn,
                ConfigMigrationValueReferences.FIELD_TYPES, Map.of("amount", "VALUE")));

        var exported = references.exportReferences(captured);
        assertEquals(bpmn, exported.get("bpmnXml"));
        assertEquals(bpmn, references.importReferences(exported, (type, code) -> code).get("bpmnXml"));
        verifyNoInteractions(users);
    }

    @ParameterizedTest(name = "未知字段的数值 {0} 对应目录候选 {1}")
    @CsvSource({"-1, -1", "-0, 0", "-1.5, -1.5"})
    void unknownFieldChecksTheActualSignedValueWhenItCollides(String conditionValue, String directoryId) {
        when(users.selectById(directoryId)).thenReturn(user(directoryId, "account"));
        String bpmn = conditionBpmn("legacyField == " + conditionValue);

        var error = assertThrows(ConfigMigrationValueReferences.ReferenceException.class,
                () -> references.exportReferences(Map.of("bpmnXml", bpmn)));
        assertTrue(error.getMessage().contains("固定 USER ID"));
        verify(users).selectById(directoryId);
        verifyNoMoreInteractions(users);
    }

    @ParameterizedTest(name = "明确的 {0} 字段内写死旧 ID")
    @ValueSource(strings = {"USER", "ROLE", "GROUP", "DEPT"})
    void typedIdentityBranchesBlockFixedIdsEvenAfterSourceRemoval(String type) {
        String bpmn = conditionBpmn("name == '1' or subject != -1");
        var captured = references.captureReferences(Map.of("businessKey", "approval", "bpmnXml", bpmn,
                ConfigMigrationValueReferences.FIELD_TYPES, Map.of("subject", type)));

        assertEquals(bpmn, captured.get("bpmnXml"));
        var error = assertThrows(ConfigMigrationValueReferences.ReferenceException.class,
                () -> references.exportReferences(captured));
        assertTrue(error.getMessage().contains("$[approval].bpmnXml[route].conditionExpression"));
        assertTrue(error.getMessage().contains("固定 " + type + " ID"));
        verifyNoInteractions(users);
    }

    @Test
    void runtimeIdentityParametersAndEmptyChecksRemainPortable() {
        String bpmn = conditionBpmn("!empty submitterId and submitterId != '' and deptId == currentDepartment");
        var exported = references.exportReferences(Map.of("bpmnXml", bpmn));
        assertEquals(bpmn, references.importReferences(exported, (type, code) -> code).get("bpmnXml"));
        verifyNoInteractions(users);
    }

    @ParameterizedTest(name = "业务记录固定值 {0}")
    @ValueSource(strings = {
            "entityDataId == 'source-business-record'",
            "'source-business-record' != (entityDataId)",
            "id == 42",
            "processInstanceId != 'flow-1'",
            "currentTaskId == 'task-1'",
            "customer == 'customer-record'",
            "name == '1' or entityDataId == 'source-record'"
    })
    void fixedBusinessRecordIdsAreRejectedAtExportAndWhenImportingAlreadyCheckedPackages(String condition) {
        String bpmn = conditionBpmn(condition);
        Map<String, Object> olderSnapshot = Map.of("businessKey", "expense", "bpmnXml", bpmn,
                ConfigMigrationValueReferences.PROTOCOL, 1,
                ConfigMigrationValueReferences.FIELD_TYPES, Map.of("customer", "RECORD:customer", "entityDataId", "VALUE"));
        assertEquals(bpmn, references.captureReferences(olderSnapshot).get("bpmnXml"));

        for (Runnable check : List.<Runnable>of(() -> references.exportReferences(olderSnapshot),
                () -> references.importReferences(olderSnapshot, (type, code) -> code))) {
            var failure = assertThrows(ConfigMigrationValueReferences.ReferenceException.class, check::run);
            assertEquals("$[expense].bpmnXml[route].conditionExpression", failure.risk().get("path"));
            assertTrue(failure.getMessage().contains("固定业务记录 ID"));
        }
        verifyNoInteractions(users);
    }

    @Test
    void checkingMissingRecordsAndComparingBusinessCodesStillRoundTrip() {
        String bpmn = conditionBpmn("empty entityDataId or entityDataId == null or entityDataId != '' or entityDataId == targetRecordId or code == 'invoice-1'");
        var exported = references.exportReferences(Map.of("bpmnXml", bpmn));

        assertEquals(bpmn, references.importReferences(exported, (type, code) -> code).get("bpmnXml"));
        verifyNoInteractions(users);
    }

    @ParameterizedTest(name = "历史与现行的跳过条件写法 {index}")
    @ValueSource(strings = {
            "<bpmn:userTask id=\"skip\" flowable:skipExpression=\"${%s}\"/>",
            "<bpmn:userTask id=\"skip\"><bpmn:extensionElements><flowable:skipExpression>${%s}</flowable:skipExpression></bpmn:extensionElements></bpmn:userTask>",
            "<bpmn:userTask id=\"skip\"><bpmn:extensionElements><flowable:property name=\"skipExpression\" value=\"${%s}\"/></bpmn:extensionElements></bpmn:userTask>"
    })
    void skipConditionFormatsUseTheSameFieldContract(String template) {
        when(users.selectById("1")).thenReturn(user("1", "admin"));
        String business = ConfigMigrationAssignmentSupportTest.bpmn(template.formatted("status == '1'"));
        assertEquals(business, references.exportReferences(Map.of("bpmnXml", business)).get("bpmnXml"));

        String identity = ConfigMigrationAssignmentSupportTest.bpmn(template.formatted("deptId == '1'"));
        var captured = references.captureReferences(Map.of("bpmnXml", identity));
        var error = assertThrows(ConfigMigrationValueReferences.ReferenceException.class,
                () -> references.exportReferences(captured));
        assertTrue(error.getMessage().contains("[skip]"));
        assertTrue(error.getMessage().contains("固定 DEPT ID"));
        String record = ConfigMigrationAssignmentSupportTest.bpmn(template.formatted("entityDataId == 'source-record'"));
        var recordError = assertThrows(ConfigMigrationValueReferences.ReferenceException.class,
                () -> references.importReferences(references.captureReferences(Map.of("bpmnXml", record)), (type, code) -> code));
        assertTrue(recordError.getMessage().contains("[skip]"));
        assertTrue(recordError.getMessage().contains("固定业务记录 ID"));
        verifyNoInteractions(users);
    }

    @Test
    void legacyBpmnCanBeExportedAgainButCannotBypassTheExportCheck() {
        when(users.selectById("1")).thenReturn(user("1", "admin"));
        String bpmn = conditionBpmn("name == '1'");
        var legacy = Map.<String, Object>of("bpmnXml", bpmn);
        var error = assertThrows(ConfigMigrationValueReferences.ReferenceException.class,
                () -> references.importReferences(legacy, (type, code) -> code));
        assertTrue(error.getMessage().contains("重新导出"));

        var reexported = references.exportReferences(legacy);
        assertEquals(bpmn, references.importReferences(reexported, (type, code) -> code).get("bpmnXml"));
        verifyNoInteractions(users);
    }

    @Test
    void captureDefersAmbiguousCodeAndParameterChecksUntilExport() {
        sourceUser();
        for (Map<String, Object> input : List.of(
                Map.<String, Object>of("sql", "select * from entity_expense where submitter_id = 'source-u'"),
                Map.<String, Object>of("extraParams", Map.of("owner", "source-u")),
                Map.<String, Object>of("bpmnXml", ConfigMigrationAssignmentSupportTest.bpmn("""
                        <bpmn:userTask id="skip" flowable:skipExpression="${owner == 'source-u'}"/>
                        """)))) {
            Map<String, Object> captured = references.captureReferences(input);
            input.forEach((key, value) -> assertEquals(value, captured.get(key)));
            assertThrows(IllegalArgumentException.class, () -> references.exportReferences(captured));
        }
    }

    @Test
    void identityComparisonUsesItsOwnFieldContractEvenWhenDirectoryDoesNotContainTheOldId() {
        String bpmn = ConfigMigrationAssignmentSupportTest.bpmn("""
                <bpmn:sequenceFlow id="route" sourceRef="start" targetRef="end">
                  <bpmn:conditionExpression>${name == '1' || (obsoleteUserId != submitterId &amp;&amp; 'deleted-user' == submitterId)}</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
                """);
        Map<String, Object> captured = references.captureReferences(Map.of("bpmnXml", bpmn));
        var error = assertThrows(IllegalArgumentException.class, () -> references.exportReferences(captured));
        assertTrue(error.getMessage().contains("[route].conditionExpression"));
        assertTrue(error.getMessage().contains("固定 USER ID"));
        verifyNoInteractions(users);
    }

    @Test
    void generatedSelectionCodeConditionsKeepTheirCodesAndStillCheckTheOtherConditions() {
        when(users.selectById("1")).thenReturn(user("1", "admin"));
        String bpmn = ConfigMigrationAssignmentSupportTest.bpmn("""
                <bpmn:sequenceFlow id="route" sourceRef="start" targetRef="end">
                  <bpmn:conditionExpression>${_wfSelectionCondition.matches(execution, 'selection', 'selectionEquals', '1')
                    || _wfSelectionCondition.matches(execution, 'choices', 'selectionAny', '1,2')}</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
                """);

        assertEquals(bpmn, references.exportReferences(Map.of("bpmnXml", bpmn)).get("bpmnXml"));
        verifyNoInteractions(users);

        String mixed = bpmn.replace("'1,2')}", "'1,2') || owner == '1'}");
        var error = assertThrows(IllegalArgumentException.class, () -> references.exportReferences(Map.of("bpmnXml", mixed)));
        assertTrue(error.getMessage().contains("[route].conditionExpression"));
        verify(users).selectById("1");
    }

    @Test
    void dynamicSqlAndCodeBasedQueriesRemainUnchangedAfterSourceCheck() {
        sourceUser();
        String sql = "select * from entity_expense where submitter_id = #{userId} and code = 'stable-code'";
        Map<String, Object> snapshot = Map.of("sql", sql, "paramsJson", "{\"businessCode\":\"stable-code\"}");
        Map<String, Object> portable = references.exportReferences(snapshot);
        reset(users);
        Map<String, Object> target = references.importReferences(portable, (type, code) -> code);
        assertEquals(sql, target.get("sql"));
        assertEquals(snapshot.get("paramsJson"), target.get("paramsJson"));
        verifyNoInteractions(users);
    }

    @Test
    void oldUncheckedCodeAndParametersBlockWithReexportAdvice() {
        for (Map<String, Object> legacy : List.of(Map.<String, Object>of("sql", "select 1"),
                Map.<String, Object>of("paramsJson", "{\"owner\":\"source-u\"}"))) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> references.importReferences(legacy, (type, code) -> code));
            assertTrue(error.getMessage().contains("重新导出"));
            assertTrue(error.getMessage().contains("建议"));
        }
        verifyNoInteractions(users);
    }

    @Test
    void invalidAndOverlappingProviderPointersAreRejectedWithRepairSteps() {
        for (Map<String, String> declaration : List.of(Map.of("/missing", "USER"),
                Map.of("", "USER", "/owner", "USER"))) {
            references.configureParameterReferences(List.of((configuration, name) -> declaration));
            var error = assertThrows(IllegalArgumentException.class,
                    () -> references.exportReferences(Map.of("extraParams", Map.of("owner", "source-u"))));
            assertTrue(error.getMessage().contains("建议"));
            assertTrue(error.getMessage().contains("路径"));
        }
    }

    private String conditionBpmn(String condition) {
        return ConfigMigrationAssignmentSupportTest.bpmn("""
                <bpmn:sequenceFlow id="route" sourceRef="start" targetRef="end">
                  <bpmn:conditionExpression><![CDATA[${%s}]]></bpmn:conditionExpression>
                </bpmn:sequenceFlow>
                """.formatted(condition));
    }

    private void sourceUser() { when(users.selectById("source-u")).thenReturn(user("source-u", "alice")); }
    private SysUser user(String id, String name) { var value = new SysUser(); value.setId(id); value.setUsername(name); return value; }
}
