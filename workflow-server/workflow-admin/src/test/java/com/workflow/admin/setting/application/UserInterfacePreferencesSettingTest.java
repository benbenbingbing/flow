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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static com.workflow.admin.setting.application.GlobalSettingRegistry.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 统一 JSON 偏好的字段级继承、稀疏写入、恢复和账号隔离。 */
class UserInterfacePreferencesSettingTest {
    private static final String KEY = USER_INTERFACE_PREFERENCES;
    private final GlobalSettingMapper mapper = mock(GlobalSettingMapper.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final GlobalSettingRegistry registry = new GlobalSettingRegistry();
    private final GlobalSettingService service = new GlobalSettingService(mapper, users, registry);

    @BeforeEach void setup() {
        UserContext.setCurrentUser("u1", "user");
        var user = new SysUser(); user.setId("u1"); user.setStatus("0"); user.setDeleted(0);
        when(users.selectById("u1")).thenReturn(user);
    }
    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void catalogContainsOneJsonPreferenceAndRejectsRetiredKeys() {
        var items = service.listSystem();
        assertEquals(1, items.stream().filter(item -> item.userOverridable()).count());
        var view = service.readMine(KEY);
        assertEquals("JSON", view.settingValueType());
        assertEquals(UserInterfacePreferences.defaultValue(), view.value());
        assertNull(view.override()); assertNull(view.overrideValue());
        for (String legacy : List.of("ui.entity_design.field_types_collapsed", "ui.layout.sidebar_collapsed", "ui.layout.tabs_enabled")) {
            assertThrows(GlobalSettingException.class, () -> registry.require(legacy));
        }
        verify(mapper, never()).insert(any(GlobalSettingRecord.class));
    }

    @Test void personalFalseOverridesOnlyItsFieldAndOtherFieldsFollowSystemChanges() {
        var personal = row("personal", "{\"sidebarCollapsed\":false}");
        var system = row("system", "{\"sidebarCollapsed\":true,\"tabsEnabled\":true}");
        when(mapper.find(USER, "u1", KEY)).thenReturn(personal);
        when(mapper.find(SYSTEM, "0", KEY)).thenReturn(system);
        var view = service.readMine(KEY);
        assertFalse(view.value().path("sidebarCollapsed").booleanValue());
        assertTrue(view.value().path("tabsEnabled").booleanValue());
        assertFalse(view.value().path("fieldTypesCollapsed").booleanValue());
        assertEquals(1, view.overrideValue().size());
        system.setSettingValue("{\"sidebarCollapsed\":true,\"fieldTypesCollapsed\":true}");
        view = service.readMine(KEY);
        assertFalse(view.value().path("sidebarCollapsed").booleanValue());
        assertFalse(view.value().path("tabsEnabled").booleanValue());
        assertTrue(view.value().path("fieldTypesCollapsed").booleanValue());
        assertEquals(2, view.override().version());
    }

    @Test void sparseSaveAndSingleFieldResetKeepOtherChoicesAndVersionProtection() {
        AtomicReference<GlobalSettingRecord> stored = new AtomicReference<>();
        when(mapper.find(USER, "u1", KEY)).thenAnswer(call -> stored.get());
        when(mapper.find(SYSTEM, "0", KEY)).thenReturn(row("system", "{\"fieldTypesCollapsed\":true}"));
        when(mapper.insert(any(GlobalSettingRecord.class))).thenAnswer(call -> {
            GlobalSettingRecord record = call.getArgument(0); record.setId("mine"); stored.set(record); return 1;
        });
        when(mapper.updateValue(any())).thenAnswer(call -> {
            stored.get().setVersion(stored.get().getVersion() + 1); return 1;
        });
        service.saveMine(KEY, new GlobalSettingRequests.Save("{\"fieldTypesCollapsed\":false,\"tabsEnabled\":true}", null, null));
        assertEquals("USER", stored.get().getScopeType()); assertEquals("u1", stored.get().getOwnerId());
        // 删除一个字段的覆盖，不删除同一 JSON 中其他个人选择。
        var view = service.saveMine(KEY, new GlobalSettingRequests.Save("{\"tabsEnabled\":true}", "mine", 0L));
        assertTrue(view.value().path("fieldTypesCollapsed").booleanValue());
        assertTrue(view.value().path("tabsEnabled").booleanValue());
        assertEquals("{\"tabsEnabled\":true}", stored.get().getSettingValue());
        assertEquals(409, assertThrows(GlobalSettingException.class, () -> service.saveMine(KEY,
                new GlobalSettingRequests.Save("{}", "mine", 0L))).status());
        when(mapper.deleteVersion(USER, "u1", KEY, "mine", 1L)).thenAnswer(call -> { stored.set(null); return 1; });
        view = service.resetMine(KEY, new GlobalSettingRequests.Reset("mine", 1L));
        assertNull(view.overrideValue()); assertFalse(view.value().path("tabsEnabled").booleanValue());
        assertTrue(view.value().path("fieldTypesCollapsed").booleanValue());
    }

    @Test void systemSparseValuesAndEmptyOrInvalidPersonalOverridesPreserveInheritance() {
        var system = row("system", "{\"tabsEnabled\":true}");
        when(mapper.findAll(SYSTEM, "0")).thenReturn(List.of(system));
        when(mapper.find(SYSTEM, "0", KEY)).thenReturn(system);
        assertTrue(service.readSystemValue(KEY).path("tabsEnabled").booleanValue());
        assertEquals(3, service.readSystemValue(KEY).size());
        var item = service.listSystem().stream().filter(view -> KEY.equals(view.settingKey())).findFirst().orElseThrow();
        assertEquals(1, item.overrideValue().size()); assertEquals(3, item.value().size());
        for (String value : List.of("{}", "{\"tabsEnabled\":\"false\"}")) {
            when(mapper.find(USER, "u1", KEY)).thenReturn(row("mine", value));
            var view = service.readMine(KEY);
            assertTrue(view.value().path("tabsEnabled").booleanValue());
            assertEquals(SYSTEM, view.source()); assertNotNull(view.override());
        }
    }

    @Test void changingAccountDoesNotReadAnotherUsersOverrides() {
        when(mapper.find(USER, "u1", KEY)).thenReturn(row("mine", "{\"tabsEnabled\":true}"));
        assertTrue(service.readMine(KEY).value().path("tabsEnabled").booleanValue());
        var user = new SysUser(); user.setId("u2"); user.setStatus("0"); user.setDeleted(0);
        when(users.selectById("u2")).thenReturn(user);
        UserContext.setCurrentUser("u2", "other");
        assertFalse(service.readMine(KEY).value().path("tabsEnabled").booleanValue());
    }

    @ParameterizedTest @ValueSource(strings = {"true", "[]", "null", "{\"other\":true}",
            "{\"sidebarCollapsed\":null}", "{\"tabsEnabled\":1}", "{\"fieldTypesCollapsed\":\"false\"}"})
    void rejectsUnknownFieldsAndNonBooleanValuesBeforeWrite(String input) {
        assertThrows(GlobalSettingException.class, () -> service.saveMine(KEY, new GlobalSettingRequests.Save(input, null, null)));
        verify(mapper, never()).insert(any(GlobalSettingRecord.class));
    }

    private GlobalSettingRecord row(String id, String value) {
        var row = new GlobalSettingRecord(); row.setId(id); row.setSettingKey(KEY); row.setVersion(2L);
        row.setSettingValueType("JSON"); row.setSettingValue(value); return row;
    }
}
