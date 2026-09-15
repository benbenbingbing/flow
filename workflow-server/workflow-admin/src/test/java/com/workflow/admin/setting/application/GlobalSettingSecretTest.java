package com.workflow.admin.setting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.setting.api.*;
import com.workflow.admin.setting.infrastructure.persistence.mapper.GlobalSettingMapper;
import com.workflow.admin.setting.infrastructure.persistence.record.GlobalSettingRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static com.workflow.admin.setting.application.GlobalSettingRegistry.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 验证密钥的系统级存储、脱敏、即时读取及修改边界，避免将真实值返回普通页面。 */
class GlobalSettingSecretTest {
    private final GlobalSettingMapper mapper = mock(GlobalSettingMapper.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final GlobalSettingRegistry registry = new GlobalSettingRegistry();
    private final GlobalSettingService service = new GlobalSettingService(mapper, users, registry);
    private final ObjectMapper json = new ObjectMapper();
    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @BeforeEach
    void login() {
        UserContext.setCurrentUser("admin", "admin");
        SysUser user = new SysUser();
        user.setStatus("0"); user.setDeleted(0);
        when(users.selectById("admin")).thenReturn(user);
    }

    @AfterEach
    void logout() { UserContext.clear(); }

    @Test
    void listOnlyReturnsConfiguredStatusAndVersion() throws Exception {
        when(mapper.findAll(SYSTEM, "0")).thenReturn(List.of(row(KEY)));
        var view = service.listSystem().stream().filter(value -> value.sensitive()).findFirst().orElseThrow();
        assertTrue(view.configured());
        assertNull(view.value());
        assertNull(view.defaultValue());
        assertFalse(view.userOverridable());
        assertEquals("secret", view.override().id());
        assertFalse(json.writeValueAsString(view).contains(KEY));
        assertThrows(GlobalSettingException.class, () -> service.readMine(MIGRATION_SIGNING_KEY));
        assertThrows(GlobalSettingException.class, () -> service.saveMine(MIGRATION_SIGNING_KEY,
                new GlobalSettingRequests.Save(json.writeValueAsString(KEY), null, null)));
    }

    @Test
    void saveUpdatesPersistedSecretButNeverEchoesIt() throws Exception {
        var current = row(KEY);
        String replacement = KEY + "-replacement";
        when(mapper.find(SYSTEM, "0", MIGRATION_SIGNING_KEY)).thenReturn(current);
        when(mapper.updateValue(any())).thenAnswer(call -> {
            current.setVersion(current.getVersion() + 1); return 1;
        });
        var view = service.saveSystem(MIGRATION_SIGNING_KEY,
                new GlobalSettingRequests.Save(json.writeValueAsString(replacement), "secret", 3L));
        assertNull(view.value());
        assertTrue(view.configured());
        assertEquals(json.writeValueAsString(replacement), current.getSettingValue());
        assertEquals(4, view.override().version());
        assertEquals(replacement, service.readSystemValue(MIGRATION_SIGNING_KEY).textValue());
    }

    @Test
    void internalReadSeesUpdatesWithoutLoginOrCachedValue() {
        UserContext.clear();
        when(mapper.find(SYSTEM, "0", MIGRATION_SIGNING_KEY)).thenReturn(row(KEY), row(KEY + "new"));
        assertEquals(KEY, service.readSystemValue(MIGRATION_SIGNING_KEY).textValue());
        assertEquals(KEY + "new", service.readSystemValue(MIGRATION_SIGNING_KEY).textValue());
        verify(mapper, times(2)).find(SYSTEM, "0", MIGRATION_SIGNING_KEY);
    }

    @Test
    void missingOrCorruptSecretHasNoEnvironmentOrPublicDefault() {
        when(mapper.find(SYSTEM, "0", MIGRATION_SIGNING_KEY)).thenReturn(null, row("short"));
        assertThrows(GlobalSettingException.class, () -> service.readSystemValue(MIGRATION_SIGNING_KEY));
        assertThrows(GlobalSettingException.class, () -> service.readSystemValue(MIGRATION_SIGNING_KEY));
        var view = service.listSystem().stream().filter(value -> value.sensitive()).findFirst().orElseThrow();
        assertFalse(view.configured());
        assertNull(view.value());
    }

    @Test
    void secretCannotBeDeletedAndStalePageCannotOverwriteIt() throws Exception {
        assertThrows(GlobalSettingException.class, () -> service.resetSystem(MIGRATION_SIGNING_KEY,
                new GlobalSettingRequests.Reset("secret", 3L)));
        when(mapper.find(SYSTEM, "0", MIGRATION_SIGNING_KEY)).thenReturn(row(KEY));
        assertEquals(409, assertThrows(GlobalSettingException.class, () -> service.saveSystem(MIGRATION_SIGNING_KEY,
                new GlobalSettingRequests.Save(json.writeValueAsString(KEY), "secret", 2L))).status());
        verify(mapper, never()).updateValue(any());
        verify(mapper, never()).deleteVersion(any(), any(), any(), any(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "workflow-config-migration-development-key", "replace-with-a-secret-at-least-32-bytes", " 0123456789abcdef0123456789abcdef", "0123456789abcdef0123456789abcdef "})
    void invalidSecretCannotBeSaved(String value) throws Exception {
        assertThrows(GlobalSettingException.class, () -> service.saveSystem(MIGRATION_SIGNING_KEY,
                new GlobalSettingRequests.Save(json.writeValueAsString(value), null, null)));
        verifyNoInteractions(mapper);
    }

    private GlobalSettingRecord row(String value) {
        var row = new GlobalSettingRecord();
        row.setId("secret"); row.setScopeType(SYSTEM); row.setOwnerId("0"); row.setVersion(3L);
        row.setSettingKey(MIGRATION_SIGNING_KEY); row.setSettingValueType("STRING");
        row.setSettingValue(json.getNodeFactory().textNode(value).toString());
        return row;
    }
}
