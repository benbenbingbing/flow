package com.workflow.admin.setting.application;

import com.workflow.admin.setting.api.error.GlobalSettingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.workflow.admin.setting.application.GlobalSettingRegistry.ValueType.*;
import static org.junit.jupiter.api.Assertions.*;

/** 四种值格式的服务端校验，防止仅依赖前端输入控件。 */
class GlobalSettingValueTypeTest {
    private final GlobalSettingRegistry registry = new GlobalSettingRegistry();

    @Test
    void acceptsFourTypesWithoutCoercingScalarValues() {
        assertFalse(registry.parse(BOOLEAN, "false").booleanValue());
        assertEquals(-12.5, registry.parse(NUMBER, "-12.5").doubleValue());
        assertEquals(0, registry.parse(NUMBER, "0").intValue());
        assertEquals("true", registry.parse(STRING, "\"true\"").textValue());
        assertEquals("", registry.parse(STRING, "\"\"").textValue());
        assertTrue(registry.parse(JSON, "{\"width\":240}").isObject());
        assertTrue(registry.parse(JSON, "[false,1,\"中文\"]").isArray());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "BOOLEAN|1", "BOOLEAN|\"true\"", "NUMBER|\"12\"", "NUMBER|false",
            "NUMBER|1e9999", "NUMBER|NaN", "STRING|true", "STRING|123",
            "JSON|null", "JSON|true", "JSON|12", "JSON|\"text\"", "JSON|[] {}"
    })
    void rejectsMismatchedOrInvalidFormats(GlobalSettingRegistry.ValueType type, String text) {
        assertEquals(400, assertThrows(GlobalSettingException.class, () -> registry.parse(type, text)).status());
    }
}
