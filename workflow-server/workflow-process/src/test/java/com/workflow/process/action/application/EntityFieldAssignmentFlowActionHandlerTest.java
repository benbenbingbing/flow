package com.workflow.process.action.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.port.EntityCodeCatalogPort;
import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** 配置输入与运行写入共用校验：覆盖权限边界、字面值、清空及精确数值。 */
class EntityFieldAssignmentFlowActionHandlerTest {
    final ObjectMapper json = new ObjectMapper();
    final EntityCodeCatalogPort catalog = mock(EntityCodeCatalogPort.class);
    final EntityPublishedSnapshotService snapshots = mock(EntityPublishedSnapshotService.class);
    final EntityMutationPort mutations = mock(EntityMutationPort.class);
    final SysDictItemService dictionaries = mock(SysDictItemService.class);
    final EntityFieldAssignmentFlowActionHandler handler = new EntityFieldAssignmentFlowActionHandler(catalog, snapshots,
            new EntityFieldValidationRuleService(json), dictionaries, mutations, json);
    EntityPublishedSnapshot snapshot;

    @BeforeEach void setup() {
        snapshot = new EntityPublishedSnapshot();
        snapshot.setFields(new ArrayList<>(List.of(field("flag", EntityField.FieldType.BOOLEAN),
                field("note", EntityField.FieldType.STRING), field("quantity", EntityField.FieldType.INTEGER),
                field("serial", EntityField.FieldType.LONG), field("amount", EntityField.FieldType.DECIMAL),
                field("date", EntityField.FieldType.DATE), field("time", EntityField.FieldType.DATETIME),
                field("choice", EntityField.FieldType.SELECT))));
        snapshot.getFields().get(7).setOptionsJson("[{\"value\":\"A\",\"label\":\"可选\"},{\"value\":\"B\",\"disabled\":true}]");
        when(catalog.findEntityCodeByProcessDefinitionId("process")).thenReturn("expense");
        when(snapshots.getLatestByEntityCode("expense")).thenReturn(snapshot);
    }

    @AfterEach void clearTransaction() { TransactionSynchronizationManager.setActualTransactionActive(false); }

