package com.workflow.admin.setting.application;

import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.setting.api.*;
import com.workflow.admin.setting.infrastructure.persistence.mapper.GlobalSettingMapper;
import com.workflow.admin.setting.infrastructure.persistence.record.GlobalSettingRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static com.workflow.admin.setting.application.GlobalSettingRegistry.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证继承、文本协议、归属隔离以及删除重建/并发写入边界。 */
class GlobalSettingServiceTest {
    private static final String KEY = FIELD_TYPES_COLLAPSED;
    private final GlobalSettingMapper mapper = mock(GlobalSettingMapper.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final GlobalSettingRegistry registry = spy(new GlobalSettingRegistry());
    private final GlobalSettingService service = new GlobalSettingService(mapper, users, registry);

    @BeforeEach
    void setup() {
        UserContext.setCurrentUser("u1", "user");
        SysUser user = new SysUser();
        user.setId("u1"); user.setStatus("0"); user.setDeleted(0);
        when(users.selectById("u1")).thenReturn(user);
    }

    @AfterEach
    void cleanup() { UserContext.clear(); }

    @Test
    void missingRecordsReturnDefaultWithoutCreatingPersonalOverride() {
        var view = service.readMine(KEY);
        assertFalse(view.value().booleanValue());
        assertEquals("DEFAULT", view.source());
        assertNull(view.override());
        verify(mapper, never()).insert(any(GlobalSettingRecord.class));
    }

    @Test
    void explicitFalseOverridesTrueSystemDefault() {
        when(mapper.find(USER, "u1", KEY)).thenReturn(row("mine", USER, "false", 2));
        when(mapper.find(SYSTEM, "0", KEY)).thenReturn(row("sys", SYSTEM, "true", 8));
        var view = service.readMine(KEY);
        assertFalse(view.value().booleanValue());
        assertEquals(USER, view.source());
        assertEquals("mine", view.override().id());
        assertEquals(2, view.override().version());
    }

    @Test
    void malformedPersonalValueInheritsSystemButKeepsPersonalVersionForRepair() {
        when(mapper.find(USER, "u1", KEY)).thenReturn(row("mine", USER, "broken", 2));
        when(mapper.find(SYSTEM, "0", KEY)).thenReturn(row("sys", SYSTEM, "true", 8));
        var view = service.readMine(KEY);
        assertTrue(view.value().booleanValue());
        assertEquals(SYSTEM, view.source());
        assertEquals("mine", view.override().id());
        verify(mapper, never()).updateValue(any());
    }

    @Test
    void invalidSystemValueFallsBackToProgramDefault() {
        when(mapper.find(SYSTEM, "0", KEY)).thenReturn(row("sys", SYSTEM, "null", 8));
        assertEquals("DEFAULT", service.readMine(KEY).source());
    }

    @Test
    void persistedTypeMismatchCannotOverrideTheRegisteredBusinessType() {
        var invalid = row("mine", USER, "\"false\"", 2);
        invalid.setSettingValueType("STRING");
        when(mapper.find(USER, "u1", KEY)).thenReturn(invalid);
        when(mapper.find(SYSTEM, "0", KEY)).thenReturn(row("sys", SYSTEM, "true", 8));
        var view = service.readMine(KEY);
        assertTrue(view.value().booleanValue());
        assertEquals("BOOLEAN", view.settingValueType());
        assertEquals("mine", view.override().id());
    }

    @Test
    void createUsesAuthenticatedOwnerAndStoresTextWithNameAndDetailedRemark() {
        AtomicReference<GlobalSettingRecord> stored = new AtomicReference<>();
        when(mapper.find(USER, "u1", KEY)).thenAnswer(call -> stored.get());
        when(mapper.insert(any(GlobalSettingRecord.class))).thenAnswer(call -> {
            GlobalSettingRecord row = call.getArgument(0);
            row.setId("new"); stored.set(row); return 1;
        });
        var view = service.saveMine(KEY, new GlobalSettingRequests.Save(" true ", null, null));
        assertEquals("true", stored.get().getSettingValue());
        assertEquals("u1", stored.get().getOwnerId());
        assertEquals(USER, stored.get().getScopeType());
        assertEquals("BOOLEAN", stored.get().getSettingValueType());
        assertEquals("BOOLEAN", view.settingValueType());
        assertEquals(registry.require(KEY).name(), stored.get().getName());
        assertEquals(registry.require(KEY).remark(), stored.get().getRemark());
        assertEquals("u1", stored.get().getCreatedBy());
        assertTrue(view.value().booleanValue());
        assertEquals(0, view.override().version());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "1", "\"true\"", "{}", "[]", "true false", "true junk", "", "  "})
    void refusesNonBooleanOrMalformedTextBeforeWrite(String value) {
        var error = assertThrows(GlobalSettingException.class,
                () -> service.saveMine(KEY, new GlobalSettingRequests.Save(value, null, null)));
        assertEquals(400, error.status());
        verify(mapper, never()).insert(any(GlobalSettingRecord.class));
        verify(mapper, never()).updateValue(any());
    }

