package com.workflow.admin.setting.api.web;

import com.workflow.admin.auth.application.AuthSessionService;
import com.workflow.admin.auth.infrastructure.AuthInterceptor;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.infrastructure.EndpointAuthorizationInterceptor;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.setting.api.MobileThemeView;
import com.workflow.admin.setting.application.GlobalSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.HandlerMethod;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MobileThemeControllerTest {
    @Test void publicProjectionIsReadOnlyUncachedAndContainsOnlyThemeFields() throws Exception {
        var service = mock(GlobalSettingService.class);
        when(service.readMobileTheme()).thenReturn(new MobileThemeView(1, "green", "#196B62", "#F4F7F6", "#FFFFFF"));
        var controller = new MobileThemeController(service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/system/mobile-theme")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data.primaryColor").value("#196B62"));
        mvc.perform(post("/api/system/mobile-theme")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/system/mobile-theme/config.migration.signing_key")).andExpect(status().isNotFound());
        var auth = new AuthInterceptor((AuthSessionService) null);
        var request = new MockHttpServletRequest("GET", "/api/system/mobile-theme");
        var handler = new HandlerMethod(controller, MobileThemeController.class.getMethod("read"));
        assertTrue(auth.preHandle(request, new MockHttpServletResponse(), handler));
        var authorization = new EndpointAuthorizationInterceptor(mock(SysMenuMapper.class), mock(CurrentUserRoleService.class));
        assertTrue(authorization.preHandle(request, new MockHttpServletResponse(), handler));
        for (var protectedRequest : new MockHttpServletRequest[]{new MockHttpServletRequest("POST", "/api/system/mobile-theme"), new MockHttpServletRequest("GET", "/api/system/settings"), new MockHttpServletRequest("GET", "/api/system/mobile-theme/other")}) {
            assertFalse(auth.preHandle(protectedRequest, new MockHttpServletResponse(), handler));
        }
    }
}
