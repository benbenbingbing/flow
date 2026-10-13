package com.workflow.entity.data.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真数据库比较覆盖排序规则、精确数值、日期、软删除及规范化占位，不模拟匹配结果。 */
class EntityUniqueBatchPrecheckServiceTest {
    private JdbcTemplate jdbc;
    private com.workflow.entity.data.infrastructure.persistence.mapper.EntityUniquePrecheckMapper precheckMapper;
    private EntityUniqueBatchPrecheckService service;
    private final DynamicTableService tables = mock(DynamicTableService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final EntityRuntimeRecordMapper records = new EntityRuntimeRecordMapper(json);

    @BeforeEach void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:unique_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = spy(new JdbcTemplate(source));
        jdbc.execute("CREATE TABLE records(id VARCHAR(64) PRIMARY KEY, deleted INT DEFAULT 0, text_value VARCHAR_IGNORECASE(100), exact_text VARCHAR(100), amount DECIMAL(30,8), business_date DATE)");
        jdbc.execute("CREATE TABLE entity_unique_value(entity_code VARCHAR(100), field_code VARCHAR(100), value_hash VARCHAR(64), normalized_value VARCHAR(1000), record_id VARCHAR(64), PRIMARY KEY(entity_code,field_code,value_hash))");
        when(tables.getTableName("sample")).thenReturn("records");
        precheckMapper = mock(com.workflow.entity.data.infrastructure.persistence.mapper.EntityUniquePrecheckMapper.class,
                org.mockito.AdditionalAnswers.delegatesTo(com.workflow.testsupport.EntityMapperTestFactory.mapper(jdbc.getDataSource(), com.workflow.entity.data.infrastructure.persistence.mapper.EntityUniquePrecheckMapper.class)));
        service = new EntityUniqueBatchPrecheckService(precheckMapper, tables, DatabaseQueryDialects.forDatabaseId("MYSQL"), records);
        clearInvocations(jdbc);
    }

