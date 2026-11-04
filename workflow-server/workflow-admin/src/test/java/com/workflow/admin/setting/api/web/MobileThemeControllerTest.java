package com.workflow.admin.setting.api.web;

import com.workflow.admin.auth.application.AuthSessionService;
import com.workflow.admin.auth.infrastructure.web.AuthInterceptor;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.infrastructure.EndpointAuthorizationInterceptor;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.setting.api.response.MobileThemeView;
import com.workflow.admin.setting.application.GlobalSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MobileThemeControllerTest {
    @Test void publicProjectionIsReadOnlyUncachedAndContainsOnlyThemeFields() throws Exception {
        var service = mock(GlobalSettingService.class);
        when(service.readMobileTheme()).thenReturn(new MobileThemeView(1, "green", "#196B62", "#F4F7F6", "#FFFFFF"));
        var controller = new MobileThemeController(service);
        // 通过实际 MVC 路由和两层拦截器验证公开投影，方法与路径边界由映射负责，匿名访问由注解负责。
        var auth = new AuthInterceptor((AuthSessionService) null);
        var authorization = new EndpointAuthorizationInterceptor(mock(SysMenuMapper.class), mock(CurrentUserRoleService.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(auth, authorization).build();
        mvc.perform(get("/api/system/mobile-theme")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data.primaryColor").value("#196B62"));
        mvc.perform(post("/api/system/mobile-theme")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/system/mobile-theme/config.migration.signing_key")).andExpect(status().isNotFound());
    }
}
