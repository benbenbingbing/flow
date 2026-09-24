package com.workflow.entity.data.application;

import com.workflow.core.error.BusinessForbiddenException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntityProcessRestartContextTest {
    @Test void rawRequestCannotAuthorizeAnotherRecordAndExceptionClearsScope() {
        assertThrows(BusinessForbiddenException.class,
                () -> EntityProcessRestartContext.require("expense", "1", "old"));
        assertThrows(IllegalStateException.class, () -> EntityProcessRestartContext.execute("expense", "1", "old", () -> {
            EntityProcessRestartContext.require("expense", "1", "old");
            assertThrows(BusinessForbiddenException.class,
                    () -> EntityProcessRestartContext.require("expense", "2", "old"));
            assertThrows(BusinessForbiddenException.class,
                    () -> EntityProcessRestartContext.require("expense", "1", "older"));
            throw new IllegalStateException("写入失败");
        }));
        assertThrows(BusinessForbiddenException.class,
                () -> EntityProcessRestartContext.require("expense", "1", "old"));
    }
}