    @Test void usesDatabaseColumnCaseRulesWithoutJavaReverseLookup() {
        jdbc.update("INSERT INTO records(id,text_value,exact_text) VALUES ('secret-record','Alpha','Alpha')");
        var errors = service.validate("sample", snapshot(field("text_value", EntityField.FieldType.STRING), field("exact_text", EntityField.FieldType.STRING)),
                List.of(Map.of("text_value", "aLPHA", "exact_text", "aLPHA")));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).fieldCode()).isEqualTo("text_value");
        assertThat(errors.get(0).rowIndex()).isZero();
        assertThat(errors.toString()).doesNotContain("secret-record", "Alpha", "aLPHA");
    }

    @Test void normalizedReservationsCatchTrimmedValuesEvenWhenPhysicalColumnDoesNotMatch() {
        jdbc.update("INSERT INTO records(id,exact_text) VALUES ('secret-record','  Alpha  ')");
        new EntityUniqueValueService(com.workflow.testsupport.EntityMapperTestFactory.mapper(jdbc.getDataSource(), com.workflow.entity.data.infrastructure.persistence.mapper.EntityUniqueValueMapper.class)).replace("sample", "secret-record", Map.of("exact_text", "  Alpha  "));
        var errors = service.validate("sample", snapshot(field("exact_text", EntityField.FieldType.STRING)), List.of(Map.of("exact_text", "alpha")));
        assertThat(errors).extracting(EntityDataMutationValidator.BatchFieldError::code).containsExactly("UNIQUE_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entity_unique_value", Integer.class)).isEqualTo(1);
    }

    @Test void comparesLargeDecimalsAndDatesWithTypedParametersAndIgnoresSoftDeletedRows() {
        jdbc.update("INSERT INTO records(id,amount,business_date) VALUES ('decimal',99999999999999999999.12345678,DATE '2026-10-01')");
        jdbc.update("INSERT INTO records(id,deleted,amount) VALUES ('deleted',1,12.50)");
        var errors = service.validate("sample", snapshot(field("amount", EntityField.FieldType.DECIMAL), field("business_date", EntityField.FieldType.DATE)),
                List.of(Map.of("amount", new BigDecimal("99999999999999999999.123456780"), "business_date", "2026-10-01"),
                        Map.of("amount", new BigDecimal("12.50"), "business_date", LocalDate.of(2026, 10, 2)),
                        Map.of("amount", new BigDecimal("99999999999999999999.12345679"), "business_date", "2026-10-03")));
        assertThat(errors).hasSize(2).allMatch(error -> error.rowIndex() == 0 && error.code().equals("UNIQUE_CONFLICT"));
    }

    @Test void finalDefaultsAndFormattingProduceFileDuplicatesAcrossAllAffectedRows() {
        var fields = snapshot(field("text_value", EntityField.FieldType.STRING), field("amount", EntityField.FieldType.DECIMAL));
        // 两行空输入经可信默认值/表单处理后均有姓名和金额；预检必须比较这些最终数据。
        var rows = List.<Map<String, Object>>of(Map.of("text_value", " 默认值 ", "amount", new BigDecimal("1.000")),
                Map.of("text_value", "默认值", "amount", "1.0"), Map.of("text_value", " ", "amount", "2.0"));
        var errors = service.validate("sample", fields, rows);
        assertThat(errors).hasSize(4).allMatch(error -> error.code().equals("DUPLICATE_IN_FILE"));
        assertThat(errors).extracting(EntityDataMutationValidator.BatchFieldError::rowIndex).containsExactlyInAnyOrder(0, 1, 0, 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM records", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entity_unique_value", Integer.class)).isZero();
    }

    @Test void databaseRoundTripsAreBatchedInsteadOfOneQueryPerCell() {
        var rows = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < 251; index++) rows.add(Map.of("text_value", "row-" + index));
        var errors = service.validate("sample", snapshot(field("text_value", EntityField.FieldType.STRING)), rows);
        assertThat(errors).isEmpty();
        verify(precheckMapper, times(3)).selectConflictIndexes(any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void conversionErrorsRetainInputCoordinatesAndDoNotQueryInvalidValues() {
        var rows = Arrays.<Map<String, Object>>asList(null, Map.of("amount", "not-a-number"), Map.of("amount", ""));
        var errors = service.validate("sample", snapshot(field("amount", EntityField.FieldType.DECIMAL)), rows);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).rowIndex()).isEqualTo(1);
        assertThat(errors.get(0).code()).isEqualTo("FIELD_TYPE");
        verifyNoInteractions(jdbc);
    }

    @Test void publicBatchApiCollectsRulesAndUniqueErrorsWithoutSingleCellDatabaseChecks() {
        var dynamic = mock(EntityDataDynamicMapper.class);
        var snapshots = mock(EntityPublishedSnapshotService.class);
        var rules = mock(EntityFieldValidationRuleService.class);
        var required = field("text_value", EntityField.FieldType.STRING); required.setIsRequired(true);
        when(snapshots.getLatestByEntityCode("sample")).thenReturn(snapshot(required, field("amount", EntityField.FieldType.DECIMAL)));
        var validator = new EntityDataMutationValidator(dynamic, tables, snapshots, records, rules, json);
        ReflectionTestUtils.setField(validator, "uniqueBatchPrecheckService", service);
        var errors = validator.validatePublishedFieldsBatch("sample", List.of(Map.of("amount", new BigDecimal("1.0")), Map.of("amount", new BigDecimal("1.00"))));
        assertThat(errors).hasSize(4);
        assertThat(errors).filteredOn(e -> "FIELD_VALIDATION".equals(e.code())).hasSize(2);
        assertThat(errors).filteredOn(e -> "DUPLICATE_IN_FILE".equals(e.code())).hasSize(2);
        verifyNoInteractions(dynamic);
        verify(snapshots, times(1)).getLatestByEntityCode("sample");
    }

    @Test void moreThanTenThousandErrorsHasAnExplicitFileLevelSentinel() {
        EntityField[] fields = new EntityField[11];
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < fields.length; index++) {
            fields[index] = field("value" + index, EntityField.FieldType.STRING);
            row.put("value" + index, "same-final-default");
        }
        var errors = service.validate("sample", snapshot(fields), Collections.nCopies(1000, row));
        assertThat(errors).hasSize(10_001);
        assertThat(errors.get(10_000).code()).isEqualTo("ERROR_LIMIT");
        assertThat(errors.get(10_000).rowIndex()).isEqualTo(-1);
        verifyNoInteractions(jdbc);
    }

    private EntityField field(String code, EntityField.FieldType type) {
        var value = new EntityField(); value.setFieldCode(code); value.setFieldName(code); value.setFieldType(type); value.setIsUnique(true); return value;
    }

    private EntityPublishedSnapshot snapshot(EntityField... fields) {
        var snapshot = new EntityPublishedSnapshot(); snapshot.setFields(Arrays.asList(fields)); return snapshot;
    }
}
