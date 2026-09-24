package com.workflow.entity.list.application;

import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class EntityListQueryPolicyTest {
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
