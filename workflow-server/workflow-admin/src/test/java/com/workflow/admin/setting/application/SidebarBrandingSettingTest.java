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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static com.workflow.admin.setting.application.GlobalSettingRegistry.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证默认标识、系统持久化、恢复默认和普通用户读取时的作用域约束。 */
class SidebarBrandingSettingTest {
    private final GlobalSettingMapper mapper = mock(GlobalSettingMapper.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final GlobalSettingRegistry registry = new GlobalSettingRegistry();
    private final GlobalSettingService service = new GlobalSettingService(mapper, users, registry);

    @BeforeEach void setup() {
        UserContext.setCurrentUser("u1", "user");
        var user = new SysUser();
        user.setId("u1"); user.setStatus("0"); user.setDeleted(0);
        when(users.selectById("u1")).thenReturn(user);
    }

    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void defaultIsListedWithoutSeedingDatabaseOrPersonalOverrides() {
        assertEquals(Set.of(SYSTEM), registry.require(SIDEBAR_BRANDING).scopes());
        var view = service.readMine(SIDEBAR_BRANDING);
        assertEquals("DEFAULT", view.source());
        assertEquals("流程配置系统", view.value().path("title").textValue());
        assertEquals("Connection", view.value().path("icon").textValue());
        assertEquals("", view.value().path("imageBase64").textValue());
        assertFalse(view.userOverridable());
        assertTrue(service.listSystem().stream().anyMatch(item -> SIDEBAR_BRANDING.equals(item.settingKey())));
        verify(mapper, never()).insert(any(GlobalSettingRecord.class));
        verify(mapper, never()).find(eq(USER), anyString(), eq(SIDEBAR_BRANDING));
    }

    @Test void savedBrandingIsSharedAndResetRestoresOriginalWithVersionProtection() {
        AtomicReference<GlobalSettingRecord> stored = new AtomicReference<>();
        when(mapper.find(SYSTEM, "0", SIDEBAR_BRANDING)).thenAnswer(call -> stored.get());
        when(mapper.insert(any(GlobalSettingRecord.class))).thenAnswer(call -> {
            GlobalSettingRecord row = call.getArgument(0);
            row.setId("brand"); stored.set(row); return 1;
        });
        when(mapper.deleteVersion(SYSTEM, "0", SIDEBAR_BRANDING, "brand", 0L))
                .thenAnswer(call -> { stored.set(null); return 1; });
        var request = new GlobalSettingRequests.Save("{\"title\":\" 项目管理 \" ,\"icon\":\"OfficeBuilding\"}", null, null);
        assertEquals(403, assertThrows(GlobalSettingException.class, () -> service.saveMine(SIDEBAR_BRANDING, request)).status());
        var saved = service.saveSystem(SIDEBAR_BRANDING, request);
        assertEquals("JSON", stored.get().getSettingValueType());
        assertEquals(SYSTEM, stored.get().getScopeType());
        assertEquals("0", stored.get().getOwnerId());
        assertEquals("项目管理", saved.value().path("title").textValue());
        assertEquals(saved.value(), service.readMine(SIDEBAR_BRANDING).value());
        assertEquals(409, assertThrows(GlobalSettingException.class, () -> service.saveSystem(SIDEBAR_BRANDING, request)).status());
        assertEquals(409, assertThrows(GlobalSettingException.class, () -> service.resetSystem(SIDEBAR_BRANDING,
                new GlobalSettingRequests.Reset("brand", 1L))).status());
        var reset = service.resetSystem(SIDEBAR_BRANDING, new GlobalSettingRequests.Reset("brand", 0L));
        assertEquals(SidebarBrandingConfiguration.defaultValue(), reset.value());
        assertEquals("DEFAULT", reset.source());
        assertNull(reset.override());
    }

    @Test void emptyIconUsesDefaultAndInvalidStoredConfigurationFallsBack() {
        var value = SidebarBrandingConfiguration.defaultValue().put("icon", " ");
        assertEquals("Connection", registry.parse(registry.require(SIDEBAR_BRANDING), value.toString()).path("icon").textValue());
        var row = new GlobalSettingRecord();
        row.setSettingValueType("JSON"); row.setSettingValue("{}");
        when(mapper.find(SYSTEM, "0", SIDEBAR_BRANDING)).thenReturn(row);
        assertEquals(SidebarBrandingConfiguration.defaultValue(), service.readMine(SIDEBAR_BRANDING).value());
        value.put("title", "长".repeat(41));
        assertThrows(GlobalSettingException.class, () -> registry.parse(registry.require(SIDEBAR_BRANDING), value.toString()));
    }

    @Test void legacyBrandingWithoutImageKeepsItsExistingTitleAndIcon() {
        var row = new GlobalSettingRecord();
        row.setSettingValueType("JSON"); row.setSettingValue("{\"title\":\"已有平台\",\"icon\":\"OfficeBuilding\"}");
        when(mapper.find(SYSTEM, "0", SIDEBAR_BRANDING)).thenReturn(row);
        var view = service.readMine(SIDEBAR_BRANDING);
        assertEquals("已有平台", view.value().path("title").textValue());
        assertEquals("OfficeBuilding", view.value().path("icon").textValue());
        assertEquals("", view.value().path("imageBase64").textValue());
        assertEquals(SYSTEM, view.source());
    }

    @Test void imageRoundTripsInsideJsonAndCanBeRemovedWithoutChangingBranding() throws Exception {
        AtomicReference<GlobalSettingRecord> stored = new AtomicReference<>();
        when(mapper.find(SYSTEM, "0", SIDEBAR_BRANDING)).thenAnswer(call -> stored.get());
        when(mapper.insert(any(GlobalSettingRecord.class))).thenAnswer(call -> {
            GlobalSettingRecord row = call.getArgument(0); row.setId("image-brand"); stored.set(row); return 1;
        });
        when(mapper.updateValue(any())).thenReturn(1);
        String image = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes("png"));
        var value = SidebarBrandingConfiguration.defaultValue().put("title", "图片平台").put("imageBase64", image);
        service.saveSystem(SIDEBAR_BRANDING, new GlobalSettingRequests.Save(value.toString(), null, null));
        assertEquals(image, service.readMine(SIDEBAR_BRANDING).value().path("imageBase64").textValue());
        assertTrue(stored.get().getSettingValue().contains("imageBase64"));
        assertEquals("JSON", stored.get().getSettingValueType());
        value.put("imageBase64", "");
        var removed = service.saveSystem(SIDEBAR_BRANDING, new GlobalSettingRequests.Save(value.toString(), "image-brand", 0L));
        assertEquals("", removed.value().path("imageBase64").textValue());
        assertEquals("图片平台", removed.value().path("title").textValue());
        assertEquals("Connection", removed.value().path("icon").textValue());
    }

