package com.workflow.migration.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.setting.application.GlobalSettingService;
import com.workflow.migration.infrastructure.persistence.mapper.*;
import com.workflow.migration.infrastructure.persistence.record.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import static com.workflow.admin.setting.application.GlobalSettingRegistry.MIGRATION_SIGNING_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实 wfpack 编解码覆盖跨密钥确认、内容完整性、确认审计及后续依赖阻断。 */
@ExtendWith(MockitoExtension.class)
class ConfigMigrationSignatureImportTest {
    private static final String SOURCE_KEY = "source-key-0123456789abcdef0123456789";
    private static final String TARGET_KEY = "target-key-0123456789abcdef0123456789";
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    @Mock ConfigMigrationAssetService assetService;
    @Mock ConfigImportPackageMapper importPackageMapper;
    @Mock ConfigImportItemMapper importItemMapper;
    @Mock ConfigEnvironmentMappingMapper environmentMappingMapper;
    @Mock SysUserMapper userMapper;
    @Spy ConfigMigrationPackageCodec packageCodec = codec(TARGET_KEY);
    @Spy ConfigMigrationPackageDocumentSupport documents = new ConfigMigrationPackageDocumentSupport(json);
    @InjectMocks ConfigMigrationPackageService service;

    @BeforeEach
    void login() { UserContext.setCurrentUser("operator-id", "operator"); }

    @AfterEach
    void logout() { UserContext.clear(); }

    @Test
    void mismatchReturnsConfirmationWithoutWritingImportOrItems() throws Exception {
        var response = service.importPackage(file(packageData(SOURCE_KEY)), "DEV", null);
        assertEquals(true, response.get("confirmationRequired"));
        assertTrue(response.get("checksum").toString().matches("[0-9a-f]{64}"));
        assertEquals("WFP-SIGNATURE-TEST", response.get("packageNo"));
        verifyNoInteractions(importPackageMapper, importItemMapper, assetService, userMapper);
    }

    @Test
    void explicitConfirmationImportsAndRecordsEvidenceButKeepsMissingUserDependency() throws Exception {
        byte[] bytes = packageData(SOURCE_KEY);
        var preview = service.importPackage(file(bytes), "DEV", null);
        var result = service.importPackage(file(bytes), "DEV", preview.get("checksum").toString());
        assertEquals("MISMATCH_CONFIRMED", result.get("signatureStatus"));
        assertEquals("operator", result.get("signatureConfirmedBy"));
        assertNotNull(result.get("signatureConfirmedAt"));
        ArgumentCaptor<ConfigImportPackage> packages = ArgumentCaptor.forClass(ConfigImportPackage.class);
        verify(importPackageMapper).insert(packages.capture());
        assertArrayEquals(bytes, packages.getValue().getPackageData());
        assertEquals(preview.get("checksum"), packages.getValue().getChecksum());
        ArgumentCaptor<ConfigImportItem> items = ArgumentCaptor.forClass(ConfigImportItem.class);
        verify(importItemMapper).insert(items.capture());
        assertEquals("UNRESOLVED", items.getValue().getMappingStatus());
        assertEquals("PENDING", items.getValue().getPublishStatus());
        assertTrue(assertThrows(IllegalStateException.class,
                () -> service.requireResolvedDependencies(List.of(items.getValue()))).getMessage().contains("USER:missing-user"));
    }

    @Test
    void matchingSignatureImportsWithoutConfirmation() throws Exception {
        var result = service.importPackage(file(packageData(TARGET_KEY)), "DEV", null);
        assertEquals("VERIFIED", result.get("signatureStatus"));
        assertNull(result.get("signatureConfirmedBy"));
        assertNull(result.get("signatureConfirmedAt"));
        verify(importPackageMapper).insert(any(ConfigImportPackage.class));
    }

    @Test
    void confirmationCannotBeAppliedToAnotherFileEvenIfNewFileSignaturePasses() throws Exception {
        String first = service.importPackage(file(packageData(SOURCE_KEY)), "DEV", null).get("checksum").toString();
        assertThrows(IllegalArgumentException.class,
                () -> service.importPackage(file(packageData(TARGET_KEY)), "DEV", first));
        verifyNoInteractions(importPackageMapper, importItemMapper);
    }

    @Test
    void changingGlobalSettingIsEffectiveForNextEncodeAndDecode() throws Exception {
        var settings = (GlobalSettingService) ReflectionTestUtils.getField(packageCodec, "globalSettings");
        byte[] oldPackage = packageData(TARGET_KEY);
        assertTrue(packageCodec.decode(oldPackage).signatureVerified());
        when(settings.readSystemValue(MIGRATION_SIGNING_KEY)).thenReturn(TextNode.valueOf(SOURCE_KEY));
        assertFalse(packageCodec.decode(oldPackage).signatureVerified());
        byte[] newPackage = packageCodec.encode("NEW", "REL-NEW", List.of(asset()), Map.of()).data();
        assertTrue(codec(SOURCE_KEY).decode(newPackage).signatureVerified());
        assertFalse(codec(TARGET_KEY).decode(newPackage).signatureVerified());
    }

