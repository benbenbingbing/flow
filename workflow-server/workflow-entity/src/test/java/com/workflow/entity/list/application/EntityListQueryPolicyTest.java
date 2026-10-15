package com.workflow.entity.list.application;

import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class EntityListQueryPolicyTest {
    private final JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());

    @Test
    void explicitSortRequiresAnEnabledVisibleEntityColumn() {
        var field = sortableField();
        assertDoesNotThrow(() -> EntityListQueryPolicy.validateRequestedSort(List.of(field), "amount", "asc", codec));
        field.setShowInList(false);
        assertThrows(IllegalArgumentException.class,
                () -> EntityListQueryPolicy.validateRequestedSort(List.of(field), "amount", "desc", codec));
        field.setShowInList(true);
        field.setDataSourceType("INTERFACE");
        assertThrows(IllegalArgumentException.class,
                () -> EntityListQueryPolicy.validateRequestedSort(List.of(field), "amount", "asc", codec));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"sortable\":false}", "{\"sortable\":\"true\"}"})
    void legacyAndDisabledColumnsCannotBeSortedInteractively(String config) {
        var field = sortableField();
        field.setColumnConfig(config);
        assertThrows(IllegalArgumentException.class,
                () -> EntityListQueryPolicy.validateRequestedSort(List.of(field), "amount", "asc", codec));
        // 默认排序与交互开关独立，不能破坏旧列表已经发布的默认顺序。
        assertDoesNotThrow(() -> EntityListQueryPolicy.validateSort(List.of(field), "amount"));
    }

    @Test
    void rejectsUnknownFieldsAndSqlFragmentsBeforeDataAccess() {
        assertThrows(IllegalArgumentException.class, () -> EntityListQueryPolicy.validateRequestedSort(
                List.of(sortableField()), "amount desc; drop table x", "asc", codec));
        assertThrows(IllegalArgumentException.class, () -> EntityListQueryPolicy.validateRequestedSort(
                List.of(sortableField()), "amount", "asc nulls last", codec));
        assertThrows(IllegalArgumentException.class, () -> EntityListQueryPolicy.validateRequestedSort(
                List.of(sortableField()), "amount", null, codec));
        assertThrows(IllegalArgumentException.class, () -> EntityListQueryPolicy.validateRequestedSort(
                List.of(sortableField()), null, "asc", codec));
        assertDoesNotThrow(() -> EntityListQueryPolicy.validateRequestedSort(List.of(), null, null, codec));
    }

    @Test
    void savedSortFlagsMustBeBooleanAndCannotEnableVirtualColumns() {
        assertThrows(IllegalArgumentException.class,
                () -> EntityListQueryPolicy.validateColumnSort(sortableField(), Map.of("sortable", "true")));
        assertThrows(IllegalArgumentException.class,
                () -> EntityListQueryPolicy.validateColumnSort(virtualField(), Map.of("sortable", true)));
        assertDoesNotThrow(() -> EntityListQueryPolicy.validateColumnSort(virtualField(), Map.of("sortable", false)));
    }

    private EntityListField sortableField() {
        var field = new EntityListField();
        field.setFieldCode("amount");
        field.setShowInList(true);
        field.setColumnConfig("{\"sortable\":true}");
        return field;
    }

    @ParameterizedTest
    @ValueSource(strings = {"summary", "summary_op", "summary_start", "summary_end"})
    void rejectsEveryVirtualConditionEvenIfNotConfiguredAsQueryable(String key) {
        var field = virtualField();
        field.setIsQuery(false);
        assertThrows(IllegalArgumentException.class,
                () -> EntityListQueryPolicy.validateFilters(List.of(field), Map.of(key, "value")));
    }

    @Test
    void rejectsLegacyQueryFlagAndVirtualSort() {
        var field = virtualField();
        field.setIsQuery(true);
        assertThrows(IllegalArgumentException.class, () -> EntityListQueryPolicy.validateConfiguration(List.of(field)));
        assertThrows(IllegalArgumentException.class, () -> EntityListQueryPolicy.validateSort(List.of(field), "summary"));
    }

    @Test
    void interfaceOverridesAndVirtualIdentityCannotImpersonateEntityFields() {
        var field = virtualField();
        field.setDataSourceType("ENTITY_FIELD");
        assertFalse(EntityListQueryPolicy.isEntityField(field));
        field.setFieldId("real-field");
        field.setInterfaceExtensionId("extension");
        assertFalse(EntityListQueryPolicy.isEntityField(field));
    }

    /** 接口列即使复用了真实字段身份，也不能把计算后的值用于数据库筛选或排序。 */
    @Test
    void interfaceSourceCannotQueryOrSortPhysicalFieldIdentity() {
        var field = virtualField();
        field.setFieldId("real-field");
        field.setDataSourceType("INTERFACE");
        field.setInterfaceExtensionId("extension");
        field.setIsQuery(true);
        assertThrows(IllegalArgumentException.class,
                () -> EntityListQueryPolicy.validateConfiguration(List.of(field)));
        assertThrows(IllegalArgumentException.class,
                () -> EntityListQueryPolicy.validateFilters(List.of(field), Map.of("summary", "value")));
        assertThrows(IllegalArgumentException.class,
                () -> EntityListQueryPolicy.validateSort(List.of(field), "summary"));
    }

    @Test
    void realFieldWithSuffixInItsNameRemainsQueryable() {
        var real = new EntityListField();
        real.setFieldCode("summary_end");
        real.setDataSourceType("ENTITY_FIELD");
        real.setIsQuery(true);
        assertDoesNotThrow(() -> EntityListQueryPolicy.validateConfiguration(List.of(real)));
        assertDoesNotThrow(() -> EntityListQueryPolicy.validateFilters(List.of(real, virtualField()), Map.of("summary_end", 10)));
    }

    private EntityListField virtualField() {
        var field = new EntityListField();
        field.setFieldId("virtual_summary");
        field.setFieldCode("summary");
        field.setDataSourceType("FIELD_TEMPLATE");
        return field;
    }
}
