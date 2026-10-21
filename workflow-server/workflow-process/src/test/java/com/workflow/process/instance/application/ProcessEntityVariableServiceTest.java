package com.workflow.process.instance.application;

import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.port.EntityRecordQueryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProcessEntityVariableServiceTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"1", "2", ""})
    void readsPersistedSystemNameWithoutChangingInput(String name) {
        EntityRecordQueryPort records = mock(EntityRecordQueryPort.class);
        EntityRecordData record = new EntityRecordData();
        record.setName(name);
        record.setData(Map.of("name", "custom-field-must-not-shadow-system-name", "amount", 100));
        when(records.findById("expense", "record-1")).thenReturn(record);
        Map<String, Object> variables = coordinates();

        Map<String, Object> missing = new ProcessEntityVariableService(records)
                .missingRecordVariables(variables);

        assertEquals(1, missing.size());
        assertTrue(missing.containsKey("name"));
        assertEquals(name, missing.get("name"));
        assertFalse(variables.containsKey("name"), "只读预览不能修改输入快照");
        assertFalse(missing.containsKey("amount"), "只补已知遗漏字段，不覆盖业务变量");
        verify(records).findById("expense", "record-1");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"explicit-name", ""})
    void preservesExistingNameIncludingExplicitNullWithoutReadingRecord(String name) {
        EntityRecordQueryPort records = mock(EntityRecordQueryPort.class);
        Map<String, Object> variables = coordinates();
        variables.put("name", name);

        assertTrue(new ProcessEntityVariableService(records).missingRecordVariables(variables).isEmpty());
        assertTrue(variables.containsKey("name"));
        assertEquals(name, variables.get("name"));
        verifyNoInteractions(records);
    }

    @Test
    void skipsInstancesWithoutCompleteStringEntityCoordinates() {
        EntityRecordQueryPort records = mock(EntityRecordQueryPort.class);
        ProcessEntityVariableService service = new ProcessEntityVariableService(records);
        assertTrue(service.missingRecordVariables(null).isEmpty());
        for (Map<String, Object> variables : List.<Map<String, Object>>of(
                Map.of(), Map.of("entityCode", "expense"), Map.of("entityDataId", "record-1"),
                Map.of("entityCode", " ", "entityDataId", "record-1"),
                Map.of("entityCode", "expense", "entityDataId", ""),
                Map.of("entityCode", 1, "entityDataId", "record-1"),
                Map.of("entityCode", "expense", "entityDataId", 1))) {
            assertTrue(service.missingRecordVariables(variables).isEmpty());
        }
        verifyNoInteractions(records);
    }

    @Test
    void missingRecordFailsInsteadOfInventingANullName() {
        EntityRecordQueryPort records = mock(EntityRecordQueryPort.class);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ProcessEntityVariableService(records).missingRecordVariables(coordinates()));
        assertTrue(error.getMessage().contains("实体记录不存在"));
    }

    @Test
    void recordReadFailureIsNotConvertedToAConditionValue() {
        EntityRecordQueryPort records = mock(EntityRecordQueryPort.class);
        when(records.findById("expense", "record-1")).thenThrow(new IllegalStateException("read failed"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ProcessEntityVariableService(records).missingRecordVariables(coordinates()));
        assertEquals("read failed", error.getMessage());
    }

    private Map<String, Object> coordinates() {
        return new LinkedHashMap<>(Map.of("entityCode", "expense", "entityDataId", "record-1"));
    }
}