    @Test
    void damagedContentIsRejectedEvenWithMatchingConfirmationChecksum() throws Exception {
        var entries = unzip(packageData(SOURCE_KEY));
        entries.put("manifest.json", "{}".getBytes(StandardCharsets.UTF_8));
        byte[] damaged = zip(entries);
        assertThrows(IllegalArgumentException.class,
                () -> service.importPackage(file(damaged), "DEV", sha(damaged)));
        verifyNoInteractions(importPackageMapper, importItemMapper);
    }

    @Test
    void incompleteChecksumManifestCannotBeConfirmed() throws Exception {
        var entries = unzip(packageData(SOURCE_KEY));
        var checksums = json.readValue(entries.get("checksums.json"), new TypeReference<Map<String, String>>() {});
        checksums.remove("manifest.json");
        entries.put("checksums.json", json.writeValueAsBytes(checksums));
        byte[] damaged = zip(entries);
        assertThrows(IllegalArgumentException.class,
                () -> service.importPackage(file(damaged), "DEV", sha(damaged)));
        verifyNoInteractions(importPackageMapper, importItemMapper);
    }

    @Test
    void unsupportedFormatIsRejectedBeforeSignatureConfirmation() throws Exception {
        var entries = unzip(packageData(SOURCE_KEY));
        var manifest = json.readValue(entries.get("manifest.json"), new TypeReference<Map<String, Object>>() {});
        manifest.put("formatVersion", 999);
        entries.put("manifest.json", json.writeValueAsBytes(manifest));
        refreshChecksums(entries);
        byte[] invalid = zip(entries);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.importPackage(file(invalid), "DEV", sha(invalid))).getMessage().contains("格式版本"));
        verifyNoInteractions(importPackageMapper, importItemMapper);
    }

    @Test
    void assetHashMismatchIsRejectedAfterFileChecksumsPass() throws Exception {
        var entries = unzip(packageData(SOURCE_KEY));
        String assetPath = entries.keySet().stream().filter(path -> path.startsWith("assets/")).findFirst().orElseThrow();
        var snapshot = json.readValue(entries.get(assetPath), new TypeReference<Map<String, Object>>() {});
        snapshot.put("assetName", "changed");
        entries.put(assetPath, json.writeValueAsBytes(snapshot));
        refreshChecksums(entries);
        byte[] invalid = zip(entries);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.importPackage(file(invalid), "DEV", sha(invalid))).getMessage().contains("资产哈希"));
        verifyNoInteractions(importPackageMapper, importItemMapper);
    }

    @Test
    void missingSignatureIsAFormatErrorNotAConfirmationBypass() throws Exception {
        var entries = unzip(packageData(SOURCE_KEY));
        entries.remove("signature.sig");
        byte[] invalid = zip(entries);
        assertThrows(IllegalArgumentException.class,
                () -> service.importPackage(file(invalid), "DEV", sha(invalid)));
        verifyNoInteractions(importPackageMapper, importItemMapper);
    }

    private ConfigMigrationPackageCodec codec(String key) {
        var settings = mock(GlobalSettingService.class);
        lenient().when(settings.readSystemValue(MIGRATION_SIGNING_KEY)).thenReturn(TextNode.valueOf(key));
        var codec = new ConfigMigrationPackageCodec(json, settings);
        ReflectionTestUtils.setField(codec, "environmentName", "DEV");
        return codec;
    }

    private byte[] packageData(String key) throws Exception {
        return codec(key).encode("WFP-SIGNATURE-TEST", "REL-TEST", List.of(asset()), Map.of()).data();
    }

    private ConfigMigrationAsset asset() throws Exception {
        var asset = new ConfigMigrationAsset();
        asset.setId("asset-1"); asset.setAssetType("ENTITY"); asset.setBusinessKey("test_entity");
        asset.setAssetName("测试实体"); asset.setSourceVersion(1);
        asset.setSnapshotJson(json.writeValueAsString(Map.of("schemaVersion", 1, "assetType", "ENTITY",
                "businessKey", "test_entity", "definition", Map.of("entityCode", "test_entity"),
                "dependencies", List.of(Map.of("type", "USER", "key", "missing-user", "required", true, "targetOnly", true)))));
        return asset;
    }

    private MockMultipartFile file(byte[] data) {
        return new MockMultipartFile("file", "test.wfpack", "application/octet-stream", data);
    }

    private Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        var entries = new LinkedHashMap<String, byte[]>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null;) entries.put(entry.getName(), input.readAllBytes());
        }
        return entries;
    }

    private byte[] zip(Map<String, byte[]> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey())); output.write(entry.getValue()); output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private void refreshChecksums(Map<String, byte[]> entries) throws Exception {
        Map<String, String> checksums = new LinkedHashMap<>();
        for (var entry : entries.entrySet()) {
            if (!Set.of("signature.sig", "checksums.json").contains(entry.getKey())) checksums.put(entry.getKey(), sha(entry.getValue()));
        }
        entries.put("checksums.json", json.writeValueAsBytes(checksums));
    }

    private String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