    @Test void validatesMultipleValuesAndExplicitNullWithoutLosingPrecision() {
        var values = handler.validateConfiguration(action("""
                {"assignments":[{"fieldCode":"flag","operation":"SET","value":false},
                {"fieldCode":"note","operation":"CLEAR"},
                {"fieldCode":"serial","operation":"SET","value":"9223372036854775807"},
                {"fieldCode":"amount","operation":"SET","value":"1234567890123456.78"},
                {"fieldCode":"date","operation":"SET","value":"2028-02-29"},
                {"fieldCode":"time","operation":"SET","value":"2028-02-29 12:30:45"}]}
                """));
        assertEquals(false, values.get("flag")); assertTrue(values.containsKey("note")); assertNull(values.get("note"));
        assertEquals(Long.MAX_VALUE, values.get("serial"));
        assertEquals(new BigDecimal("1234567890123456.78"), values.get("amount"));
        verifyNoInteractions(mutations);
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"fieldCode\":\"status\",\"operation\":\"SET\",\"value\":\"APPROVED\"}",
            "{\"fieldCode\":\"missing\",\"operation\":\"CLEAR\"}",
            "{\"fieldCode\":\"flag\",\"operation\":\"SET\",\"value\":\"false\"}",
            "{\"fieldCode\":\"quantity\",\"operation\":\"SET\",\"value\":1.5}",
            "{\"fieldCode\":\"quantity\",\"operation\":\"SET\",\"value\":2147483648}",
            "{\"fieldCode\":\"serial\",\"operation\":\"SET\",\"value\":9223372036854775807}",
            "{\"fieldCode\":\"serial\",\"operation\":\"SET\",\"value\":\"9223372036854775808\"}",
            "{\"fieldCode\":\"amount\",\"operation\":\"SET\",\"value\":\"1.001\"}",
            "{\"fieldCode\":\"amount\",\"operation\":\"SET\",\"value\":\"12345678901234567.89\"}",
            "{\"fieldCode\":\"date\",\"operation\":\"SET\",\"value\":\"2027-02-29\"}",
            "{\"fieldCode\":\"time\",\"operation\":\"SET\",\"value\":\"2028-02-29T12:30:00\"}",
            "{\"fieldCode\":\"choice\",\"operation\":\"SET\",\"value\":\"B\"}",
            "{\"fieldCode\":\"choice\",\"operation\":\"SET\",\"value\":\"C\"}",
            "{\"fieldCode\":\"note\",\"operation\":\"SET\",\"value\":null}",
            "{\"fieldCode\":\"note\",\"operation\":\"CLEAR\",\"value\":\"unexpected\"}",
            "{\"fieldCode\":\"note\",\"operation\":\"EXPRESSION\",\"value\":\"${x}\"}",
            "{\"fieldCode\":\"note\",\"operation\":\"CLEAR\",\"entityCode\":\"other\"}"
    }) void rejectsInvalidAssignmentBeforeAnyWrite(String row) {
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(action("{\"assignments\":[" + row + "]}")));
        verifyNoInteractions(mutations);
    }

    @Test void protectsPublishedSystemReadonlyAndComplexFieldsEvenWhenMetadataIsMislabelled() {
        var system = field("process_status", EntityField.FieldType.STRING);
        var customSystem = field("protected", EntityField.FieldType.STRING); customSystem.setIsSystem(true);
        var readonly = field("readonly", EntityField.FieldType.STRING); readonly.setEditable(false);
        snapshot.getFields().addAll(List.of(system, customSystem, readonly, field("files", EntityField.FieldType.FILE)));
        for (String code : List.of("process_status", "protected", "readonly", "files", "draft_only")) {
            assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(action(clear(code))));
        }
        assertEquals(8, handler.fieldsForProcess("process").size());
    }

    @Test void rejectsRequiredClearDuplicatesUnknownRootAndMalformedDocuments() {
        snapshot.getFields().get(1).setIsRequired(true);
        for (String raw : List.of(clear("note"), "{\"assignments\":[{\"fieldCode\":\"note\",\"operation\":\"SET\",\"value\":\" \"}]}",
                "{\"assignments\":[{\"fieldCode\":\"flag\",\"operation\":\"CLEAR\"},{\"fieldCode\":\"flag\",\"operation\":\"CLEAR\"}]}",
                "{\"assignments\":[],\"entityCode\":\"other\"}", "{\"assignments\":[]}", "null", "{}", "{", clear("flag") + " {}")) {
            assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(action(raw)), raw);
        }
    }

    @Test void rejectsWrongScopePolicyOrUnboundEntity() {
        for (String policy : List.of("CONTINUE", "CUSTOM", "IGNORE")) {
            var action = action(clear("note")); action.setFailurePolicy(policy);
            assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(action));
        }
        var action = action(clear("note")); action.setExecutionMode("AFTER_COMMIT");
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(action));
        FlowAction invalidScope = action(clear("note")); invalidScope.setScopeType("NODE");
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(invalidScope));
        when(catalog.findEntityCodeByProcessDefinitionId("process")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> handler.fieldsForProcess("process"));
    }

    @Test void runtimeWritesSinglePatchWithCurrentTargetAndStableKeyAndLeavesVariablesAlone() throws Exception {
        var context = context("{\"assignments\":[{\"fieldCode\":\"note\",\"operation\":\"SET\",\"value\":\"${literal}\"},{\"fieldCode\":\"flag\",\"operation\":\"CLEAR\"}]}");
        assertThrows(IllegalStateException.class, () -> handler.execute(context));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        handler.execute(context); handler.execute(context);
        var captor = ArgumentCaptor.forClass(EntityMutationCommand.class);
        verify(mutations, times(2)).execute(captor.capture());
        var command = captor.getValue();
        assertEquals("expense", command.entityCode()); assertEquals("record", command.recordId());
        assertEquals(captor.getAllValues().get(0).operationId(), command.operationId());
        Map<?, ?> patch = (Map<?, ?>) command.payload().get("data");
        assertEquals("${literal}", patch.get("note")); assertTrue(patch.containsKey("flag")); assertNull(patch.get("flag"));
        assertEquals("action", command.context().sourceId()); assertEquals("operator", command.context().operatorId());
        assertNull(context.getVariablesSnapshot());
        snapshot.getFields().removeIf(field -> field.getFieldCode().equals("note"));
        assertThrows(IllegalArgumentException.class, () -> handler.execute(context));
        verify(mutations, times(2)).execute(any());
    }

    @Test void dictionaryAllowsOnlyEnabledStoredValues() {
        snapshot.getFields().get(7).setDictType("RESULT");
        when(dictionaries.listDictionaryValues("RESULT")).thenReturn(List.of(
                new SysDictItemService.DictionaryValue("YES", "同意", true),
                new SysDictItemService.DictionaryValue("NO", "拒绝", false)));
        assertEquals("YES", handler.validateConfiguration(action("{\"assignments\":[{\"fieldCode\":\"choice\",\"operation\":\"SET\",\"value\":\"YES\"}]}")).get("choice"));
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(action("{\"assignments\":[{\"fieldCode\":\"choice\",\"operation\":\"SET\",\"value\":\"NO\"}]}")));
    }

    static EntityField field(String code, EntityField.FieldType type) {
        var field = new EntityField(); field.setFieldCode(code); field.setFieldName(code); field.setFieldType(type);
        field.setIsSystem(false); field.setEditable(true); return field;
    }
    static String clear(String code) { return "{\"assignments\":[{\"fieldCode\":\"" + code + "\",\"operation\":\"CLEAR\"}]}"; }
    static FlowAction action(String raw) {
        var action = new FlowAction(); action.setProcessConfigId("process"); action.setScopeType("SEQUENCE_FLOW");
        action.setTriggerTiming("TRANSITION_TAKEN"); action.setExecutionMode("IN_TRANSACTION"); action.setFailurePolicy("ROLLBACK");
        action.setParamsJson(raw); return action;
    }
    FlowActionContext context(String raw) throws Exception {
        var context = new FlowActionContext(); context.setEntityCode("expense"); context.setEntityDataId("record");
        context.setScopeType("SEQUENCE_FLOW"); context.setTriggerTiming("TRANSITION_TAKEN");
        context.setExecutionMode("IN_TRANSACTION"); context.setFailurePolicy("ROLLBACK");
        context.setActionId("action"); context.setOperatorId("operator"); context.setIdempotencyKey("execution-key");
        context.setExtraParams(json.readValue(raw, Map.class)); return context;
    }
}
