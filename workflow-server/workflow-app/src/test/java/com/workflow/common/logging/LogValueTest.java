package com.workflow.common.logging;

import com.workflow.core.logging.LogValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogValueTest {

    @Test
    void rendersControlCharactersWithoutCreatingNewLogLines() {
        assertEquals("a\\r\\nb\\tc?", LogValue.safe("a\r\nb\tc\u0000"));
    }

    @Test
    void boundsLargeValues() {
        String value = "\n".repeat(600);
        String rendered = LogValue.safe(value);
        assertTrue(rendered.endsWith("..."));
        assertTrue(rendered.length() <= 512);
    }
}
