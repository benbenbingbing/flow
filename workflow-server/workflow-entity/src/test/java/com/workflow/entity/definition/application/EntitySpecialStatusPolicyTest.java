package com.workflow.entity.definition.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class EntitySpecialStatusPolicyTest {
    @Test void specialCategoriesAreUniqueEvenWhenTheirNamesAndCodesDiffer() {
        assertThrows(BusinessConflictException.class, () -> EntitySpecialStatusPolicy.validate(
                List.of(status("WITHDRAWN", "BACK"), status("WITHDRAWN", "RETRACT")), Set.of()));
        assertDoesNotThrow(() -> EntitySpecialStatusPolicy.validate(
                List.of(status("PROCESSING", "FINANCE"), status("PROCESSING", "MANAGER")), Set.of()));
    }

    @Test void enabledOperationsRequireExactlyOneTargetButDisabledOnesMayBeAbsent() {
        assertThrows(BusinessConflictException.class, () -> EntitySpecialStatusPolicy.validate(List.of(), Set.of("TERMINATED")));
        assertDoesNotThrow(() -> EntitySpecialStatusPolicy.validate(List.of(), Set.of()));
        assertEquals("CUSTOM_CLOSED", EntitySpecialStatusPolicy.requireTarget("TERMINATED", List.of(status("TERMINATED", "CUSTOM_CLOSED"))));
        assertThrows(BusinessConflictException.class, () -> EntitySpecialStatusPolicy.requireTarget("WITHDRAWN", List.of()));
    }

    private EntityStatus status(String category, String code) {
        var status = new EntityStatus();
        status.setStatusCategory(category); status.setStatusCode(code);
        return status;
    }
}
