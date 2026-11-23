package com.workflow.admin.setting.infrastructure.adapter;

import com.fasterxml.jackson.databind.node.TextNode;
import com.workflow.admin.setting.application.GlobalSettingRegistry;
import com.workflow.admin.setting.application.GlobalSettingService;
import com.workflow.core.error.ForbiddenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalSettingReadAdapterTest {
    @Test
    void exposesOnlyRegisteredPublicValues() {
        var settings = mock(GlobalSettingService.class);
        var port = new GlobalSettingReadAdapter(new GlobalSettingRegistry(), settings);
        assertThrows(ForbiddenException.class, () -> port.getPublicSetting(GlobalSettingRegistry.MIGRATION_SIGNING_KEY));
        verifyNoInteractions(settings);

        when(settings.readSystemValue(GlobalSettingRegistry.MOBILE_THEME)).thenReturn(TextNode.valueOf("public"));
        assertEquals("\"public\"", port.getPublicSetting(GlobalSettingRegistry.MOBILE_THEME));
    }
}
