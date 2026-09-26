package com.workflow.admin.setting.application;

import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.setting.api.error.GlobalSettingException;
import com.workflow.admin.setting.api.request.GlobalSettingRequests;
import com.workflow.admin.setting.infrastructure.persistence.mapper.GlobalSettingMapper;
import com.workflow.admin.setting.infrastructure.persistence.record.GlobalSettingRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static com.workflow.admin.setting.application.GlobalSettingRegistry.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证系统级 JSON 协议、刷新读新值、异常回退和个人覆盖隔离。 */
class MobileThemeSettingTest {
    private final GlobalSettingMapper mapper = mock(GlobalSettingMapper.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final GlobalSettingRegistry registry = new GlobalSettingRegistry();
    private final GlobalSettingService service = new GlobalSettingService(mapper, users, registry);
    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void defaultAndLiveReadsDoNotNeedLoginOrCacheInvalidation() {
        assertEquals(Set.of(SYSTEM), registry.require(MOBILE_THEME).scopes());
        assertEquals(ValueType.JSON, registry.require(MOBILE_THEME).valueType());
        assertEquals("#196B62", service.readMobileTheme().primaryColor());
        var record = new GlobalSettingRecord();
        record.setSettingValueType("JSON");
        record.setSettingValue(MobileThemeConfiguration.defaultValue().put("primaryColor", "#1677ff").toString());
        when(mapper.find(SYSTEM, "0", MOBILE_THEME)).thenReturn(record);
        assertEquals("#1677FF", service.readMobileTheme().primaryColor());
        assertEquals("custom", service.readMobileTheme().preset());
        record.setSettingValue(MobileThemeConfiguration.defaultValue().put("primaryColor", "#722ED1").toString());
        assertEquals("#722ED1", service.readMobileTheme().primaryColor());
        record.setSettingValue("{}");
        assertEquals("#196B62", service.readMobileTheme().primaryColor());
        verifyNoInteractions(users);
        verify(mapper, never()).find(eq(USER), anyString(), anyString());
    }

    @ParameterizedTest @ValueSource(strings = {"#fff", "var(--secret)", "red", "url(https://example.test)"})
    void refusesNonLiteralColorsBeforePersistence(String color) {
        var value = MobileThemeConfiguration.defaultValue().put("primaryColor", color);
        assertThrows(GlobalSettingException.class, () -> registry.parse(registry.require(MOBILE_THEME), value.toString()));
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({
            "green,#196B62,#F4F7F6", "blue,#2563EB,#F7F8FA", "purple,#722ED1,#F8F7FB",
            "orange,#B45309,#FAF8F5", "rose,#B82F61,#FCF7F9", "cyan,#087E8B,#F3F9FA", "slate,#475569,#F6F7F9"})
    void acceptsAllSevenCompletePresets(String preset, String primary, String background) {
        var value = MobileThemeConfiguration.defaultValue().put("preset", preset).put("primaryColor", primary).put("backgroundColor", background);
        assertEquals(value, registry.parse(registry.require(MOBILE_THEME), value.toString()));
    }

    @Test void rejectsUnknownPropertiesVersionsAndDarkSurfaces() {
        for (var value : new com.fasterxml.jackson.databind.node.ObjectNode[]{
                MobileThemeConfiguration.defaultValue().put("css", "body{}"),
                MobileThemeConfiguration.defaultValue().put("version", 2),
                MobileThemeConfiguration.defaultValue().put("backgroundColor", "#111111"),
                MobileThemeConfiguration.defaultValue().put("surfaceColor", "#111111")}) {
            assertThrows(GlobalSettingException.class, () -> registry.parse(registry.require(MOBILE_THEME), value.toString()));
        }
    }

    @Test void storesJsonTextOnlyAtSystemScopeAndRejectsPersonalOverride() {
        UserContext.setCurrentUser("u1", "admin");
        var user = new SysUser(); user.setId("u1"); user.setStatus("0"); user.setDeleted(0);
        when(users.selectById("u1")).thenReturn(user);
        AtomicReference<GlobalSettingRecord> stored = new AtomicReference<>();
        when(mapper.find(SYSTEM, "0", MOBILE_THEME)).thenAnswer(call -> stored.get());
        when(mapper.insert(any(GlobalSettingRecord.class))).thenAnswer(call -> { var row = (GlobalSettingRecord) call.getArgument(0); row.setId("theme"); stored.set(row); return 1; });
        var request = new GlobalSettingRequests.Save(MobileThemeConfiguration.defaultValue().put("primaryColor", "#722ed1").toString(), null, null);
        assertEquals(403, assertThrows(GlobalSettingException.class, () -> service.saveMine(MOBILE_THEME, request)).status());
        var saved = service.saveSystem(MOBILE_THEME, request);
        assertFalse(saved.userOverridable());
        assertEquals("JSON", stored.get().getSettingValueType());
        assertEquals("0", stored.get().getOwnerId());
        assertEquals("SYSTEM", stored.get().getScopeType());
        assertEquals("#722ED1", saved.value().path("primaryColor").textValue());
        assertEquals(409, assertThrows(GlobalSettingException.class, () -> service.saveSystem(MOBILE_THEME, request)).status());
    }
}
