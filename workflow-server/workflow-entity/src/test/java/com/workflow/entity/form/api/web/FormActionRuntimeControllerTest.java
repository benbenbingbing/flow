package com.workflow.entity.form.api.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.workflow.entity.form.api.request.FormActionResolveRequest;
import com.workflow.entity.form.api.response.FormActionRuntimeDTO;
import com.workflow.entity.form.application.EntityFormActionService;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormActionRuntimeControllerTest {

    @Test
    void embedButtonsRemainTheExactNativeFlowResolution() {
        EntityFormActionService service = mock(EntityFormActionService.class);
        FormActionResolveRequest body = new FormActionResolveRequest();
        FormActionRuntimeDTO save = action("save", "builtin");
        FormActionRuntimeDTO custom = action("approve-extra", "custom");
        FormActionRuntimeDTO close = action("close", "builtin");
        when(service.resolve(body)).thenReturn(List.of(save, custom, close));
        FormActionRuntimeController controller =
                new FormActionRuntimeController(service);

        controller.resolve(body);

        assertTrue(save.isVisible());
        assertTrue(save.isEnabled());
        assertTrue(custom.isVisible());
        assertTrue(custom.isEnabled());
        assertTrue(close.isVisible());
        assertTrue(close.isEnabled());
    }

    @Test
    void actionExecuteKeepsExactFlowCustomButtonResolution() {
        EntityFormActionService service = mock(EntityFormActionService.class);
        FormActionResolveRequest body = new FormActionResolveRequest();
        FormActionRuntimeDTO allowed = action("future-widget-action", "custom");
        allowed.setReason("Flow 行级规则允许");
        FormActionRuntimeDTO deniedByFlow = action("restricted-action", "custom");
        deniedByFlow.setVisible(false);
        deniedByFlow.setEnabled(false);
        deniedByFlow.setReason("Flow 数据权限拒绝");
        when(service.resolve(body)).thenReturn(List.of(allowed, deniedByFlow));
        new FormActionRuntimeController(service).resolve(body);

        assertTrue(allowed.isVisible());
        assertTrue(allowed.isEnabled());
        assertTrue("Flow 行级规则允许".equals(allowed.getReason()));
        assertFalse(deniedByFlow.isVisible());
        assertFalse(deniedByFlow.isEnabled());
        assertTrue("Flow 数据权限拒绝".equals(
                deniedByFlow.getReason()));
    }

    private static FormActionRuntimeDTO action(String key, String type) {
        FormActionRuntimeDTO value = new FormActionRuntimeDTO();
        value.setKey(key);
        value.setType(type);
        value.setVisible(true);
        value.setEnabled(true);
        return value;
    }
}