    @Test
    void refusesOversizedTextAndUnknownKey() {
        assertThrows(GlobalSettingException.class, () -> service.saveMine(KEY,
                new GlobalSettingRequests.Save(" ".repeat(16384) + "true", null, null)));
        assertThrows(GlobalSettingException.class, () -> service.readMine("unknown"));
        assertThrows(GlobalSettingException.class, () -> service.readMine(KEY.toUpperCase()));
    }

    @Test
    void staleVersionAndRecreatedIdCannotOverwriteCurrentValue() {
        when(mapper.find(USER, "u1", KEY)).thenReturn(row("new-row", USER, "false", 0));
        assertEquals(409, assertThrows(GlobalSettingException.class, () -> service.saveMine(KEY,
                new GlobalSettingRequests.Save("true", "old-row", 0L))).status());
        assertEquals(409, assertThrows(GlobalSettingException.class, () -> service.saveMine(KEY,
                new GlobalSettingRequests.Save("true", "new-row", 2L))).status());
        verify(mapper, never()).updateValue(any());
    }

    @Test
    void concurrentCompareAndSetFailureReturnsConflict() {
        when(mapper.find(USER, "u1", KEY)).thenReturn(row("mine", USER, "false", 0));
        when(mapper.updateValue(any())).thenReturn(0);
        assertEquals(409, assertThrows(GlobalSettingException.class, () -> service.saveMine(KEY,
                new GlobalSettingRequests.Save("true", "mine", 0L))).status());
    }

    @Test
    void duplicateFirstInsertReturnsConflict() {
        when(mapper.insert(any(GlobalSettingRecord.class))).thenThrow(new DuplicateKeyException("concurrent create"));
        assertEquals(409, assertThrows(GlobalSettingException.class, () -> service.saveMine(KEY,
                new GlobalSettingRequests.Save("true", null, null))).status());
    }

    @Test
    void resetDeletesOnlyCurrentUsersVersionAndReturnsInheritance() {
        when(mapper.find(USER, "u1", KEY)).thenReturn(row("mine", USER, "true", 2), null);
        when(mapper.find(SYSTEM, "0", KEY)).thenReturn(row("sys", SYSTEM, "false", 0));
        when(mapper.deleteVersion(USER, "u1", KEY, "mine", 2)).thenReturn(1);
        var view = service.resetMine(KEY, new GlobalSettingRequests.Reset("mine", 2L));
        assertNull(view.override());
        assertFalse(view.value().booleanValue());
        assertEquals(SYSTEM, view.source());
        verify(mapper).deleteVersion(USER, "u1", KEY, "mine", 2);
    }

    @Test
    void anotherUsersRecordIdCannotBeUsedForReset() {
        when(mapper.find(USER, "u1", KEY)).thenReturn(row("mine", USER, "true", 0));
        assertEquals(409, assertThrows(GlobalSettingException.class, () -> service.resetMine(KEY,
                new GlobalSettingRequests.Reset("someone-else", 0L))).status());
        verify(mapper, never()).deleteVersion(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void systemOnlySettingIgnoresIllegalPersonalRowsAndRefusesPersonalWrite() {
        Definition original = registry.require(KEY);
        Definition systemOnly = new Definition(KEY, original.name(), original.remark(), original.valueType(),
                original.defaultValue(), Set.of(SYSTEM), true);
        doReturn(systemOnly).when(registry).require(KEY);
        when(mapper.find(SYSTEM, "0", KEY)).thenReturn(row("sys", SYSTEM, "true", 0));
        assertTrue(service.readMine(KEY).value().booleanValue());
        verify(mapper, never()).find(USER, "u1", KEY);
        assertEquals(403, assertThrows(GlobalSettingException.class, () -> service.saveMine(KEY,
                new GlobalSettingRequests.Save("false", null, null))).status());
    }

    @Test
    void nonPublicSettingCannotBeReadThroughPersonalEndpoint() {
        Definition original = registry.require(KEY);
        doReturn(new Definition(KEY, original.name(), original.remark(), original.valueType(),
                original.defaultValue(), Set.of(SYSTEM), false)).when(registry).require(KEY);
        assertEquals(403, assertThrows(GlobalSettingException.class, () -> service.readMine(KEY)).status());
    }

    @Test
    void disabledOrAbsentIdentityCannotReadOrWriteSettings() {
        users.selectById("u1").setStatus("1");
        assertEquals(403, assertThrows(GlobalSettingException.class, () -> service.readMine(KEY)).status());
        UserContext.clear();
        assertEquals(403, assertThrows(GlobalSettingException.class, () -> service.saveMine(KEY,
                new GlobalSettingRequests.Save("true", null, null))).status());
        verifyNoInteractions(mapper);
    }

    private GlobalSettingRecord row(String id, String scope, String value, long version) {
        GlobalSettingRecord row = new GlobalSettingRecord();
        row.setId(id); row.setScopeType(scope); row.setOwnerId(USER.equals(scope) ? "u1" : "0");
        row.setSettingKey(KEY); row.setSettingValue(value); row.setVersion(version);
        row.setSettingValueType("BOOLEAN");
        return row;
    }
}