    @ParameterizedTest @ValueSource(strings = {"png", "jpeg", "gif"})
    void acceptsRasterImageDataUrls(String format) throws Exception {
        String image = "data:image/" + format + ";base64," + Base64.getEncoder().encodeToString(imageBytes(format));
        var value = SidebarBrandingConfiguration.defaultValue().put("imageBase64", image);
        assertEquals(image, registry.parse(registry.require(SIDEBAR_BRANDING), value.toString()).path("imageBase64").textValue());
    }

    @Test void acceptsWebpHeaderAndAllows32KiBOnlyForBranding() throws Exception {
        String webp = "data:image/webp;base64,UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA";
        var value = SidebarBrandingConfiguration.defaultValue().put("imageBase64", webp);
        assertEquals(webp, registry.parse(registry.require(SIDEBAR_BRANDING), value.toString()).path("imageBase64").textValue());
        byte[] png = imageBytes("png");
        String image = "data:image/png;base64," + Base64.getEncoder().encodeToString(Arrays.copyOf(png, 32 * 1024));
        value.put("imageBase64", image);
        assertTrue(value.toString().length() > 16 * 1024);
        assertEquals(image, registry.parse(registry.require(SIDEBAR_BRANDING), value.toString()).path("imageBase64").textValue());
        assertThrows(GlobalSettingException.class, () -> registry.parse(ValueType.JSON, value.toString()));
        value.put("imageBase64", "data:image/png;base64," + Base64.getEncoder().encodeToString(Arrays.copyOf(png, 32 * 1024 + 1)));
        assertThrows(GlobalSettingException.class, () -> registry.parse(registry.require(SIDEBAR_BRANDING), value.toString()));
    }

    @ParameterizedTest @ValueSource(strings = {"https://example.test/logo.png", "data:image/svg+xml;base64,PHN2Zy8+",
            "data:text/html;base64,PGgxPkE8L2gxPg==", "data:image/png;base64,bm90LWFuLWltYWdl",
            "data:image/png;base64,%%%", "data:image/png;base64,iVBORw=", "data:image/png;base64,"})
    void rejectsUnsupportedOrMalformedImagesBeforePersistence(String image) {
        var value = SidebarBrandingConfiguration.defaultValue().put("imageBase64", image);
        assertThrows(GlobalSettingException.class, () -> service.saveSystem(SIDEBAR_BRANDING,
                new GlobalSettingRequests.Save(value.toString(), null, null)));
        verify(mapper, never()).insert(any(GlobalSettingRecord.class));
    }

    @Test void rejectsNonStringImageAndMismatchedMime() throws Exception {
        var value = SidebarBrandingConfiguration.defaultValue().putNull("imageBase64");
        assertThrows(GlobalSettingException.class, () -> registry.parse(registry.require(SIDEBAR_BRANDING), value.toString()));
        value.put("imageBase64", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes("png")));
        assertThrows(GlobalSettingException.class, () -> registry.parse(registry.require(SIDEBAR_BRANDING), value.toString()));
    }

    private byte[] imageBytes(String format) throws Exception {
        var output = new ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output));
        return output.toByteArray();
    }

    @ParameterizedTest @ValueSource(strings = {
            "[]", "{}", "{\"title\":\"名称\"}", "{\"title\":\" \" ,\"icon\":\"Connection\"}",
            "{\"title\":12,\"icon\":\"Connection\"}", "{\"title\":\"名称\",\"icon\":null}",
            "{\"title\":\"名称\",\"icon\":\"https://example.test/logo.svg\"}",
            "{\"title\":\"名称\",\"icon\":\"<svg/>\"}",
            "{\"title\":\"名称\",\"icon\":\"Connection\",\"css\":\"body{}\"}"})
    void refusesInvalidConfigurationBeforePersistence(String input) {
        assertThrows(GlobalSettingException.class, () -> service.saveSystem(SIDEBAR_BRANDING,
                new GlobalSettingRequests.Save(input, null, null)));
        verify(mapper, never()).insert(any(GlobalSettingRecord.class));
    }
}
